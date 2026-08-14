<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {fetchLanguage, fetchLanguageScenarios, fetchMyLanguagePermissions} from "../api/languages";
import {fetchScenarioThumbnails} from "../api/scenarios";
import {buildApiUrl, apiFetch} from "../api/rest";
import {useAuth} from "../composables/useAuth";
import {useToast} from "../composables/useToast";
import {useLanguageFollows} from "../composables/useLanguageFollows";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useBookmarkCategories} from "../composables/useBookmarkCategories";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import ScenarioDiscussionModal from "../components/community/ScenarioDiscussionModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseBadge from "../components/ui/BaseBadge.vue";

const props = defineProps({ id: { type: String, required: true } });

const { loadMe, isAuthenticated, currentUser } = useAuth();
const toast = useToast();
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

const language   = ref(null);
const scenarios  = ref([]);
const error      = ref("");
const loading    = ref(false);

// ── Follow ────────────────────────────────────────────────────────────────
const { isFollowing: isFollowingFn, toggleFollow: toggleFollowFn, followedIdsArray } = useLanguageFollows();
const isFollowing = ref(false);
watch(followedIdsArray, () => { isFollowing.value = isFollowingFn(props.id); });

async function toggleFollow() {
  if (!isAuthenticated.value) return;
  const was = isFollowing.value;
  await toggleFollowFn(props.id, language.value?.name ?? null);
  isFollowing.value = isFollowingFn(props.id);
  toast.success(was
    ? `Unfollowed ${language.value?.name ?? "this language"}.`
    : `Following ${language.value?.name ?? "this language"}.`
  );
}

// ── Thumbnails ────────────────────────────────────────────────────────────
const thumbnailUrls = ref({});
const likeCounts    = ref({}); // { scenarioId: number }

async function loadThumbnails(list) {
  const urls   = {};
  const counts = {};
  await Promise.all(list.map(async (s) => {
    try {
      const thumbs = await fetchScenarioThumbnails(s.id);
      if (thumbs?.length) {
        const sorted = [...thumbs].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
        urls[s.id] = buildApiUrl(`/api/thumbnails/${sorted[0].id}/content`);
      }
    } catch {}
    try {
      const status = await apiFetch(`/api/scenarios/${s.id}/interactions`);
      counts[s.id] = status?.likeCount ?? 0;
    } catch { counts[s.id] = 0; }
  }));
  thumbnailUrls.value = urls;
  likeCounts.value    = counts;
  // Initialize reactive map from fetched counts
  likeCountMap.value  = { ...counts };
}

// ── Filtres ───────────────────────────────────────────────────────────────
const search      = ref("");
const activeTag   = ref("");
const sortBy      = ref("recent"); // "recent" | "title"
const sortOpen    = ref(false);
const infoOpen      = ref(false);
const discussionScenario = ref(null);
function openDiscussion(s) { discussionScenario.value = s; }
function closeDiscussion() { discussionScenario.value = null; }
const likeCountMap  = ref({}); // reactive local like counts { scenarioId: number }
const viewMode    = ref("grid");   // "grid" | "single"

const allTags = computed(() => {
  const set = new Set();
  for (const s of scenarios.value) {
    for (const t of (s.tags ?? [])) set.add(t);
  }
  return [...set].sort();
});

const published = computed(() =>
  scenarios.value.filter(s => s.visibilityStatus === "PUBLISHED")
);

const filtered = computed(() => {
  let list = published.value;
  const q = search.value.trim().toLowerCase();
  if (q) list = list.filter(s =>
    [s.title ?? "", s.authorUsername ?? "", s.description ?? "", ...(s.tags ?? [])]
      .some(v => v.toLowerCase().includes(q))
  );
  if (activeTag.value) list = list.filter(s => (s.tags ?? []).includes(activeTag.value));
  if (sortBy.value === "title") list = [...list].sort((a, b) => (a.title ?? "").localeCompare(b.title ?? ""));
  else list = [...list].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  return list;
});

// ── Single view navigation ────────────────────────────────────────────────
const singleIndex = ref(0);
watch(filtered, () => { singleIndex.value = 0; infoOpen.value = false; });
watch(singleIndex, () => {
  infoOpen.value = false;
});
const currentScenario = computed(() => filtered.value[singleIndex.value] ?? null);
function prevScenario() { if (singleIndex.value > 0) singleIndex.value--; }
function nextScenario() { if (singleIndex.value < filtered.value.length - 1) singleIndex.value++; }

// ── Load ──────────────────────────────────────────────────────────────────
function levelVariant(level) {
  if (!level) return "neutral";
  const l = String(level).toLowerCase();
  if (l.includes("family")) return "info";
  if (l.includes("language")) return "success";
  if (l.includes("dialect")) return "warning";
  return "neutral";
}

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
}

async function handleToggleLike(scenarioId) {
  const id = String(scenarioId);
  const wasLiked = isLiked(id);
  // Optimistic update for snappy UI — reconciled with the server's count below
  const current = likeCountMap.value[id] ?? 0;
  likeCountMap.value = { ...likeCountMap.value, [id]: current + (wasLiked ? -1 : 1) };

  const status = await toggleLike(id);
  if (status) likeCountMap.value = { ...likeCountMap.value, [id]: status.likeCount };
}

function avatarColor(username) {
  const colors = ["#C04A08","#982800","#7A3812","#D4580A","#b45309","#065f46","#6d28d9","#1e40af"];
  return username ? colors[username.charCodeAt(0) % colors.length] : colors[0];
}

const TILE_GRADIENTS = [
  "linear-gradient(135deg,#D4E5CA,#c5d9b8)",
  "linear-gradient(135deg,#FFE0C0,#FFF0EE)",
  "linear-gradient(135deg,#c5d9b8,#afc8a0)",
  "linear-gradient(135deg,#ddd4f5,#c8bde8)",
  "linear-gradient(135deg,#A8C498,#8fb87f)",
];
function placeholderGradient(i) { return TILE_GRADIENTS[i % TILE_GRADIENTS.length]; }

