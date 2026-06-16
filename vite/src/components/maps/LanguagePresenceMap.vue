<script setup>
import {computed, ref} from "vue";
import {geoNaturalEarth1, geoPath} from "d3-geo";
import {feature as topojsonFeature} from "topojson-client";

import worldAtlas110m from "world-atlas/countries-110m.json";
import worldCountries from "world-countries";

const props = defineProps({
  countryIds: {
    type: String,
    default: "",
  },
  latitude: {
    type: [Number, String],
    default: null,
  },
  longitude: {
    type: [Number, String],
    default: null,
  },
  languageName: {
    type: String,
    default: "",
  },
  mapSize: {
    type: String,
    default: "default",
    validator: (value) => ["default", "large"].includes(value),
  },
});

const VIEWBOX_WIDTH = 980;
const VIEWBOX_HEIGHT = 420;

const hoveredIsoA3 = ref("");

const iso2ToIso3 = new Map(
    worldCountries
        .filter((country) => country?.cca2 && country?.cca3)
        .map((country) => [String(country.cca2).toUpperCase(), String(country.cca3).toUpperCase()])
);

const validIso3 = new Set(
    worldCountries
        .filter((country) => country?.cca3)
        .map((country) => String(country.cca3).toUpperCase())
);

function parseCountryIds(raw) {
  if (!raw) return [];

  const tokens = String(raw)
      .split(/[\s,;]+/)
      .map((token) => token.trim().toUpperCase())
      .filter(Boolean);

  const mapped = tokens
      .map((token) => {
        if (token.length === 3 && validIso3.has(token)) {
          return token;
        }
        if (token.length === 2 && iso2ToIso3.has(token)) {
          return iso2ToIso3.get(token);
        }
        return null;
      })
      .filter(Boolean);

  return Array.from(new Set(mapped));
}

const highlightedIsoA3 = computed(() => parseCountryIds(props.countryIds));
const highlightedSet = computed(() => new Set(highlightedIsoA3.value));

const byNumericCode = new Map(
    worldCountries
        .filter((country) => typeof country?.ccn3 === "string" && country.ccn3)
        .map((country) => [country.ccn3, country])
);

const countriesCollection = topojsonFeature(worldAtlas110m, worldAtlas110m.objects.countries);
const projection = geoNaturalEarth1().fitSize([VIEWBOX_WIDTH, VIEWBOX_HEIGHT], countriesCollection);
const path = geoPath(projection);

const countryFeatures = (() => {
  return countriesCollection.features
      .map((countryShape) => {
        const numericCode = String(countryShape.id ?? "").padStart(3, "0");
        const countryInfo = byNumericCode.get(numericCode);
        const isoA3 = countryInfo?.cca3 ?? "";
        const centroid = path.centroid(countryShape);

        return {
          id: numericCode,
          isoA3,
          name: countryInfo?.name?.common || "Unknown",
          path: path(countryShape) || "",
          cx: centroid?.[0] ?? 0,
          cy: centroid?.[1] ?? 0,
        };
      })
      .filter((country) => country.isoA3 && country.path);
})();

const countryByIso = new Map(countryFeatures.map((country) => [country.isoA3, country]));

function toFiniteNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

const languagePinPoint = computed(() => {
  const latitude = toFiniteNumber(props.latitude);
  const longitude = toFiniteNumber(props.longitude);
  if (latitude == null || longitude == null) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;

  const point = projection([longitude, latitude]);
  if (!point || point.length < 2) return null;

  return {x: point[0], y: point[1]};
});

const fallbackCountryPins = computed(() => {
  return highlightedIsoA3.value
      .map((isoA3) => countryByIso.get(isoA3))
      .filter(Boolean)
      .map((country) => ({
        x: country.cx,
        y: country.cy,
      }));
});

const visiblePins = computed(() => {
  if (languagePinPoint.value) {
    return [languagePinPoint.value];
  }
  return fallbackCountryPins.value;
});

function countryClass(isoA3) {
  return {
    "is-highlighted": highlightedSet.value.has(isoA3),
    "is-hovered": hoveredIsoA3.value === isoA3,
  };
}

