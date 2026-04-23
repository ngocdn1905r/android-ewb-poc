package com.example.androidpoc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HelloResponseModel(
    val message: String? = null,
    val subject: String? = null,
    val claims: ClaimModel? = null,
)

@Serializable
data class ClaimModel(
    @SerialName("email_verified")
    val emailVerified: Boolean? = null,
    @SerialName("preferred_username")
    val userName: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerialName("realm_access")
    val access: RealmAccess

)