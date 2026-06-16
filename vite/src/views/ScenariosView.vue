<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {fetchScenarios, fetchScenarioThumbnails} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useDebouncedRef} from "../composables/useDebouncedRef";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";

const scenarios = ref([]);
const previewMap = ref({});
const error = ref("");
const loading = ref(false);

const {source: search, debounced} = useDebouncedRef("", 250);
const effectiveSearch = ref("");
watch(debounced, (v) => { effectiveSearch.value = v.trim().toLowerCase(); });

const languageFilter = ref("");
const { openReader, activeScenario, closeReader } = useScenarioReader();

const filtered = computed(() => {
  const q = effectiveSearch.value;
  const lang = languageFilter.value.trim().toLowerCase();
  return scenarios.value.filter(s => {
    const matchesSearch = !q || [
      s.title ?? "",
      s.authorUsername ?? "",
      s.description ?? "",
      ...(s.tags ?? []).map(String),
    ].some(v => v.toLowerCase().includes(q));
    const matchesLang = !lang || String(s.languageId ?? "").toLowerCase().includes(lang);
    return matchesSearch && matchesLang;
  });
});

function thumbnailUrl(id) {
  const thumbId = previewMap.value[id];
  return thumbId ? buildApiUrl(`/api/thumbnails/${thumbId}/content`) : null;
}

const TILE_GRADIENTS = [
  "linear-gradient(135deg,#D4E5CA,#c5d9b8)",
  "linear-gradient(135deg,#FFE0C0,#FFF0EE)",
  "linear-gradient(135deg,#c5d9b8,#afc8a0)",
  "linear-gradient(135deg,#ddd4f5,#c8bde8)",
  "linear-gradient(135deg,#A8C498,#8fb87f)",
];

