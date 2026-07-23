<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {fetchScenarios, fetchScenarioThumbnails} from "../api/scenarios";
import {fetchLanguages} from "../api/languages";
import {buildApiUrl} from "../api/rest";
import {useDebouncedRef} from "../composables/useDebouncedRef";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import ScenarioDiscussionModal from "../components/community/ScenarioDiscussionModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useBookmarkCategories} from "../composables/useBookmarkCategories";
import {useAuth} from "../composables/useAuth";
import { forkScenario } from "../api/scenarios";
import { useRouter } from "vue-router";

const scenarios = ref([]);
const previewMap = ref({});
const languageNameMap = ref({}); // { languageId: languageName }
const error = ref("");
const loading = ref(false);

const {source: search, debounced} = useDebouncedRef("", 250);
const effectiveSearch = ref("");
watch(debounced, (v) => { effectiveSearch.value = v.trim().toLowerCase(); });

const languageFilter = ref("");
const { openReader, activeScenario, closeReader } = useScenarioReader();
const { isLiked, toggleLike, isBookmarked, toggleBookmark } = useScenarioInteractions();

const { getCategory, setCategory, removeCategory, categoryList: bookmarkCategoryList, addCategory: addBookmarkCategory } = useBookmarkCategories();

const bookmarkCategoryPickerId = ref(null);
const newBookmarkCategoryName = ref("");
const bookmarkPickerEl = ref(null);

function handleBookmarkClick(scenarioId) {
  const wasBookmarked = isBookmarked(scenarioId);
  if (wasBookmarked) {
    removeCategory(scenarioId);
    toggleBookmark(scenarioId);
    bookmarkCategoryPickerId.value = null;
  } else {
    toggleBookmark(scenarioId);
    bookmarkCategoryPickerId.value = scenarioId;
  }
}

function assignBookmarkCategory(scenarioId, category) {
  setCategory(scenarioId, category);
  bookmarkCategoryPickerId.value = null;
}

function createAndAssignBookmarkCategory(scenarioId) {
  const name = newBookmarkCategoryName.value.trim();
  if (!name) return;
  addBookmarkCategory(name);
  setCategory(scenarioId, name);
  newBookmarkCategoryName.value = "";
  bookmarkCategoryPickerId.value = null;
}

function onDocumentClickForBookmarkPicker(event) {
  if (
      bookmarkCategoryPickerId.value !== null &&
      bookmarkPickerEl.value &&
      !bookmarkPickerEl.value.contains(event.target)
  ) {
    bookmarkCategoryPickerId.value = null;
  }
}

onMounted(() => document.addEventListener("click", onDocumentClickForBookmarkPicker));
onBeforeUnmount(() => document.removeEventListener("click", onDocumentClickForBookmarkPicker));

const { isAuthenticated, currentUser } = useAuth();

const router = useRouter();
const copyingId = ref(null);
const copyError = ref("");
const discussionScenario = ref(null);
function openDiscussion(s) { discussionScenario.value = s; }
function closeDiscussion() { discussionScenario.value = null; }

async function copyScenario(s) {
  if (copyingId.value) return;
  copyingId.value = s.id;
  copyError.value = "";
  try {
    const result = await forkScenario(s.id);
    router.push(`/scenarios/${result.id}`);
  } catch (e) {
    copyError.value = e.message || "Could not copy this scenario.";
  } finally {
    copyingId.value = null;
  }
}

