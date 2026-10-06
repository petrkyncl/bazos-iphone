package cz.petrkyncl.bazosiphony.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** Zobrazení výsledků (galerie s velkými fotkami / kompaktní seznam), uložené v telefonu. */
object ViewMode {
    var gallery by mutableStateOf(true)
        private set

    fun init(context: Context) {
        gallery = prefs(context).getBoolean("gallery", true)
    }

    fun toggle(context: Context) {
        gallery = !gallery
        prefs(context).edit { putBoolean("gallery", gallery) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui", Context.MODE_PRIVATE)
}

/** Tlačítko do lišty: přepne velké fotky ↔ kompaktní seznam. */
@Composable
fun ViewModeButton() {
    val context = LocalContext.current
    IconButton(onClick = { ViewMode.toggle(context) }) {
        Icon(
            if (ViewMode.gallery) Icons.Default.ViewList else Icons.Default.ViewAgenda,
            if (ViewMode.gallery) "Kompaktní seznam" else "Velké fotky",
        )
    }
}
