package com.ua.astrumon.common.extension

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.result.ResultContainer

/**
 * Lifts a nullable payload into a failure, narrowing `ResultContainer<T?>` to `ResultContainer<T>`.
 *
 * The repository layer is allowed to return `ResultContainer<Member?>`; turning the absent case into
 * a [BaseException] is the service layer's job, and this is that conversion in one place.
 *
 * Because [ResultContainer] is covariant, a `ResultContainer<Member>` also matches the
 * `ResultContainer<T?>` receiver — calling this on an already-narrowed result compiles into a silent
 * no-op that never reaches [errorProvider]. Kotlin cannot demand a nullable type argument, so apply
 * this directly to the repository call, never to something a previous `orFailure` already narrowed.
 */
inline fun <T> ResultContainer<T?>.orFailure(errorProvider: () -> BaseException): ResultContainer<T> = flatMap { value ->
    if (value != null) {
        ResultContainer.success(value)
    } else {
        ResultContainer.failure(errorProvider())
    }
}

/**
 * Folds a list of results into a result of list, failing with the first [BaseException] encountered.
 * The alternative — `catching { map { it.getOrThrow() } }` — uses exceptions as control flow.
 */
fun <T> List<ResultContainer<T>>.collectAll(): ResultContainer<List<T>> {
    val successes = ArrayList<T>(size)

    for (result in this) {
        when (result) {
            is ResultContainer.Success -> successes.add(result.data)
            is ResultContainer.Failure -> return result
        }
    }

    return ResultContainer.success(successes)
}
