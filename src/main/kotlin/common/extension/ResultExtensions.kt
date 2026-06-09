package com.ua.astrumon.common.extension

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer

inline fun <T> ResultContainer<T>.validate(
    predicate: (T) -> Boolean,
    errorProvider: () -> BaseException,
): ResultContainer<T> =
    flatMap { value ->
        if (predicate(value)) {
            ResultContainer.success(value)
        } else {
            ResultContainer.failure(errorProvider())
        }
    }

inline fun <T> ResultContainer<T>.validateNotNull(errorProvider: () -> BaseException): ResultContainer<T> =
    flatMap { value ->
        if (value != null) {
            ResultContainer.success(value)
        } else {
            ResultContainer.failure(errorProvider())
        }
    }

suspend fun <T> List<ResultContainer<T>>.collectAll(): ResultContainer<List<T>> {
    val successes = mutableListOf<T>()
    val failures = mutableListOf<BaseException>()

    for (result in this) {
        when (result) {
            is ResultContainer.Success -> successes.add(result.data)
            is ResultContainer.Failure -> failures.add(result.exception)
        }
    }

    return if (failures.isEmpty()) {
        ResultContainer.success(successes)
    } else {
        ResultContainer.failure(failures.first())
    }
}

suspend fun <T> List<ResultContainer<T>>.collectFirstSuccess(): ResultContainer<T> {
    for (result in this) {
        if (result is ResultContainer.Success) return result
    }
    return ResultContainer.failure(
        DatabaseException("All operations failed"),
    )
}
