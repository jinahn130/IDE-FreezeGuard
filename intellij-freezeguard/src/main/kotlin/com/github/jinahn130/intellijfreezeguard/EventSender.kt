package com.github.jinahn130.intellijfreezeguard

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

data class ActionEvent(
    val action: String,
    val durationMs: Double,
    val thread: String,
    val heapDeltaBytes: Long,
    val edtStalls: Int,
    val edtLongestStallMs: Double,
    val tsIso: String
)

object EventSender {
    private val log = Logger.getInstance(EventSender::class.java)

    private const val BASE = "http://127.0.0.1:8000"
    private const val INGEST = "$BASE/ingest"
    private const val METRICS = "$BASE/metrics"

    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)           // keep it simple
        .connectTimeout(Duration.ofMillis(1500))
        .build()

    //Calling this ping() function from BadBlockingAction to verify connectivity
    //Without this the terminal logs do not show explicitly why the EventSender did not send events to Prometheus correctly.
    fun ping() = client.sendAsync(
        HttpRequest.newBuilder()
            .uri(URI.create(METRICS))
            .timeout(Duration.ofMillis(1000))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.discarding()
    ).thenApply { it.statusCode() }
        .exceptionally { ex -> log.warn("FreezeGuard ping error", ex); -1 }

    fun sendAsync(event: ActionEvent) {
        val json = """{
          "action":"${event.action}",
          "duration_ms":${"%.3f".format(event.durationMs)},
          "thread":"${event.thread}",
          "heap_delta_bytes":${event.heapDeltaBytes},
          "edt_stalls":${event.edtStalls},
          "edt_longest_stall_ms":${"%.3f".format(event.edtLongestStallMs)},
          "ts":"${event.tsIso}"
        }""".trimIndent()

        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        log.warn("FreezeGuard payload bytes=${bytes.size}")

        val req = HttpRequest.newBuilder()
            .uri(URI.create(INGEST))
            .timeout(Duration.ofMillis(2000))
            .header("Content-Type", "application/json; charset=UTF-8")
            // DO NOT set Content-Length; HttpClient will set it for ofByteArray(...)
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { resp ->
                val code = resp.statusCode()
                if (code / 100 != 2) {
                    log.warn("FreezeGuard ingest HTTP $code body='${resp.body().take(200)}'")
                } else {
                    log.warn("FreezeGuard ingest OK $code")
                }
            }
            .orTimeout(3, TimeUnit.SECONDS)
            .exceptionally { ex -> log.warn("FreezeGuard ingest error", ex); null }
    }
}
