package com.jmussel.chessgame.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DatabaseAvailabilityWorkflowTest {
    private val workflow = findWorkflow().readText()
    private val script = extractRunScript(workflow)

    @Test
    fun theWorkflowHasTheTwoPhaseBoundedPublicProbeContract() {
        val applicationUrl = "APPLICATION_HEALTH_URL: https://chessgame-hit7.onrender.com/health"
        val databaseUrl = "DATABASE_HEALTH_URL: https://chessgame-hit7.onrender.com/health/database"

        assertTrue(workflow.contains("cron: '23 6 * * 1,3,5'"))
        assertTrue(workflow.contains("workflow_dispatch:"))
        assertTrue(workflow.contains("permissions: {}"))
        assertTrue(workflow.indexOf(applicationUrl) < workflow.indexOf(databaseUrl))
        assertTrue(workflow.contains("APPLICATION_WAKE_ATTEMPTS:-8"))
        assertTrue(workflow.contains("APPLICATION_WAKE_RETRY_DELAY_SECONDS:-20"))
        assertTrue(workflow.contains("DATABASE_HEALTH_ATTEMPTS:-3"))
        assertTrue(workflow.contains("DATABASE_HEALTH_RETRY_DELAY_SECONDS:-10"))
        assertTrue(workflow.contains("--max-time 30"))
        assertTrue(workflow.contains("http_status\" == 5??"))
        assertTrue(workflow.contains("timeout-minutes: 10"))
        assertTrue(script.indexOf("\"Application wake/liveness phase\"") < script.indexOf("\"Database health phase\""))
        assertTrue(script.contains("\"status\"[[:space:]]*:[[:space:]]*\"ok\""))
    }

    @Test
    fun theWorkflowChecksOutNoCodeUsesNoSecretAndSendsNoWrite() {
        assertFalse(workflow.contains("actions/checkout"))
        assertFalse(workflow.contains("secrets."))
        assertFalse(workflow.contains("--request POST"))
        assertFalse(workflow.contains("--data"))
        assertFalse(workflow.contains("git commit"))
        assertFalse(workflow.contains("git push"))
    }

    @Test
    fun aTransientWakeFailureRecoversBeforeTheDatabaseProbe() =
        withServer(
            "/health" to listOf(Response(503), Response(200, "healthy")),
            "/health/database" to listOf(Response(200, OK_BODY)),
        ) { server ->
            val result = runWorkflow(server)

            assertEquals(0, result.exitCode, result.stderr)
            assertEquals(2, server.requestsTo("/health"))
            assertEquals(1, server.requestsTo("/health/database"))
            assertTrue(result.stdout.isBlank())
            assertTrue(result.stderr.isBlank())
        }

    @Test
    fun anExhaustedWakePhaseNeverCallsTheDatabaseEndpoint() =
        withServer(
            "/health" to listOf(Response(503)),
            "/health/database" to listOf(Response(200, OK_BODY)),
        ) { server ->
            val result = runWorkflow(server, wakeAttempts = 2)

            assertNotEquals(0, result.exitCode)
            assertEquals(2, server.requestsTo("/health"))
            assertEquals(0, server.requestsTo("/health/database"))
            assertTrue(result.stderr.contains("Application wake/liveness phase failed"))
        }

    @Test
    fun aPermanentWakeFailureIsNotRetried() =
        withServer(
            "/health" to listOf(Response(404)),
            "/health/database" to listOf(Response(200, OK_BODY)),
        ) { server ->
            val result = runWorkflow(server, wakeAttempts = 3)

            assertNotEquals(0, result.exitCode)
            assertEquals(1, server.requestsTo("/health"))
            assertEquals(0, server.requestsTo("/health/database"))
        }

    @Test
    fun aTransientDatabaseFailureUsesItsOwnRetryBudget() =
        withServer(
            "/health" to listOf(Response(200, "healthy")),
            "/health/database" to listOf(Response(503), Response(200, OK_BODY)),
        ) { server ->
            val result = runWorkflow(server, wakeAttempts = 1, databaseAttempts = 2)

            assertEquals(0, result.exitCode, result.stderr)
            assertEquals(1, server.requestsTo("/health"))
            assertEquals(2, server.requestsTo("/health/database"))
        }

    @Test
    fun aPermanentDatabaseFailureIsNotRetried() =
        withServer(
            "/health" to listOf(Response(200, "healthy")),
            "/health/database" to listOf(Response(404)),
        ) { server ->
            val result = runWorkflow(server, databaseAttempts = 3)

            assertNotEquals(0, result.exitCode)
            assertEquals(1, server.requestsTo("/health"))
            assertEquals(1, server.requestsTo("/health/database"))
            assertTrue(result.stderr.contains("Database health phase failed"))
        }

    @Test
    fun databaseSuccessStillRequiresStatusOk() =
        withServer(
            "/health" to listOf(Response(200, "healthy")),
            "/health/database" to listOf(Response(200, "{\"status\":\"o k\"}")),
        ) { server ->
            val result = runWorkflow(server, databaseAttempts = 3)

            assertNotEquals(0, result.exitCode)
            assertEquals(1, server.requestsTo("/health"))
            assertEquals(1, server.requestsTo("/health/database"))
            assertTrue(result.stderr.contains("Database health phase returned HTTP 200 without status ok"))
        }

    private fun runWorkflow(
        server: ScenarioServer,
        wakeAttempts: Int = 2,
        databaseAttempts: Int = 2,
    ): ProcessResult {
        val scriptFile = Files.createTempFile("database-availability-workflow", ".sh")
        Files.writeString(scriptFile, script)

        return try {
            val process =
                ProcessBuilder(bashExecutable(), scriptFile.toString().replace('\\', '/'))
                    .apply {
                        environment()["APPLICATION_HEALTH_URL"] = "${server.baseUrl}/health"
                        environment()["DATABASE_HEALTH_URL"] = "${server.baseUrl}/health/database"
                        environment()["APPLICATION_WAKE_ATTEMPTS"] = wakeAttempts.toString()
                        environment()["APPLICATION_WAKE_RETRY_DELAY_SECONDS"] = "0"
                        environment()["DATABASE_HEALTH_ATTEMPTS"] = databaseAttempts.toString()
                        environment()["DATABASE_HEALTH_RETRY_DELAY_SECONDS"] = "0"
                    }.start()

            check(process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                "Database availability workflow test process timed out"
            }

            ProcessResult(
                exitCode = process.exitValue(),
                stdout = process.inputStream.bufferedReader().readText(),
                stderr = process.errorStream.bufferedReader().readText(),
            )
        } finally {
            Files.deleteIfExists(scriptFile)
        }
    }

    private fun withServer(
        vararg responses: Pair<String, List<Response>>,
        block: (ScenarioServer) -> Unit,
    ) {
        ScenarioServer(responses.toMap()).use(block)
    }

    private fun bashExecutable(): String =
        if (System.getProperty("os.name").startsWith("Windows")) {
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            Path.of(programFiles, "Git", "bin", "bash.exe").toString()
        } else {
            "/bin/bash"
        }

    private fun extractRunScript(yaml: String): String {
        val lines = yaml.lines()
        val runLine = lines.indexOfFirst { it.trim() == "run: |" }
        check(runLine >= 0) { "Workflow has no literal run block" }

        val contentIndent = lines.getOrNull(runLine + 1)?.indexOfFirst { !it.isWhitespace() } ?: -1
        check(contentIndent > 0) { "Workflow run block has no content" }

        return lines
            .drop(runLine + 1)
            .takeWhile { it.isBlank() || it.indexOfFirst { character -> !character.isWhitespace() } >= contentIndent }
            .joinToString("\n") { line -> if (line.isBlank()) "" else line.drop(contentIndent) }
    }

    private fun findWorkflow(): Path {
        val start = Path.of(System.getProperty("user.dir")).toAbsolutePath()

        return generateSequence(start) { it.parent }
            .map { it.resolve(".github/workflows/database-availability.yml") }
            .firstOrNull(Files::exists)
            ?: error("Could not find .github/workflows/database-availability.yml from $start")
    }

    private class ScenarioServer(
        responses: Map<String, List<Response>>,
    ) : AutoCloseable {
        private val queues = responses.mapValues { (_, values) -> ArrayDeque(values) }
        private val requests = responses.mapValues { AtomicInteger() }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/") { exchange -> respond(exchange) }
            server.start()
        }

        fun requestsTo(path: String): Int = requests.getValue(path).get()

        private fun respond(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val queue = queues[path]
            val response =
                if (queue == null) {
                    Response(404)
                } else {
                    requests.getValue(path).incrementAndGet()
                    synchronized(queue) {
                        if (queue.size > 1) queue.removeFirst() else queue.first()
                    }
                }
            val body = response.body.toByteArray()

            exchange.sendResponseHeaders(response.status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        override fun close() = server.stop(0)
    }

    private data class Response(
        val status: Int,
        val body: String = "",
    )

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private companion object {
        const val OK_BODY = "{\"status\":\"ok\"}"
    }
}
