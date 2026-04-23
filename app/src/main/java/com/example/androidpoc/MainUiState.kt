package com.example.androidpoc

data class MainUiState(
    val userToken: String? = null,
    val idToken: String? = null,
    val userType: String? = null,
    val screenType: ScreenType = ScreenType.LOGIN,
    val response: String? = null
)

enum class ScreenType {
    LOGIN,
    WELCOME
}