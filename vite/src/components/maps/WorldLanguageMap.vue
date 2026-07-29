<script setup>
import {computed, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";
import {geoNaturalEarth1, geoPath} from "d3-geo";
import {feature as topojsonFeature} from "topojson-client";

import worldAtlas110m from "world-atlas/countries-110m.json";
import worldCountries from "world-countries";

import {fetchLanguagesByCountry} from "@/api/languageMap";
import BaseLoader from "@/components/ui/BaseLoader.vue";
import BaseAlert from "@/components/ui/BaseAlert.vue";

const VIEWBOX_WIDTH = 1120;
const VIEWBOX_HEIGHT = 590;

const MOCK_CARD_TEMPLATES = [
  {title: "Basic greetings", level: "Beginner", status: "Coming soon", type: "Vocabulary"},
  {title: "Daily conversation", level: "Beginner", status: "Coming soon", type: "Dialogue"},
  {title: "Travel essentials", level: "Intermediate", status: "Coming soon", type: "Vocabulary"},
  {title: "Culture snapshots", level: "Intermediate", status: "Coming soon", type: "Listening"},
];

const loading = ref(false);
const error = ref("");
const hoveredIsoA3 = ref("");
const selectedIsoA3 = ref("");
const languagesByCountry = ref({});

const byNumericCode = new Map(
    worldCountries
        .filter((country) => typeof country?.ccn3 === "string" && country.ccn3)
        .map((country) => [country.ccn3, country])
);

const countryFeatures = (() => {
  const countriesCollection = topojsonFeature(worldAtlas110m, worldAtlas110m.objects.countries);
  const projection = geoNaturalEarth1().fitSize([VIEWBOX_WIDTH, VIEWBOX_HEIGHT], countriesCollection);
  const path = geoPath(projection);

  return countriesCollection.features
      .map((countryShape) => {
        const numericCode = String(countryShape.id ?? "").padStart(3, "0");
        const countryInfo = byNumericCode.get(numericCode);
        const isoA3 = countryInfo?.cca3 ?? "";

        return {
          id: numericCode,
          isoA3,
          name: countryInfo?.name?.common || countryShape?.properties?.name || "Unknown",
          path: path(countryShape) || "",
        };
      })
      .filter((country) => country.isoA3 && country.path);
})();

const selectedCountry = computed(() => {
  if (!selectedIsoA3.value) return null;
  return countryFeatures.find((country) => country.isoA3 === selectedIsoA3.value) || null;
});

const selectedLanguages = computed(() => {
  if (!selectedIsoA3.value) return [];
  return languagesByCountry.value[selectedIsoA3.value] ?? [];
});

const activeCountryName = computed(() => {
  const isoA3 = hoveredIsoA3.value || selectedIsoA3.value;
  if (!isoA3) return "";
  const match = countryFeatures.find((country) => country.isoA3 === isoA3);
  return match?.name ?? "";
});

const countriesWithDataCount = computed(() => {
  return Object.keys(languagesByCountry.value).filter((isoA3) => (languagesByCountry.value[isoA3]?.length || 0) > 0).length;
});

const uniqueLanguages = computed(() => {
  const byId = new Map();

  Object.values(languagesByCountry.value).forEach((rows) => {
    if (!Array.isArray(rows)) return;

    rows.forEach((language) => {
      const key = String(language?.id ?? "").trim();
      if (!key || byId.has(key)) return;
      byId.set(key, language);
    });
  });

  return Array.from(byId.values());
});

const recentlyAdded = computed(() => {
  return [...uniqueLanguages.value]
      .sort((a, b) => String(b.id ?? "").localeCompare(String(a.id ?? "")))
      .slice(0, 4);
});

const featuredLanguages = computed(() => {
  return [...uniqueLanguages.value]
      .sort((a, b) => String(a.name ?? "").localeCompare(String(b.name ?? "")))
      .slice(0, 4);
});

const continueExploring = computed(() => {
  if (selectedLanguages.value.length) {
    return selectedLanguages.value.slice(0, 4);
  }

  return [...uniqueLanguages.value]
      .sort((a, b) => String(a.level ?? "").localeCompare(String(b.level ?? "")))
      .slice(0, 4);
});

const learningCards = computed(() => {
  if (!selectedCountry.value || !selectedLanguages.value.length) return [];

  return selectedLanguages.value.slice(0, 4).map((language, index) => {
    const template = MOCK_CARD_TEMPLATES[index % MOCK_CARD_TEMPLATES.length];

    return {
      title: template.title,
      language: language.name || "Unknown language",
      level: template.level,
      status: template.status,
      type: template.type,
    };
  });
});

function countryStateClass(isoA3) {
  return {
    "is-hovered": hoveredIsoA3.value === isoA3,
    "is-selected": selectedIsoA3.value === isoA3,
    "has-data": (languagesByCountry.value[isoA3]?.length || 0) > 0,
  };
}

function onCountryEnter(isoA3) {
  hoveredIsoA3.value = isoA3;
}

function onCountryLeave() {
  hoveredIsoA3.value = "";
}

function onCountryClick(isoA3) {
  selectedIsoA3.value = isoA3;
}

async function loadCountryLanguageData() {
  loading.value = true;
  error.value = "";

  try {
    languagesByCountry.value = await fetchLanguagesByCountry();
  } catch (err) {
    error.value = err?.message || "Failed to load language data.";
  } finally {
    loading.value = false;
  }
}

onMounted(loadCountryLanguageData);
</script>

<template>
  <section class="world-language-explorer">
    <header class="explorer-hero">
      <div class="hero-copy">
        <p class="hero-kicker">Global language journey</p>
        <h1>Explore languages through the world map</h1>
        <p>
          Select a country to reveal its languages and preview upcoming learning cards.
          The map is your main navigation.
        </p>
      </div>

      <div class="hero-actions">
        <RouterLink to="/languages" class="btn btn--ghost">Back to catalog</RouterLink>
        <div class="hero-stats">
          <article class="stat-chip">
            <span class="stat-value">{{ countriesWithDataCount }}</span>
            <span class="stat-label">Countries with data</span>
          </article>
          <article class="stat-chip">
            <span class="stat-value">{{ uniqueLanguages.length }}</span>
            <span class="stat-label">Languages indexed</span>
          </article>
        </div>
      </div>
    </header>

    <div class="explorer-stage">
      <div class="map-column">
        <p class="active-country-name">{{ activeCountryName || "Hover a country to begin" }}</p>

        <div class="map-surface card" role="img" aria-label="Interactive world map by country">
          <svg
              class="world-svg"
              :viewBox="`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`"
              xmlns="http://www.w3.org/2000/svg"
          >
            <defs>
              <radialGradient id="wl-continent-fill" cx="32%" cy="28%" r="78%">
                <stop offset="0%" stop-color="#f3e6e1"/>
                <stop offset="42%" stop-color="#e4d2ca"/>
                <stop offset="100%" stop-color="#d4bfb5"/>
              </radialGradient>
              <linearGradient id="wl-continent-hover" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#f0d4ce"/>
                <stop offset="55%" stop-color="#e2b8b0"/>
                <stop offset="100%" stop-color="#d09c94"/>
              </linearGradient>
              <linearGradient id="wl-continent-data" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#d4e5ca"/>
                <stop offset="55%" stop-color="#b8d0a8"/>
                <stop offset="100%" stop-color="#94b882"/>
              </linearGradient>
              <linearGradient id="wl-continent-selected" x1="15%" y1="10%" x2="90%" y2="95%">
                <stop offset="0%" stop-color="#d98994"/>
                <stop offset="55%" stop-color="#8f3c4e"/>
                <stop offset="100%" stop-color="#5f2432"/>
              </linearGradient>
              <filter id="wl-continent-glow" x="-35%" y="-35%" width="170%" height="170%">
                <feGaussianBlur in="SourceAlpha" stdDeviation="1.4" result="blur"/>
                <feFlood flood-color="#5B1928" flood-opacity="0.14" result="rose"/>
                <feComposite in="rose" in2="blur" operator="in" result="glow"/>
                <feMerge>
                  <feMergeNode in="glow"/>
                  <feMergeNode in="SourceGraphic"/>
                </feMerge>
              </filter>
            </defs>
            <path
                v-for="country in countryFeatures"
                :key="country.id"
                :d="country.path"
                class="country-shape"
                :class="countryStateClass(country.isoA3)"
                :data-iso-a3="country.isoA3"
                :aria-label="`${country.name} (${country.isoA3})`"
                tabindex="0"
                @mouseenter="onCountryEnter(country.isoA3)"
                @mouseleave="onCountryLeave"
                @focus="onCountryEnter(country.isoA3)"
                @blur="onCountryLeave"
                @click="onCountryClick(country.isoA3)"
                @keydown.enter.prevent="onCountryClick(country.isoA3)"
                @keydown.space.prevent="onCountryClick(country.isoA3)"
            />
          </svg>

          <div v-if="loading" class="map-overlay">
            <BaseLoader>Loading language map data...</BaseLoader>
          </div>
        </div>

        <BaseAlert v-if="error" type="error" class="map-error">
          {{ error }}
        </BaseAlert>
      </div>

      <aside class="details-column card" aria-live="polite">
        <template v-if="selectedCountry">
          <header class="details-header">
            <div>
              <p class="details-label">Selected country</p>
              <h2>{{ selectedCountry.name }}</h2>
            </div>
            <p class="iso-chip">{{ selectedCountry.isoA3 }}</p>
          </header>

          <section class="panel-block">
            <h3>Languages</h3>
            <div v-if="selectedLanguages.length" class="language-list">
              <article
                  v-for="language in selectedLanguages"
                  :key="`${selectedCountry.isoA3}-${language.id}`"
                  class="language-item"
              >
                <h4>{{ language.name }}</h4>
                <p><strong>Level:</strong> {{ language.level }}</p>
                <p><strong>Family:</strong> {{ language.family }}</p>
                <p><strong>Parent:</strong> {{ language.parent }}</p>
              </article>
            </div>
            <p v-else class="empty-state">No language data available for this country yet.</p>
          </section>

          <section class="panel-block">
            <h3>Learning cards</h3>
            <div v-if="learningCards.length" class="learning-grid">
              <article
                  v-for="card in learningCards"
                  :key="`${selectedCountry.isoA3}-${card.title}-${card.language}`"
                  class="learning-card"
              >
                <p class="card-type">{{ card.type }}</p>
                <h4>{{ card.title }}</h4>
                <p class="card-language">{{ card.language }}</p>
                <div class="card-meta">
                  <span>{{ card.level }}</span>
                  <span>{{ card.status }}</span>
                </div>
              </article>
            </div>
            <p v-else class="empty-state">No learning cards available yet.</p>
          </section>
        </template>

        <template v-else>
          <h2>Choose a country</h2>
          <p class="hint">
            Click on the world map to reveal local languages and temporary learning cards.
          </p>
        </template>
      </aside>
    </div>

    <section class="exploration-sections">
      <article class="discovery-block card">
        <header>
          <h3>Recently added</h3>
          <p>Fresh entries in the language catalog.</p>
        </header>
        <ul v-if="recentlyAdded.length" class="compact-list">
          <li v-for="language in recentlyAdded" :key="`recent-${language.id}`">
            <span>{{ language.name }}</span>
            <small>{{ language.level }}</small>
          </li>
        </ul>
        <p v-else class="empty-state">No recent entries yet.</p>
      </article>

      <article class="discovery-block card">
        <header>
          <h3>Featured languages</h3>
          <p>Highlights to start your exploration.</p>
        </header>
        <ul v-if="featuredLanguages.length" class="compact-list">
          <li v-for="language in featuredLanguages" :key="`featured-${language.id}`">
            <span>{{ language.name }}</span>
            <small>{{ language.family || "Unknown family" }}</small>
          </li>
        </ul>
        <p v-else class="empty-state">No featured languages yet.</p>
      </article>

      <article class="discovery-block card">
        <header>
          <h3>Continue exploring</h3>
          <p>Keep navigating the map for more discoveries.</p>
        </header>
        <ul v-if="continueExploring.length" class="compact-list">
          <li v-for="language in continueExploring" :key="`continue-${language.id}`">
            <span>{{ language.name }}</span>
            <small>{{ language.level }}</small>
          </li>
        </ul>
        <p v-else class="empty-state">Explore the map to reveal language suggestions.</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.world-language-explorer {
  --explorer-ink: var(--text, #2F1722);
  --explorer-soft-ink: var(--text-soft, rgba(47, 23, 34, 0.65));
  --explorer-line: rgba(109, 31, 52, 0.14);
  --explorer-water: #f5e7e4;
  --explorer-sea: #ecc8c0;
  --explorer-land: #efe2d2;
  --explorer-land-data: #A8C498;
  --explorer-land-selected: #5B1928;

  display: grid;
  gap: 1.35rem;
  color: var(--explorer-ink);
}

.explorer-hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.2rem;
  border: 1px solid var(--explorer-line);
  border-radius: 20px;
  background:
    radial-gradient(circle at 12% 8%, #f9e8e4 0%, rgba(249, 232, 228, 0) 36%),
    radial-gradient(circle at 92% 10%, #f5ede8 0%, rgba(245, 237, 232, 0) 30%),
    linear-gradient(180deg, #f6f1e7 0%, #f3ebe3 100%);
}

.hero-copy h1 {
  margin: 0.15rem 0 0.5rem;
  font-size: clamp(1.55rem, 2.3vw, 2.25rem);
  line-height: 1.2;
  letter-spacing: -0.01em;
  font-family: "Fraunces", "Iowan Old Style", "Georgia", serif;
}

.hero-copy p {
  margin: 0;
  max-width: 60ch;
  color: var(--explorer-soft-ink);
  font-family: "Manrope", "Avenir Next", "Segoe UI", sans-serif;
}

.hero-kicker {
  margin: 0;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--primary, #5B1928);
  font-family: "Manrope", "Avenir Next", "Segoe UI", sans-serif;
}

.hero-actions {
  display: grid;
  gap: 0.75rem;
  justify-items: end;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(140px, 1fr));
  gap: 0.65rem;
}

.stat-chip {
  display: grid;
  padding: 0.65rem 0.7rem;
  border-radius: 12px;
  border: 1px solid rgba(152, 184, 222, 0.5);
  background: rgba(255, 255, 255, 0.7);
}

.stat-value {
  font-size: 1.2rem;
  font-weight: 800;
}

.stat-label {
  color: var(--explorer-soft-ink);
  font-size: 0.78rem;
}

.explorer-stage {
  display: grid;
  grid-template-columns: minmax(0, 2.2fr) minmax(320px, 1fr);
  gap: 1rem;
  align-items: stretch;
}

.map-column {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.active-country-name {
  margin: 0;
  min-height: 1.4rem;
  color: var(--primary, #5B1928);
  font-weight: 700;
  letter-spacing: 0.01em;
}

.map-surface {
  position: relative;
  overflow: hidden;
  min-height: 500px;
  border-radius: 20px;
  border: 1px solid var(--explorer-line);
  background:
    radial-gradient(ellipse 60% 50% at 20% 18%, rgba(255, 255, 255, 0.9) 0%, transparent 58%),
    radial-gradient(ellipse 48% 42% at 78% 22%, rgba(223, 232, 218, 0.55) 0%, transparent 55%),
    radial-gradient(ellipse 55% 48% at 48% 82%, rgba(245, 212, 206, 0.4) 0%, transparent 60%),
    linear-gradient(160deg, #f7f4f1 0%, #f0ebe6 42%, #ebe4df 100%);
  box-shadow: 0 18px 40px rgba(91, 25, 40, 0.08);
}

.map-surface::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.38;
  background-image:
    linear-gradient(rgba(72, 91, 56, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(72, 91, 56, 0.045) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 72% 66% at 50% 48%, black 18%, transparent 76%);
  z-index: 0;
}

.world-svg {
  position: relative;
  z-index: 1;
  width: 100%;
  height: auto;
  display: block;
  filter: url(#wl-continent-glow);
}

.country-shape {
  fill: url(#wl-continent-fill);
  stroke: rgba(91, 25, 40, 0.22);
  stroke-width: 0.65;
  paint-order: stroke fill;
  vector-effect: non-scaling-stroke;
  transition: fill 240ms ease, stroke 240ms ease, filter 180ms ease, opacity 240ms ease;
  cursor: pointer;
  outline: none;
  opacity: 1;
}

.country-shape:hover,
.country-shape.is-hovered,
.country-shape:focus-visible {
  fill: url(#wl-continent-hover);
  stroke: rgba(91, 25, 40, 0.4);
  filter: drop-shadow(0 0 4px rgba(91, 25, 40, 0.16));
  opacity: 1;
}

.country-shape.has-data {
  fill: url(#wl-continent-data);
  stroke: rgba(52, 66, 40, 0.4);
}

.country-shape.has-data:hover,
.country-shape.has-data.is-hovered,
.country-shape.has-data:focus-visible {
  fill: url(#wl-continent-data);
  stroke: rgba(52, 66, 40, 0.55);
  filter: drop-shadow(0 0 4px rgba(72, 91, 56, 0.22));
}

.country-shape.is-selected {
  fill: url(#wl-continent-selected);
  stroke: rgba(60, 16, 24, 0.65);
}

.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(2px);
}

.details-column {
  min-height: 500px;
  border-radius: 18px;
  border: 1px solid var(--explorer-line);
  background: linear-gradient(180deg, #ffffff, #fff0ee 70%);
  box-shadow: 0 14px 32px rgba(91, 25, 40, 0.08);
}

.details-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--explorer-line);
}

.details-label {
  margin: 0;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  color: var(--explorer-soft-ink);
}

.details-header h2 {
  margin: 0.2rem 0 0;
  font-family: "Fraunces", "Iowan Old Style", "Georgia", serif;
}

.iso-chip {
  margin: 0;
  padding: 0.34rem 0.58rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
  background: rgba(109, 31, 52, 0.08);
  color: var(--primary, #5B1928);
  border: 1px solid rgba(109, 31, 52, 0.18);
}

.panel-block {
  margin-top: 1rem;
}

.panel-block h3 {
  margin: 0 0 0.6rem;
  font-size: 1rem;
}

.language-list,
.learning-grid {
  display: grid;
  gap: 0.65rem;
}

.language-item {
  border: 1px solid var(--explorer-line);
  border-radius: 12px;
  padding: 0.65rem;
  background: #fffdfb;
  transition: transform 160ms ease, box-shadow 180ms ease;
}

.language-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(91, 25, 40, 0.08);
}

.language-item h4,
.learning-card h4 {
  margin: 0 0 0.32rem;
  font-size: 0.95rem;
}

.language-item p {
  margin: 0.17rem 0;
  color: var(--explorer-soft-ink);
  font-size: 0.86rem;
}

.learning-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.learning-card {
  padding: 0.72rem;
  border-radius: 12px;
  border: 1px solid var(--explorer-line);
  background:
      radial-gradient(circle at 20% 12%, rgba(59, 130, 246, 0.12), rgba(59, 130, 246, 0) 52%),
      linear-gradient(180deg, #ffffff, #fff0ee);
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.learning-card:hover {
  transform: translateY(-2px);
  border-color: rgba(91, 25, 40, 0.28);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.08);
}

.card-type {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--primary, #5B1928);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.card-language {
  margin: 0;
  color: var(--primary, #5B1928);
}

.card-meta {
  margin-top: 0.55rem;
  display: flex;
  justify-content: space-between;
  gap: 0.4rem;
  font-size: 0.78rem;
  color: var(--explorer-soft-ink);
}

.exploration-sections {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.85rem;
}

.discovery-block {
  border-radius: 16px;
  border: 1px solid #dbe7f6;
  background:
      radial-gradient(circle at 88% 10%, rgba(250, 204, 21, 0.14), rgba(250, 204, 21, 0) 38%),
      linear-gradient(180deg, #ffffff, #fff0ee 78%);
}

.discovery-block header h3 {
  margin: 0;
  font-size: 1rem;
}

.discovery-block header p {
  margin: 0.2rem 0 0.7rem;
  color: var(--explorer-soft-ink);
  font-size: 0.88rem;
}

.compact-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.55rem;
}

.compact-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.5rem 0.55rem;
  border-radius: 10px;
  border: 1px solid var(--explorer-line);
  background: rgba(255, 255, 255, 0.8);
}

.compact-list li span {
  font-weight: 600;
}

.compact-list li small {
  color: var(--explorer-soft-ink);
}

.hint,
.empty-state {
  margin: 0.2rem 0 0;
  color: #5a6f88;
}

.map-error {
  margin-top: 0.12rem;
}

@media (max-width: 1200px) {
  .explorer-stage {
    grid-template-columns: 1fr;
  }

  .details-column {
    min-height: 0;
  }

  .learning-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 920px) {
  .explorer-hero {
    flex-direction: column;
  }

  .hero-actions {
    justify-items: start;
  }

  .hero-stats {
    grid-template-columns: 1fr 1fr;
  }

  .map-surface {
    min-height: 420px;
  }

  .exploration-sections {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .world-language-explorer {
    gap: 1rem;
  }

  .map-surface {
    min-height: 300px;
  }

  .learning-grid {
    grid-template-columns: 1fr;
  }

  .hero-stats {
    grid-template-columns: 1fr;
    width: 100%;
  }
}
</style>
