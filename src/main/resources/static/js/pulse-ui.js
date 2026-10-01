(function () {
  'use strict';

  const ensureLoading = () => {
    let overlay = document.getElementById('pulse-loading');
    if (overlay) return overlay;
    overlay = document.createElement('div');
    overlay.id = 'pulse-loading';
    overlay.className = 'pulse-loading';
    overlay.setAttribute('aria-hidden', 'true');
    overlay.innerHTML = `
      <div class="pulse-loading-card" role="status" aria-live="polite" aria-label="P.U.L.S.E is loading">
        <div class="pulse-loading-pills" aria-hidden="true"><span class="pulse-pill pulse-pill-a"></span><span class="pulse-pill pulse-pill-b"></span><span class="pulse-pill pulse-pill-c"></span></div>
        <div class="pulse-carebot pulse-carebot-loading" aria-hidden="true">
          <div class="carebot-head"><span class="carebot-eye carebot-eye-l"></span><span class="carebot-eye carebot-eye-r"></span><span class="carebot-mouth"></span></div>
          <div class="carebot-body"><div class="carebot-chest"><svg viewBox="0 0 220 52" preserveAspectRatio="none"><polyline points="0,27 37,27 53,27 67,27 80,7 94,46 108,27 145,27 162,27 175,17 187,37 199,27 220,27"></polyline></svg></div></div>
          <div class="carebot-arm carebot-arm-l"></div><div class="carebot-arm carebot-arm-r"></div>
          <div class="carebot-leg carebot-leg-l"></div><div class="carebot-leg carebot-leg-r"></div>
        </div>
        <div class="pulse-loader-title">P.U.L.S.E</div>
        <div class="pulse-loader-subtitle">Checking the system pulse…</div>
      </div>`;
    document.body.appendChild(overlay);
    return overlay;
  };

  const show = () => {
    const overlay = ensureLoading();
    overlay.classList.add('is-visible');
  };
  const hide = () => {
    const overlay = document.getElementById('pulse-loading');
    if (overlay) overlay.classList.remove('is-visible');
  };

  window.PulseLoading = { show, hide };

  const installNavigationLoading = () => {
    document.addEventListener('click', (event) => {
      const link = event.target.closest('a');
      if (!link || event.defaultPrevented) return;
      if (link.target === '_blank' || link.hasAttribute('download') || link.dataset.noPulseLoading !== undefined) return;
      const href = link.href || '';
      if (!href || href.startsWith('mailto:') || href.startsWith('tel:') || link.hash && href.split('#')[0] === location.href.split('#')[0]) return;
      try {
        const destination = new URL(href, location.href);
        if (destination.origin !== location.origin && !destination.hostname.endsWith('railway.app') && !destination.hostname.endsWith('up.railway.app')) return;
      } catch (_) { return; }
      show();
    }, true);

    document.addEventListener('submit', (event) => {
      if (event.defaultPrevented) return;
      const form = event.target;
      if (!(form instanceof HTMLFormElement) || form.dataset.noPulseLoading !== undefined) return;
      if (form.method.toLowerCase() === 'dialog') return;
      show();
    }, true);

    window.addEventListener('pageshow', hide);
    window.addEventListener('load', () => setTimeout(hide, 120));
  };

  const installCookieNotice = () => {
    let accepted = false;
    try { accepted = localStorage.getItem('pulse.essential-cookie-notice.v1') === 'seen'; } catch (_) {}
    if (accepted || document.getElementById('pulse-cookie-notice')) return;

    const banner = document.createElement('aside');
    banner.id = 'pulse-cookie-notice';
    banner.className = 'pulse-cookie-notice';
    banner.innerHTML = `
      <div><strong>Essential cookies only</strong><p>P.U.L.S.E uses necessary session and CSRF cookies for secure sign-in. We do not use advertising or tracking cookies.</p></div>
      <div class="pulse-cookie-actions"><a href="/privacy">Privacy Policy</a><button type="button">Continue</button></div>`;
    document.body.appendChild(banner);
    banner.querySelector('button').addEventListener('click', () => {
      try { localStorage.setItem('pulse.essential-cookie-notice.v1', 'seen'); } catch (_) {}
      banner.remove();
    });
  };

  const boot = () => {
    ensureLoading();
    installNavigationLoading();
    installCookieNotice();
    hide();
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
