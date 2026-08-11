<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";
import {fetchScenariosByAuthor, fetchScenarioThumbnails} from "../api/scenarios";
import {fetchLanguages} from "../api/languages";
import {buildApiUrl} from "../api/rest";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import ScenarioDiscussionModal from "../components/community/ScenarioDiscussionModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useBookmarkCategories} from "../composables/useBookmarkCategories";
import {useAuth} from "../composables/useAuth";

const props = defineProps({
  username: {type: String, required: true},
});

const scenarios = ref([]);
const previewMap = ref({});
const languageNameMap = ref({});
const error = ref("");
const notFound = ref(false);
const loading = ref(false);

const { openReader, activeScenario, closeReader } = useScenarioReader();
const { isLiked, toggleLike, isBookmarked, toggleBookmark } = useScenarioInteractions();
const { getCategory, setCategory, removeCategory, categoryList: bookmarkCategoryList, addCategory: addBookmarkCategory } = useBookmarkCategories();
const { isAuthenticated } = useAuth();

const bookmarkCategoryPickerId = ref(null);
const newBookmarkCategoryName = ref("");
const bookmarkPickerEl = ref(null);

const discussionScenario = ref(null);
function openDiscussion(s) { discussionScenario.value = s; }
function closeDiscussion() { discussionScenario.value = null; }

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

function languageName(id) {
  return languageNameMap.value[String(id)] ?? id ?? "";
}

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

const countLabel = computed(() =>
    `${scenarios.value.length} published scenario${scenarios.value.length !== 1 ? "s" : ""}`
);