function placeholderGradient(index) {
  return TILE_GRADIENTS[index % TILE_GRADIENTS.length];
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await fetchScenarios();
    // Only show published scenarios in the public catalogue
    const all = Array.isArray(data) ? data : (data.content ?? []);
    scenarios.value = all.filter(s => s.visibilityStatus === "PUBLISHED");

    const map = {};
    await Promise.all(
      scenarios.value.map(async (s) => {
        try {
          const thumbs = await fetchScenarioThumbnails(s.id);
          map[s.id] = thumbs?.[0]?.id ?? null;
        } catch {
          map[s.id] = null;
        }
      })
    );
    previewMap.value = map;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main class="sc-root">

    <!-- Hero -->
    <div class="sc-hero">
      <div>
        <p class="sc-eyebrow">Community catalogue</p>
        <h1 class="sc-title">Scenarios</h1>
        <p class="sc-subtitle">Browse published storyboards from the Vignette community.</p>
      </div>
      <RouterLink to="/create-scenario" class="sc-hero__cta">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        Create scenario
      </RouterLink>
    </div>

    <!-- Search bar -->
    <div class="sc-bar">
      <div class="sc-search">
        <svg class="sc-search__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8"/>
          <path d="m21 21-4.35-4.35"/>
        </svg>
        <input
          v-model="search"
          class="sc-search__input"
          placeholder="Search by title, author, tag…"
        />
        <button v-if="search" type="button" class="sc-search__clear" @click="search = ''">×</button>
      </div>
      <div class="sc-search sc-search--lang">
        <svg class="sc-search__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
          <path d="M2 12h20"/>
        </svg>
        <input
          v-model="languageFilter"
          class="sc-search__input"
          placeholder="Filter by language ID…"
        />
        <button v-if="languageFilter" type="button" class="sc-search__clear" @click="languageFilter = ''">×</button>
      </div>
    </div>

    <!-- Count -->
    <div class="sc-meta" v-if="!loading && !error">
      <span>{{ filtered.length }} scenario{{ filtered.length !== 1 ? 's' : '' }}</span>
      <span v-if="filtered.length !== scenarios.value?.length" class="sc-meta__filtered">
        · filtered from {{ scenarios.length }} total
      </span>
    </div>

    <BaseLoader v-if="loading">Loading scenarios…</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else>
      <!-- Grid -->
      <div v-if="filtered.length" class="sc-grid">
        <div
          v-for="(s, index) in filtered"
          :key="s.id"
          class="sc-card"
        >
          <!-- Thumbnail -->
          <RouterLink :to="`/scenarios/${s.id}`" class="sc-card__thumb" tabindex="-1">
            <img
              v-if="thumbnailUrl(s.id)"
              :src="thumbnailUrl(s.id)"
              :alt="s.title || 'Scene preview'"
              class="sc-card__img"
            />
            <div v-else class="sc-card__placeholder" :style="{ background: placeholderGradient(index) }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"
                   stroke-linecap="round" stroke-linejoin="round" class="sc-card__placeholder-icon">
                <rect x="3" y="3" width="7" height="7" rx="1"/>
                <rect x="14" y="3" width="7" height="7" rx="1"/>
                <rect x="3" y="14" width="7" height="7" rx="1"/>
                <rect x="14" y="14" width="7" height="7" rx="1"/>
              </svg>
            </div>
            <div class="sc-card__overlay" aria-hidden="true">
              <span class="sc-card__overlay-label">Open →</span>
            </div>
          </RouterLink>

          <!-- Body -->
          <div class="sc-card__body">
            <RouterLink :to="`/scenarios/${s.id}`" class="sc-card__title-link">
              <h3 class="sc-card__title">{{ s.title || "Untitled scenario" }}</h3>
            </RouterLink>

            <div class="sc-card__meta">
              <span class="sc-card__author">{{ s.authorUsername ?? "Unknown" }}</span>
              <span v-if="s.languageId" class="sc-card__lang">{{ s.languageId }}</span>
              <template v-if="s.tags?.length">
                <span v-for="tag in s.tags.slice(0, 2)" :key="tag" class="sc-card__tag">#{{ tag }}</span>
              </template>
            </div>

            <p v-if="s.description?.trim()" class="sc-card__desc">
              {{ s.description.trim().length > 90 ? s.description.trim().slice(0, 87) + "…" : s.description.trim() }}
            </p>

            <!-- Actions -->
            <div class="sc-card__actions">
              <button
                type="button"
                class="sc-card__action sc-card__action--read"
                @click="openReader(s)"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="13" height="13">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <RouterLink :to="`/scenarios/${s.id}`" class="sc-card__action sc-card__action--open">
                Open
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                     stroke-linecap="round" stroke-linejoin="round" width="12" height="12">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </RouterLink>
            </div>
          </div>
        </div>
      </div>

      <!-- No results -->
      <div v-else-if="scenarios.length" class="sc-noresults">
        <div class="sc-noresults__icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
               stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.35-4.35"/>
          </svg>
        </div>
        <p class="sc-noresults__text">No scenarios match <strong>"{{ search || languageFilter }}"</strong></p>
        <button type="button" class="sc-noresults__reset" @click="search = ''; languageFilter = ''">
          Clear filters
        </button>
      </div>

      <!-- Empty catalogue -->
      <div v-else class="sc-empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"
             stroke-linecap="round" stroke-linejoin="round" class="sc-empty__icon">
          <rect x="3" y="3" width="7" height="7" rx="1"/>
          <rect x="14" y="3" width="7" height="7" rx="1"/>
          <rect x="3" y="14" width="7" height="7" rx="1"/>
          <rect x="14" y="14" width="7" height="7" rx="1"/>
        </svg>
        <h2 class="sc-empty__title">No published scenarios yet</h2>
        <p class="sc-empty__sub">Be the first to create and publish a scenario.</p>
        <RouterLink to="/create-scenario" class="sc-hero__cta">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          Create scenario
        </RouterLink>
      </div>
    </template>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
  </main>
</template>

<style scoped>
.sc-root {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 80px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Hero */
.sc-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.sc-eyebrow {
  margin: 0 0 6px;
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--primary);
}

.sc-title {
  margin: 0 0 6px;
  font-size: clamp(1.8rem, 4vw, 2.6rem);
  font-weight: 950;
  letter-spacing: -0.025em;
  color: var(--text);
  line-height: 1.05;
}

.sc-subtitle {
  margin: 0;
  font-size: 0.92rem;
  color: var(--text-soft);
}

.sc-hero__cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 20px;
  border-radius: 14px;
  background: var(--text);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 800;
  text-decoration: none;
  white-space: nowrap;
  transition: background 160ms ease, transform 120ms ease;
}

.sc-hero__cta:hover { background: var(--primary); transform: translateY(-1px); }
.sc-hero__cta svg { width: 14px; height: 14px; }

/* Search bar */
.sc-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.sc-search {
  flex: 2;
  min-width: 200px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1.5px solid var(--border);
  border-radius: 12px;
  padding: 0 14px;
  background: #fff;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.sc-search--lang { flex: 1; min-width: 160px; }

.sc-search:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(192, 74, 8, 0.08);
}

