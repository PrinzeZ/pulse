(function () {
    'use strict';

    // P.U.L.S.E Smart Connection Routing
    //
    // Priority:
    //   1. localhost
    //   2. LAN (pulse.local)
    //   3. Railway
    //
    // Automatic recovery:
    //   Railway -> localhost -> LAN
    //   LAN     -> localhost
    //   Local   -> LAN -> Railway
    //
    // No LAN URL is ever appended to the Railway URL.

    const CLOUD_URL = 'https://pulse-production-096d.up.railway.app/';
    const LOCAL_URL = 'http://localhost:8080';
    const LAN_URL = 'http://pulse.local:8080';

    const CHECK_INTERVAL = 5000;
    const PROBE_TIMEOUT = 1200;
    const ROUTE_KEY = 'pulse.routing.enabled';

    try {
        if (localStorage.getItem(ROUTE_KEY) === 'false') {
            return;
        }
    } catch (_) {}

    const normalize = (value) => {
        try {
            return new URL(value).origin;
        } catch (_) {
            return null;
        }
    };

    const currentOrigin = window.location.origin;
    const host = window.location.hostname;
    const cloudOrigin = normalize(CLOUD_URL);
    const localOrigin = normalize(LOCAL_URL);
    const lanOrigin = normalize(LAN_URL);

    const isLocal =
        host === 'localhost' ||
        host === '127.0.0.1';

    const isCloud =
        currentOrigin === cloudOrigin ||
        host.endsWith('.up.railway.app') ||
        host.endsWith('.railway.app');

    const isLan =
        currentOrigin === lanOrigin ||
        host === 'pulse.local' ||
        /^(10|192\.168)\./.test(host) ||
        /^172\.(1[6-9]|2\d|3[01])\./.test(host);

    /*
     * Preserve the page the user is currently viewing.
     *
     * Example:
     *   /staff/dashboard
     *
     * becomes:
     *   http://pulse.local:8080/staff/dashboard
     *
     * No pulseLan query parameter is added.
     */
    const buildTargetUrl = (origin) => {
        const target = new URL(origin);

        target.pathname = window.location.pathname;
        target.search = window.location.search;
        target.hash = window.location.hash;

        return target.toString();
    };

    /*
     * Simple reachability check.
     *
     * /health is used only to determine whether the server is reachable.
     * The response itself does not need to be readable by the browser.
     */
    const probe = async (origin) => {
        const controller = new AbortController();

        const timer = setTimeout(() => {
            controller.abort();
        }, PROBE_TIMEOUT);

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

    let redirecting = false;

    /*
     * Redirect to another P.U.L.S.E server.
     */
    const go = (origin) => {
        if (redirecting) {
            return;
        }

        const normalized = normalize(origin);

        if (!normalized || normalized === currentOrigin) {
            return;
        }

        redirecting = true;

        const target = buildTargetUrl(normalized);

        console.log('[P.U.L.S.E] Switching connection:', {
            from: currentOrigin,
            to: normalized
        });

        window.location.replace(target);
    };

    /*
     * Initial routing decision.
     *
     * If the user opens Railway:
     *   localhost -> LAN -> stay on Railway
     *
     * If the user opens LAN:
     *   localhost -> stay on LAN
     *
     * If the user opens localhost:
     *   stay on localhost
     */
    const startupRoute = async () => {
        if (redirecting) {
            return;
        }

        // Localhost is already the highest-priority connection.
        if (isLocal) {
            return;
        }

        // Check localhost first.
        if (await probe(LOCAL_URL)) {
            go(LOCAL_URL);
            return;
        }

        // If localhost is unavailable, check LAN.
        if (!isLan && await probe(LAN_URL)) {
            go(LAN_URL);
            return;
        }
    };

    /*
     * Continuous automatic failover/recovery.
     */
    const failoverLoop = async () => {
        if (redirecting) {
            return;
        }

        /*
         * CURRENT = LOCALHOST
         *
         * Local is highest priority.
         * If it dies:
         *   localhost -> LAN -> Railway
         */
        if (isLocal) {
            if (await probe(currentOrigin)) {
                return;
            }

            if (await probe(LAN_URL)) {
                go(LAN_URL);
                return;
            }

            go(cloudOrigin);
            return;
        }

        /*
         * CURRENT = LAN
         *
         * If localhost becomes available:
         *   LAN -> localhost
         *
         * If LAN dies:
         *   LAN -> Railway
         */
        if (isLan) {
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL);
                return;
            }

            if (await probe(currentOrigin)) {
                return;
            }

            go(cloudOrigin);
            return;
        }

        /*
         * CURRENT = RAILWAY
         *
         * Continuously look for:
         *   localhost first
         *   LAN second
         *
         * This means a phone sitting on Railway will automatically
         * move to LAN when the LAN server becomes available.
         */
        if (isCloud) {
            if (await probe(LOCAL_URL)) {
                go(LOCAL_URL);
                return;
            }

            if (await probe(LAN_URL)) {
                go(LAN_URL);
                return;
            }
        }
    };

    /*
     * First routing check shortly after page load.
     */
    setTimeout(startupRoute, 150);

    /*
     * Keep checking every 5 seconds.
     */
    setInterval(failoverLoop, CHECK_INTERVAL);

})();