async function load() {
  loading.value = true;
  error.value = "";
  notFound.value = false;
  try {
    const [data, languages] = await Promise.all([
      fetchScenariosByAuthor(props.username),
      fetchLanguages().catch(() => []),
    ]);

    const langList = Array.isArray(languages) ? languages : (languages.content ?? []);
    const langMap = {};
    for (const l of langList) {
      langMap[String(l.id)] = l.name ?? String(l.id);
    }
    languageNameMap.value = langMap;

    scenarios.value = Array.isArray(data) ? data : (data.content ?? []);

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
    if (e.message === "User not found") {
      notFound.value = true;
    } else {
      error.value = e.message || "Failed to load scenarios.";
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main class="us-root">
    <div class="us-hero">
      <p class="us-eyebrow">Community member</p>
      <h1 class="us-title">@{{ username }}</h1>
      <p v-if="!loading && !error && !notFound" class="us-subtitle">{{ countLabel }}</p>
    </div>

    <BaseLoader v-if="loading">Loading scenarios…</BaseLoader>
    <BaseAlert v-else-if="notFound" type="error">No user found with username "{{ username }}".</BaseAlert>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else>
      <div v-if="scenarios.length" class="us-grid">
        <article v-for="(s, index) in scenarios" :key="s.id" class="us-card">
          <button type="button" class="us-card__thumb" @click="openReader(s)">
            <img
                v-if="thumbnailUrl(s.id)"
                :src="thumbnailUrl(s.id)"
                :alt="s.title || 'Scene preview'"
                class="us-card__img"
            />
            <div v-else class="us-card__placeholder" :style="{ background: placeholderGradient(index) }">
              <span class="us-card__placeholder-num">{{ String(index + 1).padStart(2, '0') }}</span>
            </div>
            <div class="us-card__overlay" aria-hidden="true">
              <span class="us-card__overlay-label">Read →</span>
            </div>
          </button>

          <div class="us-card__body">
            <button type="button" class="us-card__title-link" @click="openReader(s)">
              <h3 class="us-card__title">{{ s.title || "Untitled scenario" }}</h3>
            </button>
            <div class="us-card__meta">
              <span v-if="s.languageId" class="us-card__lang">{{ languageName(s.languageId) }}</span>
              <template v-if="s.tags?.length">
                <span v-for="tag in s.tags.slice(0, 2)" :key="tag" class="us-card__tag">#{{ tag }}</span>
              </template>
            </div>

            <div class="us-card__actions">
              <button
                type="button"
                class="us-card__action us-card__action--read"
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
                class="us-card__icon-btn"
                :class="{ 'us-card__icon-btn--active': isLiked(s.id) }"
                :title="isLiked(s.id) ? 'Unlike' : 'Like'"
                @click="toggleLike(s.id)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" :fill="isLiked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
              </button>
              <div v-if="isAuthenticated" :ref="el => { if (bookmarkCategoryPickerId === s.id) bookmarkPickerEl = el }" class="us-card__bookmark-wrap">
                <button
                  type="button"
                  class="us-card__icon-btn"
                  :class="{ 'us-card__icon-btn--active': isBookmarked(s.id) }"
                  :title="isBookmarked(s.id) ? 'Remove bookmark' : 'Bookmark'"
                  @click="handleBookmarkClick(s.id)"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" :fill="isBookmarked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                  </svg>
                </button>

                <div v-if="bookmarkCategoryPickerId === s.id" class="us-card__bookmark-picker">
                  <p class="us-card__bookmark-picker-label">Save to category</p>
                  <button type="button" class="us-card__bookmark-picker-item us-card__bookmark-picker-item--none" @click="assignBookmarkCategory(s.id, null)">
                    No category
                  </button>
                  <div v-if="bookmarkCategoryList.length" class="us-card__bookmark-picker-divider"></div>
                  <button
                    v-for="cat in bookmarkCategoryList"
                    :key="cat"
                    type="button"
                    class="us-card__bookmark-picker-item"
                    :class="{ 'us-card__bookmark-picker-item--active': getCategory(s.id) === cat }"
                    @click="assignBookmarkCategory(s.id, cat)"
                  >
                    {{ cat }}
                    <span v-if="getCategory(s.id) === cat">✓</span>
                  </button>
                  <div class="us-card__bookmark-picker-divider"></div>
                  <div class="us-card__bookmark-picker-new">
                    <input
                      v-model="newBookmarkCategoryName"
                      class="us-card__bookmark-picker-input"
                      placeholder="New category…"
                      @keydown.enter="createAndAssignBookmarkCategory(s.id)"
                    />
                    <button
                      type="button"
                      class="us-card__bookmark-picker-add"
                      :disabled="!newBookmarkCategoryName.trim()"
                      @click="createAndAssignBookmarkCategory(s.id)"
                    >
                      Add
                    </button>
                  </div>
                </div>
              </div>
              <button
                type="button"
                class="us-card__icon-btn"
                title="Discussion"
                @click="openDiscussion(s)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
            </div>
          </div>
        </article>
      </div>

      <BaseEmptyState v-else title="No published scenarios yet" message="This person hasn't published any scenarios yet.">
        <RouterLink to="/scenarios" class="us-empty__link">Browse the catalogue →</RouterLink>
      </BaseEmptyState>
    </template>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
    <ScenarioDiscussionModal :scenario="discussionScenario" @close="closeDiscussion" />
  </main>
</template>

<style scoped>
.us-root {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 80px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.us-hero { display: flex; flex-direction: column; gap: 4px; }
.us-eyebrow {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--primary);
}
.us-title {
  margin: 0;
  font-size: clamp(1.6rem, 3.6vw, 2.2rem);
  font-weight: 950;
  letter-spacing: -0.02em;
  color: var(--text);
}
.us-subtitle { margin: 0; font-size: 0.9rem; color: var(--text-soft); }

.us-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
  align-items: start;
}

.us-card {
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: visible;
  background: #fff;
  border: 1.5px solid var(--border);
  box-shadow: 0 2px 8px rgba(30, 8, 18, 0.05);
  transition: transform 200ms ease, box-shadow 200ms ease;
}
.us-card:hover { transform: translateY(-4px); box-shadow: 0 14px 32px rgba(30, 8, 18, 0.1); }

.us-card__thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
  background: var(--surface-alt);
  display: block;
  border: 0;
  padding: 0;
  cursor: pointer;
  width: 100%;
}
.us-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 300ms ease; }
.us-card:hover .us-card__img { transform: scale(1.04); }
.us-card__placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.us-card__placeholder-num { font-size: 2rem; font-weight: 950; color: rgba(30,8,18,0.18); letter-spacing: -0.04em; }
.us-card__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30,8,18,0.45);
  opacity: 0;
  transition: opacity 200ms ease;
}
.us-card:hover .us-card__overlay { opacity: 1; }
.us-card__overlay-label { color: #fff; font-size: 0.85rem; font-weight: 800; }

.us-card__body { padding: 12px 14px 16px; display: flex; flex-direction: column; gap: 5px; }
.us-card__title-link { border: 0; background: transparent; padding: 0; text-align: left; cursor: pointer; }
.us-card__title-link:hover .us-card__title { color: var(--primary); }
.us-card__title { margin: 0; font-size: 0.95rem; font-weight: 800; color: var(--text); line-height: 1.25; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.us-card__meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.us-card__lang { font-size: 0.72rem; font-weight: 700; color: var(--primary); background: rgba(192,74,8,0.08); border-radius: 6px; padding: 2px 7px; }
.us-card__tag { font-size: 0.7rem; font-weight: 600; color: var(--text-soft); }

.us-card__actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}

.us-card__action {
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

.us-card__action--read {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
  flex: 1;
  justify-content: center;
}
.us-card__action--read:hover { background: var(--primary-strong); border-color: var(--primary-strong); }

.us-card__icon-btn {
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
.us-card__icon-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.05);
}
.us-card__icon-btn--active {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.08);
}

.us-card__bookmark-wrap {
  position: relative;
  display: inline-flex;
}

.us-card__bookmark-picker {
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

.us-card__bookmark-picker-label {
  margin: 2px 6px 4px;
  font-size: 0.63rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-soft);
}

.us-card__bookmark-picker-item {
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
.us-card__bookmark-picker-item:hover { background: var(--surface-alt); }
.us-card__bookmark-picker-item--active { color: var(--primary); background: rgba(192, 74, 8, 0.06); }
.us-card__bookmark-picker-item--none { color: var(--text-soft); }

.us-card__bookmark-picker-divider {
  height: 1px;
  background: var(--border);
  margin: 4px 0;
}

.us-card__bookmark-picker-new {
  display: flex;
  gap: 6px;
  padding: 2px;
}

.us-card__bookmark-picker-input {
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
.us-card__bookmark-picker-input:focus { border-color: var(--primary); }

.us-card__bookmark-picker-add {
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
.us-card__bookmark-picker-add:hover:not(:disabled) { background: var(--primary-strong); }
.us-card__bookmark-picker-add:disabled { opacity: 0.45; cursor: not-allowed; }

.us-empty__link { color: var(--primary); font-weight: 700; text-decoration: none; }
.us-empty__link:hover { text-decoration: underline; }

@media (max-width: 640px) {
  .us-root { padding: 20px 14px 60px; }
  .us-grid { grid-template-columns: 1fr; }
}
</style>
