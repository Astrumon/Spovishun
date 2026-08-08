package com.ua.astrumon.common.result

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.NotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * The exception-classification contract of [ResultContainer.catching] (spovishun-173).
 *
 * `safeDbQuery` is the single entry point for every database call and delegates its whole error
 * handling here, so what this function captures and what it lets through is the error contract of
 * the entire data layer.
 */
class ResultContainerCatchingTest {
    @Test
    fun `should_returnSuccess_when_blockCompletes`() {
        val result = ResultContainer.catching { "alice" }

        assertEquals("alice", result.getOrNull())
    }

    @Test
    fun `should_passThroughOwnException_when_blockThrowsBaseException`() {
        val expected = NotFoundException("member")

        val result = ResultContainer.catching { throw expected }

        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun `should_wrapInDatabaseException_when_blockThrowsForeignException`() {
        val cause = IllegalArgumentException("bad column")

        val result = ResultContainer.catching { throw cause }

        val failure = requireNotNull(result.exceptionOrNull()) { "expected a Failure" }
        assertEquals(DatabaseException::class, failure::class)
        assertSame(cause, failure.cause)
    }

    /**
     * The reason this file exists: `CancellationException` is an `Exception`, so the generic
     * fallback used to capture it and hand the caller a fabricated `Failure` while the coroutine
     * was supposed to be unwinding.
     */
    @Test
    fun `should_rethrow_when_blockThrowsCancellationException`() {
        val cancellation = CancellationException("scope shut down")

        val thrown = assertFailsWith<CancellationException> {
            ResultContainer.catching { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }
}
