package com.pulse.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-process brute-force guard for username/password authentication. */
@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(10);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) return false;
        Instant now = Instant.now();
        if (attempt.lockedUntil != null && now.isBefore(attempt.lockedUntil)) return true;
        if (attempt.firstFailure.plus(WINDOW).isBefore(now)) {
            attempts.remove(key);
            return false;
        }
        return false;
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, current) -> {
            if (current == null || current.firstFailure.plus(WINDOW).isBefore(now)) {
                return new Attempt(now, 1, null);
            }
            int failures = current.failures + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCKOUT) : current.lockedUntil;
            return new Attempt(current.firstFailure, failures, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private record Attempt(Instant firstFailure, int failures, Instant lockedUntil) {}
}
