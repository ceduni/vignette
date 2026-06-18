<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from "vue";
import {geoNaturalEarth1, geoPath} from "d3-geo";
import {feature as topojsonFeature} from "topojson-client";
import {Search} from "lucide-vue-next";

import worldAtlas110m from "world-atlas/countries-110m.json";
import worldCountries from "world-countries";

import {useLanguageStore} from "@/composables/useLanguageStore";

const MAP_WIDTH = 1200;
const MAP_HEIGHT = 640;
const ASPECT = MAP_WIDTH / MAP_HEIGHT;
const MIN_VIEWBOX_WIDTH = 260;
const MAX_VIEWBOX_WIDTH = MAP_WIDTH;
const MAP_ZOOM_ENABLED = true;

const store = useLanguageStore();
const countrySearch = ref("");
const hideSearchSuggestions = ref(false);

const svgRef = ref<SVGSVGElement | null>(null);
const rafId = ref<number | null>(null);
const interactionEndTimer = ref<number | null>(null);
const isInteracting = ref(false);

const currentView = reactive({x: 0, y: 0, w: MAP_WIDTH, h: MAP_HEIGHT});
const targetView = reactive({x: 0, y: 0, w: MAP_WIDTH, h: MAP_HEIGHT});

const dragState = reactive({
  active: false,
  pointerId: -1,
  startX: 0,
  startY: 0,
  lastX: 0,
  lastY: 0,
  moved: false,
  downCountryIso: "",
});

const pointers = new Map<number, {clientX: number; clientY: number}>();
const pinchState = reactive({
  active: false,
  startDist: 0,
  startMidX: 0,
  startMidY: 0,
  startW: MAP_WIDTH,
  startH: MAP_HEIGHT,
  startX: 0,
  startY: 0,
});

const byNumericCode = new Map(
    worldCountries
        .filter((country) => typeof country?.ccn3 === "string" && country.ccn3)
        .map((country) => [country.ccn3, country])
);

const collection = topojsonFeature(worldAtlas110m, worldAtlas110m.objects.countries);
const projection = geoNaturalEarth1().fitExtent(
    [[24, 20], [MAP_WIDTH - 24, MAP_HEIGHT - 84]],
    collection
);
const path = geoPath(projection);

const countryFeatures = collection.features
    .map((shape) => {
      const numericCode = String(shape.id ?? "").padStart(3, "0");
      const info = byNumericCode.get(numericCode);
      const isoA3 = info?.cca3 ?? "";
      const centroid = path.centroid(shape);

      return {
        id: numericCode,
        isoA3,
        name: info?.name?.common || shape?.properties?.name || "Unknown",
        d: path(shape) || "",
        cx: centroid?.[0] ?? 0,
        cy: centroid?.[1] ?? 0,
      };
    })
    .filter((country) => country.isoA3 && country.d);

const countryByIso = new Map(countryFeatures.map((country) => [country.isoA3, country]));

const viewBoxValue = computed(() => `${currentView.x} ${currentView.y} ${currentView.w} ${currentView.h}`);

const highlightedSet = computed(() => new Set(store.highlightedCountryIds.value));
const activeLanguagePinPoint = computed(() => {
  const pin = store.activeLanguagePin.value;
  if (!pin) return null;
  if (!Number.isFinite(pin.latitude) || !Number.isFinite(pin.longitude)) return null;
  if (pin.latitude < -90 || pin.latitude > 90 || pin.longitude < -180 || pin.longitude > 180) return null;

  const point = projection([pin.longitude, pin.latitude]);
  if (!point || point.length < 2) return null;

  return {
    x: point[0],
    y: point[1],
    name: pin.name,
  };
});

const clusterFeatures = computed(() => {
  return countryFeatures
      .map((country) => ({
        ...country,
        count: Number(store.clusterCountByIso.value[country.isoA3] ?? 0),
      }))
      .filter((country) => country.count > 0);
});

const showLanguageCountryNames = computed(() => {
  return store.highlightedCountryIds.value.length > 0 || !!store.activeCountryId.value;
});

