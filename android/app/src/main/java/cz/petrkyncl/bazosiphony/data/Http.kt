package cz.petrkyncl.bazosiphony.data

import kotlinx.coroutines.Dispatchers
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

    /** GET → (JSON, hlavičky). Hodnoty `null`/prázdné se z query vynechají. */
    suspend fun get(url: String, params: Map<String, Any?> = emptyMap(), userAgent: String): Pair<JsonElement, okhttp3.Headers> =
        withContext(Dispatchers.IO) {
            val builder = url.toHttpUrl().newBuilder()
            params.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) builder.addQueryParameter(k, v.toString()) }
            val request = Request.Builder().url(builder.build())
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { res ->
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
