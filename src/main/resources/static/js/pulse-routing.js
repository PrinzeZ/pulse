(function () {
    'use strict';

    // P.U.L.S.E smart routing:
    //   localhost -> current LAN -> Railway
    //
    // The current LAN URL is registered by the local P.U.L.S.E process with
    // Railway. Nothing is hard-coded and there is no pulse.local/mDNS dependency.
    const CLOUD_URL = 'https://pulse-production-096d.up.railway.app';
    const LOCAL_URL = 'http://localhost:8080';
    const CHECK_INTERVAL = 5000;
    const PROBE_TIMEOUT = 2200;

    const normalize = (value) => {
        try {
            const url = new URL(value);
            url.hash = '';
            url.search = '';
            return url.origin;
        } catch (_) {
            return null;
        }
    };

    const currentOrigin = window.location.origin;
    const host = window.location.hostname;
    const cloudOrigin = normalize(CLOUD_URL);
    const isLocal = host === 'localhost' || host === '127.0.0.1';
    const isPrivateIpv4 = /^(10|192\.168)\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host);
    const isCloud = currentOrigin === cloudOrigin || host.endsWith('.railway.app') || host.endsWith('.up.railway.app');
    const isLan = !isLocal && !isCloud && isPrivateIpv4;
    let redirecting = false;

    // Preserve the current P.U.L.S.E path/query/hash while replacing only the origin.
    // Explicitly clear the destination port so Railway never becomes :8080.
    const withPath = (origin) => {
        const target = new URL(window.location.href);
        const destination = new URL(origin);
        target.protocol = destination.protocol;
        target.hostname = destination.hostname;
        target.port = destination.port;
        target.username = '';
        target.password = '';
        return target.toString();
    };

    const probe = async (origin) => {
        const normalized = normalize(origin);
        if (!normalized) return false;

        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), PROBE_TIMEOUT);
        try {
            // no-cors is intentional: we only need to know whether the endpoint
            // is reachable. The local server supplies PNA headers for Chromium.
            await fetch(normalized + '/health?pulseProbe=1&ts=' + Date.now(), {
                method: 'GET',
                mode: 'no-cors',
                cache: 'no-store',
                credentials: 'omit',
                signal: controller.signal
            });
            return true;
        } catch (_) {
            return false;
        } finally {
            clearTimeout(timer);
        }
    };

    const getRegisteredLan = async () => {
        if (!isCloud) return null;
        try {
            const response = await fetch('/api/public/lan?ts=' + Date.now(), {
                method: 'GET',
                cache: 'no-store',
                credentials: 'same-origin'
            });
            if (!response.ok) return null;
            const data = await response.json();
            return data.available ? normalize(data.url) : null;
        } catch (_) {
            return null;
        }
    };

    const go = (origin) => {
        if (redirecting) return;
        const normalized = normalize(origin);
        if (!normalized || normalized === currentOrigin) return;
        redirecting = true;
        window.location.replace(withPath(normalized));
    };

    const startupRoute = async () => {
        if (isLocal) return;

        // Always prefer localhost when the same device is running P.U.L.S.E.
        if (await probe(LOCAL_URL)) {
            go(LOCAL_URL);
            return;
        }

        // When the page was opened from Railway, ask Railway for the laptop's
        // currently registered LAN URL and test it from this device.
        if (isCloud) {
            const lanUrl = await getRegisteredLan();
            if (lanUrl && await probe(lanUrl)) {
                go(lanUrl);
            }
        }
    };

    const failoverLoop = async () => {
        if (redirecting) return;

        if (isLocal) {
            // If localhost disappears, fall back to Railway (never :8080).
            if (await probe(currentOrigin)) return;
            go(cloudOrigin);
            return;
        }

        if (isLan) {
            // Prefer localhost if it becomes available on this device.
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL);
                return;
            }

            // Stay on LAN while it is reachable.
            if (await probe(currentOrigin)) return;

            // LAN is gone: use the public Railway origin, with no :8080.
            go(cloudOrigin);
            return;
        }

        if (isCloud) {
            // Same-device localhost wins.
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL);
                return;
            }

            // Otherwise discover and test the currently registered LAN endpoint.
            const lanUrl = await getRegisteredLan();
            if (lanUrl && await probe(lanUrl)) {
                go(lanUrl);
            }
        }
    };

    setTimeout(startupRoute, 150);
    setInterval(failoverLoop, CHECK_INTERVAL);
})();
