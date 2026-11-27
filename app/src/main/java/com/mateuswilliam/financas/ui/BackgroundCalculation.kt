package com.mateuswilliam.financas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun <T> calculateInBackground(inputs: List<Any?>, calculate: () -> T): State<Result<T>?> = key(inputs) {
    produceState<Result<T>?>(null) {
        value = withContext(Dispatchers.Default) {
            try {
                Result.success(calculate())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
}
