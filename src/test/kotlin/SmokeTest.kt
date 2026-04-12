import data.db.TestDatabaseFactory
import data.db.testDbQuery
import io.mockk.clearAllMocks
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SmokeTest {

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `database initialization should complete without errors`() {
        TestDatabaseFactory.initialize()
        assertTrue(true)
    }

    @Test
    fun `basic database query should execute without errors`() = runTest {
        TestDatabaseFactory.initialize()
        testDbQuery { }
        assertTrue(true)
    }
}