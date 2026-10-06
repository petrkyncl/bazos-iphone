package cz.petrkyncl.bazosiphony.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Brzda pro Bazoš: nejvýš 3 dotazy najednou a ~6 za sekundu. Bazoš při přetížení vrací HTTP 429.
    private val bazosLimit = Semaphore(3)
    private val pacing = Mutex()
    private var lastBazosRequest = 0L
    private const val MIN_GAP_MS = 160L

    class RateLimited : IOException("Bazoš nás dočasně brzdí (příliš mnoho dotazů) — zkus to za chvíli.")

    /** GET → (JSON, hlavičky). Hodnoty `null`/prázdné se z query vynechají. */
    suspend fun get(url: String, params: Map<String, Any?> = emptyMap(), userAgent: String): Pair<JsonElement, okhttp3.Headers> {
        val builder = url.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) builder.addQueryParameter(k, v.toString()) }
        val request = Request.Builder().url(builder.build())
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        if (request.url.host != "www.bazos.cz") return execute(request)
        return bazosLimit.withPermit {
            var attempt = 0
            while (true) {
                pacing.withLock {
                    val wait = lastBazosRequest + MIN_GAP_MS - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    lastBazosRequest = System.currentTimeMillis()
                }
                try {
                    return@withPermit execute(request)
                } catch (e: RateLimited) {
                    // 429: počkat 2 s, pak 4 s a zkusit znovu (max. 2×)
                    if (++attempt > 2) throw e
                    delay(2000L * attempt)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("nedosažitelné")
        }
    }

    private suspend fun execute(request: Request): Pair<JsonElement, okhttp3.Headers> = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { res ->
            if (res.code == 429) throw RateLimited()
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${request.url.encodedPath}")
            json.parseToJsonElement(res.body.string()) to res.headers
        }
    }
}

// Bazoš vrací skoro všechno jako řetězce („"views":"88"", „"topped":"true"") → tolerantní čtení.
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
fun JsonObject.bool(key: String): Boolean = str(key) == "true"
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
