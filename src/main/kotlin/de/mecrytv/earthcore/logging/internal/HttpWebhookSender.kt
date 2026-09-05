package de.mecrytv.earthcore.logging.internal

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpWebhookSender(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) : WebhookSender {

    override fun send(url: String, body: String): WebhookResponse {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("User-Agent", "EarthCore")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return WebhookResponse(response.statusCode(), retryAfter(response))
    }

    private fun retryAfter(response: HttpResponse<String>): Long {
        val header = response.headers().firstValue("Retry-After").orElse(null) ?: return 0
        val sekunden = header.toDoubleOrNull() ?: return 0
        return (sekunden * 1000).toLong()
    }
}
