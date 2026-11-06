package com.raushan.jarvis

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * System-managed entry point for JARVIS as the selected digital assistant.
 *
 * Lifecycle:
 * - Android binds/creates this service when the selected voice interaction
 *   implementation is needed.
 * - onReady() means the system has finished initializing this service.
 * - onShutdown() is the system's shutdown callback.
 *
 * Do not start an always-running microphone loop here. Audio capture must be
 * user-initiated and follow Android microphone/foreground-service restrictions.
 */
class JarvisVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "JARVIS voice interaction service ready")
    }

    override fun onShutdown() {
        Log.i(TAG, "JARVIS voice interaction service shutting down")
        super.onShutdown()
    }

    private companion object {
        const val TAG = "JarvisVoiceService"
    }
}
