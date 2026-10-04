package com.nexus.pdv.support;

import com.nexus.pdv.auth.application.PasswordResetNotifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    RecordingPasswordResetNotifier recordingPasswordResetNotifier() {
        return new RecordingPasswordResetNotifier();
    }

    /** Captura os links de redefinição de senha (em vez de enviar e-mail). */
    public static class RecordingPasswordResetNotifier implements PasswordResetNotifier {

        private final Map<String, String> links = new ConcurrentHashMap<>();

        @Override
        public void sendResetLink(String email, String name, String link) {
            links.put(email, link);
        }

        public String tokenFor(String email) {
            String link = links.get(email);
            return link == null ? null : link.substring(link.indexOf("token=") + 6);
        }
    }
}
