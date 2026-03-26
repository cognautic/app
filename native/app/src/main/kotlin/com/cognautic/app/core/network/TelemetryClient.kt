package com.cognautic.app.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.cognautic.app.BuildConfig
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object TelemetryClient {
    private const val TAG = "TelemetryClient"
    private const val supabaseUrl = BuildConfig.SUPABASE_URL
    private const val anonKey = BuildConfig.SUPABASE_ANON_KEY
    
    fun trackEvent(
        event: String,
        version: String = "1.0.0",
        userId: String? = null,
        meta: Map<String, Any>? = null
    ) {
        if (supabaseUrl.isEmpty() || anonKey.isEmpty()) {
            Log.w(TAG, "Telemetry disabled: Supabase URL or Key is missing")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var conn: HttpURLConnection? = null
            try {
                Log.d(TAG, "Tracking event: $event")
                val url = URL("$supabaseUrl/rest/v1/events")
                conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Authorization", "Bearer $anonKey")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("product", "App")
                    put("version", version)
                    put("user_id", userId)
                    put("platform", "android")
                    put("event", event)
                    meta?.let {
                        put("meta", JSONObject(it))
                    }
                }

                conn.outputStream.use { os ->
                    val input = body.toString().toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                    os.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode >= 400) {
                    val errorString = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Failed to send telemetry: $responseCode - $errorString")
                } else {
                    Log.d(TAG, "Telemetry sent successfully: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending telemetry", e)
            } finally {
                conn?.disconnect()
            }
        }
    }
}
