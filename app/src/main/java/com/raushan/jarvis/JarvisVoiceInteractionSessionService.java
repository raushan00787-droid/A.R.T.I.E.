package com.raushan.jarvis;

import android.content.Intent;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class JarvisVoiceInteractionSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(android.os.Bundle args) {
        return new JarvisVoiceInteractionSession(this);
    }

    private static class JarvisVoiceInteractionSession extends VoiceInteractionSession {
        JarvisVoiceInteractionSession(JarvisVoiceInteractionSessionService service) {
            super(service);
        }

        @Override
        public void onShow(android.os.Bundle args, int showFlags) {
            super.onShow(args, showFlags);
            Intent launch = new Intent(getContext(), MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startAssistantActivity(launch);
        }
    }
}
