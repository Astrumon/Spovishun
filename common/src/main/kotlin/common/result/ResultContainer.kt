package com.ua.astrumon.common.result

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.DatabaseException
import kotlin.coroutines.cancellation.CancellationException

sealed class ResultContainer<out T> {
    data class Success<T>(
        val data: T,
    ) : ResultContainer<T>()

    data class Failure(
        val exception: BaseException,
    ) : ResultContainer<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun exceptionOrNull(): BaseException? = when (this) {
        is Success -> null
        is Failure -> exception
    }

    inline fun onSuccess(action: (T) -> Unit): ResultContainer<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (BaseException) -> Unit): ResultContainer<T> {
        if (this is Failure) action(exception)
        return this
    }

    inline fun <R> map(transform: (T) -> R): ResultContainer<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> ResultContainer<R>): ResultContainer<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (BaseException) -> R,
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(exception)
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw exception
    }

    companion object {
        fun <T> success(value: T): ResultContainer<T> = Success(value)

        fun failure(exception: BaseException): ResultContainer<Nothing> = Failure(exception)

        /**
         * Runs [block], turning a throw into [Failure].
         *
         * [CancellationException] is deliberately re-thrown rather than captured (spovishun-173).
         * It is an `Exception`, so a bare `catch (e: Exception)` swallows it: a coroutine cancelled
         * mid-call would stop unwinding and hand its caller a fabricated `Failure` instead. Since
         * `safeDbQuery` is the single entry point for every database call, that turned every
         * shutdown-time query into a spurious error line.
         */
        inline fun <T> catching(block: () -> T): ResultContainer<T> = try {
            success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: BaseException) {
            failure(e)
        } catch (e: Exception) {
            failure(DatabaseException("Unexpected error", e))
        }
    }
}