function languageName(id) {
  return languageNameMap.value[String(id)] ?? id ?? "";
}

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
    const matchesLang = !lang || languageName(s.languageId).toLowerCase().includes(lang);
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
    const [data, languages] = await Promise.all([
      fetchScenarios(),
      fetchLanguages().catch(() => []),
    ]);

    // Build language ID → name map
    const langList = Array.isArray(languages) ? languages : (languages.content ?? []);
    const langMap = {};
    for (const l of langList) {
      langMap[String(l.id)] = l.name ?? String(l.id);
    }
    languageNameMap.value = langMap;

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
          placeholder="Filter by language name…"
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
          <RouterLink v-if="currentUser && s.authorUsername === currentUser.username" :to="`/scenarios/${s.id}`" class="sc-card__thumb" tabindex="-1">
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
              <span class="sc-card__overlay-label">Open studio →</span>
            </div>
          </RouterLink>
          <button v-else type="button" class="sc-card__thumb" @click="openReader(s)">
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
              <span class="sc-card__overlay-label">Read →</span>
            </div>
          </button>

          <!-- Body -->
          <div class="sc-card__body">
            <RouterLink v-if="currentUser && s.authorUsername === currentUser.username" :to="`/scenarios/${s.id}`" class="sc-card__title-link">
              <h3 class="sc-card__title">{{ s.title || "Untitled scenario" }}</h3>
            </RouterLink>
            <button v-else type="button" class="sc-card__title-link" @click="openReader(s)">
              <h3 class="sc-card__title">{{ s.title || "Untitled scenario" }}</h3>
            </button>

            <div class="sc-card__meta">
              <span class="sc-card__author">{{ s.authorUsername ?? "Unknown" }}</span>
              <span v-if="s.languageId" class="sc-card__lang">{{ languageName(s.languageId) }}</span>
              <template v-if="s.tags?.length">
                <span v-for="tag in s.tags.slice(0, 2)" :key="tag" class="sc-card__tag">#{{ tag }}</span>
              </template>
            </div>

            <p v-if="s.description?.trim()" class="sc-card__desc">
              {{ s.description.trim().length > 90 ? s.description.trim().slice(0, 87) + "…" : s.description.trim() }}
            </p>

            <!-- Actions -->
            <div class="sc-card__actions">
              <RouterLink
                v-if="currentUser && s.authorUsername === currentUser.username"
                :to="`/scenarios/${s.id}`"
                class="sc-card__action sc-card__action--open"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" width="13" height="13">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
                Open
              </RouterLink>
              <button
                v-if="currentUser && s.authorUsername === currentUser.username"
                type="button"
                class="sc-card__action sc-card__action--read"
                @click="openReader(s)"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="13" height="13">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <button
                v-else
                type="button"
                class="sc-card__action sc-card__action--read"
                @click="openReader(s)"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="13" height="13">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <button
                v-if="isAuthenticated"
                type="button"
                class="sc-card__icon-btn"
                :class="{ 'sc-card__icon-btn--active': isLiked(s.id) }"
                :title="isLiked(s.id) ? 'Unlike' : 'Like'"
                @click="toggleLike(s.id)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" :fill="isLiked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
              </button>
              <div v-if="isAuthenticated" :ref="el => { if (bookmarkCategoryPickerId === s.id) bookmarkPickerEl = el }" class="sc-card__bookmark-wrap">
                <button
                  type="button"
                  class="sc-card__icon-btn"
                  :class="{ 'sc-card__icon-btn--active': isBookmarked(s.id) }"
                  :title="isBookmarked(s.id) ? 'Remove bookmark' : 'Bookmark'"
                  @click="handleBookmarkClick(s.id)"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" :fill="isBookmarked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                  </svg>
                </button>

                <div v-if="bookmarkCategoryPickerId === s.id" class="sc-card__bookmark-picker">
                  <p class="sc-card__bookmark-picker-label">Save to category</p>
                  <button type="button" class="sc-card__bookmark-picker-item sc-card__bookmark-picker-item--none" @click="assignBookmarkCategory(s.id, null)">
                    No category
                  </button>
                  <div v-if="bookmarkCategoryList.length" class="sc-card__bookmark-picker-divider"></div>
                  <button
                    v-for="cat in bookmarkCategoryList"
                    :key="cat"
                    type="button"
                    class="sc-card__bookmark-picker-item"
                    :class="{ 'sc-card__bookmark-picker-item--active': getCategory(s.id) === cat }"
                    @click="assignBookmarkCategory(s.id, cat)"
                  >
                    {{ cat }}
                    <span v-if="getCategory(s.id) === cat">✓</span>
                  </button>
                  <div class="sc-card__bookmark-picker-divider"></div>
                  <div class="sc-card__bookmark-picker-new">
                    <input
                      v-model="newBookmarkCategoryName"
                      class="sc-card__bookmark-picker-input"
                      placeholder="New category…"
                      @keydown.enter="createAndAssignBookmarkCategory(s.id)"
                    />
                    <button
                      type="button"
                      class="sc-card__bookmark-picker-add"
                      :disabled="!newBookmarkCategoryName.trim()"
                      @click="createAndAssignBookmarkCategory(s.id)"
                    >
                      Add
                    </button>
                  </div>
                </div>
              </div>
              <button
                v-if="isAuthenticated && currentUser && s.authorUsername !== currentUser.username"
                type="button"
                class="sc-card__icon-btn"
                :disabled="copyingId === s.id"
                :title="copyingId === s.id ? 'Copying…' : 'Copy to my scenarios'"
                @click="copyScenario(s)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
              <button
                type="button"
                class="sc-card__icon-btn"
                title="Discussion"
                @click="openDiscussion(s)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </button>

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
    <ScenarioDiscussionModal :scenario="discussionScenario" @close="closeDiscussion" />
    <BaseAlert v-if="copyError" type="error">{{ copyError }}</BaseAlert>
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
  border-radius: 18px 18px 0 0; 
  background: var(--surface-alt);
  display: block;
  text-decoration: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  width: 100%;
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