async function load(id) {
  loading.value = true;
  error.value   = "";
  language.value = null;
  scenarios.value = [];
  search.value = "";
  activeTag.value = "";
  singleIndex.value = 0;

  try {
    await loadMe();
    const [lang, scens] = await Promise.all([
      fetchLanguage(id),
      fetchLanguageScenarios(id),
    ]);
    language.value  = lang;
    scenarios.value = scens;
    isFollowing.value = isFollowingFn(id);
    await loadThumbnails(scens.filter(s => s.visibilityStatus === "PUBLISHED"));
  } catch (e) {
    error.value = e.message || "Failed to load.";
  } finally {
    loading.value = false;
  }
}

watch(() => props.id, (id) => load(id), { immediate: true });
</script>

<template>
  <main class="lv-root">
    <BaseLoader v-if="loading">Loading…</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else-if="language">

      <!-- ── Hero ─────────────────────────────────────────────────────── -->
      <div class="lv-hero">
        <div class="lv-hero__left">
          <div class="lv-hero__icon" :style="{ background: `linear-gradient(135deg, ${avatarColor(language.name)} 0%, #1E0812 100%)` }">
            {{ (language.name ?? "?").slice(0, 2).toUpperCase() }}
          </div>
          <div class="lv-hero__info">
            <div class="lv-hero__badges">
              <BaseBadge :variant="levelVariant(language.level)">{{ language.level ?? "Language" }}</BaseBadge>
              <BaseBadge v-if="language.iso639P3code" variant="neutral">
                <code>{{ language.iso639P3code }}</code>
              </BaseBadge>
            </div>
            <h1 class="lv-hero__title">{{ language.name }}</h1>
            <p class="lv-hero__meta">
              <span v-if="language.familyName">{{ language.familyName }}</span>
              <span v-if="language.countryIds"> · {{ language.countryIds }}</span>
            </p>
          </div>
        </div>

        <div class="lv-hero__right">
          <div class="lv-hero__stats">
            <div class="lv-stat">
              <span class="lv-stat__num">{{ published.length }}</span>
              <span class="lv-stat__lbl">Scenarios</span>
            </div>
          </div>
          <button
            v-if="isAuthenticated"
            type="button"
            class="lv-follow-btn"
            :class="{ 'lv-follow-btn--active': isFollowing }"
            @click="toggleFollow"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              <circle v-if="isFollowing" cx="19" cy="5" r="3" fill="currentColor" stroke="none"/>
            </svg>
            {{ isFollowing ? "Following" : "Follow" }}
          </button>
        </div>
      </div>

      <!-- ── Toolbar ───────────────────────────────────────────────────── -->
      <div class="lv-toolbar">

        <!-- Search -->
        <div class="lv-search">
          <svg class="lv-search__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <input v-model="search" class="lv-search__input" placeholder="Search scenarios…"/>
          <button v-if="search" type="button" class="lv-search__clear" @click="search = ''">×</button>
        </div>

        <!-- Sort -->
        <div class="lv-dropdown" :class="{ 'lv-dropdown--open': sortOpen }">
          <button type="button" class="lv-dropdown__trigger" @click="sortOpen = !sortOpen">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 6h18M7 12h10M11 18h2"/>
            </svg>
            {{ sortBy === 'recent' ? 'Most recent' : 'Title A–Z' }}
            <svg class="lv-dropdown__chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="m6 9 6 6 6-6"/>
            </svg>
          </button>
          <div v-if="sortOpen" class="lv-dropdown__menu">
            <button type="button" class="lv-dropdown__item" :class="{ 'lv-dropdown__item--active': sortBy === 'recent' }" @click="sortBy = 'recent'; sortOpen = false">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
              </svg>
              Most recent
            </button>
            <button type="button" class="lv-dropdown__item" :class="{ 'lv-dropdown__item--active': sortBy === 'title' }" @click="sortBy = 'title'; sortOpen = false">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 6h18M7 12h10M11 18h2"/>
              </svg>
              Title A–Z
            </button>
          </div>
        </div>

        <!-- View toggle -->
        <div class="lv-view-toggle">
          <button
            type="button"
            class="lv-view-btn"
            :class="{ 'lv-view-btn--active': viewMode === 'grid' }"
            title="Grid view"
            @click="viewMode = 'grid'"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="7" height="7" rx="1"/>
              <rect x="14" y="3" width="7" height="7" rx="1"/>
              <rect x="3" y="14" width="7" height="7" rx="1"/>
              <rect x="14" y="14" width="7" height="7" rx="1"/>
            </svg>
          </button>
          <button
            type="button"
            class="lv-view-btn"
            :class="{ 'lv-view-btn--active': viewMode === 'single' }"
            title="Single view"
            @click="viewMode = 'single'"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="2" y="4" width="20" height="16" rx="2"/>
              <path d="M10 9l5 3-5 3V9z" fill="currentColor" stroke="none"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- Tag filters -->
      <div v-if="allTags.length" class="lv-tags">
        <button
          type="button"
          class="lv-tag"
          :class="{ 'lv-tag--active': activeTag === '' }"
          @click="activeTag = ''"
        >All</button>
        <button
          v-for="tag in allTags"
          :key="tag"
          type="button"
          class="lv-tag"
          :class="{ 'lv-tag--active': activeTag === tag }"
          @click="activeTag = activeTag === tag ? '' : tag"
        >#{{ tag }}</button>
      </div>

      <!-- Count -->
      <p class="lv-count">
        {{ filtered.length }} scenario{{ filtered.length !== 1 ? 's' : '' }}
        <span v-if="activeTag || search" class="lv-count__filtered">· filtered</span>
      </p>

      <!-- ── Grid view ─────────────────────────────────────────────────── -->
      <div v-if="viewMode === 'grid'" class="lv-grid">
        <div
          v-for="(s, index) in filtered"
          :key="s.id"
          class="lv-card"
          :class="{ 'lv-card--popover-open': bookmarkCategoryPickerId === s.id }"
        >
          <button type="button" class="lv-card__thumb" @click="openReader(s)">
            <img v-if="thumbnailUrls[s.id]" :src="thumbnailUrls[s.id]" :alt="s.title" class="lv-card__img"/>
            <div v-else class="lv-card__placeholder" :style="{ background: placeholderGradient(index) }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" class="lv-card__placeholder-icon">
                <rect x="3" y="3" width="7" height="7" rx="1"/>
                <rect x="14" y="3" width="7" height="7" rx="1"/>
                <rect x="3" y="14" width="7" height="7" rx="1"/>
                <rect x="14" y="14" width="7" height="7" rx="1"/>
              </svg>
            </div>
            <div class="lv-card__overlay"><span>Read →</span></div>
          </button>

          <div class="lv-card__body">
            <button type="button" class="lv-card__title-btn" @click="openReader(s)">
              {{ s.title || "Untitled" }}
            </button>
            <div class="lv-card__meta">
              <span class="lv-card__author">{{ s.authorUsername ?? "Unknown" }}</span>
            </div>
            <div v-if="s.tags?.length" class="lv-card__tags">
              <span v-for="tag in s.tags.slice(0, 3)" :key="tag" class="lv-card__tag">#{{ tag }}</span>
            </div>
            <div class="lv-card__actions">
              <button type="button" class="lv-card__read-btn" @click="openReader(s)">
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="12" height="12">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <button
                v-if="isAuthenticated"
                type="button"
                class="lv-card__icon-btn"
                :class="{ 'lv-card__icon-btn--active': isLiked(s.id) }"
                :title="isLiked(s.id) ? 'Unlike' : 'Like'"
                @click="handleToggleLike(s.id)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" :fill="isLiked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
              </button>
              <div v-if="isAuthenticated" :ref="el => { if (bookmarkCategoryPickerId === s.id) bookmarkPickerEl = el }" class="lv-card__bookmark-wrap">
                <button
                  type="button"
                  class="lv-card__icon-btn"
                  :class="{ 'lv-card__icon-btn--active': isBookmarked(s.id) }"
                  :title="isBookmarked(s.id) ? 'Remove bookmark' : 'Bookmark'"
                  @click="handleBookmarkClick(s.id)"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" :fill="isBookmarked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                  </svg>
                </button>

                <div v-if="bookmarkCategoryPickerId === s.id" class="lv-card__bookmark-picker">
                  <p class="lv-card__bookmark-picker__label">Save to category</p>
                  <button type="button" class="lv-card__bookmark-picker__item lv-card__bookmark-picker__item--none" @click="assignBookmarkCategory(s.id, null)">
                    No category
                  </button>
                  <div v-if="bookmarkCategoryList.length" class="lv-card__bookmark-picker__divider"></div>
                  <button
                    v-for="cat in bookmarkCategoryList"
                    :key="cat"
                    type="button"
                    class="lv-card__bookmark-picker__item"
                    :class="{ 'lv-card__bookmark-picker__item--active': getCategory(s.id) === cat }"
                    @click="assignBookmarkCategory(s.id, cat)"
                  >
                    {{ cat }}
                    <span v-if="getCategory(s.id) === cat">✓</span>
                  </button>
                  <div class="lv-card__bookmark-picker__divider"></div>
                  <div class="lv-card__bookmark-picker__new">
                    <input
                      v-model="newBookmarkCategoryName"
                      class="lv-card__bookmark-picker__new-input"
                      placeholder="New category…"
                      @keydown.enter="createAndAssignBookmarkCategory(s.id)"
                    />
                    <button
                      type="button"
                      class="lv-card__bookmark-picker__new-btn"
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
                  class="lv-card__icon-btn"
                  title="Discussion"
                  @click="openDiscussion(s)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div v-if="!filtered.length" class="lv-empty">
          <template v-if="!published.length">
            <div class="lv-empty__tiles" aria-hidden="true">
              <div class="lv-empty__tile lv-empty__tile--1"></div>
              <div class="lv-empty__tile lv-empty__tile--2"></div>
              <div class="lv-empty__tile lv-empty__tile--3"></div>
            </div>
            <p class="lv-empty__eyebrow">Nothing here yet</p>
            <h2 class="lv-empty__title">No scenarios for this language</h2>
            <p class="lv-empty__sub">Be the first to create and publish a scenario for {{ language?.name ?? "this language" }}.</p>
            <RouterLink to="/create-scenario" class="lv-empty__cta">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 5v14M5 12h14"/>
              </svg>
              Create a scenario
            </RouterLink>
          </template>
          <template v-else>
            <p class="lv-empty__title">No scenarios match your filters</p>
            <button type="button" class="lv-empty__reset" @click="search = ''; activeTag = ''">Clear filters</button>
          </template>
        </div>
      </div>

      <!-- ── Single view ───────────────────────────────────────────────── -->
      <div v-else class="lv-single">
        <template v-if="filtered.length">
          <div class="lv-single__card">
            <!-- Image -->
            <div class="lv-single__img-wrap">
              <img
                v-if="thumbnailUrls[currentScenario.id]"
                :src="thumbnailUrls[currentScenario.id]"
                :alt="currentScenario.title"
                class="lv-single__img"
              />
              <div
                v-else
                class="lv-single__img-placeholder"
                :style="{ background: placeholderGradient(singleIndex) }"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" class="lv-single__placeholder-icon">
                  <rect x="3" y="3" width="7" height="7" rx="1"/>
                  <rect x="14" y="3" width="7" height="7" rx="1"/>
                  <rect x="3" y="14" width="7" height="7" rx="1"/>
                  <rect x="14" y="14" width="7" height="7" rx="1"/>
                </svg>
              </div>
              <div class="lv-single__img-overlay">
                <button type="button" class="lv-single__play-btn" @click="openReader(currentScenario)">
                  <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="22" height="22">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                  </svg>
                  Read scenario
                </button>
              </div>
            </div>

            <!-- Info -->
            <div class="lv-single__info">
              <div class="lv-single__counter">
                {{ singleIndex + 1 }} / {{ filtered.length }}
              </div>
              <h2 class="lv-single__title">{{ currentScenario.title || "Untitled" }}</h2>
              <div class="lv-single__meta">
                <div class="lv-single__avatar" :style="{ background: avatarColor(currentScenario.authorUsername) }">
                  {{ (currentScenario.authorUsername ?? "?").slice(0, 2).toUpperCase() }}
                </div>
                <span>{{ currentScenario.authorUsername ?? "Unknown" }}</span>
              </div>
              <p v-if="currentScenario.description?.trim()" class="lv-single__desc">
                {{ currentScenario.description.trim() }}
              </p>
              <div v-if="currentScenario.tags?.length" class="lv-single__tags">
                <span v-for="tag in currentScenario.tags" :key="tag" class="lv-card__tag">#{{ tag }}</span>
              </div>

              <div class="lv-single__actions">
                <button type="button" class="lv-single__read-btn" @click="openReader(currentScenario)">
                  <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="14" height="14">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                  </svg>
                  Read
                </button>
                <button
                  v-if="isAuthenticated"
                  type="button"
                  class="lv-card__icon-btn"
                  :class="{ 'lv-card__icon-btn--active': isLiked(currentScenario.id) }"
                  @click="handleToggleLike(currentScenario.id)"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" :fill="isLiked(currentScenario.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                  </svg>
                </button>
                <div v-if="isAuthenticated" :ref="el => { if (bookmarkCategoryPickerId === currentScenario.id) bookmarkPickerEl = el }" class="lv-card__bookmark-wrap">
                  <button
                    type="button"
                    class="lv-card__icon-btn"
                    :class="{ 'lv-card__icon-btn--active': isBookmarked(currentScenario.id) }"
                    :title="isBookmarked(currentScenario.id) ? 'Remove bookmark' : 'Bookmark'"
                    @click="handleBookmarkClick(currentScenario.id)"
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" :fill="isBookmarked(currentScenario.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                    </svg>
                  </button>

                  <div v-if="bookmarkCategoryPickerId === currentScenario.id" class="lv-card__bookmark-picker">
                    <p class="lv-card__bookmark-picker__label">Save to category</p>
                    <button type="button" class="lv-card__bookmark-picker__item lv-card__bookmark-picker__item--none" @click="assignBookmarkCategory(currentScenario.id, null)">
                      No category
                    </button>
                    <div v-if="bookmarkCategoryList.length" class="lv-card__bookmark-picker__divider"></div>
                    <button
                      v-for="cat in bookmarkCategoryList"
                      :key="cat"
                      type="button"
                      class="lv-card__bookmark-picker__item"
                      :class="{ 'lv-card__bookmark-picker__item--active': getCategory(currentScenario.id) === cat }"
                      @click="assignBookmarkCategory(currentScenario.id, cat)"
                    >
                      {{ cat }}
                      <span v-if="getCategory(currentScenario.id) === cat">✓</span>
                    </button>
                    <div class="lv-card__bookmark-picker__divider"></div>
                    <div class="lv-card__bookmark-picker__new">
                      <input
                        v-model="newBookmarkCategoryName"
                        class="lv-card__bookmark-picker__new-input"
                        placeholder="New category…"
                        @keydown.enter="createAndAssignBookmarkCategory(currentScenario.id)"
                      />
                      <button
                        type="button"
                        class="lv-card__bookmark-picker__new-btn"
                        :disabled="!newBookmarkCategoryName.trim()"
                        @click="createAndAssignBookmarkCategory(currentScenario.id)"
                      >
                        Add
                      </button>
                    </div>
                  </div>
                </div>
                <button
                    type="button"
                    class="lv-card__icon-btn"
                    title="Discussion"
                    @click="openDiscussion(currentScenario)"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                  </svg>
                </button>
              </div>

              <!-- Nav -->
              <!-- Like count + info button -->
              <div class="lv-single__stats">
                <span class="lv-single__like-count">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                  </svg>
                  {{ likeCountMap[currentScenario.id] ?? 0 }} like{{ (likeCountMap[currentScenario.id] ?? 0) !== 1 ? 's' : '' }}
                </span>
                <button type="button" class="lv-single__info-btn" :class="{ 'lv-single__info-btn--active': infoOpen }" @click="infoOpen = !infoOpen" title="Scenario info">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
                  </svg>
                  Info
                </button>
              </div>



              <div class="lv-single__nav">
                <button type="button" class="lv-single__nav-btn" :disabled="singleIndex === 0" @click="prevScenario">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
                  Previous
                </button>
                <div class="lv-single__dots">
                  <button
                    v-for="(_, i) in filtered.slice(0, 7)"
                    :key="i"
                    type="button"
                    class="lv-single__dot"
                    :class="{ 'lv-single__dot--active': i === singleIndex }"
                    @click="singleIndex = i"
                  />
                  <span v-if="filtered.length > 7" class="lv-single__dots-more">+{{ filtered.length - 7 }}</span>
                </div>
                <button type="button" class="lv-single__nav-btn" :disabled="singleIndex >= filtered.length - 1" @click="nextScenario">
                  Next
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>
                </button>
              </div>
            </div>
          </div>
        </template>

        <div v-else class="lv-empty">
          <template v-if="!published.length">
            <div class="lv-empty__tiles" aria-hidden="true">
              <div class="lv-empty__tile lv-empty__tile--1"></div>
              <div class="lv-empty__tile lv-empty__tile--2"></div>
              <div class="lv-empty__tile lv-empty__tile--3"></div>
            </div>
            <p class="lv-empty__eyebrow">Nothing here yet</p>
            <h2 class="lv-empty__title">No scenarios for this language</h2>
            <p class="lv-empty__sub">Be the first to create and publish a scenario for {{ language?.name ?? "this language" }}.</p>
            <RouterLink to="/create-scenario" class="lv-empty__cta">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 5v14M5 12h14"/>
              </svg>
              Create a scenario
            </RouterLink>
          </template>
          <template v-else>
            <p class="lv-empty__title">No scenarios match your filters</p>
            <button type="button" class="lv-empty__reset" @click="search = ''; activeTag = ''">Clear filters</button>
          </template>
        </div>
      </div>

    </template>

    <!-- Info popup -->
    <Teleport to="body">
      <Transition name="lv-popup">
        <div v-if="infoOpen && currentScenario" class="lv-info-backdrop" @click.self="infoOpen = false">
          <div class="lv-info-popup" role="dialog" aria-modal="true" aria-label="Scenario info">
            <div class="lv-info-popup__head">
              <h3 class="lv-info-popup__title">{{ currentScenario.title || "Untitled" }}</h3>
              <button type="button" class="lv-info-popup__close" @click="infoOpen = false">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
              </button>
            </div>
            <div class="lv-info-popup__body">
              <div class="lv-info-popup__row">
                <span class="lv-info-popup__label">Author</span>
                <span class="lv-info-popup__value">{{ currentScenario.authorUsername ?? "Unknown" }}</span>
              </div>
              <div class="lv-info-popup__row">
                <span class="lv-info-popup__label">Published</span>
                <span class="lv-info-popup__value">{{ formatDate(currentScenario.createdAt) || "—" }}</span>
              </div>
              <div class="lv-info-popup__row">
                <span class="lv-info-popup__label">Language</span>
                <span class="lv-info-popup__value">{{ language?.name ?? "—" }}</span>
              </div>
              <div v-if="currentScenario.tags?.length" class="lv-info-popup__row">
                <span class="lv-info-popup__label">Tags</span>
                <span class="lv-info-popup__value">{{ currentScenario.tags.join(", ") }}</span>
              </div>
              <div class="lv-info-popup__row">
                <span class="lv-info-popup__label">Likes</span>
                <span class="lv-info-popup__value">{{ likeCountMap[currentScenario.id] ?? 0 }}</span>
              </div>
              <div class="lv-info-popup__row">
                <span class="lv-info-popup__label">Status</span>
                <span class="lv-info-popup__value lv-info-popup__value--pub">Published</span>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
    <ScenarioDiscussionModal :scenario="discussionScenario" @close="closeDiscussion" />
  </main>
</template>

<style scoped>
.lv-root { max-width: 1200px; margin: 0 auto; padding: 32px 24px 80px; display: flex; flex-direction: column; gap: 20px; }

/* ── Hero ── */
.lv-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; flex-wrap: wrap; padding: 28px; background: linear-gradient(180deg,#FFFCF7 0%,#FFF0EE 100%); border: 1.5px solid rgba(192,74,8,0.2); border-radius: 20px; box-shadow: 0 4px 24px rgba(42,21,0,0.07); }
.lv-hero__left { display: flex; align-items: center; gap: 18px; flex: 1; min-width: 0; }
.lv-hero__icon { width: 72px; height: 72px; border-radius: 20px; display: flex; align-items: center; justify-content: center; font-size: 1.5rem; font-weight: 800; color: #fff; flex-shrink: 0; box-shadow: 0 4px 16px rgba(30,8,18,0.25); }
.lv-hero__badges { display: flex; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
.lv-hero__title { margin: 0 0 4px; font-size: clamp(1.6rem,4vw,2.4rem); font-weight: 950; letter-spacing: -0.025em; color: var(--text); line-height: 1.05; }
.lv-hero__meta { margin: 0; font-size: 0.88rem; color: var(--text-soft); }
.lv-hero__right { display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.lv-hero__stats { display: flex; gap: 10px; }
.lv-stat { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 10px 18px; background: rgba(255,255,255,0.7); border: 1px solid var(--border); border-radius: 14px; }
.lv-stat__num { font-size: 1.6rem; font-weight: 950; color: var(--text); line-height: 1; }
.lv-stat__lbl { font-size: 0.68rem; font-weight: 700; color: var(--text-soft); text-transform: uppercase; letter-spacing: 0.08em; }
.lv-follow-btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border-radius: 999px; border: 1.5px solid var(--border); background: #fff; color: var(--primary); font: inherit; font-size: 0.88rem; font-weight: 700; cursor: pointer; transition: all 0.15s; }
.lv-follow-btn:hover { border-color: var(--primary); background: rgba(192,74,8,0.06); }
.lv-follow-btn--active { background: var(--primary); border-color: var(--primary); color: #fff; }
.lv-follow-btn--active:hover { background: var(--primary-strong); }

/* ── Toolbar ── */
.lv-toolbar { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.lv-search { flex: 1; min-width: 200px; display: flex; align-items: center; gap: 8px; border: 1.5px solid var(--border); border-radius: 12px; padding: 0 14px; background: #fff; transition: border-color 160ms, box-shadow 160ms; }
.lv-search:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(192,74,8,0.08); }
.lv-search__icon { width: 15px; height: 15px; flex-shrink: 0; color: var(--text-soft); }
.lv-search__input { flex: 1; border: 0; outline: none; padding: 11px 0; font: inherit; font-size: 0.88rem; color: var(--text); background: transparent; }
.lv-search__input::placeholder { color: var(--text-soft); }
.lv-search__clear { border: 0; background: transparent; color: var(--text-soft); cursor: pointer; font-size: 18px; line-height: 1; padding: 0; }
.lv-dropdown { position: relative; flex-shrink: 0; }
.lv-dropdown__trigger { display: inline-flex; align-items: center; gap: 7px; padding: 10px 14px; border: 1.5px solid var(--border); border-radius: 12px; background: #fff; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; transition: border-color 160ms, box-shadow 160ms; white-space: nowrap; }
.lv-dropdown__trigger:hover { border-color: var(--primary); }
.lv-dropdown--open .lv-dropdown__trigger { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(192,74,8,0.1); }
.lv-dropdown__chevron { transition: transform 200ms ease; flex-shrink: 0; color: var(--text-soft); }
.lv-dropdown--open .lv-dropdown__chevron { transform: rotate(180deg); }
.lv-dropdown__menu { position: absolute; top: calc(100% + 6px); left: 0; z-index: 50; background: #fff; border: 1.5px solid var(--border); border-radius: 14px; box-shadow: 0 8px 24px rgba(42,21,0,0.12); padding: 5px; min-width: 170px; display: flex; flex-direction: column; gap: 2px; }
.lv-dropdown__item { display: flex; align-items: center; gap: 8px; padding: 9px 12px; border: 0; border-radius: 9px; background: transparent; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer; text-align: left; transition: background 120ms, color 120ms; }
.lv-dropdown__item:hover { background: var(--surface-alt); color: var(--primary); }
.lv-dropdown__item--active { background: rgba(192,74,8,0.08); color: var(--primary); font-weight: 800; }
.lv-view-toggle { display: flex; gap: 2px; padding: 3px; border: 1.5px solid var(--border); border-radius: 12px; background: var(--surface-alt); flex-shrink: 0; }
.lv-view-btn { display: grid; place-items: center; width: 36px; height: 36px; border: 0; border-radius: 8px; background: transparent; color: var(--text-soft); cursor: pointer; transition: all 0.15s; }
.lv-view-btn:hover { color: var(--text); background: rgba(30,8,18,0.06); }
.lv-view-btn--active { background: #fff; color: var(--primary); box-shadow: 0 1px 4px rgba(30,8,18,0.1); }

/* ── Tag filters ── */
.lv-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.lv-tag { display: inline-flex; align-items: center; padding: 5px 14px; border-radius: 999px; border: 1.5px solid var(--border); background: #fff; color: var(--text-soft); font: inherit; font-size: 0.78rem; font-weight: 700; cursor: pointer; transition: all 0.15s; white-space: nowrap; }
.lv-tag:hover { border-color: var(--primary); color: var(--primary); }
.lv-tag--active { background: var(--primary); border-color: var(--primary); color: #fff; }

.lv-count { font-size: 0.82rem; color: var(--text-soft); margin: 0; }
.lv-count__filtered { opacity: 0.7; }

/* ── Grid view ── */
.lv-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; align-items: start; }
.lv-card { position: relative; display: flex; flex-direction: column; border-radius: 18px; background: #fff; border: 1.5px solid var(--border); box-shadow: 0 2px 8px rgba(42,21,0,0.05); transition: transform 200ms ease, box-shadow 200ms ease; }
.lv-card:hover { transform: translateY(-4px); box-shadow: 0 14px 36px rgba(42,21,0,0.11); }
/* Grid items paint as atomic units in grid order — a descendant's z-index
   can't escape past a later sibling card unless the card itself is raised,
   otherwise the bookmark picker renders behind the row below it. */
.lv-card--popover-open { z-index: 50; }
.lv-card__thumb { position: relative; aspect-ratio: 4/3; overflow: hidden; border-radius: 18px 18px 0 0; background: var(--surface-alt); display: block; border: 0; padding: 0; cursor: pointer; width: 100%; }
.lv-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 300ms ease; }
.lv-card:hover .lv-card__img { transform: scale(1.04); }
.lv-card__placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.lv-card__placeholder-icon { width: 36px; height: 36px; color: rgba(42,21,0,0.2); }
.lv-card__overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(42,21,0,0.42); opacity: 0; transition: opacity 200ms; backdrop-filter: blur(2px); color: #fff; font-size: 0.9rem; font-weight: 800; }
.lv-card:hover .lv-card__overlay { opacity: 1; }
.lv-card__body { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 5px; }
.lv-card__title-btn { border: 0; background: transparent; padding: 0; text-align: left; font: inherit; font-size: 0.95rem; font-weight: 800; color: var(--text); cursor: pointer; line-height: 1.25; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; width: 100%; }
.lv-card__title-btn:hover { color: var(--primary); }
.lv-card__meta { display: flex; align-items: center; gap: 6px; }
.lv-card__author { font-size: 0.76rem; font-weight: 600; color: var(--text-soft); }
.lv-card__tags { display: flex; flex-wrap: wrap; gap: 4px; }
.lv-card__tag { font-size: 0.7rem; font-weight: 700; color: var(--primary); background: rgba(192,74,8,0.08); border-radius: 6px; padding: 2px 7px; }
.lv-card__actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; padding-top: 10px; border-top: 1px solid var(--border); }
.lv-card__read-btn { display: inline-flex; align-items: center; gap: 5px; flex: 1; justify-content: center; border: 0; border-radius: 10px; padding: 7px 12px; background: var(--primary); color: #fff; font: inherit; font-size: 0.78rem; font-weight: 700; cursor: pointer; transition: background 140ms; }
.lv-card__read-btn:hover { background: var(--primary-strong); }
.lv-card__icon-btn { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex-shrink: 0; border: 1.5px solid var(--border); border-radius: 10px; background: transparent; color: var(--text-soft); cursor: pointer; transition: all 0.15s; }
.lv-card__icon-btn:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); }
.lv-card__icon-btn--active { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.08); }

.lv-card__bookmark-wrap { position: relative; display: inline-flex; }
.lv-card__bookmark-picker {
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
.lv-card__bookmark-picker__label { margin: 2px 6px 4px; font-size: 0.63rem; font-weight: 800; letter-spacing: 0.1em; text-transform: uppercase; color: var(--text-soft); }
.lv-card__bookmark-picker__item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 7px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--text); font: inherit; font-size: 0.83rem; font-weight: 600; text-align: left; cursor: pointer; transition: background 120ms ease; }
.lv-card__bookmark-picker__item:hover { background: var(--surface-alt); }
.lv-card__bookmark-picker__item--active { color: var(--primary); background: rgba(192, 74, 8, 0.06); }
.lv-card__bookmark-picker__item--none { color: var(--text-soft); }
.lv-card__bookmark-picker__divider { height: 1px; background: var(--border); margin: 4px 0; }
.lv-card__bookmark-picker__new { display: flex; gap: 6px; padding: 2px; }
.lv-card__bookmark-picker__new-input { flex: 1; min-width: 0; border: 1.5px solid var(--border); border-radius: 8px; padding: 6px 9px; font: inherit; font-size: 0.8rem; outline: none; transition: border-color 140ms ease; }
.lv-card__bookmark-picker__new-input:focus { border-color: var(--primary); }
.lv-card__bookmark-picker__new-btn { border: 0; border-radius: 8px; padding: 0 11px; background: var(--primary); color: #fff; font: inherit; font-size: 0.76rem; font-weight: 700; cursor: pointer; transition: background 140ms ease; }
.lv-card__bookmark-picker__new-btn:hover:not(:disabled) { background: var(--primary-strong); }
.lv-card__bookmark-picker__new-btn:disabled { opacity: 0.45; cursor: not-allowed; }

.lv-card__open-btn { display: inline-flex; align-items: center; gap: 5px; border: 1.5px solid var(--border); border-radius: 10px; padding: 7px 12px; background: transparent; color: var(--text-soft); font: inherit; font-size: 0.78rem; font-weight: 700; text-decoration: none; cursor: pointer; transition: all 0.15s; }
.lv-card__open-btn:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); }
.lv-single__open-btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: 1.5px solid var(--border); border-radius: 12px; background: transparent; color: var(--text); font: inherit; font-size: 0.9rem; font-weight: 700; text-decoration: none; cursor: pointer; transition: all 0.15s; }
.lv-single__open-btn:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); transform: translateY(-1px); }

/* ── Single view ── */
.lv-single { display: flex; flex-direction: column; gap: 20px; }
.lv-single__card { display: grid; grid-template-columns: 1fr 1fr; gap: 32px; background: #fff; border: 1.5px solid var(--border); border-radius: 22px; box-shadow: 0 4px 24px rgba(42,21,0,0.08); }
.lv-single__img-wrap { position: relative; aspect-ratio: 4/3; border-radius: 22px 0 0 22px; background: var(--surface-alt); overflow: hidden; }
.lv-single__img { width: 100%; height: 100%; object-fit: cover; display: block; }
.lv-single__img-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.lv-single__placeholder-icon { width: 64px; height: 64px; color: rgba(42,21,0,0.15); }
.lv-single__img-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(42,21,0,0.35); opacity: 0; transition: opacity 200ms; backdrop-filter: blur(2px); }
.lv-single__card:hover .lv-single__img-overlay { opacity: 1; }
.lv-single__play-btn { display: inline-flex; align-items: center; gap: 8px; padding: 14px 28px; border-radius: 999px; border: 2px solid rgba(255,255,255,0.7); background: rgba(255,255,255,0.15); backdrop-filter: blur(8px); color: #fff; font: inherit; font-size: 1rem; font-weight: 800; cursor: pointer; transition: all 0.15s; }
.lv-single__play-btn:hover { background: var(--primary); border-color: var(--primary); }
.lv-single__info { padding: 32px 32px 32px 0; display: flex; flex-direction: column; gap: 14px; justify-content: center; }
.lv-single__counter { font-size: 0.72rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.12em; color: var(--primary); }
.lv-single__title { margin: 0; font-size: clamp(1.4rem,3vw,2rem); font-weight: 950; letter-spacing: -0.025em; color: var(--text); line-height: 1.1; }
.lv-single__meta { display: flex; align-items: center; gap: 10px; }
.lv-single__avatar { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 0.68rem; font-weight: 800; color: #fff; flex-shrink: 0; }
.lv-single__meta span { font-size: 0.88rem; font-weight: 600; color: var(--text-soft); }
.lv-single__desc { margin: 0; font-size: 0.9rem; color: var(--text-soft); line-height: 1.6; }
.lv-single__tags { display: flex; flex-wrap: wrap; gap: 5px; }
.lv-single__actions { display: flex; gap: 8px; align-items: center; }
.lv-single__read-btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 24px; border: 0; border-radius: 12px; background: var(--primary); color: #fff; font: inherit; font-size: 0.9rem; font-weight: 800; cursor: pointer; transition: background 140ms, transform 120ms; }
.lv-single__read-btn:hover { background: var(--primary-strong); transform: translateY(-1px); }
.lv-single__nav { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-top: 14px; border-top: 1px solid var(--border); margin-top: auto; }
.lv-single__nav-btn { display: inline-flex; align-items: center; gap: 4px; border: 1.5px solid var(--border); border-radius: 10px; padding: 7px 14px; background: #fff; color: var(--text-soft); font: inherit; font-size: 0.82rem; font-weight: 700; cursor: pointer; transition: all 0.15s; }
.lv-single__nav-btn:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.lv-single__nav-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.lv-single__dots { display: flex; align-items: center; gap: 5px; }
.lv-single__dot { width: 7px; height: 7px; border-radius: 50%; border: 0; background: var(--border); cursor: pointer; padding: 0; transition: all 0.2s; }
.lv-single__dot--active { background: var(--primary); width: 20px; border-radius: 3px; }
.lv-single__dots-more { font-size: 0.72rem; color: var(--text-soft); font-weight: 700; }

.lv-single__stats { display: flex; align-items: center; gap: 10px; }
.lv-single__like-count { display: inline-flex; align-items: center; gap: 5px; font-size: 0.82rem; font-weight: 700; color: var(--primary); }
.lv-single__info-btn { display: inline-flex; align-items: center; gap: 5px; padding: 6px 14px; border: 1.5px solid var(--border); border-radius: 999px; background: #fff; color: var(--text-soft); font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer; transition: all 0.15s; }
.lv-single__info-btn:hover { border-color: var(--primary); color: var(--primary); }
.lv-single__info-btn--active { border-color: var(--primary); background: rgba(192,74,8,0.06); color: var(--primary); }

.lv-single__info-panel { background: var(--surface-alt); border: 1.5px solid var(--border); border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 8px; }
.lv-single__info-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 0.83rem; }
.lv-single__info-label { font-weight: 700; color: var(--text-soft); white-space: nowrap; }
.lv-single__info-value { font-weight: 600; color: var(--text); text-align: right; }
.lv-single__info-value--pub { color: #4A6741; font-weight: 800; }

.lv-info-slide-enter-active, .lv-info-slide-leave-active { transition: opacity 160ms ease, transform 160ms ease; }
.lv-info-slide-enter-from, .lv-info-slide-leave-to { opacity: 0; transform: translateY(-6px); }

/* Info popup */
.lv-info-backdrop { position: fixed; inset: 0; z-index: 1200; background: rgba(30,8,18,0.45); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; padding: 20px; }
.lv-info-popup { background: #fff; border: 1.5px solid var(--border); border-radius: 20px; box-shadow: 0 16px 48px rgba(30,8,18,0.18); width: min(400px, 100%); overflow: hidden; }
.lv-info-popup__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 18px 20px 14px; border-bottom: 1px solid var(--border); }
.lv-info-popup__title { margin: 0; font-size: 1rem; font-weight: 800; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.lv-info-popup__close { display: grid; place-items: center; width: 28px; height: 28px; flex-shrink: 0; border: 0; border-radius: 8px; background: transparent; color: var(--text-soft); cursor: pointer; transition: background 140ms; }
.lv-info-popup__close:hover { background: var(--surface-alt); color: var(--text); }
.lv-info-popup__body { display: flex; flex-direction: column; gap: 0; padding: 6px 0; }
.lv-info-popup__row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 20px; transition: background 120ms; }
.lv-info-popup__row:hover { background: var(--surface-alt); }
.lv-info-popup__label { font-size: 0.82rem; font-weight: 700; color: var(--text-soft); }
.lv-info-popup__value { font-size: 0.85rem; font-weight: 600; color: var(--text); text-align: right; }
.lv-info-popup__value--pub { color: #4A6741; font-weight: 800; }
.lv-popup-enter-active, .lv-popup-leave-active { transition: opacity 180ms ease, transform 180ms ease; }
.lv-popup-enter-from, .lv-popup-leave-to { opacity: 0; transform: scale(0.96); }

/* ── Empty ── */
.lv-empty { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 60px 20px 80px; text-align: center; grid-column: 1 / -1; }
.lv-empty__tiles { display: flex; align-items: flex-end; gap: 10px; margin-bottom: 8px; }
.lv-empty__tile { border: 2.5px solid #1E0812; border-radius: 12px; box-shadow: 3px 3px 0 #1E0812; opacity: 0.4; }
.lv-empty__tile--1 { width: 110px; height: 100px; background: linear-gradient(135deg,#D4E5CA,#c5d9b8); }
.lv-empty__tile--2 { width: 80px; height: 130px; background: linear-gradient(135deg,#FFF0EE,#D4E5CA); }
.lv-empty__tile--3 { width: 100px; height: 88px; background: linear-gradient(135deg,#c5d9b8,#afc8a0); }
.lv-empty__eyebrow { margin: 0; font-size: 0.68rem; font-weight: 900; color: var(--text-soft); letter-spacing: 0.14em; text-transform: uppercase; }
.lv-empty__title { margin: 0; font-size: clamp(1.2rem,3vw,1.6rem); font-weight: 950; color: var(--text); letter-spacing: -0.02em; }
.lv-empty__sub { margin: 0; font-size: 0.9rem; color: var(--text-soft); line-height: 1.6; max-width: 380px; }
.lv-empty__cta { display: inline-flex; align-items: center; gap: 8px; padding: 13px 26px; border: 0; border-radius: 14px; background: var(--text); color: #fff; font: inherit; font-size: 0.9rem; font-weight: 800; text-decoration: none; cursor: pointer; transition: background 160ms, transform 120ms; }
.lv-empty__cta:hover { background: var(--primary); transform: translateY(-1px); }
.lv-empty__cta svg { width: 14px; height: 14px; }
.lv-empty__reset { border: 1.5px solid var(--border); border-radius: 10px; padding: 8px 18px; background: #fff; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; transition: border-color 140ms; }
.lv-empty__reset:hover { border-color: var(--primary); color: var(--primary); }

@media (max-width: 700px) {
  .lv-single__card { grid-template-columns: 1fr; }
  .lv-single__info { padding: 20px; }
  .lv-hero { flex-direction: column; align-items: flex-start; }
  .lv-hero__right { flex-direction: row; flex-wrap: wrap; align-items: center; }
}
</style>