const highlightedCountryNames = computed(() => {
  const fromHighlighted = store.highlightedCountryIds.value
      .map((isoA3) => countryByIso.get(isoA3)?.name || isoA3)
      .filter(Boolean);

  if (fromHighlighted.length) return fromHighlighted;

  const activeIso = String(store.activeCountryId.value || "").toUpperCase();
  if (!activeIso) return [];
  return [countryByIso.get(activeIso)?.name || activeIso];
});

const countryNamesText = computed(() => {
  if (!highlightedCountryNames.value.length) return "Aucun pays detecte";
  return highlightedCountryNames.value.join(" • ");
});

const normalizedCountrySearch = computed(() => String(countrySearch.value ?? "").trim().toLowerCase());

const countrySearchMatches = computed(() => {
  if (!normalizedCountrySearch.value) return [];
  return countryFeatures
      .filter((country) => {
        const haystack = `${country.name} ${country.isoA3}`.toLowerCase();
        return haystack.includes(normalizedCountrySearch.value);
      })
      .slice(0, 8);
});

function focusCountryFromSearch(nextIsoA3?: string) {
  const candidate = String(nextIsoA3 ?? "").toUpperCase();
  const fallback = countrySearchMatches.value[0]?.isoA3;
  const isoA3 = candidate || fallback;
  if (!isoA3) return;

  const hit = countryByIso.get(isoA3);
  if (!hit) return;

  countrySearch.value = hit.name;
  hideSearchSuggestions.value = true;
  store.activateCountryFromMap(isoA3);
}

function onSearchEnter() {
  if (!normalizedCountrySearch.value) return;

  const upperQuery = normalizedCountrySearch.value.toUpperCase();
  const exactIso = countryFeatures.find((country) => country.isoA3 === upperQuery)?.isoA3;
  if (exactIso) {
    focusCountryFromSearch(exactIso);
    return;
  }

  const exactName = countryFeatures.find(
      (country) => country.name.toLowerCase() === normalizedCountrySearch.value
  )?.isoA3;

  focusCountryFromSearch(exactName);
}

function onSearchInput() {
  hideSearchSuggestions.value = false;
}

function clampView(x: number, y: number, w: number, h: number) {
  const clampedW = Math.min(Math.max(w, MIN_VIEWBOX_WIDTH), MAX_VIEWBOX_WIDTH);
  const clampedH = clampedW / ASPECT;

  const maxX = MAP_WIDTH - clampedW;
  const maxY = MAP_HEIGHT - clampedH;

  return {
    x: Math.min(Math.max(x, 0), Math.max(maxX, 0)),
    y: Math.min(Math.max(y, 0), Math.max(maxY, 0)),
    w: clampedW,
    h: clampedH,
  };
}

function setTargetView(next: {x: number; y: number; w: number; h: number}, immediate = false) {
  const clamped = clampView(next.x, next.y, next.w, next.h);
  targetView.x = clamped.x;
  targetView.y = clamped.y;
  targetView.w = clamped.w;
  targetView.h = clamped.h;

  if (immediate) {
    currentView.x = clamped.x;
    currentView.y = clamped.y;
    currentView.w = clamped.w;
    currentView.h = clamped.h;
    return;
  }

  scheduleFrame();
}

function markInteracting() {
  isInteracting.value = true;
  if (interactionEndTimer.value != null) {
    clearTimeout(interactionEndTimer.value);
    interactionEndTimer.value = null;
  }
}

function scheduleInteractionEnd(delayMs = 120) {
  if (interactionEndTimer.value != null) {
    clearTimeout(interactionEndTimer.value);
  }
  interactionEndTimer.value = window.setTimeout(() => {
    isInteracting.value = false;
    interactionEndTimer.value = null;
  }, delayMs);
}