const activeCountryName = computed(() => {
  if (hoveredIsoA3.value) {
    const hoveredCountry = countryFeatures.find((item) => item.isoA3 === hoveredIsoA3.value);
    return hoveredCountry?.name ?? "";
  }

  if (!highlightedIsoA3.value.length) return "";

  const names = highlightedIsoA3.value
      .map((isoA3) => countryFeatures.find((item) => item.isoA3 === isoA3)?.name)
      .filter(Boolean);

  return names.join(", ");
});
</script>

<template>
  <section :class="['card', 'map-card', `map-card--${props.mapSize}`]">
    <div class="map-content">
      <p class="country-name">{{ activeCountryName }}</p>

      <div class="map-wrapper">
        <svg
            class="map-svg"
            :viewBox="`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`"
            xmlns="http://www.w3.org/2000/svg"
            role="img"
            aria-label="World map showing highlighted countries for the selected language"
        >
          <path
              v-for="country in countryFeatures"
              :key="country.id"
              :d="country.path"
              class="country-shape"
              :class="countryClass(country.isoA3)"
              :aria-label="`${country.name} (${country.isoA3})`"
              tabindex="0"
              @mouseenter="hoveredIsoA3 = country.isoA3"
              @mouseleave="hoveredIsoA3 = ''"
              @focus="hoveredIsoA3 = country.isoA3"
              @blur="hoveredIsoA3 = ''"
          />

          <g
              v-for="(pin, index) in visiblePins"
              :key="`pin-${index}`"
              class="language-pin"
              :transform="`translate(${pin.x}, ${pin.y})`"
              aria-label="Language location pin"
          >
            <circle class="language-pin__pulse" r="9"/>
            <circle class="language-pin__ring" r="5.2"/>
            <circle class="language-pin__core" r="3.2"/>
          </g>
        </svg>
      </div>
    </div>
  </section>
</template>

<style scoped>
.map-card {
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid rgba(203, 213, 225, 0.72);
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.96), rgba(246, 251, 255, 0.96));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
}

.map-content,
.map-wrapper {
  width: 100%;
}

.map-card--large {
  padding: 0.75rem;
}

.country-name {
  margin: 0 0 0.65rem;
  min-height: 1.5rem;
  color: #112d4d;
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.map-svg {
  width: 100%;
  max-width: none;
  height: auto;
  display: block;
  background:
      radial-gradient(circle at 22% 18%, rgba(148, 207, 255, 0.32) 0%, rgba(148, 207, 255, 0) 38%),
      radial-gradient(circle at 82% 14%, rgba(186, 230, 253, 0.28) 0%, rgba(186, 230, 253, 0) 36%),
      linear-gradient(180deg, #f7fbff 0%, #f2f8ff 100%);
  border-radius: 1rem;
  border: 1px solid rgba(219, 234, 254, 0.65);
}

.country-shape {
  fill: #dce6f2;
  stroke: #8aa0b8;
  stroke-width: 0.5;
  vector-effect: non-scaling-stroke;
  transition: fill 180ms ease, stroke 180ms ease, filter 180ms ease;
  cursor: pointer;
  outline: none;
}

.country-shape:hover,
.country-shape.is-hovered,
.country-shape:focus-visible {
  fill: #84b7ff;
  stroke: #255fb8;
  filter: drop-shadow(0 0 2px rgba(37, 99, 235, 0.2));
}

.country-shape.is-highlighted {
  fill: #40a964;
  stroke: #1f6b3a;
}

.country-shape.is-highlighted:hover,
.country-shape.is-highlighted.is-hovered,
.country-shape.is-highlighted:focus-visible {
  fill: #2f9254;
  stroke: #104f2a;
  filter: drop-shadow(0 0 2px rgba(16, 185, 129, 0.22));
}

.language-pin {
  pointer-events: none;
}

.language-pin__pulse {
  fill: rgba(192, 106, 47, 0.24);
  transform-origin: center;
  animation: mapPinPulse 1800ms ease-out infinite;
}

.language-pin__ring {
  fill: #fef6e8;
  stroke: #8d4d24;
  stroke-width: 1.1;
}

.language-pin__core {
  fill: #c06a2f;
  stroke: #fff7e9;
  stroke-width: 0.8;
}

@keyframes mapPinPulse {
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
</style>
