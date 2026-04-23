package com.example.androidpoc

import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.android.jwt.JWT
import com.example.androidpoc.ext.ResponseMapper.asResult
import com.example.androidpoc.ext.KEYCLOAK_PORT
import com.example.androidpoc.ext.LOCAL_ADDRESS
import com.example.androidpoc.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val BASE_URL =
            "${LOCAL_ADDRESS}:${KEYCLOAK_PORT}/realms/poc-realm/protocol/openid-connect"

        private const val REGISTRATION_URL =
            "${LOCAL_ADDRESS}:${KEYCLOAK_PORT}/realms/poc-realm/clients-registrations/openid-connect"
        private const val CLIENT_ID = "mobile-poc"
        private const val REDIRECT_URI = "com.example.androidpoc.auth://callback"

        private const val END_SESSION_URI = "com.example.androidpoc.auth://logout"
        private const val SCOPES = "openid profile roles offline_access"
    }

    private val _uiState: MutableStateFlow<MainUiState> = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> get() = _uiState

    private val _effect = MutableSharedFlow<MainEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effect = _effect.asSharedFlow()

    private var _retryCount = 0

    init {
        checkUserState()
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.RequestLogin -> requestLogin(event.context)

            is MainEvent.RequestLogout -> requestLogout(event.context)

            is MainEvent.ExchangeToken -> exchangeToken(event.context, event.authResponse)

            MainEvent.InformLogoutSuccess -> logoutSuccess()

            is MainEvent.RefreshToken -> refreshToken(event.context)

            MainEvent.RequestHello -> callHello()

            else -> Unit
        }

    }

    private fun requestLogin(context: Context) {
        val authService = getAuthService(context)
        val authRequest = getAuthRequest()
        val customTabsIntent = CustomTabsIntent.Builder().build()

        val authIntent = authService.getAuthorizationRequestIntent(
            authRequest,
            customTabsIntent
        )
        launchEffect(MainEffect.LaunchLogin(authIntent))
    }

    private fun requestLogout(context: Context) {
        _uiState.value.idToken?.let { idToken ->
            val authService = getAuthService(context)
            val endSessionRequest = getEndSessionRequest(idToken)
            val logoutIntent =
                authService.getEndSessionRequestIntent(endSessionRequest)
            launchEffect(MainEffect.LaunchLogout(logoutIntent))
        }
    }

    private fun exchangeToken(context: Context, authResponse: AuthorizationResponse?) {
        if (null == authResponse) {
            launchEffect(MainEffect.LoginFailure)
        } else {
            val tokenExchangeRequest = authResponse.createTokenExchangeRequest()
            Log.d(TAG, "tokenExchangeRequest $authResponse")

            val authService = getAuthService(context)

            authService.performTokenRequest(tokenExchangeRequest) { tokenResponse, exception ->
                Log.d(TAG, "token response $tokenResponse - $exception")
                if (tokenResponse != null) {
                    Log.d(TAG, "token = ${tokenResponse.accessToken}")

                    _uiState.value = _uiState.value.copy(
                        userToken = tokenResponse.accessToken,
                        idToken = tokenResponse.idToken,
                        screenType = ScreenType.WELCOME
                    )

                    tokenResponse.idToken?.let { idToken ->
                        val role = JWT(idToken).getClaim("roles").asList(String::class.java)
                        val customRoles =
                            JWT(idToken).getClaim("custom_roles").asList(String::class.java)
                        Log.d(TAG, "user roles => $role - $customRoles")
                    }

                    tokenResponse.accessToken?.let { idToken ->
                        val role = JWT(idToken).getClaim("roles").asList(String::class.java)
                        val customRoles =
                            JWT(idToken).getClaim("custom_roles").asList(String::class.java)
                        Log.e(TAG, "user roles => $role - $customRoles")
                    }

                    getUserInfo()

                    saveToLocalStorage(
                        tokenResponse.accessToken,
                        tokenResponse.idToken,
                        tokenResponse.refreshToken
                    )

                    launchEffect(MainEffect.LoginSuccess)

                } else {
                    launchEffect(MainEffect.LoginFailure)
                }
            }
        }
    }

    private fun refreshToken(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            userRepository.refreshToken.firstOrNull()?.let { refreshToken ->
                val serviceConfig = getServiceConfig()
                val tokenRequest = TokenRequest.Builder(serviceConfig, CLIENT_ID)
                    .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                    .setRefreshToken(refreshToken)
                    .build()
                val authService = getAuthService(context)

                authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                    Log.d(TAG, "token response ${tokenResponse} - $exception")
                    if (tokenResponse != null) {
                        Log.d(TAG, "refresh token = ${tokenResponse.refreshToken}")

                        _uiState.value = _uiState.value.copy(
                            userToken = tokenResponse.accessToken,
                            idToken = tokenResponse.idToken
                        )
                        saveToLocalStorage(
                            tokenResponse.accessToken,
                            tokenResponse.idToken,
                            tokenResponse.refreshToken
                        )
                        onComplete.invoke()
                    } else if (null != exception) {
                        launchEffect(MainEffect.RefreshTokenFailure)
                        clearLocalStorage()
                        viewModelScope.launch {
                            delay(2000)
                            launchEffect(MainEffect.LogoutSuccess)
                        }
                    }
                }
            }
        }
    }

    private fun logoutSuccess() {
        clearLocalStorage()
        _uiState.value = _uiState.value.copy(
            userToken = null,
            idToken = null,
            userType = null,
            screenType = ScreenType.LOGIN
        )
        launchEffect(MainEffect.LogoutSuccess)
    }

    private fun getAuthService(context: Context): AuthorizationService {
        val appAuthConfig = AppAuthConfiguration
            .Builder()
            .setConnectionBuilder { uri ->
                URL(uri.toString()).openConnection() as HttpURLConnection
            }
            .setSkipIssuerHttpsCheck(true)
            .build()

        return AuthorizationService(context, appAuthConfig)
    }

    private fun getAuthRequest(): AuthorizationRequest {
        val serviceConfig = getServiceConfig()

        return AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            REDIRECT_URI.toUri(),
        ).setScope(SCOPES).build()
    }

    private fun getEndSessionRequest(idToken: String): EndSessionRequest {
        val serviceConfig = getServiceConfig()
        return EndSessionRequest.Builder(serviceConfig)
            .setIdTokenHint(idToken)
            .setPostLogoutRedirectUri(END_SESSION_URI.toUri())
            .build()
    }

    private fun getServiceConfig(): AuthorizationServiceConfiguration {
        return AuthorizationServiceConfiguration(
            "${BASE_URL}/auth".toUri(),
            "${BASE_URL}/token".toUri(),
            REGISTRATION_URL.toUri(),
            "${BASE_URL}/logout".toUri(),
        )
    }

    private fun launchEffect(effect: MainEffect) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }

    private fun saveToLocalStorage(token: String?, idToken: String?, refreshToken: String?) {
        viewModelScope.launch {
            userRepository.updateToken(token, idToken, refreshToken)
        }
    }

    private fun callHello() {
        uiState.value.userToken?.let { token ->
            userRepository.callHello(token).asResult()
                .onEach { result ->
                    result.onSuccess { data ->
                        Log.d(TAG, "response success $data")
                        _uiState.value = _uiState.value.copy(
                            response = data.toString()
                        )
                    }
                    result.onFailure { exception ->
                        Log.e(TAG, "exception $exception")
                        if (exception is ClientRequestException) {
                            if (exception.response.status.value == 401) {
                                refreshToken(context, onComplete = {
                                    _retryCount++
                                    if (_retryCount > 1) {
                                        launchEffect(MainEffect.ForceLogout)
                                    } else {
                                        Log.e(TAG, "retry call hello")
                                        callHello()
                                    }
                                })
                            } else {
                                launchEffect(MainEffect.ForceLogout)
                            }
                        } else {
                            launchEffect(MainEffect.ForceLogout)
                        }
                    }
                }.launchIn(viewModelScope)
        }

    }

    private fun getUserInfo() {
        uiState.value.userToken?.let { token ->
            userRepository.getUserInfo(token).asResult()
                .onEach { result ->
                    result.onSuccess { data ->
                        Log.d(TAG, "response success $result")
                        val userType = if (data.access.roles?.contains("mobile-user") == true) {
                            "PREMIUM"
                        } else {
                            "FREE"
                        }
                        _uiState.value = _uiState.value.copy(
                            userType = userType
                        )

                    }
                    result.onFailure { exception ->
                        Log.e(TAG, "exception $exception")
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun checkUserState() {
        viewModelScope.launch {
            val userToken = userRepository.userToken.firstOrNull()
            val idToken = userRepository.idToken.firstOrNull()
            if (null == userToken) {
                _uiState.value = _uiState.value.copy(
                    screenType = ScreenType.LOGIN,
                    userToken = null,
                    idToken = null,
                    userType = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    screenType = ScreenType.WELCOME,
                    userToken = userToken,
                    idToken = idToken,
                )
                getUserInfo()
            }
        }
    }

    private fun clearLocalStorage() {
        viewModelScope.launch {
            userRepository.clear()
        }
    }
}