function scheduleFrame() {
  if (rafId.value != null) return;

  rafId.value = requestAnimationFrame(function tick() {
    rafId.value = null;

    const alpha = 0.22;

    currentView.x += (targetView.x - currentView.x) * alpha;
    currentView.y += (targetView.y - currentView.y) * alpha;
    currentView.w += (targetView.w - currentView.w) * alpha;
    currentView.h += (targetView.h - currentView.h) * alpha;

    const done =
        Math.abs(currentView.x - targetView.x) < 0.05
        && Math.abs(currentView.y - targetView.y) < 0.05
        && Math.abs(currentView.w - targetView.w) < 0.05
        && Math.abs(currentView.h - targetView.h) < 0.05;

    if (!done) {
      scheduleFrame();
    } else {
      currentView.x = targetView.x;
      currentView.y = targetView.y;
      currentView.w = targetView.w;
      currentView.h = targetView.h;
    }
  });
}

function clientToMapCoords(clientX: number, clientY: number) {
  const svg = svgRef.value;
  if (!svg) return {x: 0, y: 0};

  const rect = svg.getBoundingClientRect();
  const nx = (clientX - rect.left) / rect.width;
  const ny = (clientY - rect.top) / rect.height;

  return {
    x: currentView.x + nx * currentView.w,
    y: currentView.y + ny * currentView.h,
  };
}

function zoomAtClient(clientX: number, clientY: number, factor: number) {
  const svg = svgRef.value;
  if (!svg) return;

  const rect = svg.getBoundingClientRect();
  const nx = (clientX - rect.left) / rect.width;
  const ny = (clientY - rect.top) / rect.height;

  const anchorX = targetView.x + nx * targetView.w;
  const anchorY = targetView.y + ny * targetView.h;

  const nextW = targetView.w * factor;
  const nextH = nextW / ASPECT;

  const nextX = anchorX - nx * nextW;
  const nextY = anchorY - ny * nextH;

  setTargetView({x: nextX, y: nextY, w: nextW, h: nextH});
}

function centerOnCountries(isoCodes: string[]) {
  const countries = isoCodes.map((iso) => countryByIso.get(iso)).filter(Boolean);
  if (!countries.length) {
    setTargetView({x: 0, y: 0, w: MAP_WIDTH, h: MAP_HEIGHT});
    return;
  }

  const xs = countries.map((country) => country!.cx);
  const ys = countries.map((country) => country!.cy);

  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);

  const pad = 80;
  const spanX = Math.max(maxX - minX, 1);
  const spanY = Math.max(maxY - minY, 1);

  const widthFromX = spanX + pad * 2;
  const widthFromY = (spanY + pad * 2) * ASPECT;
  const nextW = Math.min(Math.max(Math.max(widthFromX, widthFromY), MIN_VIEWBOX_WIDTH), MAX_VIEWBOX_WIDTH);
  const nextH = nextW / ASPECT;

  const centerX = (minX + maxX) / 2;
  const centerY = (minY + maxY) / 2;

  setTargetView({
    x: centerX - nextW / 2,
    y: centerY - nextH / 2,
    w: nextW,
    h: nextH,
  });
}

function centerOnPoint(x: number, y: number) {
  const nextW = 320;
  const nextH = nextW / ASPECT;

  setTargetView({
    x: x - nextW / 2,
    y: y - nextH / 2,
    w: nextW,
    h: nextH,
  });
}

function onWheel(event: WheelEvent) {
  if (!MAP_ZOOM_ENABLED) return;
  event.preventDefault();
  markInteracting();
  const factor = event.deltaY < 0 ? 0.88 : 1.12;
  zoomAtClient(event.clientX, event.clientY, factor);
  scheduleInteractionEnd(140);
}