.sc-card__title-link { text-decoration: none; color: inherit; border: 0; background: transparent; padding: 0; text-align: left; cursor: pointer; width: 100%; }
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

.sc-card__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border: 1.5px solid var(--border);
  border-radius: 10px;
  background: transparent;
  color: var(--text-soft);
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
}

.sc-card__icon-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.05);
}

.sc-card__icon-btn--active {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.08);
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

.sc-card__bookmark-wrap {
  position: relative;
  display: inline-flex;
}

.sc-card__bookmark-picker {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 200;
  min-width: 200px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  border: 1.5px solid var(--border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(42, 21, 0, 0.16);
}

.sc-card__bookmark-picker-label {
  margin: 2px 6px 4px;
  font-size: 0.63rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-soft);
}

.sc-card__bookmark-picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 0.83rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 120ms ease;
}
.sc-card__bookmark-picker-item:hover { background: var(--surface-alt); }
.sc-card__bookmark-picker-item--active { color: var(--primary); background: rgba(192, 74, 8, 0.06); }
.sc-card__bookmark-picker-item--none { color: var(--text-soft); }

.sc-card__bookmark-picker-divider {
  height: 1px;
  background: var(--border);
  margin: 4px 0;
}

.sc-card__bookmark-picker-new {
  display: flex;
  gap: 6px;
  padding: 2px;
}

.sc-card__bookmark-picker-input {
  flex: 1;
  min-width: 0;
  border: 1.5px solid var(--border);
  border-radius: 8px;
  padding: 6px 9px;
  font: inherit;
  font-size: 0.8rem;
  outline: none;
  transition: border-color 140ms ease;
}
.sc-card__bookmark-picker-input:focus { border-color: var(--primary); }

.sc-card__bookmark-picker-add {
  border: 0;
  border-radius: 8px;
  padding: 0 11px;
  background: var(--primary);
  color: #fff;
  font: inherit;
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 140ms ease;
}
.sc-card__bookmark-picker-add:hover:not(:disabled) { background: var(--primary-strong); }
.sc-card__bookmark-picker-add:disabled { opacity: 0.45; cursor: not-allowed; }

@media (max-width: 640px) {
  .sc-root { padding: 20px 14px 60px; gap: 18px; }
  .sc-hero { flex-direction: column; align-items: flex-start; gap: 14px; }
  .sc-bar { flex-direction: column; }
  .sc-search--lang { flex: 1; min-width: 0; }
  .sc-grid { grid-template-columns: 1fr; }
}

</style>
