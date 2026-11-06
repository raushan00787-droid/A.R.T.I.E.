package com.raushan.jarvis

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Android binds to this service to create a voice interaction session.
 *
 * Lifecycle:
 * - Android owns the service lifecycle and calls onNewSession() when it
 *   needs a new interaction surface.
 * - The returned session owns the UI lifecycle for that interaction.
 */
class JarvisVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d(TAG, "Creating JARVIS voice interaction session")
        return JarvisVoiceInteractionSession(this)
    }

    private companion object {
        const val TAG = "JarvisSessionService"
    }
}
