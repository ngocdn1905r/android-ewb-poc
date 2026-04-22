package com.example.androidpoc

import android.content.Context
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.example.androidpoc.ResponseMapper.safeApiCall
import io.ktor.client.call.body
import io.ktor.client.request.get
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

    fun callHello() = safeApiCall {
        client.get(UserService.HELLO) {}.body<HelloResponseModel>()
    }.flowOn(ioDispatcher)

}

private object UserService {
    const val HELLO = "/api/hello"
}