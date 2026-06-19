import data.db.H2TestDatabaseFactory
import data.db.h2DbQuery
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
        H2TestDatabaseFactory.initialize()
        assertTrue(true)
    }

    @Test
    fun `basic database query should execute without errors`() = runTest {
        H2TestDatabaseFactory.initialize()
        h2DbQuery { }
        assertTrue(true)
    }
}
