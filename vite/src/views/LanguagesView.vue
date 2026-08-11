<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {fetchLanguage, fetchLanguagePreviewAudio, fetchLanguages} from "../api/languages";
import {buildApiUrl} from "../api/rest";
import LanguageCatalogList from "../components/LanguageCatalogList.vue";
import WorldMap from "../components/maps/WorldMap.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import {useDebouncedRef} from "../composables/useDebouncedRef";
import {useToast} from "../composables/useToast";
import {useLanguageStore} from "@/composables/useLanguageStore";
import worldCountries from "world-countries";
import {ChevronLeft, ChevronRight, Info, Volume2} from "lucide-vue-next";

const route = useRoute();
const router = useRouter();
const toast = useToast();

const languages = ref([]);
const loading = ref(false);
const refreshing = ref(false);
const error = ref("");
const page = ref(Number(route.query.page ?? 0));
const totalPages = ref(0);
const PAGE_SIZE = 20;
const languagesMenuOpen = ref(true);
const countryBubbleOpen = ref(false);
const languageInfoOpen = ref(false);
const languageInfoLoading = ref(false);
const languageInfoError = ref("");
const languageInfoData = ref(null);
const bubbleSearch = ref("");
const bubbleOffset = ref(0);
const BUBBLE_PAGE_SIZE = 5;
const catalogForegroundRef = ref(null);
const panelOffsetX = ref(0);
const panelOffsetY = ref(0);
const panelDrag = ref({
  active: false,
  startClientX: 0,
  startClientY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
  rafId: 0,
  pendingX: 0,
  pendingY: 0,
});
const visibleLevels = ref({
  language: true,
  dialect: false,
});
const audioPreviewUi = ref(null);

let previewAudioPlayer = null;
let previewAudioStopTimer = null;
let audioPreviewEmptyTimer = null;

const {source: search, debounced} = useDebouncedRef(route.query.q ?? "", 350);
const languageStore = useLanguageStore();

const selectedLanguageId = computed(() => languageStore.activeLanguageId.value || null);
const mapFocus = computed(() => languageStore.mapFocus.value);
const iso3ToCountryName = new Map(
    worldCountries
        .filter((country) => country?.cca3)
        .map((country) => [String(country.cca3).toUpperCase(), country?.name?.common || String(country.cca3).toUpperCase()])
);
const catalogForegroundStyle = computed(() => ({
  transform: `translate(${panelOffsetX.value}px, ${panelOffsetY.value}px)`,
}));

function levelFilterKey(level) {
  const normalized = String(level ?? "").trim().toLowerCase();
  if (normalized.includes("dialect")) return "dialect";
  if (normalized.includes("family")) return "family";
  return "language";
}

function normalizeCatalogRows(rows) {
  return (Array.isArray(rows) ? rows : []).map((item) => ({
    ...item,
    levelKey: levelFilterKey(item?.level),
  }));
}

const filterOptions = computed(() => ([
  {
    key: "language",
    label: "Languages",
    count: languages.value.filter((item) => item?.levelKey === "language").length,
  },
  {
    key: "dialect",
    label: "Dialects",
    count: languages.value.filter((item) => item?.levelKey === "dialect").length,
  },
]));

const filteredByLevel = computed(() => {
  const activeKeys = Object.entries(visibleLevels.value)
      .filter(([, isVisible]) => isVisible)
      .map(([key]) => key);

  if (!activeKeys.length) return [];

  return languages.value.filter((item) => {
    const key = item?.levelKey || "language";
    if (key === "family") return false;
    return activeKeys.includes(key);
  });
});

const filteredLanguages = computed(() => {
  return filteredByLevel.value;
});
const canPrevPage = computed(() => page.value > 0);
const canNextPage = computed(() => page.value + 1 < totalPages.value);

const selectedCountryBubble = computed(() => {
  const iso = String(languageStore.activeCountryId.value || "").toUpperCase();
  if (!iso) return null;
  if (languageStore.focusMode.value !== "country") return null;

  return {
    isoA3: iso,
    name: iso3ToCountryName.get(iso) || iso,
    languages: languageStore.countryFocusLanguages.value || [],
  };
});

const bubbleFilteredLanguages = computed(() => {
  const rows = selectedCountryBubble.value?.languages ?? [];
  const query = bubbleSearch.value.trim().toLowerCase();
  if (!query) return rows;

  return rows.filter((language) => {
    const haystack = [
      language?.name,
      language?.level,
      language?.family,
      language?.parent,
    ]
        .map((value) => String(value ?? "").toLowerCase())
        .join(" ");

    return haystack.includes(query);
  });
});

const bubbleVisibleLanguages = computed(() => {
  return bubbleFilteredLanguages.value.slice(
      bubbleOffset.value,
      bubbleOffset.value + BUBBLE_PAGE_SIZE
  );
});

const bubbleCanPrev = computed(() => bubbleOffset.value > 0);
const bubbleCanNext = computed(() => (
    bubbleOffset.value + BUBBLE_PAGE_SIZE < bubbleFilteredLanguages.value.length
));

function bubblePrev() {
  if (!bubbleCanPrev.value) return;
  bubbleOffset.value = Math.max(0, bubbleOffset.value - BUBBLE_PAGE_SIZE);
}

function bubbleNext() {
  if (!bubbleCanNext.value) return;
  bubbleOffset.value += BUBBLE_PAGE_SIZE;
}

function clearAudioPreviewEmptyTimer() {
  if (audioPreviewEmptyTimer) {
    window.clearTimeout(audioPreviewEmptyTimer);
    audioPreviewEmptyTimer = null;
  }
}

