package cz.petrkyncl.bazosiphony.data

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import cz.petrkyncl.bazosiphony.domain.modelLabel
import cz.petrkyncl.bazosiphony.domain.stripDiacritics
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Šablona zprávy prodejci. Schválně „lidsky nedokonalá" (bez diakritiky, model malými písmeny),
 * ať zpráva nevypadá jako od robota. {model} = model / zkrácený nadpis, {cena} = cena inzerátu.
 */
object MessageTemplate {
    const val DEFAULT = "Dobry den, je {model} stale na prodej? dekuji, {jmeno}"

    var template by mutableStateOf(DEFAULT)
        private set
    var name by mutableStateOf("Petr")
        private set

    fun init(context: Context) {
        val p = prefs(context)
        template = p.getString("template", DEFAULT) ?: DEFAULT
        name = p.getString("name", "Petr") ?: "Petr"
    }

    fun save(context: Context, template: String, name: String) {
        this.template = template.ifBlank { DEFAULT }
        this.name = name
        prefs(context).edit { putString("template", this@MessageTemplate.template); putString("name", name) }
    }

    /** Zpráva pro konkrétní inzerát. */
    fun forAd(ad: Ad): String {
        val model = ad.plus?.key?.let { modelLabel(it).lowercase() } ?: shortTitle(ad.title)
        return template
            .replace("{model}", model)
            .replace("{cena}", ad.price?.let { "$it" } ?: "")
            .replace("{jmeno}", name)
            .replace(Regex("\\s+"), " ").trim()
    }

    /** „Autosedačka Maxi-Cosi Coral 360 + základna FamilyFix" → „autosedacka maxi-cosi coral 360". */
    private fun shortTitle(title: String): String {
        val t = stripDiacritics(title)
            .replace(Regex("[^a-z0-9 .,+/-]"), " ") // pryč emoji, vykřičníky…
            .replace(Regex("\\b(top|prodam|prodej|nabizim|super|levne|novy|nova|nove)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return t.split(" ").take(5).joinToString(" ").trimEnd(',', '.', '+', '-', '/')
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("message", Context.MODE_PRIVATE)
}

/** Odeslání SMS přímo z appky (oprávnění SEND_SMS). Odeslaná SMS se zapíše do historie Zpráv. */
object SmsSender {
    private const val ACTION_SENT = "cz.petrkyncl.bazosplus.SMS_SENT"

    /** Odešle SMS a počká na výsledek od operátora/systému. true = odesláno. */
    suspend fun send(context: Context, phone: String, text: String): Boolean = suspendCancellableCoroutine { cont ->
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                runCatching { app.unregisterReceiver(this) }
                if (cont.isActive) cont.resume(resultCode == Activity.RESULT_OK)
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_SENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }

        val sent = PendingIntent.getBroadcast(app, 0, Intent(ACTION_SENT).setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)
        val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        val parts = sms.divideMessage(text)
        runCatching {
            if (parts.size > 1) sms.sendMultipartTextMessage(phone, null, parts, ArrayList(parts.map { sent }), null)
            else sms.sendTextMessage(phone, null, text, sent, null)
        }.onFailure {
            runCatching { app.unregisterReceiver(receiver) }
            if (cont.isActive) cont.resume(false)
        }
    }
}
