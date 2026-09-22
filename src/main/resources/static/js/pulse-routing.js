(function () {
    'use strict';

    // P.U.L.S.E smart connection routing.
    // Priority: localhost -> stable LAN hostname -> Railway.
    // A browser cannot enumerate arbitrary LAN addresses, so LAN uses the
    // stable pulse.local hostname configured once on each client.
    const CLOUD_URL = 'https://pulse-production-096d.up.railway.app/';
    const LOCAL_URL = 'http://localhost:8080';
    // Stable LAN hostname. Run setup-lan-hostname.ps1 once on each LAN client.
    const LAN_URL = 'http://pulse.local:8080';
    const CHECK_INTERVAL = 5000;
    const PROBE_TIMEOUT = 1200;
    const LAN_KEY = 'pulse.lastLanUrl';
    const ROUTE_KEY = 'pulse.routing.enabled';

    try {
        if (localStorage.getItem(ROUTE_KEY) === 'false') return;
    } catch (_) {}

    const normalize = (value) => {
        try {
            const u = new URL(value);
            return u.origin;
        } catch (_) {
            return null;
        }
    };

    const currentOrigin = window.location.origin;
    const host = window.location.hostname;
    const cloudOrigin = normalize(CLOUD_URL);

    const isLocal = host === 'localhost' || host === '127.0.0.1';
    const isPrivateIpv4 = /^(10|192\.168)\./.test(host) ||
        /^172\.(1[6-9]|2\d|3[01])\./.test(host);
    const isCloud = currentOrigin === cloudOrigin ||
        host.endsWith('.up.railway.app') ||
        host.endsWith('.railway.app');

    const isLan = !isLocal && !isCloud && (isPrivateIpv4 || host === 'pulse.local');

    const rememberLan = () => {
        if (isLan) {
            try { localStorage.setItem(LAN_KEY, currentOrigin); } catch (_) {}
        }
    };

    // Legacy LAN handoff support for older sessions.
    try {
        const params = new URLSearchParams(window.location.search);
        const lan = normalize(params.get('pulseLan'));
        if (lan && lan !== cloudOrigin && lan !== LOCAL_URL) {
            localStorage.setItem(LAN_KEY, lan);
            params.delete('pulseLan');
            const query = params.toString();
            history.replaceState({}, '', window.location.pathname + (query ? '?' + query : '') + window.location.hash);
        }
    } catch (_) {}

    rememberLan();

    const withPath = (origin, carryLan) => {
        const target = new URL(window.location.href);
        target.protocol = new URL(origin).protocol;
        target.host = new URL(origin).host;
        if (carryLan) {
            target.searchParams.set('pulseLan', currentOrigin);
        }
        return target.toString();
    };

    const probe = async (origin) => {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), PROBE_TIMEOUT);
        try {
            // no-cors is intentional: this is only a reachability probe.
            // The endpoint response does not need to be readable.
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

    let redirecting = false;

    const go = (origin, carryLan) => {
        if (redirecting) return;
        const normalized = normalize(origin);
        if (!normalized || normalized === currentOrigin) return;
        redirecting = true;
        window.location.replace(withPath(normalized, carryLan));
    };

    const startupRoute = async () => {
        // Never redirect a local page to itself. It should only fail over
        // outward if the local server stops responding.
        if (isLocal) return;

        // If the local hospital server is running, it always wins.
        if (await probe(LOCAL_URL)) {
            go(LOCAL_URL, false);
            return;
        }

        // Prefer the stable LAN hostname over Railway.
        if (isCloud) {
            // Stable LAN hostname is preferred over any previously remembered
            // address, so the LAN IP can change without breaking routing.
            if (await probe(LAN_URL)) {
                go(LAN_URL, false);
                return;
            }
        }
    };

    const failoverLoop = async () => {
        if (redirecting) return;

        if (isLocal) {
            if (await probe(currentOrigin)) return;
            // The local server disappeared. Railway is the final fallback.
            go(cloudOrigin, false);
            return;
        }

        if (isLan) {
            // If the same machine's faster localhost server becomes available,
            // move there even while the browser is currently using LAN.
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL, false);
                return;
            }

            // Keep the LAN server while it is healthy.
            if (await probe(currentOrigin)) return;

            // LAN disappeared: fall back to Railway.
            go(cloudOrigin, true);
            return;
        }

        if (isCloud) {
            // Keep checking for a faster local instance appearing.
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL, false);
                return;
            }

            // Stable LAN hostname: no manual IP entry is required after the
            // one-time client hosts setup.
            if (await probe(LAN_URL)) {
                go(LAN_URL, false);
            }
        }
    };

    // Give the current page a moment to render, then perform the first route
    // decision. Subsequent checks make failover and recovery automatic.
    setTimeout(startupRoute, 150);
    setInterval(failoverLoop, CHECK_INTERVAL);
})();
