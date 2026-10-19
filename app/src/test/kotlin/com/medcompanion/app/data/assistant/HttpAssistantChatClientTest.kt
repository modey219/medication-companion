package com.medcompanion.app.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.domain.assistant.AssistantAuthor
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantExchange
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * The transport (T10) against a real socket on localhost: the endpoint path,
 * the install-id header, the size of the body and every failure mapping. The
 * proxy itself is covered by backend/tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class HttpAssistantChatClientTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var store: AssistantInstallIdStore
    private lateinit var server: HttpServer

    private var status = 200
    private var body = """{"reply":"جواب"}"""
    private val installIdHeader = AtomicReference<String?>(null)
    private val requestedBody = AtomicReference("")
    private val requestedPath = AtomicReference("")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "assistant-install-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + Job())
        store =
            AssistantInstallIdStore(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(AssistantPayloads.ENDPOINT_PATH) { exchange -> handle(exchange) }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        scope.cancel()
        file.delete()
    }

    private fun handle(exchange: HttpExchange) {
        requestedPath.set(exchange.requestURI.path)
        installIdHeader.set(exchange.requestHeaders.getFirst("X-Install-Id"))
        requestedBody.set(exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) })
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun client(baseUrl: String = "http://127.0.0.1:${server.address.port}") =
        HttpAssistantChatClient(baseUrl, store)

    private fun request(
        text: String = "سؤال",
        shareMedications: Boolean = false,
        medicationContext: String? = null,
    ) = AssistantRequest(
        exchanges = listOf(AssistantExchange(AssistantAuthor.PATIENT, text)),
        shareMedications = shareMedications,
        medicationContext = medicationContext,
    )

    @Test
    fun `an answer comes back with a fresh install id on the right path`() =
        runTest {
            val result = client().send(request())

            assertEquals(AssistantResult.Replied(AssistantReply.Answer("جواب")), result)
            assertEquals(AssistantPayloads.ENDPOINT_PATH, requestedPath.get())
            assertEquals(32, installIdHeader.get()?.length)
            assertTrue(requestedBody.get().contains("\"messages\""))
            assertTrue(requestedBody.get().contains("سؤال"))
        }

    @Test
    fun `the same install id is sent on every question`() =
        runTest {
            val client = client()
            client.send(request())

            val first = installIdHeader.get()
            client.send(request("سؤال آخر"))

            assertEquals(first, installIdHeader.get())
        }

    @Test
    fun `the medication block is on the wire only when sharing is on`() =
        runTest {
            val client = client()
            client.send(request(shareMedications = true, medicationContext = "MEDICATION BLOCK"))
            assertTrue(requestedBody.get().contains("MEDICATION BLOCK"))

            client.send(request(shareMedications = false))
            assertFalse(requestedBody.get().contains("medicationContext"))
        }

    @Test
    fun `a rate limit is its own sentence`() =
        runTest {
            status = 429

            assertEquals(AssistantResult.Failed(AssistantError.RATE_LIMITED), client().send(request()))
        }

    @Test
    fun `a rejected request is not shown as a crash`() =
        runTest {
            status = 400

            assertEquals(AssistantResult.Failed(AssistantError.INVALID), client().send(request()))
        }

    @Test
    fun `a server failure asks the patient to try again`() =
        runTest {
            status = 500

            assertEquals(AssistantResult.Failed(AssistantError.SERVER), client().send(request()))
        }

    @Test
    fun `an unreadable answer is a server failure`() =
        runTest {
            body = "not json at all"

            assertEquals(AssistantResult.Failed(AssistantError.SERVER), client().send(request()))
        }

    @Test
    fun `an emergency answer becomes the emergency card`() =
        runTest {
            body = """{"reply":"","emergency":true}"""

            assertEquals(
                AssistantResult.Replied(AssistantReply.Emergency),
                client().send(request()),
            )
        }

    @Test
    fun `an unreachable proxy is an offline sentence, never a crash`() =
        runTest {
            val result = client(baseUrl = "http://127.0.0.1:1").send(request())

            assertEquals(AssistantResult.Failed(AssistantError.OFFLINE), result)
        }

    @Test
    fun `a base url with a trailing slash still hits the endpoint`() =
        runTest {
            client(baseUrl = "http://127.0.0.1:${server.address.port}/").send(request())

            assertEquals(AssistantPayloads.ENDPOINT_PATH, requestedPath.get())
        }
}
