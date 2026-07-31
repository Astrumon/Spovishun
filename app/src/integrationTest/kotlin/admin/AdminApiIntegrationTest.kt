package admin

import com.ua.astrumon.admin.config.AdminApiConfig
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.dto.HealthDto
import com.ua.astrumon.admin.server.AdminApiServer
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.admin.repository.ServerHealthRepositoryImpl
import com.ua.astrumon.domain.admin.model.ServerHealth
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import infrastructure.BaseIntegrationTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.net.ServerSocket
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration coverage for the embedded `:admin-api` Ktor server (spovishun-161, audit §8.4).
 *
 * The module's seven unit tests mount `adminApiModule` through `testApplication` with every
 * dependency mocked. Nothing ran the real CIO server, over a real socket, against a real database —
 * so bearer auth, the health query and the transport-error mapping were only ever proven in a
 * harness. Here [AdminApiServer] is started for real on a free port and driven with a real HTTP
 * client.
 *
 * ## Why this lives in `:app`
 * `:app` already owns the PostgreSQL harness (`TestDatabaseFactory`, `IntegrationDbConfig`, the
 * `.env.e2e` skip gate) and already depends on `:admin-api`; `:admin-api` has neither and no
 * dependency on `:app`. A second `integrationTest` source set over there would duplicate all of it
 * and need its own `ci.yml` wiring, for no extra coverage. `admin-api/CLAUDE.md` says not to *unit*
 * test [AdminApiServer]; running it end to end against a real database is what §8.4 asks for.
 *
 * ## Failure shapes
 * `:admin-api` has no error DTO. Every failure path answers with a bare [HttpStatusCode] and an
 * empty body, and `/health` deliberately never returns 5xx when the database is down — it reports
 * `status = "degraded"`. Both are asserted below.
 *
 * Docker is exercised through the genuine transport failure of a closed local port: `ConnectException`
 * → `DockerErrorMapper` → `DockerClientException.Unreachable` → 503. No Docker daemon is involved,
 * and nothing shells out.
 */
class AdminApiIntegrationTest : BaseIntegrationTest() {
    /**
     * Delegates to the real repository so `/health` reports the actual test database, and can be
     * switched to a failure for one case — the database is initialised once per JVM and cannot be
     * taken down mid-suite.
     */
    private class SwitchableHealthRepository(
        private val delegate: ServerHealthRepository,
    ) : ServerHealthRepository {
        var failure: DatabaseException? = null

        override suspend fun check(): ResultContainer<ServerHealth> = failure?.let { ResultContainer.failure(it) } ?: delegate.check()
    }

    private lateinit var server: AdminApiServer
    private lateinit var dockerClient: DockerApiClient
    private lateinit var healthRepository: SwitchableHealthRepository
    private lateinit var httpClient: HttpClient
    private var baseUrl: String = ""

    @BeforeAll
    fun startAdminApi() {
        // The base class's @BeforeAll runs first (JUnit orders superclass callbacks ahead of
        // subclass ones), so the database is already up and the skip gate has been applied.
        val (serverPort, deadPort) = twoFreePorts()
        val config = AdminApiConfig(
            enabled = true,
            bind = LOOPBACK,
            port = serverPort,
            token = TOKEN,
            // A port nothing listens on: every Docker call fails at connect, deterministically.
            dockerApiUrl = "http://$LOOPBACK:$deadPort",
        )
        baseUrl = "http://$LOOPBACK:${config.port}/api/v1"
        dockerClient = DockerApiClient(config.dockerApiUrl)
        healthRepository = SwitchableHealthRepository(ServerHealthRepositoryImpl())
        httpClient = HttpClient(CIO) { install(ContentNegotiation) { json(LENIENT_JSON) } }
        server = AdminApiServer(config, dockerClient, healthRepository)
        server.start()
        runBlocking { awaitAccepting() }
    }

    @AfterAll
    fun stopAdminApi() {
        if (!this::server.isInitialized) return
        httpClient.close()
        dockerClient.close()
        server.stop()
    }

    @BeforeTest
    fun resetHealthRepository() {
        healthRepository.failure = null
    }

    @Test
    fun `health returns real database data for a valid bearer token`() = runTest {
        val response = authorized("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = LENIENT_JSON.decodeFromString<HealthDto>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertTrue(body.database.connected)
        assertTrue(body.database.sizeBytes > 0, "pg_database_size must report the real test database")
    }

    @Test
    fun `a request without an Authorization header is rejected`() = runTest {
        val response = httpClient.get("$baseUrl/health")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().isEmpty(), "the bearer challenge carries no body")
    }

    @Test
    fun `a request with the wrong bearer token is rejected`() = runTest {
        val response = httpClient.get("$baseUrl/health") {
            header(HttpHeaders.Authorization, "Bearer not-$TOKEN")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `the SSE log stream sits behind the same bearer gate`() = runTest {
        val response = httpClient.get("$baseUrl/containers/abc/logs/stream")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a database outage is reported as degraded rather than a stack trace`() = runTest {
        healthRepository.failure = DatabaseException("connection refused")

        val response = authorized("/health")

        // Documented shape: /health always answers 200 and downgrades `status` — it never 5xx's.
        assertEquals(HttpStatusCode.OK, response.status)
        val body = LENIENT_JSON.decodeFromString<HealthDto>(response.bodyAsText())
        assertEquals("degraded", body.status)
        assertEquals(false, body.database.connected)
        assertEquals(0L, body.database.sizeBytes)
    }

    @Test
    fun `an unreachable docker proxy maps to 503 on metrics and containers`() = runTest {
        assertUnreachable(authorized("/metrics"))
        assertUnreachable(authorized("/containers"))
    }

    @Test
    fun `an unreachable docker proxy maps to 503 on container logs`() = runTest {
        assertUnreachable(authorized("/containers/abc/logs?tail=5"))
    }

    private suspend fun authorized(path: String): HttpResponse = httpClient.get("$baseUrl$path") {
        header(HttpHeaders.Authorization, "Bearer $TOKEN")
    }

    private suspend fun assertUnreachable(response: HttpResponse) {
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().isEmpty(), "failures answer with a bare status, never a stack trace")
    }

    /**
     * Blocks until the server accepts connections. `start(wait = false)` returns before the socket
     * is necessarily bound; the loop ends on the first HTTP status of any kind (a 401 already proves
     * the listener is up), so it is a readiness check rather than a fixed pause.
     */
    private suspend fun awaitAccepting() {
        repeat(READINESS_ATTEMPTS) {
            runCatching { httpClient.get("$baseUrl/health") }.onSuccess { return }
            delay(READINESS_INTERVAL_MILLIS)
        }
        error("admin API did not start accepting connections on $baseUrl")
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val TOKEN = "integration-test-token"
        const val READINESS_ATTEMPTS = 100
        const val READINESS_INTERVAL_MILLIS = 50L
        val LENIENT_JSON = Json { ignoreUnknownKeys = true }

        /**
         * Asks the OS for two distinct unused ports — one for the server, one guaranteed to refuse
         * connections. Both sockets are held open until each port is read, so the OS cannot hand out
         * the same number twice and route the "dead" Docker URL at the admin API itself.
         */
        fun twoFreePorts(): Pair<Int, Int> = ServerSocket(0).use { first ->
            ServerSocket(0).use { second -> first.localPort to second.localPort }
        }
    }
}
