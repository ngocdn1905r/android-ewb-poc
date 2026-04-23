package com.example.androidpoc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfoResponse(
    @SerialName("realm_access")
    val access: RealmAccess
)


@Serializable
data class RealmAccess(
    val roles: List<String>? = null
)