package cz.petrkyncl.bazosiphony.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.petrkyncl.bazosiphony.MainActivity
import cz.petrkyncl.bazosiphony.R
import java.util.concurrent.TimeUnit

/** Hlídání: periodicky projde uložená hledání a upozorní na nové inzeráty (vyšší ID než naposledy viděné). */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SavedSearchStore.get(applicationContext).apply { reload() }
        val repo = Repository.get(applicationContext)
        for (s in store.items.value) {
            val page = runCatching { repo.search(s.filters.migrated()) }.getOrNull() ?: continue
            val fresh = page.items.filter { (it.id.toLongOrNull() ?: 0) > s.lastSeenMaxId }
            if (fresh.size == s.newCount) continue
            store.setNewCount(s.id, fresh.size)
            if (s.notify && fresh.size > s.newCount) notify(s, fresh)
        }
        return Result.success()
    }

    private fun notify(s: SavedSearch, fresh: List<Listing>) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, s.id.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_SAVED_SEARCH, s.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = "${fresh.size} ${if (fresh.size == 1) "nový inzerát" else if (fresh.size < 5) "nové inzeráty" else "nových inzerátů"} · ${s.filters.label()}"
        val lines = fresh.take(5).map { "${it.title} — ${it.priceText ?: ""}" }
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6600.toInt())
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { st -> lines.forEach(st::addLine) })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(s.id.hashCode(), notification) }
    }

    companion object {
        private const val CHANNEL = "watch"
        private const val WORK = "watch-saved-searches"

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hlídání inzerátů", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        /** Naplánovat (nebo zrušit, když nic nehlídáme). */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (SavedSearchStore.get(ctx).items.value.isEmpty()) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<WatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