function hideAudioPreviewUi() {
  clearAudioPreviewEmptyTimer();
  audioPreviewUi.value = null;
}

function showPlayingPreviewUi(languageName) {
  clearAudioPreviewEmptyTimer();
  audioPreviewUi.value = {
    mode: "playing",
    languageName: String(languageName || "this language").trim() || "this language",
  };
}

function showEmptyPreviewUi(languageName) {
  clearAudioPreviewEmptyTimer();
  audioPreviewUi.value = {
    mode: "empty",
    languageName: String(languageName || "").trim(),
  };
  audioPreviewEmptyTimer = window.setTimeout(() => {
    if (audioPreviewUi.value?.mode === "empty") {
      audioPreviewUi.value = null;
    }
    audioPreviewEmptyTimer = null;
  }, 2800);
}

function previewLanguageAudio(item) {
  const languageId = String(item?.id ?? "");
  if (!languageId) return;

  const languageName = String(item?.name ?? "").trim() || languageId;
  stopPreviewAudio();
  console.info("Language selected for preview audio", {languageId, languageName});

  fetchLanguagePreviewAudio(languageId)
      .then(async (preview) => {
        const audioUrl = buildApiUrl(preview.contentUrl);

        console.info("Preview scenario selected", {
          languageId,
          scenarioId: preview.scenarioId,
          scenarioTitle: preview.scenarioTitle,
        });
        console.info("Preview audio selected", {
          languageId,
          audioId: preview.id,
          thumbnailId: preview.thumbnailId,
          audioTitle: preview.title,
        });
        console.info("Preview audio URL", {languageId, audioUrl});

        const player = new Audio(audioUrl);
        previewAudioPlayer = player;

        player.addEventListener("ended", stopPreviewAudio, {once: true});

        await player.play();
        showPlayingPreviewUi(languageName);

        previewAudioStopTimer = window.setTimeout(() => {
          if (previewAudioPlayer !== player) return;
          player.pause();
          player.currentTime = 0;
          stopPreviewAudio();
        }, 10000);
      })
      .catch((e) => {
        console.info("No preview audio available", {
          languageId,
          message: e?.message || "",
        });
        if (previewAudioPlayer) {
          previewAudioPlayer.pause();
          previewAudioPlayer = null;
        }
        if (previewAudioStopTimer) {
          window.clearTimeout(previewAudioStopTimer);
          previewAudioStopTimer = null;
        }
        showEmptyPreviewUi(languageName);
      });
}

async function openLanguageInfo(item) {
  const id = String(item?.id ?? "");
  if (!id) return;

  languageInfoOpen.value = true;
  languageInfoLoading.value = true;
  languageInfoError.value = "";
  languageInfoData.value = null;

  try {
    languageInfoData.value = await fetchLanguage(id);
  } catch (e) {
    languageInfoError.value = e?.message || "Unable to load language details.";
  } finally {
    languageInfoLoading.value = false;
  }
}

function closeLanguageInfo() {
  languageInfoOpen.value = false;
}

function stopPreviewAudio() {
  if (previewAudioStopTimer) {
    window.clearTimeout(previewAudioStopTimer);
    previewAudioStopTimer = null;
  }

  if (previewAudioPlayer) {
    previewAudioPlayer.pause();
    previewAudioPlayer.currentTime = 0;
    previewAudioPlayer = null;
  }

  if (audioPreviewUi.value?.mode === "playing") {
    hideAudioPreviewUi();
  }
}

const languageInfoCountryNames = computed(() => {
  const raw = String(languageInfoData.value?.countryIds ?? "");
  if (!raw) return [];
  const tokens = languageStore.toIsoA3Tokens(raw);
  return tokens.map((isoA3) => iso3ToCountryName.get(isoA3) || isoA3);
});

