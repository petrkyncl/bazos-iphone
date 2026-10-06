package cz.petrkyncl.bazosiphony.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.MessageTemplate
import cz.petrkyncl.bazosiphony.data.SmsSender
import kotlinx.coroutines.launch

/**
 * Připravená zpráva prodejci (podle šablony z Účtu), jde upravit. Odeslání SMS jen ťuknutím na
 * „Odeslat" — nikdy automaticky. Bez známého čísla jen zkopírovat (např. do formuláře na Bazoši).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSheet(ad: Ad, phone: String?, onDismiss: () -> Unit, openUrl: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(MessageTemplate.forAd(ad)) }
    var sending by remember { mutableStateOf(false) }

    fun send() {
        val number = phone ?: return
        sending = true
        scope.launch {
            val ok = SmsSender.send(context, number, text)
            sending = false
            Toast.makeText(context, if (ok) "SMS odeslána" else "SMS se nepodařilo odeslat", Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) send() else Toast.makeText(context, "Bez oprávnění k SMS jde zprávu jen otevřít ve Zprávách", Toast.LENGTH_LONG).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Zpráva prodejci", style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(ad.seller.name, phone).joinToString(" · ").ifEmpty { "Prodejce" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Text zprávy") })
            if (phone != null) {
                Button(onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    if (granted) send() else permission.launch(Manifest.permission.SEND_SMS)
                }, enabled = !sending && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (sending) "Odesílám…" else "Odeslat SMS na $phone")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri()).putExtra("sms_body", text))
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sms, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Otevřít ve Zprávách")
                }
            } else {
                Text(
                    "Telefon prodejce zatím neznáš — zprávu zkopíruj a vlož do formuláře na Bazoši.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { copy(context, text) }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Kopírovat")
                }
                if (phone == null) TextButton(onClick = { copy(context, text); openUrl(ad.url) }) { Text("Kopírovat a otevřít Bazoš") }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Zpráva", text))
    Toast.makeText(context, "Zkopírováno", Toast.LENGTH_SHORT).show()
}