function onPointerDown(event: PointerEvent) {
  if (!MAP_ZOOM_ENABLED) return;
  const svg = svgRef.value;
  if (!svg) return;

  svg.setPointerCapture(event.pointerId);
  markInteracting();
  pointers.set(event.pointerId, {clientX: event.clientX, clientY: event.clientY});

  // Keep initial country under pointer so simple tap/click remains reliable
  // even when pointer capture changes the eventual event target.
  const target = event.target as Element | null;
  const countryPath = target?.closest?.("[data-iso-a3]") as HTMLElement | null;
  dragState.downCountryIso = String(countryPath?.dataset?.isoA3 ?? "").toUpperCase();

  if (pointers.size === 1) {
    dragState.active = true;
    dragState.pointerId = event.pointerId;
    dragState.startX = event.clientX;
    dragState.startY = event.clientY;
    dragState.lastX = event.clientX;
    dragState.lastY = event.clientY;
    dragState.moved = false;
  }

  if (pointers.size === 2) {
    const [a, b] = Array.from(pointers.values());
    pinchState.active = true;
    pinchState.startDist = Math.hypot(b.clientX - a.clientX, b.clientY - a.clientY);
    pinchState.startMidX = (a.clientX + b.clientX) / 2;
    pinchState.startMidY = (a.clientY + b.clientY) / 2;
    pinchState.startW = targetView.w;
    pinchState.startH = targetView.h;
    pinchState.startX = targetView.x;
    pinchState.startY = targetView.y;

    dragState.active = false;
  }
}

function onPointerMove(event: PointerEvent) {
  if (!MAP_ZOOM_ENABLED) return;
  if (!pointers.has(event.pointerId)) return;

  pointers.set(event.pointerId, {clientX: event.clientX, clientY: event.clientY});

  if (pinchState.active && pointers.size >= 2) {
    const [a, b] = Array.from(pointers.values());
    const currentDist = Math.hypot(b.clientX - a.clientX, b.clientY - a.clientY);
    if (currentDist <= 0 || pinchState.startDist <= 0) return;

    const factor = pinchState.startDist / currentDist;
    const nextW = pinchState.startW * factor;
    const nextH = pinchState.startH * factor;

    const currentMidX = (a.clientX + b.clientX) / 2;
    const currentMidY = (a.clientY + b.clientY) / 2;

    const anchorNx = (pinchState.startMidX - (svgRef.value?.getBoundingClientRect().left || 0)) / (svgRef.value?.getBoundingClientRect().width || 1);
    const anchorNy = (pinchState.startMidY - (svgRef.value?.getBoundingClientRect().top || 0)) / (svgRef.value?.getBoundingClientRect().height || 1);

    const anchorX = pinchState.startX + anchorNx * pinchState.startW;
    const anchorY = pinchState.startY + anchorNy * pinchState.startH;

    const nextNx = (currentMidX - (svgRef.value?.getBoundingClientRect().left || 0)) / (svgRef.value?.getBoundingClientRect().width || 1);
    const nextNy = (currentMidY - (svgRef.value?.getBoundingClientRect().top || 0)) / (svgRef.value?.getBoundingClientRect().height || 1);

    const nextX = anchorX - nextNx * nextW;
    const nextY = anchorY - nextNy * nextH;

    setTargetView({x: nextX, y: nextY, w: nextW, h: nextH}, true);
    return;
  }

  if (dragState.active && dragState.pointerId === event.pointerId) {
    const prev = clientToMapCoords(dragState.lastX, dragState.lastY);
    const next = clientToMapCoords(event.clientX, event.clientY);

    const dx = next.x - prev.x;
    const dy = next.y - prev.y;

    if (
        Math.abs(event.clientX - dragState.startX) > 6
        || Math.abs(event.clientY - dragState.startY) > 6
    ) {
      dragState.moved = true;
    }

    dragState.lastX = event.clientX;
    dragState.lastY = event.clientY;

    setTargetView({
      x: targetView.x - dx,
      y: targetView.y - dy,
      w: targetView.w,
      h: targetView.h,
    }, true);
  }
}

