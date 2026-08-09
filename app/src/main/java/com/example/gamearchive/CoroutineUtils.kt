package com.example.gamearchive

import kotlinx.coroutines.CancellationException

/** Equivalent to runCatching, except structured-concurrency cancellation is never converted to data. */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
