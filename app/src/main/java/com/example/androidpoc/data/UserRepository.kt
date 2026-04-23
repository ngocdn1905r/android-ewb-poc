package com.example.androidpoc.data

import android.content.Context
import androidx.datastore.dataStore
import com.example.androidpoc.di.AppDispatchers
import com.example.androidpoc.di.Dispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.example.androidpoc.ext.ResponseMapper.safeApiCall
import com.example.androidpoc.ext.KEYCLOAK_PORT
import com.example.androidpoc.ext.LOCAL_ADDRESS
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.flow.flowOn

private val Context.dataStore by dataStore(
    fileName = "user-preferences",
    serializer = UserPreferencesSerializer
)

class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val client: HttpClient,
) {

    val userToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences.token }

    val idToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences.idToken }

    val refreshToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences.refreshToken }

    suspend fun updateToken(token: String?, idToken: String?, refreshToken: String?) {
        context.dataStore.updateData { preferences ->
            preferences.copy(
                token = token,
                idToken = idToken,
                refreshToken = refreshToken
            )
        }
    }

    suspend fun clear() {
        context.dataStore.updateData { preferences ->
            preferences.copy(
                token = null,
                idToken = null,
                refreshToken = null
            )
        }
    }

    fun callHello(token: String) = safeApiCall {
        client.get(UserService.HELLO) {
            header("Authorization", "Bearer $token")
        }.body<HelloResponseModel>()
    }.flowOn(ioDispatcher)

    fun getUserInfo(token: String) = safeApiCall {
        client.get("${LOCAL_ADDRESS}:${KEYCLOAK_PORT}/realms/poc-realm/protocol/openid-connect/userinfo") {
            header("Authorization", "Bearer $token")
        }.body<UserInfoResponse>()
    }.flowOn(ioDispatcher)

}

private object UserService {
    const val HELLO = "/api/hello"
}