function onPointerUp(event: PointerEvent) {
  if (!MAP_ZOOM_ENABLED) return;
  pointers.delete(event.pointerId);

  const shouldSelectCountry =
      dragState.pointerId === event.pointerId
      && !dragState.moved
      && !pinchState.active;

  if (pointers.size < 2) {
    pinchState.active = false;
  }

  if (dragState.pointerId === event.pointerId) {
    dragState.active = false;
    dragState.pointerId = -1;
  }

  if (!pointers.size) {
    scheduleInteractionEnd(90);
  }

  if (shouldSelectCountry) {
    const hit = document.elementFromPoint(event.clientX, event.clientY) as HTMLElement | null;
    const hitIso = String(hit?.closest?.("[data-iso-a3]")?.getAttribute("data-iso-a3") ?? "").toUpperCase();
    const iso = hitIso || dragState.downCountryIso;
    if (iso) {
      onCountryClick(iso);
    }
  }

  if (!dragState.active && !pointers.size) {
    dragState.downCountryIso = "";
  }
}

function onCountryClick(isoA3: string) {
  store.activateCountryFromMap(isoA3);
}

function onClusterClick(isoA3: string) {
  onCountryClick(isoA3);
}

function countryClass(isoA3: string) {
  return {
    "is-selected": store.activeCountryId.value === isoA3 && store.focusMode.value === "country",
    "is-highlighted": highlightedSet.value.has(isoA3),
    "has-cluster": (store.clusterCountByIso.value[isoA3] ?? 0) > 0,
  };
}

// Watch isolés + conditions d'arrêt anti-boucle
watch(
    () => store.activeCountryId.value,
    (isoA3) => {
      if (!isoA3) return;
      if (store.focusMode.value !== "country") return;
      centerOnCountries([isoA3]);
    }
);

watch(
    () => store.activeLanguageId.value,
    (languageId) => {
      if (store.activeLanguagePin.value && activeLanguagePinPoint.value) {
        centerOnPoint(activeLanguagePinPoint.value.x, activeLanguagePinPoint.value.y);
        return;
      }
      if (!languageId) return;
      if (store.focusMode.value !== "language") return;
      if (!store.highlightedCountryIds.value.length) return;
      centerOnCountries(store.highlightedCountryIds.value);
    }
);

watch(
    () => activeLanguagePinPoint.value,
    (point) => {
      if (!point) return;
      centerOnPoint(point.x, point.y);
    }
);

watch(countrySearch, (value) => {
  if (!String(value ?? "").trim()) {
    hideSearchSuggestions.value = false;
  }
});

onMounted(() => {
  store.loadMapData();
});

onBeforeUnmount(() => {
  if (rafId.value != null) {
    cancelAnimationFrame(rafId.value);
  }
  if (interactionEndTimer.value != null) {
    clearTimeout(interactionEndTimer.value);
  }
});
</script>

