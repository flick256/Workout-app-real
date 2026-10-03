package app.forge.fitness.data.nutrition

import app.forge.fitness.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/** The few plain HTTP calls food lookups need. Call from a background thread. */
internal object Http {
    val USER_AGENT_APP = "Forge/${BuildConfig.VERSION_NAME} (personal Android fitness app)"

    /** A normal phone browser, for reading public web pages. */
    const val USER_AGENT_BROWSER =
        "Mozilla/5.0 (Linux; Android 15; SM-S936B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

    data class Response(val code: Int, val body: String, val finalUrl: String)

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 12_000, maxChars: Int = 2_000_000): Response =
        request(url, "GET", headers, null, timeoutMs, maxChars)

    fun postForm(url: String, form: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 12_000): Response =
        request(url, "POST", headers + ("Content-Type" to "application/x-www-form-urlencoded"), form, timeoutMs, 200_000)

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int,
        maxChars: Int,
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            if ("User-Agent" !in headers) connection.setRequestProperty("User-Agent", USER_AGENT_APP)
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { reader ->
                val buffer = CharArray(8_192)
                val out = StringBuilder()
                while (out.length < maxChars) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    out.append(buffer, 0, n)
                }
                out.toString()
            } ?: ""
            Response(code, text, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }
}
