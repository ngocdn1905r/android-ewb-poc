package com.example.androidpoc

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.androidpoc.ui.theme.AndroidPOCTheme
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val BASE_URL =
            "http://10.10.11.0:8080/realms/poc-realm/protocol/openid-connect"

        private const val REGISTRATION_URL =
            "http://10.10.11.0:8080/realms/poc-realm/clients-registrations/openid-connect"
        private const val CLIENT_ID = "mobile-poc"
        private const val REDIRECT_URI = "com.example.androidpoc.auth://callback"

        private const val END_SESSION_URI = "com.example.androidpoc.auth://logout"
        private const val SCOPES = "openid profile"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authService = getAuthService()

        enableEdgeToEdge()
        setContent {
            var userToken by remember { mutableStateOf("") }
            var idToken by remember { mutableStateOf("") }

            val authLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "result $result")
                val intent = result.data
                if (null == intent) {
                    Log.e(TAG, "intent is null")
                } else {
                    val response = AuthorizationResponse.fromIntent(intent)
                    response?.let { authResponse ->
                        val tokenExchangeRequest = authResponse.createTokenExchangeRequest()

                        authService.performTokenRequest(tokenExchangeRequest) { tokenResponse, exception ->
                            if (tokenResponse != null) {
                                val accessToken = tokenResponse.accessToken
                                Log.d(TAG, "token = $accessToken")


                                userToken = accessToken.orEmpty()
                                idToken = tokenResponse.idToken.orEmpty()
                            } else {
                                //TODO: Handle error (exception will contain details)
                            }
                        }
                    }
                }
            }


            val endSessionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "result $result")
            }

            AndroidPOCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Content(
                        innerPadding = innerPadding,
                        userToken = userToken,
                        onLogin = {
                            val authRequest = getAuthRequest()
                            val customTabsIntent = CustomTabsIntent.Builder().build()

                            val authIntent = authService.getAuthorizationRequestIntent(
                                authRequest,
                                customTabsIntent
                            )
                            authLauncher.launch(authIntent)
                        },
                        onLogout = {
                            Log.e("HOME", "id token $idToken")
                            val endSessionRequest = getEndSessionRequest(idToken)
                            val logoutIntent =
                                authService.getEndSessionRequestIntent(endSessionRequest)

                            endSessionLauncher.launch(logoutIntent)
                        }
                    )
                }
            }
        }
    }

    private fun getAuthService(): AuthorizationService {
        val appAuthConfig = AppAuthConfiguration
            .Builder()
            .setConnectionBuilder { uri ->
                URL(uri.toString()).openConnection() as HttpURLConnection
            }
            .setSkipIssuerHttpsCheck(true)
            .build()

        return AuthorizationService(this@MainActivity, appAuthConfig)
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
            "$BASE_URL/auth".toUri(),
            "$BASE_URL/token".toUri(),
            REGISTRATION_URL.toUri(),
            "$BASE_URL/logout".toUri(),
        )
    }
}


@Composable
private fun Content(
    innerPadding: PaddingValues,
    userToken: String = "",
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier,
            text = "USER TOKEN:",
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            text = userToken,
            color = Color.Blue,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onLogin) {
            Text(
                text = "Login"
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onLogout) {
            Text(
                text = "Logout"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingPreview() {
    AndroidPOCTheme {
    }
}