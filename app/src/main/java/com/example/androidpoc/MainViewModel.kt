package com.example.androidpoc

import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidpoc.ResponseMapper.asResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
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
        private const val BASE_URL =
            "http://192.168.1.143:8080/realms/poc-realm/protocol/openid-connect"

        private const val REGISTRATION_URL =
            "http://192.168.1.143:8080/realms/poc-realm/clients-registrations/openid-connect"
        private const val CLIENT_ID = "mobile-poc"
        private const val REDIRECT_URI = "com.example.androidpoc.auth://callback"

        private const val END_SESSION_URI = "com.example.androidpoc.auth://logout"
        private const val SCOPES = "openid profile offline_access"
    }

    private val _uiState: MutableStateFlow<MainUiState> = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> get() = _uiState

    private val _effect = MutableSharedFlow<MainEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effect = _effect.asSharedFlow()

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

        viewModelScope.launch {
            val token = userRepository.userToken.firstOrNull()
            Log.e("HOME", "USER TOKEN = $token")
        }

//        _uiState.value.idToken?.let { idToken ->
//            val authService = getAuthService(context)
//            val endSessionRequest = getEndSessionRequest(idToken)
//            val logoutIntent =
//                authService.getEndSessionRequestIntent(endSessionRequest)
//            launchEffect(MainEffect.LaunchLogout(logoutIntent))
//        }
    }

    private fun exchangeToken(context: Context, authResponse: AuthorizationResponse?) {
        if (null == authResponse) {
            launchEffect(MainEffect.LoginFailure)
        } else {
            val tokenExchangeRequest = authResponse.createTokenExchangeRequest()
            Log.e("HOME", "tokenExchangeRequest $authResponse")

            val authService = getAuthService(context)

            authService.performTokenRequest(tokenExchangeRequest) { tokenResponse, exception ->
                Log.e("HOME", "token response ${tokenResponse} - $exception")
                if (tokenResponse != null) {
                    Log.d("MAIN", "token = ${tokenResponse.accessToken}")

                    _uiState.value = _uiState.value.copy(
                        userToken = tokenResponse.accessToken,
                        idToken = tokenResponse.idToken
                    )

                    saveToLocalStorage(
                        tokenResponse.accessToken,
                        tokenResponse.idToken,
                        tokenResponse.refreshToken
                    )

                } else {
                    launchEffect(MainEffect.LoginFailure)
                }
            }
        }
    }

    private fun refreshToken(context: Context) {
        viewModelScope.launch {
            userRepository.refreshToken.firstOrNull()?.let { refreshToken ->
                val serviceConfig = getServiceConfig()
                val tokenRequest = TokenRequest.Builder(serviceConfig, CLIENT_ID)
                    .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                    .setRefreshToken(refreshToken)
                    .build()
                val authService = getAuthService(context)

                authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                    Log.e("HOME", "token response ${tokenResponse} - $exception")
                    if (tokenResponse != null) {
                        Log.d("MAIN", "refresh token = ${tokenResponse.refreshToken}")

                        _uiState.value = _uiState.value.copy(
                            userToken = tokenResponse.accessToken,
                            idToken = tokenResponse.idToken
                        )
                        saveToLocalStorage(
                            tokenResponse.accessToken,
                            tokenResponse.idToken,
                            tokenResponse.refreshToken
                        )
                    } else if (null != exception) {
                        launchEffect(MainEffect.RefreshTokenFailure)
                    }
                }
            }
        }
    }

    private fun logoutSuccess() {
        _uiState.value = _uiState.value.copy(
            userToken = null,
            idToken = null
        )
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
        userRepository.callHello().asResult()
            .onEach { result ->
                result.onSuccess {
                    Log.e("HOME", "response success")
                }
                result.onFailure {
                    Log.e("HOME", "exception $it")
                }
            }
            .launchIn(viewModelScope)
    }
}