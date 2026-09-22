(function () {
    'use strict';

    // P.U.L.S.E smart routing: localhost -> current LAN -> Railway.
    // LAN IP is discovered at runtime from the Railway registration endpoint;
    // no pulse.local/mDNS and no hard-coded private IP are used.
    const CLOUD_URL = 'https://pulse-production-096d.up.railway.app/';
    const LOCAL_URL = 'http://localhost:8080';
    const CHECK_INTERVAL = 5000;
    const PROBE_TIMEOUT = 1800;

    const normalize = (value) => {
        try { return new URL(value).origin; } catch (_) { return null; }
    };

    const currentOrigin = window.location.origin;
    const host = window.location.hostname;
    const cloudOrigin = normalize(CLOUD_URL);
    const isLocal = host === 'localhost' || host === '127.0.0.1';
    const isPrivateIpv4 = /^(10|192\.168)\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host);
    const isCloud = currentOrigin === cloudOrigin || host.endsWith('.railway.app') || host.endsWith('.up.railway.app');
    const isLan = !isLocal && !isCloud && isPrivateIpv4;
    let redirecting = false;

    const withPath = (origin) => {
        const target = new URL(window.location.href);
        const destination = new URL(origin);
        target.protocol = destination.protocol;
        target.host = destination.host;
        return target.toString();
    };

    const probe = async (origin) => {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), PROBE_TIMEOUT);
        try {
            await fetch(origin + '/health?pulseProbe=1', {
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

        // Localhost is always the fastest option when the same device has it.
        if (await probe(LOCAL_URL)) {
            go(LOCAL_URL);
            return;
        }

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
            if (await probe(currentOrigin)) return;
            go(cloudOrigin);
            return;
        }

        if (isLan) {
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL);
                return;
            }
            if (await probe(currentOrigin)) return;
            go(cloudOrigin);
            return;
        }

        if (isCloud) {
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL);
                return;
            }
            const lanUrl = await getRegisteredLan();
            if (lanUrl && await probe(lanUrl)) {
                go(lanUrl);
            }
        }
    };

    setTimeout(startupRoute, 150);
    setInterval(failoverLoop, CHECK_INTERVAL);
})();
