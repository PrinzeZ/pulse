(function () {
    const shell = document.querySelector('.app-shell.hierarchy, .app-shell.staff-content');
    const toggle = document.querySelector('.sidebar-toggle');
    if (!shell || !toggle) return;

    const key = 'pulse.sidebar.collapsed';

    const apply = (collapsed) => {
        document.body.classList.toggle('nav-collapsed', collapsed);
        toggle.setAttribute('aria-expanded', String(!collapsed));
        toggle.setAttribute('title', collapsed ? 'Expand navigation' : 'Collapse navigation');
    };

    try { apply(localStorage.getItem(key) === 'true'); } catch (_) { apply(false); }

    toggle.addEventListener('click', function () {
        const collapsed = !document.body.classList.contains('nav-collapsed');
        apply(collapsed);
        try { localStorage.setItem(key, String(collapsed)); } catch (_) {}
    });
})();
