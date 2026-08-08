package com.ua.astrumon.common.extension

import com.ua.astrumon.common.exception.NotFoundException
import com.ua.astrumon.common.result.ResultContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ResultExtensionsTest {
    private val firstError = NotFoundException("first")
    private val secondError = NotFoundException("second")

    // --- orFailure ---

    @Test
    fun `should_narrowToNonNull_when_successCarriesValue`() {
        val result: ResultContainer<String?> = ResultContainer.success("alice")

        assertEquals("alice", result.orFailure { firstError }.getOrNull())
    }

    @Test
    fun `should_returnProvidedException_when_successCarriesNull`() {
        val result: ResultContainer<String?> = ResultContainer.success(null)

        assertSame(firstError, result.orFailure { firstError }.exceptionOrNull())
    }

    @Test
    fun `should_notInvokeProvider_when_successCarriesValue`() {
        var invoked = false
        val result: ResultContainer<String?> = ResultContainer.success("alice")

        result.orFailure {
            invoked = true
            firstError
        }

        assertFalse(invoked)
    }

    @Test
    fun `should_keepOriginalException_when_receiverAlreadyFailed`() {
        val result: ResultContainer<String?> = ResultContainer.failure(firstError)

        assertSame(firstError, result.orFailure { secondError }.exceptionOrNull())
    }
}