<template>
  <section class="world-map">
    <div class="map-ocean"></div>
    <div class="map-search-overlay">
      <div class="map-search">
        <Search :size="15" aria-hidden="true"/>
        <input
            v-model="countrySearch"
            type="text"
            placeholder="Search for a country..."
            autocomplete="off"
            aria-label="Rechercher un pays sur la carte"
            @input="onSearchInput"
            @keydown.enter.prevent="onSearchEnter"
        />
      </div>

      <ul
          v-if="countrySearchMatches.length && normalizedCountrySearch && !hideSearchSuggestions"
          class="map-search-list"
          role="listbox"
          aria-label="Country suggestions"
      >
        <li v-for="country in countrySearchMatches" :key="country.isoA3">
          <button
              type="button"
              class="map-search-item"
              @click="focusCountryFromSearch(country.isoA3)"
          >
            <span class="name">{{ country.name }}</span>
            <span class="iso">{{ country.isoA3 }}</span>
          </button>
        </li>
      </ul>
    </div>

    <svg
        ref="svgRef"
        class="world-svg"
        :viewBox="viewBoxValue"
        xmlns="http://www.w3.org/2000/svg"
        aria-label="Interactive world map"
        @wheel="onWheel"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @pointerleave="onPointerUp"
    >
      <defs>
        <filter id="continent-shadow" x="-40%" y="-40%" width="180%" height="180%">
          <feDropShadow dx="0" dy="2" stdDeviation="2.2" flood-color="#5a3a2b" flood-opacity="0.22"/>
        </filter>
      </defs>

      <g class="map-group">
        <path
            v-for="country in countryFeatures"
            :key="country.id"
            :d="country.d"
            class="country-shape"
            :class="[countryClass(country.isoA3), { 'no-shadow': isInteracting }]"
            :data-iso-a3="country.isoA3"
            :aria-label="`${country.name} (${country.isoA3})`"
            tabindex="0"
            @click="onCountryClick(country.isoA3)"
            @keydown.enter.prevent="onCountryClick(country.isoA3)"
            @keydown.space.prevent="onCountryClick(country.isoA3)"
        />
      </g>

      <g
          v-if="activeLanguagePinPoint"
          class="active-language-pin"
          :transform="`translate(${activeLanguagePinPoint.x}, ${activeLanguagePinPoint.y})`"
          aria-label="Active language location"
      >
        <circle class="active-language-pin__pulse" r="12"/>
        <circle class="active-language-pin__ring" r="6.8"/>
        <circle class="active-language-pin__core" r="4.2"/>
      </g>

      <g class="cluster-group">
        <g
            v-for="cluster in clusterFeatures"
            :key="`cluster-${cluster.isoA3}`"
            class="cluster-badge"
            :transform="`translate(${cluster.cx}, ${cluster.cy})`"
            role="button"
            tabindex="0"
            :aria-label="`Cluster ${cluster.isoA3} (${cluster.count})`"
            @click.stop="onClusterClick(cluster.isoA3)"
            @keydown.enter.prevent="onClusterClick(cluster.isoA3)"
            @keydown.space.prevent="onClusterClick(cluster.isoA3)"
        >
          <circle r="11.5"/>
          <text text-anchor="middle" dominant-baseline="central">
            +{{ cluster.count }}
          </text>
        </g>
      </g>
    </svg>

    <div v-if="showLanguageCountryNames" class="language-country-strip" aria-live="polite">
      <p class="strip-label">Pays ou cette langue est parlee</p>
      <p class="strip-values">{{ countryNamesText }}</p>
    </div>

  </section>
</template>

<style scoped>
.world-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: hidden;
}

.map-search-overlay {
  position: absolute;
  top: 0.95rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 4;
  width: min(340px, calc(100vw - 2rem));
  pointer-events: none;
}

.map-search {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  border-radius: 999px;
  border: 1px solid #e0c9b0;
  background: var(--surface);
  backdrop-filter: blur(10px);
  padding: 0.42rem 0.64rem;
  box-shadow: 0 8px 22px rgba(96, 56, 24, 0.08);
}

.map-search :deep(svg) {
  color: var(--text);
  flex-shrink: 0;
}

.map-search input {
  width: 100%;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 0.8rem;
  box-shadow: none;
}

.map-search input::placeholder {
  color: var(--text-soft);
}

.map-search:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(192, 74, 8, 0.16);
}

.map-search-list {
  list-style: none;
  margin: 0.36rem 0 0;
  padding: 0.22rem;
  border-radius: 11px;
  border: 1px solid var(--border);
  background: var(--surface);
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 24px rgba(96, 56, 24, 0.08);
  max-height: 180px;
  overflow: auto;
  pointer-events: auto;
}

.map-search-item {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text);
  border-radius: 8px;
  padding: 0.3rem 0.4rem;
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: center;
  text-align: left;
  font-size: 0.78rem;
}

.map-search-item:hover {
  background: var(--surface-alt);
  border-color: var(--border);
}

.map-search-item .name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-search-item .iso {
  color: var(--text-soft);
  font-size: 0.68rem;
  font-weight: 700;
}

.map-ocean {
  position: absolute;
  inset: 0;
  background:
      linear-gradient(rgba(245, 231, 228, 0.24), rgba(245, 231, 228, 0.24)),
      url("/fond3.avif") center / cover no-repeat;
}

.map-ocean::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.08;
  background-image:
    linear-gradient(rgba(192, 74, 8, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(192, 74, 8, 0.04) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(circle at center, black, transparent 72%);
}

.map-ocean::after {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 50% 50%, rgba(192, 74, 8, 0.05), transparent 60%);
}

