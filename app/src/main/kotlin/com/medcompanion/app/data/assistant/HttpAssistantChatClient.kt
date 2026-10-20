package com.medcompanion.app.data.assistant

import com.medcompanion.app.domain.assistant.AssistantChatClient
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the assistant proxy with [HttpURLConnection].
 *
 * The app has no HTTP client dependency on purpose: one JSON POST does not
 * justify one, and `org.json` is already part of the platform (it is what the
 * data export uses). The call runs on [Dispatchers.IO] and every failure is
 * mapped to [AssistantResult.Failed], so the Assistant tab can never crash on a
 * network problem.
 *
 * Nothing here logs. Message text, medication names and the install id are
 * never written to logcat or to a file (AGENTS.md > Privacy and Security).
 */
@Singleton
class HttpAssistantChatClient
    @Inject
    constructor(
        @AssistantBaseUrl private val baseUrl: String,
        private val installIdStore: AssistantInstallIdStore,
    ) : AssistantChatClient {
        override suspend fun send(request: AssistantRequest): AssistantResult =
            withContext(Dispatchers.IO) {
                val connection =
                    try {
                        openConnection(installIdStore.get())
                    } catch (_: IOException) {
                        return@withContext AssistantResult.Failed(AssistantError.OFFLINE)
                    }
                try {
                    connection.outputStream.use { output ->
                        output.write(AssistantPayloads.requestBody(request).toByteArray(StandardCharsets.UTF_8))
                    }
                    val status = connection.responseCode
                    if (status in 200..299) {
                        AssistantPayloads.parseReply(readBody(connection.inputStream))
                    } else {
                        // Read and drop the error body: it is never shown to the
                        // patient and never logged.
                        runCatching { readBody(connection.errorStream) }
                        AssistantResult.Failed(
                            AssistantPayloads.errorForStatus(status) ?: AssistantError.SERVER,
                        )
                    }
                } catch (_: SocketTimeoutException) {
                    AssistantResult.Failed(AssistantError.TIMEOUT)
                } catch (_: IOException) {
                    AssistantResult.Failed(AssistantError.OFFLINE)
                } finally {
                    connection.disconnect()
                }
            }

        private fun openConnection(installId: String): HttpURLConnection {
            val url = URL(baseUrl.trimEnd('/') + AssistantPayloads.ENDPOINT_PATH)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(INSTALL_ID_HEADER, installId)
            return connection
        }

        /** Reads at most [MAX_RESPONSE_CHARS] so a bad response cannot fill memory. */
        private fun readBody(stream: InputStream?): String {
            if (stream == null) return ""
            val text =
                stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    val buffer = CharArray(CHAR_BUFFER_SIZE)
                    val builder = StringBuilder()
                    while (builder.length < MAX_RESPONSE_CHARS) {
                        val read = reader.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_CHARS - builder.length))
                        if (read <= 0) break
                        builder.append(buffer, 0, read)
                    }
                    builder.toString()
                }
            return text
        }

        private companion object {
            /** Header the backend rate-limits by; see backend/README.md. */
            const val INSTALL_ID_HEADER = "X-Install-Id"
            const val CONNECT_TIMEOUT_MILLIS = 10_000
            const val READ_TIMEOUT_MILLIS = 30_000
            const val MAX_RESPONSE_CHARS = 64 * 1024
            const val CHAR_BUFFER_SIZE = 8 * 1024
        }
    }
