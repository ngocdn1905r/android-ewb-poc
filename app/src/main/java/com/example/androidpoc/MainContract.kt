package com.example.androidpoc

import android.content.Context
import android.content.Intent
import net.openid.appauth.AuthorizationResponse


sealed interface MainEvent {

    data class RequestLogin(val context: Context) : MainEvent

    data object RequestHello : MainEvent

    data class RequestLogout(val context: Context) : MainEvent

    data class ExchangeToken(val context: Context, val authResponse: AuthorizationResponse?) :
        MainEvent

    data object InformLogoutSuccess : MainEvent

    data class RefreshToken(val context: Context) : MainEvent
}


sealed interface MainEffect {

    data class LaunchLogin(val intent: Intent) : MainEffect
    data object LoginSuccess : MainEffect

    data object LoginFailure : MainEffect

    data class LaunchLogout(val intent: Intent) : MainEffect

    data object LogoutSuccess : MainEffect

    data object RefreshTokenFailure : MainEffect
}