async function load() {
  const hasVisibleRows = Array.isArray(languages.value) && languages.value.length > 0;
  loading.value = !hasVisibleRows;
  refreshing.value = hasVisibleRows;
  error.value = "";

  try {
    search.value = String(route.query.q ?? "");
    page.value = Number(route.query.page ?? 0);

    const params = new URLSearchParams({
      page: String(page.value),
      size: String(PAGE_SIZE),
    });
    if (search.value.trim()) {
      params.set("q", search.value.trim());
    }

    const data = await fetchLanguages(params);
    languages.value = normalizeCatalogRows(data.content ?? []);
    totalPages.value = Number(data.totalPages ?? 0);
    languageStore.setCatalogLanguages(languages.value);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

function updateRouteSearch(queryValue) {
  router.push({
    path: "/languages",
    query: {
      q: queryValue.trim() || undefined,
      page: 0,
    },
  });
}

function goToPage(nextPage) {
  const bounded = Math.max(0, nextPage);
  router.push({
    path: "/languages",
    query: {
      ...route.query,
      page: bounded,
    },
  });
}

function goPrevPage() {
  if (!canPrevPage.value) return;
  goToPage(page.value - 1);
}

function goNextPage() {
  if (!canNextPage.value) return;
  goToPage(page.value + 1);
}

function onSelectLanguage(item) {
  languageStore.activateLanguageFromList(item);
}

async function focusLanguageLocation(item) {
  const languageId = String(item?.id ?? "");
  if (!languageId) return;

  try {
    const language = await fetchLanguage(languageId);
    const latitude = Number(language?.latitude);
    const longitude = Number(language?.longitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      toast.info("No precise coordinates are available for this language yet.");
      return;
    }

    languageStore.activateLanguageFromList(item);
    languageStore.setActiveLanguagePin({
      id: languageId,
      name: String(language?.name ?? item?.name ?? ""),
      latitude,
      longitude,
      countryIds: String(language?.countryIds ?? item?.countryIds ?? ""),
    });
  } catch (e) {
    toast.info(e?.message || "Unable to locate this language on the map.");
  }
}

function toggleLevelVisibility(filterKey) {
  visibleLevels.value = {
    ...visibleLevels.value,
    [filterKey]: !visibleLevels.value[filterKey],
  };
  goToPage(0);
}

function toggleLanguagesMenu() {
  languagesMenuOpen.value = !languagesMenuOpen.value;
}

function toggleCountryBubble() {
  countryBubbleOpen.value = !countryBubbleOpen.value;
}

function clampPanelOffset(nextX, nextY) {
  const panelEl = catalogForegroundRef.value;
  const panelWidth = Number(panelEl?.offsetWidth ?? 420);
  const panelHeight = Number(panelEl?.offsetHeight ?? 380);
  const margin = 10;
  const baseTop = 88;
  const baseLeft = window.innerWidth - margin - panelWidth;

  const minLeft = margin;
  const maxLeft = Math.max(margin, window.innerWidth - panelWidth - margin);
  const left = Math.min(Math.max(baseLeft + nextX, minLeft), maxLeft);

  const minTop = margin;
  const maxTop = Math.max(margin, window.innerHeight - panelHeight - margin);
  const top = Math.min(Math.max(baseTop + nextY, minTop), maxTop);

  return {
    x: left - baseLeft,
    y: top - baseTop,
  };
}

function onPanelDragMove(event) {
  if (!panelDrag.value.active) return;

  const dx = event.clientX - panelDrag.value.startClientX;
  const dy = event.clientY - panelDrag.value.startClientY;
  const clamped = clampPanelOffset(
      panelDrag.value.startOffsetX + dx,
      panelDrag.value.startOffsetY + dy
  );

  panelDrag.value.pendingX = clamped.x;
  panelDrag.value.pendingY = clamped.y;

  if (panelDrag.value.rafId) return;
  panelDrag.value.rafId = requestAnimationFrame(() => {
    panelDrag.value.rafId = 0;
    panelOffsetX.value = panelDrag.value.pendingX;
    panelOffsetY.value = panelDrag.value.pendingY;
  });
}

function stopPanelDrag() {
  panelDrag.value.active = false;
  if (panelDrag.value.rafId) {
    cancelAnimationFrame(panelDrag.value.rafId);
    panelDrag.value.rafId = 0;
  }
  window.removeEventListener("pointermove", onPanelDragMove);
  window.removeEventListener("pointerup", stopPanelDrag);
  window.removeEventListener("pointercancel", stopPanelDrag);
}

function startPanelDrag(event) {
  if (window.innerWidth <= 920) return;
  panelDrag.value.active = true;
  panelDrag.value.startClientX = event.clientX;
  panelDrag.value.startClientY = event.clientY;
  panelDrag.value.startOffsetX = panelOffsetX.value;
  panelDrag.value.startOffsetY = panelOffsetY.value;

  window.addEventListener("pointermove", onPanelDragMove);
  window.addEventListener("pointerup", stopPanelDrag);
  window.addEventListener("pointercancel", stopPanelDrag);
}

watch(debounced, (value) => {
  if ((route.query.q ?? "") !== value) {
    updateRouteSearch(value);
  }
});

watch(
    () => selectedCountryBubble.value?.isoA3,
    (isoA3, previousIsoA3) => {
      if (!isoA3 || isoA3 === previousIsoA3) return;
      bubbleSearch.value = "";
      bubbleOffset.value = 0;
      countryBubbleOpen.value = true;
    }
);

watch(bubbleSearch, () => {
  bubbleOffset.value = 0;
});

watch(bubbleFilteredLanguages, (rows) => {
  if (bubbleOffset.value < rows.length) return;
  bubbleOffset.value = Math.max(0, rows.length - BUBBLE_PAGE_SIZE);
});

watch(
    () => route.query.page,
    (next) => {
      const parsed = Number(next ?? 0);
      page.value = Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
    },
    {immediate: true}
);

watch(
    [() => route.query.q, () => route.query.page],
    load
);
onMounted(() => {
  document.body.classList.add("languages-fullscreen");
  load();
  languageStore.loadMapData();
});

onBeforeUnmount(() => {
  stopPreviewAudio();
  hideAudioPreviewUi();
  stopPanelDrag();
  document.body.classList.remove("languages-fullscreen");
});
</script>

<template>
  <main class="page">
    <section class="section catalog-immersive">
      <div class="map-background">
        <WorldMap/>
      </div>

      

      <div v-if="selectedCountryBubble" class="country-bubble-launch">
        <button
            type="button"
            class="country-bubble-toggle"
            :aria-expanded="countryBubbleOpen ? 'true' : 'false'"
            aria-controls="country-bubble-content"
            @click="toggleCountryBubble"
        >
          <span class="icon" aria-hidden="true">◎</span>
          <span class="country-bubble-toggle__label">{{ selectedCountryBubble.name }}</span>
          <span class="toggle-chevron" :class="{ open: countryBubbleOpen }">⌄</span>
        </button>
      </div>

      <aside
          v-if="selectedCountryBubble && countryBubbleOpen"
          id="country-bubble-content"
          class="country-bubble"
          aria-live="polite"
      >
        <header class="bubble-header">
          <div>
            <h2>{{ selectedCountryBubble.name }}</h2>
          </div>
          <span class="bubble-iso">{{ selectedCountryBubble.isoA3 }}</span>
        </header>

        <div v-if="selectedCountryBubble.languages.length" class="bubble-languages">
          <div class="bubble-toolbar">
            <p class="bubble-meta">{{ bubbleFilteredLanguages.length }} language(s)</p>
            <div class="bubble-nav">
              <button
                  type="button"
                  class="bubble-nav-btn"
                  :disabled="!bubbleCanPrev"
                  @click="bubblePrev"
                  aria-label="Previous languages"
              >
                ←
              </button>
              <button
                  type="button"
                  class="bubble-nav-btn"
                  :disabled="!bubbleCanNext"
                  @click="bubbleNext"
                  aria-label="Next languages"
              >
                →
              </button>
            </div>
          </div>

          <input
              v-model="bubbleSearch"
              type="text"
              class="bubble-search"
              placeholder="Search in this country..."
          />

          <ul class="bubble-list">
            <li
                v-for="language in bubbleVisibleLanguages"
                :key="`${selectedCountryBubble.isoA3}-${language.id}`"
            >
              <div class="bubble-item-main">
                <span class="name">{{ language.name }}</span>
              </div>

              <div class="bubble-item-actions">
                <button
                    type="button"
                    class="bubble-action"
                    aria-label="Open language details preview"
                    @click.stop="openLanguageInfo(language)"
                >
                  <Info :size="15"/>
                </button>

                <button
                    type="button"
                    class="bubble-action bubble-action--muted"
                    aria-label="Play language preview audio"
                    @click.stop="previewLanguageAudio(language)"
                >
                  <Volume2 :size="15"/>
                </button>
              </div>
            </li>
          </ul>

          <p v-if="!bubbleVisibleLanguages.length" class="bubble-empty">
            No language matches your search.
          </p>
        </div>
        <p v-else class="bubble-empty">No language data available for this country yet.</p>
      </aside>

      <div
          ref="catalogForegroundRef"
          class="catalog-foreground"
          :style="catalogForegroundStyle"
      >
        <div class="languages-launch">
          <button
              type="button"
              class="panel-drag-handle"
              title="Drag languages panel"
              @pointerdown.prevent="startPanelDrag"
          >
            ⠿
          </button>

          <button
              type="button"
              class="languages-icon-toggle"
              :aria-expanded="languagesMenuOpen ? 'true' : 'false'"
              aria-controls="languages-menu-content"
              @click="toggleLanguagesMenu"
          >
            <span class="icon" aria-hidden="true">☰</span>
            <span>Languages</span>
            <span class="toggle-chevron" :class="{ open: languagesMenuOpen }">⌄</span>
          </button>
        </div>

        <section
            id="languages-menu-content"
            class="languages-panel"
            v-show="languagesMenuOpen"
        >
          <div class="languages-main">
            <div class="search-panel">
              <div class="toolbar search-toolbar">
                <input
                    v-model="search"
                    type="text"
                    placeholder="Search languages"
                />
              </div>
            </div>

            <BaseLoader v-if="loading">Loading languages...</BaseLoader>

            <BaseAlert v-else-if="error" type="error">
              {{ error }}
            </BaseAlert>

            <template v-else>
              <div class="filter-bar">
                <p class="filter-title">View by category</p>
                <div class="filter-pills">
                  <button
                      v-for="filter in filterOptions"
                      :key="filter.key"
                      type="button"
                      class="filter-pill"
                      :class="{ active: visibleLevels[filter.key] }"
                      @click="toggleLevelVisibility(filter.key)"
                  >
                    <span>{{ filter.label }}</span>
                  </button>
                </div>
              </div>

              <div class="results-meta">
                <span class="results-count">{{ filteredLanguages.length }} result(s)</span>

                <div class="catalog-pager-inline" aria-label="Language pagination">
                  <button
                      type="button"
                      class="catalog-pager-btn"
                      :disabled="!canPrevPage"
                      @click="goPrevPage"
                      aria-label="Previous page"
                  >
                    <ChevronLeft :size="18"/>
                  </button>

                  <span class="catalog-pager-label">
                    {{ totalPages > 0 ? `Page ${page + 1} of ${totalPages}` : "No pages" }}
                  </span>

                  <button
                      type="button"
                      class="catalog-pager-btn"
                      :disabled="!canNextPage"
                      @click="goNextPage"
                      aria-label="Next page"
                  >
                    <ChevronRight :size="18"/>
                  </button>
                </div>
              </div>

              <div class="list-card">
                <LanguageCatalogList
                    :items="filteredLanguages"
                    :selected-language-id="selectedLanguageId"
                    @select-language="onSelectLanguage"
                    @open-info="openLanguageInfo"
                    @focus-location="focusLanguageLocation"
                    @preview-audio="previewLanguageAudio"
                />
              </div>

            </template>
          </div>
        </section>
      </div>

      <div
          v-if="audioPreviewUi"
          class="audio-preview-overlay"
          :class="`audio-preview-overlay--${audioPreviewUi.mode}`"
          role="status"
          aria-live="polite"
      >
        <div class="audio-preview-stack">
          <div class="audio-preview-visual" aria-hidden="true">
            <Volume2 :size="36" stroke-width="1.7" class="audio-preview-icon"/>
            <span v-if="audioPreviewUi.mode === 'playing'" class="audio-eq">
              <i/><i/><i/><i/>
            </span>
          </div>
          <p v-if="audioPreviewUi.mode === 'playing'" class="audio-preview-copy">
            playing {{ audioPreviewUi.languageName }}...
          </p>
          <p v-else class="audio-preview-copy">
            no scenario for this language for now
          </p>
        </div>
      </div>

      <div
          v-if="languageInfoOpen"
          class="language-info-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="Language information"
          @click.self="closeLanguageInfo"
      >
        <article class="language-info-modal">
          <header class="language-info-head">
            <div>
              <p class="language-info-kicker">Language info</p>
              <h3>{{ languageInfoData?.name || "Language" }}</h3>
            </div>
            <button
                type="button"
                class="language-info-close"
                aria-label="Close language information"
                @click="closeLanguageInfo"
            >
              ×
            </button>
          </header>

          <BaseLoader v-if="languageInfoLoading">Loading language details...</BaseLoader>
          <BaseAlert v-else-if="languageInfoError" type="error">{{ languageInfoError }}</BaseAlert>

          <template v-else-if="languageInfoData">
            <div class="language-info-grid">
            <p><span>Longitude</span><strong>{{ languageInfoData.longitude || "Not specified" }}</strong></p>
            <p><span>Latitude</span><strong>{{ languageInfoData.latitude || "Not specified" }}</strong></p>
              <p><span>Level</span><strong>{{ languageInfoData.level || "Not specified" }}</strong></p>
              <p><span>Family</span><strong>{{ languageInfoData.familyName || "Not specified" }}</strong></p>
              <p><span>Parent</span><strong>{{ languageInfoData.parentName || "Not specified" }}</strong></p>
            </div>

            <div class="language-info-countries">
              <p class="label">Countries</p>
              <p v-if="languageInfoCountryNames.length" class="value">
                {{ languageInfoCountryNames.join(" • ") }}
              </p>
              <p v-else class="value">No country data available.</p>
            </div>

            <p class="language-info-description">
              {{
                languageInfoData.description
                || languageInfoData.markupDescription
                || "No detailed description is available yet."
              }}
            </p>

            <RouterLink
                v-if="languageInfoData.id"
                :to="`/languages/${languageInfoData.id}`"
                class="language-info-link"
                @click="closeLanguageInfo"
            >
              Open full info page
            </RouterLink>
          </template>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
:global(body.languages-fullscreen) {
  overflow: hidden;
}

:global(body.languages-fullscreen .app-shell) {
  height: 100dvh;
  overflow: hidden;
}

:global(body.languages-fullscreen .app-shell__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.page {
  width: 100%;
  max-width: none;
  height: 100%;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: #c56a3a;
}

.section {
  position: relative;
  isolation: isolate;
  width: 100%;
  height: 100%;
  max-width: none;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.catalog-immersive {
  --sand-50: #f8f2e8;
  --sand-100: #efe2d2;
  --sand-200: #e1cfb8;
  --sand-300: #cfb698;
  --clay-600: #9d5f2f;
  --clay-700: #834c23;
  --ember-500: #c06a2f;
  --ember-600: #ab5921;
  --ink-700: #4f3123;
  --ink-800: #3c2318;
  position: relative;
  height: 100%;
  min-height: 0;
  width: 100%;
  margin-left: 0;
  transform: none;
  border-radius: 0;
  overflow: hidden;
  border: none;
  box-shadow: none;
}

.map-background {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: auto;
  background: #c56a3a;
}

.catalog-foreground {
  position: absolute;
  z-index: 2;
  top: 5.4rem;
  right: 1rem;
  bottom: auto;
  padding: 0;
  display: grid;
  align-content: start;
  justify-items: end;
  grid-auto-rows: max-content;
  gap: 0.95rem;
  width: min(420px, calc(100vw - 2rem));
  margin-left: 0;
  pointer-events: none;
  overflow: visible;
  max-height: calc(100dvh - 6.2rem);
}

.catalog-foreground > * {
  pointer-events: auto;
  width: 100%;
}

.country-bubble {
  position: absolute;
  z-index: 3;
  bottom: 1rem;
  width: min(360px, calc(100% - 2rem));
  min-height: 332px;
  max-height: 332px;
  padding: 0.92rem;
  border-radius: 20px;
  pointer-events: auto;
  border: 1px solid color-mix(in srgb, var(--border) 74%, #ffffff 26%);
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(255, 240, 238, 0.92));
  box-shadow:
      0 22px 40px rgba(30, 8, 18, 0.16),
      0 2px 8px rgba(30, 8, 18, 0.08);
  transition: left 220ms ease, right 220ms ease;
  left: 1rem;
  right: auto;
  box-sizing: border-box;
  backdrop-filter: blur(12px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.country-bubble::before {
  content: "";
  position: absolute;
  inset: 0 0 auto 0;
  height: 4px;
  background: linear-gradient(90deg, #5B1928 0%, #485B38 100%);
  pointer-events: none;
}

.country-bubble * {
  box-sizing: border-box;
}

.country-bubble-launch {
  position: absolute;
  z-index: 4;
  top: 6.85rem;
  bottom: auto;
  pointer-events: auto;
  transition: left 220ms ease, right 220ms ease;
  left: 1rem;
  right: auto;
}

.country-bubble-toggle {
  border: 1px solid rgba(212, 168, 174, 0.58);
  border-radius: 999px;
  background: rgba(245, 224, 227, 0.94);
  color: #5B1928;
  display: inline-flex;
  align-items: center;
  gap: 0.52rem;
  padding: 0.45rem 0.78rem;
  font-size: 0.84rem;
  font-weight: 700;
  letter-spacing: 0;
  box-shadow: 0 10px 22px rgba(155, 110, 118, 0.14);
  transition: transform 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.country-bubble-toggle:hover {
  transform: translateY(-1px);
  background: rgba(240, 212, 216, 0.96);
  box-shadow: 0 14px 26px rgba(155, 110, 118, 0.18);
}

.country-bubble-toggle .icon {
  width: 1.55rem;
  height: 1.55rem;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.55);
  color: #7f3042;
}

.country-bubble-toggle__label {
  white-space: nowrap;
}

.bubble-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.7rem;
  padding-bottom: 0.72rem;
  border-bottom: 1px solid rgba(229, 208, 204, 0.78);
  flex-shrink: 0;
}

.bubble-header h2 {
  margin: 0;
  font-size: 1.08rem;
  color: #1E0812;
}

.bubble-iso {
  border-radius: 999px;
  padding: 0.3rem 0.58rem;
  font-size: 0.72rem;
  font-weight: 700;
  color: #485B38;
  background: rgba(212, 229, 202, 0.82);
  border: 1px solid rgba(72, 91, 56, 0.14);
}

.bubble-meta {
  margin: 0;
  font-size: 0.79rem;
  color: #785068;
}

.bubble-toolbar {
  margin-top: 0.78rem;
  margin-bottom: 0.6rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  flex-shrink: 0;
}

.bubble-nav {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.bubble-nav-btn {
  border: 1px solid rgba(72, 91, 56, 0.18);
  background: rgba(212, 229, 202, 0.64);
  color: #485B38;
  border-radius: 10px;
  width: 32px;
  height: 28px;
  font-weight: 700;
  line-height: 1;
  transition: transform 160ms ease, background 160ms ease, border-color 160ms ease, color 160ms ease;
}

.bubble-nav-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  background: #485B38;
  border-color: #485B38;
  color: #fff;
}

.bubble-nav-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.bubble-search {
  width: 100%;
  border: 1px solid rgba(229, 208, 204, 0.96);
  border-radius: 14px;
  padding: 0.62rem 0.78rem;
  font-size: 0.8rem;
  color: #1E0812;
  background: rgba(255, 255, 255, 0.88);
  margin-bottom: 0.6rem;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
  flex-shrink: 0;
}

.bubble-search:focus {
  outline: none;
  border-color: #5B1928;
  box-shadow: 0 0 0 4px rgba(91, 25, 40, 0.12);
}

.bubble-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.35rem;
  width: 100%;
  overflow: auto;
  align-content: start;
  min-height: 0;
  padding-right: 0.12rem;
}

.bubble-languages {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

.bubble-list li {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  align-items: center;
  border-radius: 14px;
  padding: 0.56rem 0.62rem;
  background: rgba(255, 240, 238, 0.78);
  border: 1px solid rgba(229, 208, 204, 0.94);
  width: 100%;
  min-width: 0;
  overflow: hidden;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}

.bubble-list li:hover {
  transform: translateY(-1px);
  border-color: rgba(91, 25, 40, 0.18);
  box-shadow: 0 10px 18px rgba(30, 8, 18, 0.08);
  background: rgba(255, 247, 241, 0.92);
}

.bubble-item-main {
  display: block;
  align-items: center;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}

.bubble-list .name {
  display: block;
  font-weight: 700;
  color: #1E0812;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  min-width: 0;
}

.bubble-item-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  flex-shrink: 0;
}

.bubble-action {
  width: 29px;
  height: 29px;
  border-radius: 999px;
  border: 1px solid rgba(229, 208, 204, 0.96);
  background: rgba(255, 255, 255, 0.84);
  color: #5B1928;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  transition: transform 160ms ease, border-color 160ms ease, background 160ms ease, color 160ms ease, box-shadow 160ms ease;
}

.bubble-action:hover {
  transform: translateY(-1px);
  border-color: #5B1928;
  background: #5B1928;
  color: #fff7f1;
  box-shadow: 0 8px 14px rgba(91, 25, 40, 0.16);
}

.bubble-action:focus-visible {
  outline: 2px solid rgba(91, 25, 40, 0.16);
  outline-offset: 1px;
}

.bubble-action--muted {
  color: #485B38;
}

.bubble-empty {
  margin: 0.6rem 0 0;
  font-size: 0.82rem;
  color: #785068;
}

.language-info-overlay {
  position: absolute;
  inset: 0;
  z-index: 7;
  background: rgba(15, 10, 8, 0.32);
  backdrop-filter: blur(3px);
  display: grid;
  place-items: center;
  padding: 1rem;
  pointer-events: auto;
}

.audio-preview-overlay {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: grid;
  place-items: center;
  padding: 1rem;
  pointer-events: none;
}

.audio-preview-stack {
  display: grid;
  justify-items: center;
  gap: 0.8rem;
  animation: audio-preview-in 320ms ease;
}

.audio-preview-visual {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: rgba(255, 247, 241, 0.92);
  filter: drop-shadow(0 3px 14px rgba(40, 16, 10, 0.4));
}

.audio-preview-icon {
  opacity: 0.9;
}

.audio-preview-overlay--playing .audio-preview-icon {
  animation: audio-icon-breathe 2.4s ease-in-out infinite;
}

.audio-eq {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 28px;
}

.audio-eq i {
  display: block;
  width: 3.5px;
  height: 100%;
  border-radius: 999px;
  background: rgba(255, 247, 241, 0.9);
  animation: audio-eq-bar 1.35s ease-in-out infinite;
  transform-origin: bottom center;
}

.audio-eq i:nth-child(1) { animation-delay: 0ms; }
.audio-eq i:nth-child(2) { animation-delay: 160ms; }
.audio-eq i:nth-child(3) { animation-delay: 80ms; }
.audio-eq i:nth-child(4) { animation-delay: 220ms; }

.audio-preview-copy {
  margin: 0;
  text-align: center;
  max-width: min(380px, calc(100vw - 2.5rem));
  font-size: 1.2rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: rgba(255, 248, 242, 0.94);
  text-shadow: 0 2px 16px rgba(40, 16, 10, 0.5);
}

.audio-preview-overlay--empty .audio-preview-visual,
.audio-preview-overlay--empty .audio-preview-copy {
  opacity: 0.78;
}

@keyframes audio-preview-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes audio-icon-breathe {
  0%, 100% { opacity: 0.82; }
  50% { opacity: 1; }
}

@keyframes audio-eq-bar {
  0%, 100% { transform: scaleY(0.35); opacity: 0.55; }
  50% { transform: scaleY(1); opacity: 0.95; }
}

.language-info-modal {
  width: min(470px, calc(100vw - 2rem));
  border-radius: 16px;
  border: 1px solid color-mix(in srgb, var(--sand-300) 64%, #ffffff 36%);
  background: color-mix(in srgb, var(--sand-50) 88%, #ffffff 12%);
  box-shadow: 0 22px 44px rgba(28, 18, 12, 0.24);
  padding: 0.9rem;
}

.language-info-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.7rem;
}

.language-info-kicker {
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  font-size: 0.66rem;
  color: var(--clay-700);
  font-weight: 700;
}

.language-info-head h3 {
  margin: 0.18rem 0 0;
  color: var(--ink-800);
  font-size: 1.08rem;
}

.language-info-close {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--sand-300) 66%, #ffffff 34%);
  background: color-mix(in srgb, var(--sand-100) 82%, #ffffff 18%);
  color: var(--ink-800);
  font-size: 1.05rem;
  line-height: 1;
}

.language-info-grid {
  margin-top: 0.8rem;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.45rem;
}

.language-info-grid p {
  margin: 0;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--sand-300) 62%, #ffffff 38%);
  background: color-mix(in srgb, var(--sand-50) 84%, #ffffff 16%);
  padding: 0.42rem 0.5rem;
  display: grid;
  gap: 0.18rem;
}

.language-info-grid span {
  font-size: 0.68rem;
  color: var(--ink-700);
}

.language-info-grid strong {
  font-size: 0.78rem;
  color: var(--ink-800);
}

.language-info-countries {
  margin-top: 0.65rem;
}

.language-info-countries .label {
  margin: 0;
  font-size: 0.68rem;
  color: var(--ink-700);
}

.language-info-countries .value {
  margin: 0.16rem 0 0;
  font-size: 0.78rem;
  color: var(--ink-800);
}

.language-info-description {
  margin: 0.65rem 0 0;
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--ink-700);
  max-height: 7.2rem;
  overflow: auto;
}

.language-info-link {
  margin-top: 0.72rem;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--ember-500) 46%, #ffffff 54%);
  background: color-mix(in srgb, var(--sand-100) 80%, #ffffff 20%);
  color: var(--ink-800);
  font-weight: 700;
  text-decoration: none;
  font-size: 0.78rem;
  padding: 0.36rem 0.7rem;
}

.map-focus-panel {
  position: absolute;
  z-index: 4;
  top: 1rem;
  left: 1rem;
  width: min(360px, calc(100vw - 2rem));
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(18, 18, 24, 0.56);
  backdrop-filter: blur(10px);
  border-radius: 14px;
  padding: 0.68rem 0.76rem;
  color: #e8dfd3;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.24);
}

.map-focus-kicker {
  margin: 0;
  font-size: 0.66rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #c4b39f;
  font-weight: 700;
}

.map-focus-title {
  margin: 0.22rem 0 0;
  font-size: 0.94rem;
  font-weight: 700;
}

.map-focus-subtitle {
  margin: 0.12rem 0 0;
  font-size: 0.76rem;
  color: #d5c5b0;
}

.map-focus-actions {
  margin-top: 0.5rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.scenario-btn {
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: linear-gradient(135deg, rgba(168, 103, 62, 0.9), rgba(192, 106, 47, 0.86));
  color: #fff7f0;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.24rem 0.52rem;
}

.search-panel {
  position: relative;
  border-radius: 999px;
  padding: 0;
  overflow: visible;
}

.search-panel :deep(*) {
  position: relative;
  z-index: 1;
}

.search-toolbar {
  pointer-events: auto;
  display: flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid #e0c9b0;
  background: var(--surface);
  backdrop-filter: blur(10px);
  padding: 0.42rem 0.78rem;
  box-shadow: 0 8px 22px rgba(96, 56, 24, 0.08);
}

.search-panel :deep(input) {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text);
  box-shadow: none;
  padding: 0;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-panel :deep(input::placeholder) {
  color: var(--text-soft);
}

.search-toolbar:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(192, 74, 8, 0.16);
}

.languages-main {
  display: grid;
  gap: 0.9rem;
  min-height: 0;
}

.languages-launch {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.catalog-pager-inline {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border: none;
  background: transparent;
  box-shadow: none;
  padding: 0;
}

.catalog-pager-btn {
  width: 34px;
  height: 34px;
  border: 1px solid color-mix(in srgb, var(--sand-300) 72%, #ffffff 28%);
  border-radius: 999px;
  background: color-mix(in srgb, var(--sand-50) 86%, #ffffff 14%);
  color: var(--ink-800);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  transition:
    transform 160ms ease,
    border-color 160ms ease,
    background 160ms ease;
}

.catalog-pager-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--ember-500) 60%, #ffffff 40%);
  background: color-mix(in srgb, var(--sand-100) 84%, #ffffff 16%);
}

.catalog-pager-btn:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.catalog-pager-label {
  min-width: 128px;
  text-align: center;
  font-size: 0.86rem;
  font-weight: 800;
  color: var(--ink-800);
}

.languages-panel {
  border-radius: 20px;
  overflow: auto;
  border: 1px solid color-mix(in srgb, var(--border) 74%, #ffffff 26%);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(255, 240, 238, 0.92));
  box-shadow:
      0 22px 40px rgba(30, 8, 18, 0.16),
      0 2px 8px rgba(30, 8, 18, 0.08);
  max-height: calc(100dvh - 10rem);
  backdrop-filter: blur(12px);
}

.languages-icon-toggle {
  border: 1px solid rgba(91, 25, 40, 0.18);
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(91, 25, 40, 0.96), rgba(127, 48, 66, 0.92));
  color: #fff7f1;
  display: inline-flex;
  align-items: center;
  gap: 0.52rem;
  padding: 0.45rem 0.78rem;
  font-size: 0.84rem;
  font-weight: 700;
  box-shadow: 0 14px 28px rgba(91, 25, 40, 0.18);
}

.panel-drag-handle {
  border: 1px solid rgba(212, 168, 174, 0.58);
  border-radius: 10px;
  background: rgba(245, 224, 227, 0.94);
  color: #5B1928;
  width: 42px;
  min-width: 42px;
  height: 36px;
  padding: 0;
  font-size: 1rem;
  font-weight: 700;
  cursor: grab;
  user-select: none;
  touch-action: none;
  box-shadow: 0 10px 22px rgba(155, 110, 118, 0.14);
  transition: transform 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.panel-drag-handle:hover {
  background: rgba(240, 212, 216, 0.96);
  box-shadow: 0 14px 26px rgba(155, 110, 118, 0.18);
}

.panel-drag-handle:active {
  cursor: grabbing;
  background: rgba(235, 200, 206, 0.96);
}

.languages-icon-toggle .icon {
  font-size: 0.92rem;
}

.toggle-chevron {
  display: inline-block;
  transition: transform 180ms ease;
}

.toggle-chevron.open {
  transform: rotate(180deg);
}

.filter-bar {
  padding: 0.7rem 0.8rem;
}

.filter-title {
  margin: 0 0 0.75rem;
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-700);
}

.filter-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
}

.filter-pill {
  border: 1px solid rgba(229, 208, 204, 0.96);
  background: rgba(255, 255, 255, 0.82);
  color: #1E0812;
  border-radius: 999px;
  padding: 0.36rem 0.62rem;
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  font-weight: 600;
  font-size: 0.74rem;
  transition: transform 160ms ease, background 160ms ease, box-shadow 160ms ease, color 160ms ease;
}

.filter-pill:hover {
  transform: translateY(-1px);
  background: rgba(255, 247, 241, 0.94);
  box-shadow: 0 8px 18px rgba(30, 8, 18, 0.12);
}

.filter-pill.active {
  border-color: rgba(91, 25, 40, 0.18);
  background: linear-gradient(135deg, rgba(91, 25, 40, 0.96), rgba(127, 48, 66, 0.92));
  color: white;
  box-shadow: 0 10px 20px rgba(91, 25, 40, 0.22);
}

.filter-pill .count {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  padding: 0.1rem 0.36rem;
  font-size: 0.66rem;
  font-weight: 700;
}

.filter-pill:not(.active) .count {
  background: color-mix(in srgb, var(--sand-300) 30%, #ffffff 70%);
  color: var(--ink-700);
}

.results-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.9rem;
  padding: 0.7rem 0.9rem;
  font-size: 0.9rem;
  color: #785068;
  border: 1px solid rgba(229, 208, 204, 0.96);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 240, 238, 0.88));
}

.results-count {
  font-weight: 700;
  color: #1E0812;
}

.list-card {
  border-radius: 18px;
  overflow: hidden;
  max-height: 40vh;
  overflow-y: auto;
  border: 1px solid rgba(229, 208, 204, 0.96);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 240, 238, 0.88));
}

@media (max-width: 920px) {
  .catalog-immersive {
    width: 100%;
    margin-left: 0;
    transform: none;
    border-radius: 0;
  }

  .catalog-foreground {
    width: min(420px, calc(100vw - 1.8rem));
    margin-left: 0;
    top: 5.1rem;
    right: 0.9rem;
    justify-items: end;
    transform: none !important;
    max-height: calc(100dvh - 6rem);
  }

  .country-bubble {
    bottom: 0.9rem;
    width: auto;
    top: auto;
    left: 0.9rem;
    right: auto;
  }

  .country-bubble-launch {
    left: 0.9rem;
    right: auto;
    top: 6.35rem;
    bottom: auto;
  }

}

@media (max-width: 600px) {
  .catalog-immersive {
    border-radius: 0;
  }

  .country-bubble {
    position: relative;
    left: auto;
    top: auto;
    right: auto;
    margin: 0.75rem 0.75rem 0;
    width: auto;
  }

  .country-bubble-launch {
    position: relative;
    left: auto;
    right: auto;
    bottom: auto;
    top: auto;
    margin: 0.75rem 0.75rem 0;
  }

  .map-focus-panel {
    left: 0.75rem;
    top: 3.4rem;
    width: min(340px, calc(100vw - 1.5rem));
  }

  .search-panel,
  .list-card,
  .filter-bar,
  .results-meta {
    border-radius: 14px;
  }

  .panel-drag-handle {
    display: none;
  }

  .filter-pills {
    gap: 0.45rem;
  }

  .filter-pill {
    font-size: 0.72rem;
    padding: 0.34rem 0.54rem;
  }

  .results-meta {
    align-items: stretch;
    flex-direction: column;
  }

  .catalog-pager-inline {
    justify-content: space-between;
    width: 100%;
  }

  .catalog-pager-label {
    flex: 1;
  }

}
</style>
