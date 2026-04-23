package com.example.androidpoc

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidpoc.ext.observeAsEvent
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

                    is MainEffect.LaunchLogout -> {
                        endSessionLauncher.launch(effect.intent)
                    }

                    is MainEffect.LoginSuccess -> {
                        Toast.makeText(
                            this,
                            "Login successfully",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is MainEffect.LoginFailure -> {
                        Toast.makeText(
                            this,
                            "Unable to login",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is MainEffect.RefreshTokenFailure -> {
                        Toast.makeText(
                            this,
                            "Unable to refresh token",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is MainEffect.LogoutSuccess -> {
                        Toast.makeText(
                            this,
                            "User logged out",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> Unit
                }
            }

            AndroidPOCTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                ) { innerPadding ->
                    when (uiState.screenType) {
                        ScreenType.LOGIN -> LoginContent(
                            innerPadding = innerPadding,
                            onEvent = viewModel::onEvent
                        )

                        ScreenType.WELCOME -> Content(
                            innerPadding = innerPadding,
                            userToken = uiState.userToken.orEmpty(),
                            userType = uiState.userType.orEmpty(),
                            response = uiState.response.orEmpty(),
                            onEvent = viewModel::onEvent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    innerPadding: PaddingValues,
    onEvent: (MainEvent) -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .background(Color.White)
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            modifier = Modifier.size(180.dp),
            contentDescription = "logo",
            painter = painterResource(R.drawable.ic_keycloak)
        )
        Spacer(modifier = Modifier.height(150.dp))
        Button(
            modifier = Modifier,
            onClick = {
                onEvent.invoke(MainEvent.RequestLogin(context))
            }) {
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = "Login"
            )
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}


@Composable
private fun Content(
    innerPadding: PaddingValues,
    userToken: String = "",
    userType: String = "",
    response: String = "",
    onEvent: (MainEvent) -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color(0xffeaf1fb), shape = RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Text(
                modifier = Modifier,
                text = "User token:",
                fontWeight = FontWeight.Bold
            )
            Text(
                modifier = Modifier
                    .height(80.dp)
                    .verticalScroll(rememberScrollState()),
                text = userToken,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color(0xffeaf1fb), shape = RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Text(
                modifier = Modifier,
                text = "User type: $userType",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            onEvent.invoke(MainEvent.RequestHello)
        }) {
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = "Call HELLO"
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color(0xffeaf1fb), shape = RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Text(
                modifier = Modifier,
                text = "Response:",
                fontWeight = FontWeight.Bold
            )
            Text(
                modifier = Modifier
                    .heightIn(min = 100.dp)
                    .verticalScroll(rememberScrollState()),
                text = response,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = {
            onEvent.invoke(MainEvent.RequestLogout(context))
        }) {
            Text(
                text = "Logout"
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingPreview() {
    AndroidPOCTheme {
    }
}