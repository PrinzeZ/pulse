package com.pulse.local.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Fast circuit check used by the local sync worker. The hospital node must never
 * spend the database connection pool on repeated cloud calls when the Internet
 * is unavailable.
 */
@Service
@Profile("local")
public class CloudAvailabilityService {
    private final String cloudUrl;
    private final int timeoutMs;

    private volatile long lastCheckAt;
    private volatile boolean lastAvailable;

    public CloudAvailabilityService(
            @Value("${pulse.cloud-url:https://pulse-production-096d.up.railway.app}") String cloudUrl,
            @Value("${pulse.cloud.health-timeout-ms:1500}") int timeoutMs) {
        this.cloudUrl = cloudUrl == null ? "" : cloudUrl.trim();
        this.timeoutMs = Math.max(500, timeoutMs);
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < 10_000L) return lastAvailable;
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastCheckAt < 10_000L) return lastAvailable;
            lastAvailable = probe();
            lastCheckAt = now;
            return lastAvailable;
        }
    }

    private boolean probe() {
        if (cloudUrl.isBlank()) return false;
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(cloudUrl.endsWith("/") ? cloudUrl + "health" : cloudUrl + "/health");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            return connection.getResponseCode() >= 200 && connection.getResponseCode() < 500;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
