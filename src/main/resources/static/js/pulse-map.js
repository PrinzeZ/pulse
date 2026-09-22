(() => {
  const KERALA = [10.35, 76.27];
  const COLORS = { GREEN:'#21865b', YELLOW:'#d59a16', RED:'#d64b4b', CLOSED:'#7e8993', CONNECTED:'#138b97', NOT_CONNECTED:'#aeb8bf', UNKNOWN:'#8da3ad' };
  let maps = {};
  let mapData = [];
  let selectedMedicineId = null;
  let userLocation = null;
  let routeLayer = null;
  let userMarker = null;

  function markerIcon(item) {
    const color = COLORS[item.stockStatus] || COLORS.UNKNOWN;
    const classes = ['pulse-hospital-marker', item.registered ? 'is-connected' : 'is-unconnected', item.stockStatus.toLowerCase()];
    const html = `<div class="${classes.join(' ')}" style="--marker-color:${color}"><span>✚</span></div>`;
    return L.divIcon({ className:'pulse-marker-wrap', html, iconSize:[38,46], iconAnchor:[19,42], popupAnchor:[0,-38] });
  }

  function popup(item) {
    const statusLabel = item.stockStatus === 'GREEN' ? 'Stocked' : item.stockStatus === 'YELLOW' ? 'Limited stock' : item.stockStatus === 'RED' ? 'Low / empty' : item.stockStatus === 'CLOSED' ? 'Closed' : item.stockStatus === 'CONNECTED' ? 'Connected' : item.registered ? 'Connected' : 'Not connected';
    const qty = item.hasMedicineStock ? `<div class="map-popup-stock"><strong>${item.quantity}</strong><span>units reported</span></div>` : '';
    const connection = item.registered ? '<span class="map-popup-live">P.U.L.S.E connected</span>' : '<span class="map-popup-muted">Not yet connected</span>';
    return `<div class="map-popup"><div class="map-popup-kicker">${item.category}</div><h3>${escapeHtml(item.name)}</h3><p>${escapeHtml(item.district)} · ${escapeHtml(item.address)}</p><div class="map-popup-status"><span class="map-popup-dot" style="background:${COLORS[item.stockStatus] || COLORS.UNKNOWN}"></span>${statusLabel}${item.registered ? ' · live' : ''}</div>${qty}<div class="map-popup-actions"><a href="${item.googleMapsUrl}" target="_blank" rel="noopener">Open in Google Maps ↗</a><button type="button" data-route-key="${item.key}">Route here</button></div><div>${connection}</div></div>`;
  }

  function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[ch])); }

  async function loadData(medicineId = '') {
    const url = `/api/map/hospitals${medicineId ? `?medicineId=${encodeURIComponent(medicineId)}` : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Map data could not be loaded');
    const payload = await response.json();
    mapData = payload.hospitals || [];
    selectedMedicineId = payload.medicineId ?? null;
    renderMarkers();
    return payload;
  }

  function renderMarkers() {
    Object.values(maps).forEach(map => {
      if (!map.markerLayer) map.markerLayer = L.layerGroup().addTo(map);
      map.markerLayer.clearLayers();
      mapData.forEach(item => {
        const marker = L.marker([item.latitude, item.longitude], { icon: markerIcon(item), title:item.name }).bindPopup(popup(item));
        marker.on('popupopen', () => {
          const btn = document.querySelector(`[data-route-key="${CSS.escape(item.key)}"]`);
          if (btn) btn.addEventListener('click', () => routeTo(item));
        });
        map.markerLayer.addLayer(marker);
      });
    });
  }

  let tileConfigPromise;

  async function getTileConfig() {
    if (!tileConfigPromise) {
      tileConfigPromise = fetch('/api/map/config')
        .then(r => r.ok ? r.json() : {})
        .catch(() => ({}));
    }
    return tileConfigPromise;
  }

  async function createMap(element, mode) {
    const map = L.map(element, { zoomControl:true, scrollWheelZoom: mode === 'full', attributionControl:true });
    const config = await getTileConfig();
    if (config.cartoKey) {
      L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png?key=' + encodeURIComponent(config.cartoKey), {
        maxZoom:19,
        subdomains:'abcd',
        attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions" target="_blank" rel="noopener">CARTO</a>'
      }).addTo(map);
    } else {
      // No billing/no-key fallback so a missing CARTO key never leaves the map unusable.
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom:19,
        attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
      }).addTo(map);
    }
    map.setView(KERALA, mode === 'full' ? 7.2 : 7.4);
    maps[mode] = map;
    map.markerLayer = L.layerGroup().addTo(map);
    if (mode === 'preview') {
      map.scrollWheelZoom.disable();
      map.dragging.disable();
      map.doubleClickZoom.disable();
      map.touchZoom.disable();
    }
    setTimeout(() => map.invalidateSize(), 80);
    return map;
  }

  async function init() {
    const preview = document.getElementById('pulse-map-preview');
    const full = document.getElementById('pulse-map-full');
    if (!preview && !full) return;
    if (!window.L) return;
    if (preview) await createMap(preview, 'preview');
    if (full) await createMap(full, 'full');
    try { await loadData(); } catch (e) { console.warn(e); }
    if (full) await setupFullControls();
  }

  async function setupFullControls() {
    const list = document.getElementById('medicineOptions');
    const input = document.getElementById('medicineSearch');
    const searchButton = document.getElementById('mapSearch');
    const status = document.getElementById('mapSearchStatus');
    try {
      const response = await fetch('/api/map/medicines');
      const meds = await response.json();
      list.innerHTML = '';
      meds.forEach(m => {
        const option = document.createElement('option'); option.value = m.name; option.dataset.id = m.medicineId; list.appendChild(option);
      });
      input._pulseMedicines = meds;
    } catch (e) { status.textContent = 'Medicine list could not be loaded.'; }

    searchButton.addEventListener('click', () => searchMedicine());
    input.addEventListener('keydown', e => { if (e.key === 'Enter') searchMedicine(); });
    document.getElementById('locateMe')?.addEventListener('click', locateUser);
  }

  async function searchMedicine() {
    const input = document.getElementById('medicineSearch');
    const status = document.getElementById('mapSearchStatus');
    const meds = input._pulseMedicines || [];
    const term = input.value.trim().toLowerCase();
    const medicine = meds.find(m => m.name.toLowerCase() === term) || meds.find(m => m.name.toLowerCase().includes(term));
    if (!medicine) { status.textContent = 'Choose a medicine from the suggestions.'; return; }
    status.textContent = 'Checking connected government hospitals…';
    try {
      const payload = await loadData(medicine.medicineId);
      status.textContent = `${medicine.name}: ${payload.hospitals.filter(h => h.registered && !h.closed && h.hasMedicineStock && h.quantity > 0).length} connected hospitals reporting stock.`;
      await locateUser();
    } catch (e) { status.textContent = 'Could not load medicine availability.'; }
  }

  function locateUser() {
    return new Promise(resolve => {
      if (!navigator.geolocation) { showLocationError('Your browser does not support location.'); resolve(); return; }
      navigator.geolocation.getCurrentPosition(pos => {
        userLocation = [pos.coords.latitude, pos.coords.longitude];
        Object.values(maps).forEach(map => {
          if (!userMarker) userMarker = L.circleMarker(userLocation, { radius:7, weight:3, color:'#123e60', fillColor:'#fff', fillOpacity:1 }).addTo(map);
          else userMarker.setLatLng(userLocation);
          if (map.getContainer().id === 'pulse-map-full') map.setView(userLocation, 10);
        });
        if (selectedMedicineId) findNearest();
        resolve();
      }, err => { showLocationError(err.code === 1 ? 'Location permission is needed to find the nearest hospital.' : 'Could not get your current location.'); resolve(); }, { enableHighAccuracy:true, timeout:10000, maximumAge:60000 });
    });
  }

  function showLocationError(message) { const el=document.getElementById('mapSearchStatus'); if(el) el.textContent=message; }

  async function findNearest() {
    if (!userLocation) return;
    const candidates = mapData.filter(h => h.registered && !h.closed && h.hasMedicineStock && h.quantity > 0);
    if (!candidates.length) { showNearest(null, 'No connected government hospital is currently reporting stock for this medicine.'); return; }
    candidates.sort((a,b) => distanceKm(userLocation[0],userLocation[1],a.latitude,a.longitude) - distanceKm(userLocation[0],userLocation[1],b.latitude,b.longitude));
    const nearest = candidates[0];
    await routeTo(nearest, true);
  }

  async function routeTo(item, nearest=false) {
    if (!userLocation) { await locateUser(); if (!userLocation) return; }
    const map = maps.full || maps.preview;
    if (!map) return;
    const directKm = distanceKm(userLocation[0], userLocation[1], item.latitude, item.longitude);
    showNearest(item, nearest ? 'Nearest connected government hospital with stock.' : 'Route selected.');
    try {
      const url = `https://router.project-osrm.org/route/v1/driving/${userLocation[1]},${userLocation[0]};${item.longitude},${item.latitude}?overview=full&geometries=geojson`;
      const res = await fetch(url); const data = await res.json();
      const route = data.routes?.[0];
      if (route) {
        if (routeLayer) Object.values(maps).forEach(m => m.removeLayer(routeLayer));
        routeLayer = L.geoJSON(route.geometry, { style:{ color:'#0d6f86', weight:5, opacity:.82 } });
        routeLayer.addTo(map);
        map.fitBounds(routeLayer.getBounds(), { padding:[40,40], maxZoom:14 });
        showNearest(item, `${(route.distance/1000).toFixed(1)} km driving distance · ${(route.duration/60).toFixed(0)} min`);
        return;
      }
    } catch (e) { /* fall back to straight-line distance */ }
    map.setView([item.latitude,item.longitude], 13);
    showNearest(item, `${directKm.toFixed(1)} km straight-line distance`);
  }

  function showNearest(item, subtitle) {
    const card=document.getElementById('nearestCard'); if(!card) return;
    card.classList.remove('hidden');
    if (!item) { card.innerHTML=`<div class="nearest-kicker">SEARCH RESULT</div><strong>No current match</strong><p>${escapeHtml(subtitle)}</p>`; return; }
    card.innerHTML=`<div class="nearest-kicker">NEAREST MATCH</div><h3>${escapeHtml(item.name)}</h3><p>${escapeHtml(item.district)} · ${escapeHtml(subtitle)}</p><div class="nearest-stock"><span class="nearest-stock-dot" style="background:${COLORS[item.stockStatus] || COLORS.UNKNOWN}"></span><strong>${item.quantity}</strong> units reported</div><a href="${item.googleMapsUrl}" target="_blank" rel="noopener">Open hospital in Google Maps ↗</a>`;
  }

  function distanceKm(a,b,c,d) { const R=6371, p=Math.PI/180, x=(c-a)*p, y=(d-b)*p; const q=Math.sin(x/2)**2+Math.cos(a*p)*Math.cos(c*p)*Math.sin(y/2)**2; return 2*R*Math.asin(Math.sqrt(q)); }

  window.addEventListener('load', init);
})();
