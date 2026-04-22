package com.example.androidpoc

import android.content.Context
import android.util.Log
import com.chuckerteam.chucker.api.ChuckerInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

const val DEFAULT_KTOR_CLIENT = "DEFAULT_KTOR_CLIENT"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * A plain network client with only the most basic functionalities.
     * It does not intercept the requests.
     */
    @Singleton
    @Provides
    fun provideDefaultHttpClient(
        @ApplicationContext context: Context,
    ) = HttpClient(OkHttp) {
        expectSuccess = true
        configureEngine(context, true)
        configureLogging(enableHttpLogging = true)
        configureContentNegotiation()
        configureDefaultRequest("http://192.168.1.143:8081")
    }

    fun HttpClientConfig<OkHttpConfig>.configureEngine(
        context: Context,
        enableHttpInspector: Boolean,
    ) {
        engine {
            config {
                followRedirects(true)
                dispatcher(
                    Dispatcher().apply {
                        maxRequests = 100
                        maxRequestsPerHost = 10
                    }
                )
                hostnameVerifier { _, _ -> true }
                readTimeout(0, TimeUnit.SECONDS)
                connectTimeout(30L, TimeUnit.SECONDS)
            }

            if (enableHttpInspector) {
                addInterceptor(ChuckerInterceptor(context))
            }
        }
    }

    fun HttpClientConfig<OkHttpConfig>.configureLogging(enableHttpLogging: Boolean) {
        if (enableHttpLogging.not()) return

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.i("Network", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    fun HttpClientConfig<OkHttpConfig>.configureContentNegotiation() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    isLenient = true
                    encodeDefaults = true
                }
            )
        }
    }

    private fun HttpClientConfig<OkHttpConfig>.configureDefaultRequest(url: String) {
        install(DefaultRequest) {
            url(url)
            headers {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }.build()
        }
    }
}