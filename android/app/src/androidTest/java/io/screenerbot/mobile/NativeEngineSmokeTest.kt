package io.screenerbot.mobile

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NativeEngineSmokeTest {
    @Test
    fun nativeEngineStartsAuthenticatedLoopbackDashboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataDirectory = File(context.filesDir, "runtime-smoke-test").apply {
            check(mkdirs() || isDirectory) { "Could not create test data directory: $absolutePath" }
        }
        val machineIdentifier = AndroidIdentity.getOrCreateMachineIdentifier(context)

        assertTrue(
            "JNI rejected engine startup. diagnostics=${NativeEngine.startupDiagnostics()}",
            NativeEngine.start(dataDirectory.absolutePath, machineIdentifier)
        )

        val deadline = SystemClock.elapsedRealtime() + 120_000L
        var connection: String? = null
        var lastDiagnostics = "No diagnostics yet"

        while (SystemClock.elapsedRealtime() < deadline) {
            lastDiagnostics = NativeEngine.startupDiagnostics() ?: "No startup diagnostics"
            val candidate = NativeEngine.dashboardConnection()
            val parts = candidate?.split(":", limit = 2)
            val port = parts?.getOrNull(0)?.toIntOrNull()
            val token = parts?.getOrNull(1)
            if (port != null && port in 1..65535 && !token.isNullOrBlank()) {
                connection = candidate
                break
            }

            val startupError = NativeEngine.startupError()
            if (!startupError.isNullOrBlank()) {
                throw AssertionError("Native engine startup failed: $startupError; $lastDiagnostics")
            }
            SystemClock.sleep(500)
        }

        assertNotNull(
            "Engine did not expose a valid local port and session token within 120 seconds. $lastDiagnostics",
            connection
        )

        val parts = connection!!.split(":", limit = 2)
        val port = parts[0].toInt()
        val token = parts[1]
        val request = URL("http://127.0.0.1:$port/api/health").openConnection() as HttpURLConnection
        try {
            request.connectTimeout = 5_000
            request.readTimeout = 5_000
            request.setRequestProperty("X-ScreenerBot-Token", token)
            val status = request.responseCode
            assertTrue(
                "Local authenticated health check failed: HTTP $status; $lastDiagnostics",
                status in 200..299
            )
        } finally {
            request.disconnect()
        }
        assertTrue("Local dashboard did not retain a valid port. $lastDiagnostics", port > 0)
        assertEquals("Expected authenticated local dashboard port", port, parts[0].toInt())
    }
}
