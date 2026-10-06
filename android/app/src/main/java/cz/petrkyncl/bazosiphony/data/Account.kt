package cz.petrkyncl.bazosiphony.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Ověření telefonního čísla stejně jako v oficiální appce (zjištěno dekompilací):
 *   guid.php                  → ID zařízení (hlavička X-DeviceId)
 *   POST confirm-phone-post   {"phone_number"} → Bazoš pošle SMS s kódem
 *   PUT  confirm-phone-put    {"code","phone_number"} → {"token"} (hlavička „token")
 * S ověřeným číslem jde např. zobrazit telefon prodejce (ad-phone.php).
 * Číslo, ID zařízení i token zůstávají jen v tomto telefonu.
 */
class Account private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    data class State(val phone: String?, val verified: Boolean)

    private val _state = MutableStateFlow(State(prefs.getString(KEY_PHONE, null), prefs.getString(KEY_TOKEN, null) != null))
    val state: StateFlow<State> = _state.asStateFlow()

    /** Bazoš chce číslo bez předvolby (9 číslic); „+420 731…" i „420731…" převedeme. */
    fun normalize(input: String): String {
        var d = input.filter(Char::isDigit)
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 12 && (d.startsWith("420") || d.startsWith("421"))) d = d.drop(3)
        return d
    }

    private suspend fun deviceId(): String {
        prefs.getString(KEY_DEVICE, null)?.let { return it }
        val guid = (Http.get(BASE + "guid.php", userAgent = UA).first as JsonObject).str("guid") ?: throw IOException("Bazoš nepřidělil ID zařízení")
        prefs.edit { putString(KEY_DEVICE, guid) }
        return guid
    }

    /** Krok 1: Bazoš pošle na číslo SMS s kódem. */
    suspend fun requestCode(phoneInput: String) {
        val phone = normalize(phoneInput)
        require(phone.length >= 9) { "Zadej celé číslo (9 číslic)." }
        send("POST", "confirm-phone-post.php?platform=android", buildJsonObject { put("phone_number", phone) })
        prefs.edit { putString(KEY_PHONE, phone); remove(KEY_TOKEN) }
        _state.value = State(phone, false)
    }

    /** Krok 2: ověření kódem ze SMS → token. */
    suspend fun verify(code: String) {
        val phone = _state.value.phone ?: error("Nejdřív si nech poslat kód.")
        val res = send("PUT", "confirm-phone-put.php", buildJsonObject { put("code", code.trim()); put("phone_number", phone) })
        val token = res?.str("token") ?: throw IOException("Bazoš nevrátil token — zkontroluj kód.")
        prefs.edit { putString(KEY_TOKEN, token) }
        _state.value = State(phone, true)
    }

    fun logout() {
        prefs.edit { remove(KEY_TOKEN); remove(KEY_PHONE) }
        _state.value = State(null, false)
    }

    /** Telefon prodejce (vyžaduje ověřené číslo). */
    suspend fun sellerPhone(adId: String): String {
        val token = prefs.getString(KEY_TOKEN, null) ?: error("Nejdřív ověř své číslo v Účtu.")
        val res = send("GET", "ad-phone.php?ad_id=$adId", null, token)
        return res?.let { o -> listOf("phone", "phone_number", "number").firstNotNullOfOrNull { o.str(it) } }
            ?: throw IOException("Bazoš telefon nevrátil.")
    }

    private suspend fun send(method: String, path: String, body: JsonObject?, token: String? = prefs.getString(KEY_TOKEN, null)): JsonObject? =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(BASE + path)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-DeviceId", deviceId())
            token?.let { builder.header("token", it) }
            val json = body?.toString()?.toRequestBody("application/json".toMediaType())
            builder.method(method, json ?: if (method == "GET") null else "".toRequestBody())
            Http.client.newCall(builder.build()).execute().use { res ->
                val text = res.body.string()
                val obj = runCatching { Http.json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                if (!res.isSuccessful || obj?.str("error_code") != null) {
                    // Bazoš vrací srozumitelné české hlášky („Číslo je neověřené", „Špatný kód"…)
                    throw IOException(obj?.str("message") ?: "HTTP ${res.code}")
                }
                obj
            }
        }

    companion object {
        private const val BASE = "https://www.bazos.cz/api/v2/"
        private const val UA = "ktor-client"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_TOKEN = "token"

        @Volatile private var instance: Account? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Account(context).also { instance = it } }
    }
}
