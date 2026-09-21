(function () {
    const key = 'pulse.admin.sidebar.collapsed';
    const shell = document.querySelector('.app-shell.hierarchy');
    const toggle = document.querySelector('.sidebar-toggle');
    if (!shell || !toggle) return;

    const apply = (collapsed) => {
        document.body.classList.toggle('nav-collapsed', collapsed);
        toggle.setAttribute('aria-expanded', String(!collapsed));
        toggle.textContent = collapsed ? '☰' : '☰';
    };

    try { apply(localStorage.getItem(key) === 'true'); } catch (_) {}

    toggle.addEventListener('click', function () {
        const collapsed = !document.body.classList.contains('nav-collapsed');
        apply(collapsed);
        try { localStorage.setItem(key, String(collapsed)); } catch (_) {}
    });
})();
