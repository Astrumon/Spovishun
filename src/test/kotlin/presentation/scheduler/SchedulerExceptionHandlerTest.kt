package presentation.scheduler

import com.ua.astrumon.presentation.scheduler.SchedulerExceptionHandler
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.slf4j.Logger
import kotlin.test.BeforeTest
import kotlin.test.Test

class SchedulerExceptionHandlerTest {
    private val logger: Logger = mockk(relaxed = true)

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `should log error with coroutine name when launched job throws uncaught exception`() = runTest {
        val handler = SchedulerExceptionHandler.create(logger)
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + handler + CoroutineName("test-scheduler"),
        )

        scope.launch { throw RuntimeException("boom") }
        testScheduler.runCurrent()

        verify {
            logger.error(
                "Uncaught exception in scheduler coroutine [{}]",
                "test-scheduler",
                any<RuntimeException>(),
            )
        }
        scope.cancel()
    }
}