.sc-search__icon { width: 15px; height: 15px; flex-shrink: 0; color: var(--text-soft); }

.sc-search__input {
  flex: 1;
  border: 0;
  outline: none;
  padding: 11px 0;
  font: inherit;
  font-size: 0.88rem;
  color: var(--text);
  background: transparent;
}

.sc-search__input::placeholder { color: var(--text-soft); }

.sc-search__clear {
  border: 0;
  background: transparent;
  color: var(--text-soft);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  padding: 0;
}
.sc-search__clear:hover { color: var(--text); }

/* Meta */
.sc-meta {
  font-size: 0.82rem;
  color: var(--text-soft);
  display: flex;
  gap: 4px;
}
.sc-meta__filtered { opacity: 0.7; }

/* Grid */
.sc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  align-items: start;
}

/* Card */
.sc-card {
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  border: 1.5px solid var(--border);
  box-shadow: 0 2px 8px rgba(42, 21, 0, 0.05);
  transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease;
}

.sc-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 14px 36px rgba(42, 21, 0, 0.11);
  border-color: rgba(192, 74, 8, 0.2);
}

.sc-card__thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--surface-alt);
  display: block;
  text-decoration: none;
}

.sc-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 300ms ease;
}

.sc-card:hover .sc-card__img { transform: scale(1.04); }

.sc-card__placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sc-card__placeholder-icon {
  width: 36px;
  height: 36px;
  color: rgba(42, 21, 0, 0.2);
}

.sc-card__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(42, 21, 0, 0.42);
  opacity: 0;
  transition: opacity 200ms ease;
  backdrop-filter: blur(2px);
}

.sc-card:hover .sc-card__overlay { opacity: 1; }

.sc-card__overlay-label {
  color: #fff;
  font-size: 0.9rem;
  font-weight: 800;
  transform: translateY(4px);
  transition: transform 200ms ease;
}

.sc-card:hover .sc-card__overlay-label { transform: translateY(0); }

.sc-card__body {
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sc-card__title-link { text-decoration: none; color: inherit; }
.sc-card__title-link:hover .sc-card__title { color: var(--primary); }

.sc-card__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 800;
  color: var(--text);
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sc-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.sc-card__author {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--text-soft);
}

.sc-card__lang {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--primary);
  background: rgba(192, 74, 8, 0.08);
  border-radius: 6px;
  padding: 2px 7px;
}

.sc-card__tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--text-soft);
}

.sc-card__desc {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-soft);
  line-height: 1.5;
}

/* Actions */
.sc-card__actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}

.sc-card__action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1.5px solid var(--border);
  border-radius: 10px;
  padding: 6px 12px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
}

.sc-card__action--read {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
  flex: 1;
  justify-content: center;
}

.sc-card__action--read:hover { background: var(--primary-strong); border-color: var(--primary-strong); }

.sc-card__action--open:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.05);
}

/* No results */
.sc-noresults {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 20px;
  text-align: center;
}

.sc-noresults__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: var(--surface-alt);
  border: 1px solid var(--border);
  color: var(--text-soft);
}

.sc-noresults__icon svg { width: 22px; height: 22px; }

.sc-noresults__text { margin: 0; font-size: 0.95rem; color: var(--text-soft); }

.sc-noresults__reset {
  border: 1.5px solid var(--border);
  border-radius: 10px;
  padding: 8px 18px;
  background: #fff;
  color: var(--text);
  font: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 140ms ease;
}

.sc-noresults__reset:hover { border-color: var(--primary); color: var(--primary); }

/* Empty */
.sc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 60px 20px 80px;
  text-align: center;
}

.sc-empty__icon { width: 48px; height: 48px; color: var(--text-soft); opacity: 0.3; }
.sc-empty__title { margin: 0; font-size: 1.4rem; font-weight: 800; color: var(--text); }
.sc-empty__sub { margin: 0; font-size: 0.9rem; color: var(--text-soft); }

@media (max-width: 640px) {
  .sc-root { padding: 20px 14px 60px; gap: 18px; }
  .sc-hero { flex-direction: column; align-items: flex-start; gap: 14px; }
  .sc-bar { flex-direction: column; }
  .sc-search--lang { flex: 1; min-width: 0; }
  .sc-grid { grid-template-columns: 1fr; }
}
</style>
