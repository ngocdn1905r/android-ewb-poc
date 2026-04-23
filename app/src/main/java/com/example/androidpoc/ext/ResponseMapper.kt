package com.example.androidpoc.ext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

object ResponseMapper {

    fun <T : Any?> safeApiCall(call: suspend () -> T): Flow<T> = flow {
        runCatching { call() }
            .onSuccess { result ->
                emit((result ?: Unit) as T)
            }
            .onFailure { exception -> throw exception }
    }

    fun <T> Flow<T>.asResult(): Flow<Result<T>> {
        return this
            .map<T, Result<T>> { Result.success(it) }
            .catch { e -> emit(Result.failure<T>(e)) }
    }
}