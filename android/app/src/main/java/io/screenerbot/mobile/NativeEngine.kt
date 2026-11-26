package io.screenerbot.mobile

internal object NativeEngine {
    init {
        System.loadLibrary("screenerbot")
    }

    external fun start(dataDirectory: String, machineIdentifier: String): Boolean
    external fun dashboardConnection(): String?
    external fun startupError(): String?
    external fun startupDiagnostics(): String?
}
