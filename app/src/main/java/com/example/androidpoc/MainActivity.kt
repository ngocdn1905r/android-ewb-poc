package com.example.androidpoc

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidpoc.ui.theme.AndroidPOCTheme
import dagger.hilt.android.AndroidEntryPoint
import net.openid.appauth.AuthorizationResponse


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val context = LocalContext.current

            val authLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "result $result")
                val intent = result.data
                if (null == intent) {
                    Log.e(TAG, "intent is null")
                } else {
                    val response = AuthorizationResponse.fromIntent(intent)
                    Log.e("HOME", "response $response")
                    viewModel.onEvent(MainEvent.ExchangeToken(context, response))
                }
            }

            val endSessionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                Log.d(TAG, "result $result")
                viewModel.onEvent(MainEvent.InformLogoutSuccess)
            }

            viewModel.effect.observeAsEvent { effect ->
                when (effect) {
                    is MainEffect.LaunchLogin -> {
                        authLauncher.launch(effect.intent)
                    }

                    is MainEffect.LoginSuccess -> {

                    }

                    is MainEffect.LaunchLogout -> {
                        endSessionLauncher.launch(effect.intent)
                    }

                    else -> Unit
                }
            }

            AndroidPOCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Content(
                        innerPadding = innerPadding,
                        userToken = uiState.userToken.orEmpty(),
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
    }
}


@Composable
private fun Content(
    innerPadding: PaddingValues,
    userToken: String = "",
    onEvent: (MainEvent) -> Unit = {}
) {
    val context = LocalContext.current
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
        Button(onClick = {
            onEvent.invoke(MainEvent.RequestLogin(context))
        }) {
            Text(
                text = "Login"
            )
        }
        Spacer(modifier = Modifier.height(40.dp))

        Button(onClick = {
            onEvent.invoke(MainEvent.RefreshToken(context))
        }) {
            Text(
                text = "Refresh Token"
            )
        }
        Spacer(modifier = Modifier.height(40.dp))

        Button(onClick = {
            onEvent.invoke(MainEvent.RequestHello)
        }) {
            Text(
                text = "Hello World"
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = {
            onEvent.invoke(MainEvent.RequestLogout(context))
        }) {
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