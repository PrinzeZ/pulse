package com.pulse.local.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Publishes the current local P.U.L.S.E LAN endpoint to the Railway instance.
 * Only active with the local Spring profile.
 */
@Service
@Profile("local")
public class LanRegistrationService {

    private final String cloudUrl;
    private final String lanIp;
    private final String registrationToken;
    private String lastLoggedLanUrl = "";
    private long lastSuccessLogAt = 0L;
    private boolean lastAttemptFailed = false;
    private long lastFailureLogAt = 0L;
    private static final long SUCCESS_HEARTBEAT_MILLIS = 300_000L;
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 120_000L;

    public LanRegistrationService(
            @Value("${pulse.cloud-url:https://pulse-production-096d.up.railway.app}") String cloudUrl,
            @Value("${PULSE_LAN_IP:}") String lanIp,
            @Value("${PULSE_LAN_REGISTRATION_TOKEN:}") String registrationToken) {
        this.cloudUrl = trimTrailingSlash(cloudUrl);
        this.lanIp = lanIp == null ? "" : lanIp.trim();
        this.registrationToken = registrationToken == null ? "" : registrationToken.trim();
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 10000)
    public void register() {
        if (lanIp.isBlank()) {
            long now = System.currentTimeMillis();
            if (now - lastFailureLogAt >= FAILURE_LOG_INTERVAL_MILLIS || !lastAttemptFailed) {
                System.out.println("P.U.L.S.E LAN registration skipped: no PULSE_LAN_IP was detected.");
                lastFailureLogAt = now;
            }
            lastAttemptFailed = true;
            return;
        }

        String lanUrl = "http://" + lanIp + ":8080";
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(cloudUrl + "/api/public/lan/register").toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            if (!registrationToken.isBlank()) {
                connection.setRequestProperty("X-Pulse-Lan-Token", registrationToken);
            }
            byte[] body = lanUrl.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            connection.getOutputStream().write(body);

            int status = connection.getResponseCode();
            long now = System.currentTimeMillis();
            if (status >= 200 && status < 300) {
                boolean endpointChanged = !lanUrl.equals(lastLoggedLanUrl);
                boolean recovered = lastAttemptFailed;
                boolean heartbeatDue = now - lastSuccessLogAt >= SUCCESS_HEARTBEAT_MILLIS;
                if (endpointChanged || recovered || heartbeatDue) {
                    System.out.println("P.U.L.S.E LAN endpoint registered: " + lanUrl);
                    lastLoggedLanUrl = lanUrl;
                    lastSuccessLogAt = now;
                }
                lastAttemptFailed = false;
            } else {
                if (now - lastFailureLogAt >= FAILURE_LOG_INTERVAL_MILLIS || !lastAttemptFailed) {
                    System.out.println("P.U.L.S.E LAN registration returned HTTP " + status);
                    lastFailureLogAt = now;
                }
                lastAttemptFailed = true;
            }
        } catch (IOException | IllegalArgumentException ex) {
            long now = System.currentTimeMillis();
            if (now - lastFailureLogAt >= FAILURE_LOG_INTERVAL_MILLIS || !lastAttemptFailed) {
                System.out.println("P.U.L.S.E LAN registration unavailable: " + ex.getMessage());
                lastFailureLogAt = now;
            }
            lastAttemptFailed = true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replaceAll("/+$", "");
    }
}