.world-svg {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: block;
  transform: perspective(1500px) rotateX(4deg);
  transform-origin: center 58%;
  touch-action: none;
}

.map-group {
  transition: filter 220ms ease;
}

.country-shape {
  fill: #e8caa8;
  stroke: color-mix(in srgb, #e8caa8 58%, var(--border) 42%);
  stroke-width: 0.82;
  vector-effect: non-scaling-stroke;
  filter: url(#continent-shadow);
  transition: fill 220ms ease, stroke 220ms ease;
  cursor: pointer;
  outline: none;
  -webkit-tap-highlight-color: transparent;
}

.country-shape.no-shadow {
  filter: none;
}

.country-shape:focus,
.country-shape:focus-visible {
  outline: none;
}


.country-shape:hover
{
  fill:  rgba(18, 66, 18, 0.35);
}

.country-shape.is-highlighted {
  fill: var(--primary);
  stroke: var(--primary-strong);
  stroke-width: 0.96;
}

.country-shape.is-highlighted:hover,
.country-shape.is-highlighted:focus-visible {
  fill: color-mix(in srgb, var(--primary) 86%, #ffffff 14%);
  stroke: var(--primary-strong);
}


.country-shape.is-selected,
.country-shape.is-selected.is-highlighted {
  fill: var(--primary);
  stroke: var(--primary-strong);
  stroke-width: 1.1;
}



.cluster-badge {
  cursor: pointer;
  animation: pulse-badge 2.3s ease-in-out infinite;
}

.active-language-pin {
  pointer-events: none;
}

.active-language-pin__pulse {
  fill: rgba(91, 25, 40, 0.24);
  transform-origin: center;
  animation: pin-pulse 1800ms ease-out infinite;
}

.active-language-pin__ring {
  fill: #fff7f1;
  stroke: #5B1928;
  stroke-width: 1.2;
}

.active-language-pin__core {
  fill: #5B1928;
  stroke: #fff7f1;
  stroke-width: 0.9;
}

.cluster-badge circle {
  fill: var(--primary);
  stroke: rgba(255, 252, 247, 0.92);
  stroke-width: 1.3;
  filter: drop-shadow(0 0 8px rgba(192, 74, 8, 0.22));
}

.cluster-badge text {
  fill: var(--surface);
  font-size: 8.8px;
  font-weight: 800;
  letter-spacing: 0.03em;
  pointer-events: none;
}

.language-country-strip {
  position: absolute;
  left: 50%;
  bottom: 1rem;
  transform: translateX(-50%);
  z-index: 3;
  width: min(920px, calc(100vw - 2rem));
  border-radius: 14px;
  border: 1px solid var(--border);
  background: rgba(255, 252, 247, 0.92);
  backdrop-filter: blur(10px);
  padding: 0.58rem 0.72rem;
  color: var(--text);
  box-shadow: 0 10px 24px rgba(96, 56, 24, 0.08);
}

.strip-label {
  margin: 0;
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-soft);
  font-weight: 700;
}

.strip-values {
  margin: 0.24rem 0 0;
  font-size: 0.88rem;
  font-weight: 700;
  line-height: 1.35;
  color: var(--text);
  max-height: 3.2rem;
  overflow: auto;
}

@keyframes pulse-badge {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.08);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes pin-pulse {
  0% {
    opacity: 0.64;
    transform: scale(0.86);
  }
  75% {
    opacity: 0;
    transform: scale(1.55);
  }
  100% {
    opacity: 0;
    transform: scale(1.65);
  }
}

@media (max-width: 920px) {
  .world-map {
    min-height: 100vh;
  }

  .world-svg {
    transform: perspective(1200px) rotateX(2.5deg);
  }

  .language-country-strip {
    width: calc(100vw - 1.6rem);
    bottom: 0.8rem;
  }
}

@media (max-width: 600px) {
  .world-map {
    min-height: 100vh;
  }

  .world-svg {
    transform: perspective(1000px) rotateX(1.5deg);
  }
}
</style>
