<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from "vue";
import {geoNaturalEarth1, geoPath} from "d3-geo";
import {feature as topojsonFeature} from "topojson-client";
import {MapPin, Search} from "lucide-vue-next";

import worldAtlas110m from "world-atlas/countries-110m.json";
import worldCountries from "world-countries";

import {useLanguageStore} from "@/composables/useLanguageStore";
import mapBgUrl from "@/assets/language_bg.png";

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
const isViewAnimating = ref(false);

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

const COUNTRY_NAMES_BANNER_MS = 7000;
const countryNamesBannerVisible = ref(false);
let countryNamesBannerTimer: number | null = null;

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
  if (!highlightedCountryNames.value.length) return "No countries detected";
  return highlightedCountryNames.value.join(" • ");
});

const showLanguageCountryNames = computed(() => {
  return countryNamesBannerVisible.value && highlightedCountryNames.value.length > 0;
});

function clearCountryNamesBannerTimer() {
  if (countryNamesBannerTimer != null) {
    window.clearTimeout(countryNamesBannerTimer);
    countryNamesBannerTimer = null;
  }
}

function revealCountryNamesBanner() {
  if (!highlightedCountryNames.value.length) return;
  countryNamesBannerVisible.value = true;
  clearCountryNamesBannerTimer();
  countryNamesBannerTimer = window.setTimeout(() => {
    countryNamesBannerVisible.value = false;
    countryNamesBannerTimer = null;
  }, COUNTRY_NAMES_BANNER_MS);
}

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
  isViewAnimating.value = true;

  rafId.value = requestAnimationFrame(function tick() {
    rafId.value = null;

    const alpha = 0.34;

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
      isViewAnimating.value = false;
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

  const pad = 110;
  const minFocusWidth = 460;
  const spanX = Math.max(maxX - minX, 1);
  const spanY = Math.max(maxY - minY, 1);

  const widthFromX = spanX + pad * 2;
  const widthFromY = (spanY + pad * 2) * ASPECT;
  const nextW = Math.min(Math.max(Math.max(widthFromX, widthFromY), minFocusWidth), MAX_VIEWBOX_WIDTH);
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
  const nextW = 620;
  const nextH = nextW / ASPECT;
  const targetRatioX = 0.4;
  const targetRatioY = 0.47;

  setTargetView({
    x: x - nextW * targetRatioX,
    y: y - nextH * targetRatioY,
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

function countryClass(isoA3: string) {
  return {
    "is-selected": store.activeCountryId.value === isoA3 && store.focusMode.value === "country",
    "is-highlighted": highlightedSet.value.has(isoA3),
  };
}

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

watch(
    () => ({
      languageId: store.activeLanguageId.value,
      focusMode: store.focusMode.value,
      countries: store.highlightedCountryIds.value.join(","),
      pinId: store.activeLanguagePin.value?.id || "",
    }),
    (next) => {
      if (next.focusMode !== "language" || !next.languageId) return;
      if (!next.countries && !next.pinId) return;
      revealCountryNamesBanner();
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
  clearCountryNamesBannerTimer();
});
</script>

<template>
  <section class="world-map">
    <img
        class="map-ocean-img"
        :src="mapBgUrl"
        alt=""
        aria-hidden="true"
        decoding="async"
    />
    <div class="map-ocean" aria-hidden="true"></div>
    <div class="map-search-overlay">
      <div class="map-search">
        <Search :size="15" aria-hidden="true"/>
        <input
            v-model="countrySearch"
            type="text"
            placeholder="Search for a country..."
            autocomplete="off"
            aria-label="Search for a country on the map"
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
        <radialGradient id="continent-fill" cx="32%" cy="28%" r="78%">
          <stop offset="0%" stop-color="#f3e6e1"/>
          <stop offset="42%" stop-color="#e4d2ca"/>
          <stop offset="100%" stop-color="#d4bfb5"/>
        </radialGradient>
        <linearGradient id="continent-fill-hover" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#f0d4ce"/>
          <stop offset="55%" stop-color="#e2b8b0"/>
          <stop offset="100%" stop-color="#d09c94"/>
        </linearGradient>
        <linearGradient id="continent-fill-active" x1="15%" y1="10%" x2="90%" y2="95%">
          <stop offset="0%" stop-color="#d98994"/>
          <stop offset="55%" stop-color="#8f3c4e"/>
          <stop offset="100%" stop-color="#5f2432"/>
        </linearGradient>
        <filter id="continent-glow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur in="SourceAlpha" stdDeviation="1.6" result="blur"/>
          <feFlood flood-color="#5B1928" flood-opacity="0.14" result="rose"/>
          <feComposite in="rose" in2="blur" operator="in" result="roseGlow"/>
          <feMerge>
            <feMergeNode in="roseGlow"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>

      <g class="map-group">
        <path
            v-for="country in countryFeatures"
            :key="country.id"
            :d="country.d"
            class="country-shape"
            :class="[countryClass(country.isoA3), { 'no-shadow': isInteracting || isViewAnimating }]"
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
        <foreignObject x="-12" y="-26" width="24" height="24" class="active-language-pin__icon-wrap">
          <div class="active-language-pin__icon" xmlns="http://www.w3.org/1999/xhtml">
            <MapPin :size="16" stroke-width="2.2"/>
          </div>
        </foreignObject>
      </g>

    </svg>

    <div v-if="showLanguageCountryNames" class="language-country-strip" aria-live="polite">
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
  background: #c56a3a;
}

.map-ocean-img {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  pointer-events: none;
  user-select: none;
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
  z-index: 1;
  pointer-events: none;
  background:
    radial-gradient(circle at 50% 40%, rgba(255, 236, 210, 0.12), transparent 46%),
    linear-gradient(180deg, rgba(90, 40, 24, 0.12) 0%, transparent 35%, rgba(70, 30, 18, 0.2) 100%);
}

.map-ocean::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.14;
  background-image:
    linear-gradient(rgba(91, 25, 40, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(91, 25, 40, 0.12) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: radial-gradient(ellipse 72% 66% at 50% 48%, black 18%, transparent 76%);
}

.map-ocean::after {
  display: none;
}

.world-svg {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: block;
  transform: perspective(1600px) rotateX(2deg);
  transform-origin: center 58%;
  touch-action: none;
}

.map-group {
  transition: filter 220ms ease;
  filter: url(#continent-glow);
}

.country-shape {
  fill: url(#continent-fill);
  stroke: rgba(91, 25, 40, 0.22);
  stroke-width: 0.75;
  paint-order: stroke fill;
  vector-effect: non-scaling-stroke;
  filter: none;
  transition: fill 260ms ease, stroke 260ms ease, stroke-width 260ms ease, opacity 260ms ease;
  cursor: pointer;
  outline: none;
  -webkit-tap-highlight-color: transparent;
  opacity: 1;
}

.country-shape.no-shadow {
  filter: none;
}

.country-shape:focus,
.country-shape:focus-visible {
  outline: none;
}

.country-shape:hover {
  fill: url(#continent-fill-hover);
  stroke: rgba(91, 25, 40, 0.38);
  stroke-width: 0.95;
  opacity: 1;
}

.country-shape.is-highlighted {
  fill: url(#continent-fill-active);
  stroke: rgba(60, 16, 24, 0.55);
  stroke-width: 0.95;
  opacity: 1;
}

.country-shape.is-highlighted:hover,
.country-shape.is-highlighted:focus-visible {
  fill: url(#continent-fill-active);
  stroke: rgba(60, 16, 24, 0.65);
  stroke-width: 1.05;
}

.country-shape.is-selected,
.country-shape.is-selected.is-highlighted {
  fill: url(#continent-fill-active);
  stroke: rgba(60, 16, 24, 0.7);
  stroke-width: 1.15;
  opacity: 1;
}



.active-language-pin {
  pointer-events: none;
}

.active-language-pin__icon-wrap {
  overflow: visible;
}

.active-language-pin__icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #fff7f1;
  color: #5B1928;
  box-shadow: 0 8px 14px rgba(91, 25, 40, 0.16);
}

.language-country-strip {
  position: absolute;
  left: 50%;
  bottom: 1.35rem;
  transform: translateX(-50%);
  z-index: 3;
  width: min(720px, calc(100vw - 2rem));
  pointer-events: none;
  text-align: center;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  animation: country-names-in 320ms ease;
}

.strip-values {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1.35;
  color: rgba(255, 248, 242, 0.94);
  text-shadow: 0 2px 16px rgba(40, 16, 10, 0.5);
}

@keyframes country-names-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@media (max-width: 920px) {
  .world-map {
    min-height: 100vh;
  }

  .world-svg {
    transform: perspective(1200px) rotateX(1.8deg);
  }

  .language-country-strip {
    width: calc(100vw - 1.6rem);
    bottom: 1rem;
  }
}

@media (max-width: 600px) {
  .world-map {
    min-height: 100vh;
  }

  .world-svg {
    transform: perspective(1000px) rotateX(1deg);
  }
}
</style>
