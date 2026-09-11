<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from "vue";
import { fetchScenarioBackgroundAudios, fetchScenarioThumbnails, fetchThumbnailAudios } from "../../api/scenarios";
import { buildApiUrl } from "../../api/rest";
import { buildStoryboardItems, storyboardItemStyle } from "../../utils/scenarioStoryboard.js";

const props = defineProps({
  scenario: { type: Object, default: null },
});

const emit = defineEmits(["close"]);

const loading = ref(false);
const error = ref("");
const thumbnails = ref([]);
const audioMap = ref({});
const backgroundAudios = ref([]);
const currentIndex = ref(0);
const viewMode = ref("grid");
const isAutoplay = ref(false);
const isFullscreen = ref(false);
const audioRef = ref(null);
const backgroundAudioRef = ref(null);
const containerRef = ref(null);
const autoplayTimer = ref(null);
const audioDuration = ref(0);
const audioCurrentTime = ref(0);
const audioPlaying = ref(false);
const ambienceEnabled = ref(true);
const ambiencePlaying = ref(false);
const sceneAudioIndex = ref(0);
const glossaryOpen = ref(true);
const ambienceVolume = ref(22);

const glossaryEntries = computed(() => thumbnails.value.flatMap((thumb) =>
  orderedAudiosForThumb(thumb).map((audio, index) => ({thumb, audio, index}))
).filter(({audio}) => [audio.transcription, audio.gloss, audio.freeTranslation].some((text) => text?.trim())));

const visibleGlossaryEntries = computed(() => viewMode.value === "scene"
  ? glossaryEntries.value.filter(({audio}) => audio.id === currentAudio.value?.id)
  : glossaryEntries.value);

function openGlossaryEntry(entry) {
  stopAutoplay();
  enterSceneById(entry.thumb.id);
  sceneAudioIndex.value = entry.index;
}

function selectTake(event) {
  stopAutoplay();
  sceneAudioIndex.value = Number(event.target.value);
}

watch(ambienceVolume, (volume) => {
  if (backgroundAudioRef.value) backgroundAudioRef.value.volume = Number(volume) / 100;
});

const activeBackgroundAudio = computed(() => {
  return backgroundAudios.value.find((audio) => audio.active) ?? backgroundAudios.value[0] ?? null;
});

watch(activeBackgroundAudio, (audio) => {
  ambienceVolume.value = audio?.volume ?? 22;
});

const backgroundAudioUrl = computed(() => {
  const audio = activeBackgroundAudio.value;
  if (!audio) return null;
  return buildApiUrl(audio.contentUrl || `/api/audios/${audio.id}/content`);
});

function playAmbience() {
  const el = backgroundAudioRef.value;
  if (!el || !backgroundAudioUrl.value || !ambienceEnabled.value) return;
  if (el.ended && !el.loop) return;
  el.volume = Number(ambienceVolume.value) / 100;
  el.play().catch(() => {
    ambiencePlaying.value = false;
  });
}

function pauseAmbience(reset = false) {
  const el = backgroundAudioRef.value;
  if (!el) return;
  el.pause();
  if (reset) el.currentTime = 0;
  ambiencePlaying.value = false;
}

function toggleAmbience() {
  ambienceEnabled.value = !ambienceEnabled.value;
  if (!ambienceEnabled.value) {
    pauseAmbience();
    return;
  }
  if (audioPlaying.value || isAutoplay.value || isGridSequencePlaying.value || gridPlayingId.value) {
    playAmbience();
  }
}

const currentThumb = computed(() => thumbnails.value[currentIndex.value] ?? null);

const currentAudios = computed(() => {
  if (!currentThumb.value) return [];
  return audioMap.value[currentThumb.value.id] ?? [];
});

const currentAudio = computed(() => currentAudios.value[sceneAudioIndex.value] ?? null);

const currentAudioUrl = computed(() => {
  if (!currentAudio.value) return null;
  return buildApiUrl(`/api/audios/${currentAudio.value.id}/content`);
});

const thumbImageUrl = computed(() => {
  if (!currentThumb.value) return null;
  return buildApiUrl(`/api/thumbnails/${currentThumb.value.id}/content`);
});

function gridThumbUrl(thumb) {
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

function thumbHasAudio(thumb) {
  return (audioMap.value[thumb.id] ?? []).length > 0;
}

function orderedAudiosForThumb(thumb) {
  return [...(audioMap.value[thumb.id] ?? [])]
    .sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
}

function audioUrl(audio) {
  return audio ? buildApiUrl(`/api/audios/${audio.id}/content`) : null;
}

function queueEntriesForThumb(thumb) {
  return orderedAudiosForThumb(thumb).map((audio) => ({thumb, audio}));
}

const gridPlayingId = ref(null);
const isGridSequencePlaying = ref(false);
const gridSequenceList = ref([]);
const gridSequenceIndex = ref(-1);

function stopGridSequence() {
  isGridSequencePlaying.value = false;
  gridSequenceList.value = [];
  gridSequenceIndex.value = -1;
  pauseAmbience(true);
}

function toggleGridAudio(thumb) {
  const el = audioRef.value;
  const entries = queueEntriesForThumb(thumb);
  if (!el || !entries.length) return;

  if (gridPlayingId.value === String(thumb.id)) {
    stopGridSequence();
    el.pause();
    return;
  }

  stopGridSequence();
  el.pause();
  isGridSequencePlaying.value = true;
  gridSequenceList.value = entries;
  gridSequenceIndex.value = -1;
  advanceGridSequence();
}

function playFromStart() {
  const entries = thumbnails.value.flatMap(queueEntriesForThumb);
  if (!entries.length) { enterScene(0, true); return; }
  isGridSequencePlaying.value = true;
  gridSequenceList.value = entries;
  gridSequenceIndex.value = -1;
  advanceGridSequence();
}

function advanceGridSequence() {
  const nextIndex = gridSequenceIndex.value + 1;
  if (nextIndex >= gridSequenceList.value.length) {
    stopGridSequence();
    gridPlayingId.value = null;
    return;
  }
  gridSequenceIndex.value = nextIndex;
  const entry = gridSequenceList.value[nextIndex];
  const el = audioRef.value;
  const url = audioUrl(entry?.audio);
  if (!el || !url) { advanceGridSequence(); return; }

  el.pause();
  el.src = url;
  el.currentTime = 0;
  gridPlayingId.value = String(entry.thumb.id);
  playAmbience();
  el.play().catch(() => { advanceGridSequence(); });
}

const storyboardItems = computed(() => {
  return buildStoryboardItems({
    thumbnails: thumbnails.value,
    layoutMode: props.scenario?.storyboardLayoutMode ?? "PRESET",
    preset: props.scenario?.storyboardPreset ?? "GRID_3",
    columns: props.scenario?.storyboardColumns ?? 3,
  });
});

const progressPercent = computed(() => {
  if (!audioDuration.value) return 0;
  return (audioCurrentTime.value / audioDuration.value) * 100;
});

const totalScenes = computed(() => thumbnails.value.length);
const hasNext = computed(() => currentIndex.value < totalScenes.value - 1);
const hasPrev = computed(() => currentIndex.value > 0);

async function loadScenario(id) {
  stopAutoplay();
  loading.value = true;
  error.value = "";
  thumbnails.value = [];
  audioMap.value = {};
  backgroundAudios.value = [];
  ambienceEnabled.value = true;
  ambienceVolume.value = 22;
  glossaryOpen.value = true;
  sceneAudioIndex.value = 0;
  currentIndex.value = 0;
  viewMode.value = "grid";
  stopGridSequence();
  gridPlayingId.value = null;

  try {
    const [thumbs, ambience] = await Promise.all([
      fetchScenarioThumbnails(id),
      fetchScenarioBackgroundAudios(id).catch(() => []),
    ]);
    const sorted = [...thumbs].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
    thumbnails.value = sorted;
    backgroundAudios.value = (Array.isArray(ambience) ? [...ambience] : [])
      .sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));

    const map = {};
    await Promise.all(
      sorted.map(async (t) => {
        try {
          const audios = await fetchThumbnailAudios(t.id);
          map[t.id] = [...audios].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
        }
        catch { map[t.id] = []; }
      })
    );
    audioMap.value = map;
  } catch (e) {
    error.value = e.message || "Failed to load scenario.";
  } finally {
    loading.value = false;
  }
}

function enterScene(index, autoplay = false) {
  if (index < 0 || index >= totalScenes.value) return;
  viewMode.value = "scene";
  goTo(index, false);
  if (autoplay) startAutoplay();
}

function enterSceneById(id) {
  stopGridSequence();
  const index = thumbnails.value.findIndex((t) => String(t.id) === String(id));
  enterScene(index);
}

function backToGrid() {
  stopAutoplay();
  viewMode.value = "grid";
}

function clearTimer() {
  if (autoplayTimer.value) {
    clearTimeout(autoplayTimer.value);
    autoplayTimer.value = null;
  }
}

function goTo(index, keepAutoplay = false) {
  if (index < 0 || index >= totalScenes.value) return;
  clearTimer();
  if (!keepAutoplay) isAutoplay.value = false;
  audioRef.value?.pause();
  if (!keepAutoplay) pauseAmbience();
  currentIndex.value = index;
  audioCurrentTime.value = 0;
  audioDuration.value = 0;
  audioPlaying.value = false;
  sceneAudioIndex.value = 0;
  gridPlayingId.value = null;
}

function goNext(keepAutoplay = false) {
  if (hasNext.value) goTo(currentIndex.value + 1, keepAutoplay);
  else {
    isAutoplay.value = false;
    pauseAmbience(true);
  }
}

function goPrev() { goTo(currentIndex.value - 1, false); }

const AUTOPLAY_FALLBACK_MS = 3500;

function startAutoplay() {
  isAutoplay.value = true;
  playAmbience();
  playCurrentScene();
}

function stopAutoplay() {
  isAutoplay.value = false;
  clearTimer();
  stopGridSequence();
  gridPlayingId.value = null;
  if (audioRef.value) {
    audioRef.value.pause();
    audioRef.value.currentTime = 0;
  }
  pauseAmbience(true);
}

function toggleAutoplay() {
  if (isAutoplay.value) stopAutoplay();
  else startAutoplay();
}

function playCurrentScene() {
  if (!isAutoplay.value) return;
  clearTimer();
  sceneAudioIndex.value = 0;

  if (currentAudios.value.length && audioRef.value) {
    playSceneAudio(0);
  } else {
    autoplayTimer.value = setTimeout(() => {
      if (isAutoplay.value) goNext(true);
    }, AUTOPLAY_FALLBACK_MS);
  }
}

function playSceneAudio(index) {
  const audio = currentAudios.value[index];
  const el = audioRef.value;
  const url = audioUrl(audio);
  if (!el || !url) return false;

  sceneAudioIndex.value = index;
  el.pause();
  el.src = url;
  el.currentTime = 0;
  el.load();
  playAmbience();
  el.play().catch(() => {
    if (index + 1 < currentAudios.value.length) {
      playSceneAudio(index + 1);
    } else if (isAutoplay.value) {
      autoplayTimer.value = setTimeout(() => goNext(true), AUTOPLAY_FALLBACK_MS);
    } else {
      pauseAmbience(true);
    }
  });
  return true;
}

watch(currentIndex, () => {
  if (isAutoplay.value) {
    nextTick(() => playCurrentScene());
  }
});

function onAudioEnded() {
  audioPlaying.value = false;
  if (isGridSequencePlaying.value) {
    advanceGridSequence();
    return;
  }
  if (sceneAudioIndex.value + 1 < currentAudios.value.length) {
    playSceneAudio(sceneAudioIndex.value + 1);
    return;
  }
  gridPlayingId.value = null;
  if (isAutoplay.value) {
    autoplayTimer.value = setTimeout(() => {
      if (isAutoplay.value) goNext(true);
    }, 600);
  } else {
    pauseAmbience(true);
  }
}

function onAudioPlay() {
  audioPlaying.value = true;
  playAmbience();
}
function onAudioPause() {
  audioPlaying.value = false;
  if (!isGridSequencePlaying.value) gridPlayingId.value = null;
  if (!isAutoplay.value && !isGridSequencePlaying.value) pauseAmbience();
}
function onAudioTimeUpdate() {
  if (audioRef.value) audioCurrentTime.value = audioRef.value.currentTime;
}
function onAudioLoadedMetadata() {
  if (audioRef.value) audioDuration.value = audioRef.value.duration;
}
function onAudioCanPlay() {
}

function toggleAudio() {
  if (!audioRef.value) return;
  if (audioPlaying.value) {
    audioRef.value.pause();
    pauseAmbience();
  } else {
    playAmbience();
    const expectedUrl = currentAudioUrl.value;
    if (expectedUrl && audioRef.value.src !== expectedUrl) {
      playSceneAudio(sceneAudioIndex.value);
    } else {
      audioRef.value.play().catch(() => {});
    }
  }
}

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await containerRef.value?.requestFullscreen?.();
    isFullscreen.value = true;
  } else {
    await document.exitFullscreen?.();
    isFullscreen.value = false;
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement;
}

function onKeyDown(e) {
  if (!props.scenario) return;
  if (e.key === "Escape") {
    if (isFullscreen.value) { document.exitFullscreen?.(); return; }
    if (viewMode.value === "scene") { backToGrid(); return; }
    close();
    return;
  }

  if (e.target?.closest?.("input, select, textarea, button, a, [contenteditable=true]")) return;
  if (viewMode.value !== "scene") return;
  if (e.key === "ArrowRight" || e.key === "ArrowDown") { e.preventDefault(); goNext(); }
  else if (e.key === "ArrowLeft" || e.key === "ArrowUp") { e.preventDefault(); goPrev(); }
  else if (e.key === " ") { e.preventDefault(); currentAudioUrl.value ? toggleAudio() : toggleAutoplay(); }
  else if (e.key === "f" || e.key === "F") toggleFullscreen();
}

function close() {
  stopAutoplay();
  if (isFullscreen.value) document.exitFullscreen?.();
  emit("close");
}

watch(() => props.scenario, (s) => {
  if (s?.id) loadScenario(s.id);
  else stopAutoplay();
}, { immediate: true });

onMounted(() => {
  window.addEventListener("keydown", onKeyDown);
  document.addEventListener("fullscreenchange", onFullscreenChange);
});

onUnmounted(() => {
  stopAutoplay();
  window.removeEventListener("keydown", onKeyDown);
  document.removeEventListener("fullscreenchange", onFullscreenChange);
});
</script>

<template>
  <Teleport to="body">
    <Transition name="reader-fade">
      <div v-if="scenario" class="reader-backdrop" @click.self="close">
        <div ref="containerRef" class="reader" :class="{ 'reader--fullscreen': isFullscreen }">

          <div class="reader-header">
            <div class="reader-header__info">
              <button v-if="viewMode === 'scene'" type="button" class="reader-back" @click="backToGrid">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="m15 18-6-6 6-6"/>
                </svg>
                Storyboard
              </button>
              <p class="reader-header__eyebrow">
                {{ viewMode === "scene" ? `Scene ${currentIndex + 1} of ${totalScenes}` : "Scenario reader" }}
              </p>
              <h2 class="reader-header__title">{{ scenario.title ?? "Untitled" }}</h2>
              <p class="reader-header__meta">By {{ scenario.authorUsername ?? "Unknown" }}</p>
            </div>
            <div class="reader-header__actions">
              <button v-if="glossaryEntries.length" type="button" class="reader-btn"
                :aria-expanded="glossaryOpen" aria-controls="reader-glossary"
                @click="glossaryOpen = !glossaryOpen">Glossary</button>
              <button
                v-if="backgroundAudioUrl"
                type="button"
                class="reader-btn reader-ambience-btn"
                :class="{ 'reader-btn--active': ambienceEnabled }"
                :aria-pressed="ambienceEnabled"
                :title="`${activeBackgroundAudio?.title || 'Vignette ambience'} · ${ambienceEnabled ? 'click to mute' : 'click to enable'}`"
                @click="toggleAmbience"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M11 5 6 9H2v6h4l5 4V5Z"/>
                  <path v-if="ambienceEnabled" d="M15.5 8.5a5 5 0 0 1 0 7M18 6a9 9 0 0 1 0 12"/>
                  <path v-else d="m16 9 5 5M21 9l-5 5"/>
                </svg>
                <span class="reader-ambience-btn__dot" :class="{ 'reader-ambience-btn__dot--playing': ambiencePlaying }"></span>
                {{ ambienceEnabled ? "Ambience on" : "Ambience off" }}
              </button>

              <button
                v-if="viewMode === 'scene'"
                type="button"
                class="reader-btn"
                :class="{ 'reader-btn--active': isAutoplay }"
                :title="isAutoplay ? 'Stop autoplay' : 'Start autoplay'"
                @click="toggleAutoplay"
              >
                <svg v-if="!isAutoplay" width="16" height="16" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <path d="M8 5v14l11-7z"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>
                </svg>
                {{ isAutoplay ? "Stop" : "Autoplay" }}
              </button>

              <button
                type="button"
                class="reader-icon-btn"
                :title="isFullscreen ? 'Exit fullscreen' : 'Fullscreen'"
                @click="toggleFullscreen"
              >
                <svg v-if="!isFullscreen" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
                </svg>
                <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"/>
                </svg>
              </button>

              <button type="button" class="reader-icon-btn" title="Close (Esc)" @click="close">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
              </button>
            </div>
          </div>

          <div v-if="viewMode === 'scene'" class="reader-progress-bar">
            <div
              v-for="(_, i) in thumbnails"
              :key="i"
              class="reader-progress-bar__segment"
              :class="{
                'reader-progress-bar__segment--done': i < currentIndex,
                'reader-progress-bar__segment--active': i === currentIndex,
              }"
              @click="goTo(i)"
            >
              <div
                v-if="i === currentIndex && currentAudioUrl"
                class="reader-progress-bar__fill"
                :style="{ width: progressPercent + '%' }"
              />
            </div>
          </div>

          <div class="reader-stage" :class="{ 'reader-stage--grid': viewMode === 'grid' }">
            <div v-if="loading" class="reader-loader">
              <div class="reader-spinner"></div>
              <p>Loading scenes…</p>
            </div>

            <div v-else-if="error" class="reader-error">
              <p>{{ error }}</p>
              <button type="button" class="reader-btn" @click="loadScenario(scenario.id)">Retry</button>
            </div>

            <div v-else-if="!thumbnails.length" class="reader-empty">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
              </svg>
              <p>This scenario has no scenes yet.</p>
            </div>

            <div v-else-if="viewMode === 'grid'" class="reader-grid-wrap">
              <div class="reader-grid-toolbar">
                <span class="reader-grid-count">{{ totalScenes }} scene{{ totalScenes !== 1 ? "s" : "" }}</span>
                <button
                    v-if="isGridSequencePlaying"
                    type="button"
                    class="reader-btn reader-btn--primary"
                    @click="stopAutoplay"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                    <rect x="6" y="5" width="4" height="14" rx="1"/><rect x="14" y="5" width="4" height="14" rx="1"/>
                  </svg>
                  Stop
                </button>
                <button v-else type="button" class="reader-btn reader-btn--primary" @click="playFromStart">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                    <path d="M8 5v14l11-7z"/>
                  </svg>
                  Play from start
                </button>
              </div>

              <div class="reader-grid" :class="{ 'reader-grid--spotlight': !!gridPlayingId }">
                <div
                  v-for="item in storyboardItems"
                  :key="item.id"
                  class="reader-grid-card"
                  :class="{ 'reader-grid-card--active': gridPlayingId === String(item.id) }"
                  :style="storyboardItemStyle(item)"
                  role="button"
                  tabindex="0"
                  @click="enterSceneById(item.id)"
                  @keydown.enter="enterSceneById(item.id)"
                >
                  <span class="reader-grid-card__num">{{ String(item._sceneNumber).padStart(2, "0") }}</span>
                  <button
                    v-if="thumbHasAudio(item)"
                    type="button"
                    class="reader-grid-card__audio"
                    :class="{ 'reader-grid-card__audio--playing': gridPlayingId === String(item.id) }"
                    :title="gridPlayingId === String(item.id) ? 'Pause audio' : 'Play audio'"
                    @click.stop="toggleGridAudio(item)"
                  >
                    <svg v-if="gridPlayingId !== String(item.id)" width="11" height="11" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                      <path d="M8 5v14l11-7z"/>
                    </svg>
                    <span v-else class="reader-audio-bars">
                      <span></span><span></span><span></span>
                    </span>
                  </button>
                  <img
                    :src="gridThumbUrl(item)"
                    :alt="item.title || `Scene ${item._sceneNumber}`"
                    class="reader-grid-card__img"
                    loading="lazy"
                  />
                  <span v-if="item.title" class="reader-grid-card__title">{{ item.title }}</span>
                </div>
              </div>
            </div>

            <template v-else-if="viewMode === 'scene' && currentThumb">
              <button
                type="button"
                class="reader-nav reader-nav--prev"
                :disabled="!hasPrev"
                aria-label="Previous scene"
                @click="goPrev"
              >
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="m15 18-6-6 6-6"/>
                </svg>
              </button>

              <Transition name="scene-slide" mode="out-in">
                <div :key="currentThumb.id" class="reader-scene">
                  <img
                    v-if="thumbImageUrl"
                    :src="thumbImageUrl"
                    :alt="currentThumb.title || `Scene ${currentIndex + 1}`"
                    class="reader-scene__img"
                  />
                  <div v-else class="reader-scene__placeholder">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                      <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
                    </svg>
                    <p>No image</p>
                  </div>

                  <div class="reader-scene__label">
                    <span class="reader-scene__counter">{{ currentIndex + 1 }} / {{ totalScenes }}</span>
                    <span v-if="currentThumb.title" class="reader-scene__title">{{ currentThumb.title }}</span>
                    <span v-if="currentAudios.length > 1" class="reader-scene__counter">
                      Take {{ sceneAudioIndex + 1 }} / {{ currentAudios.length }}
                    </span>
                  </div>

                  <button
                    v-if="currentAudioUrl"
                    type="button"
                    class="reader-scene__audio-btn"
                    :class="{ 'reader-scene__audio-btn--playing': audioPlaying }"
                    :title="audioPlaying ? 'Pause audio' : 'Play audio'"
                    @click="toggleAudio"
                  >
                    <svg v-if="!audioPlaying" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                      <path d="M8 5v14l11-7z"/>
                    </svg>
                    <span v-else class="reader-audio-bars">
                      <span></span><span></span><span></span>
                    </span>
                  </button>
                </div>
              </Transition>

              <button
                type="button"
                class="reader-nav reader-nav--next"
                :disabled="!hasNext"
                aria-label="Next scene"
                @click="goNext"
              >
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="m9 18 6-6-6-6"/>
                </svg>
              </button>
            </template>
          </div>

          <section v-if="glossaryOpen && glossaryEntries.length" id="reader-glossary" class="reader-glossary" aria-label="Glossary">
            <div class="reader-glossary__heading">
              <h3>Glossary</h3>
              <label v-if="viewMode === 'scene' && currentAudios.length > 1">
                Take
                <select :value="sceneAudioIndex" aria-label="Glossary take" @change="selectTake">
                  <option v-for="(audio, index) in currentAudios" :key="audio.id" :value="index">
                    {{ index + 1 }} · {{ audio.title || 'Recording' }}
                  </option>
                </select>
              </label>
            </div>
            <p v-if="!visibleGlossaryEntries.length">No glossary for this take.</p>
            <article v-for="entry in visibleGlossaryEntries" :key="entry.audio.id" class="reader-glossary__entry"
              :class="{'reader-glossary__entry--active': viewMode === 'grid' && isGridSequencePlaying && gridSequenceList[gridSequenceIndex]?.audio.id === entry.audio.id}">
              <button v-if="viewMode === 'grid'" type="button" class="reader-back" @click="openGlossaryEntry(entry)">
                {{ entry.thumb.title || 'Scene' }} · Take {{ entry.index + 1 }}
              </button>
              <dl>
                <template v-if="entry.audio.transcription"><dt>Transcription</dt><dd>{{ entry.audio.transcription }}</dd></template>
                <template v-if="entry.audio.gloss"><dt>Gloss</dt><dd>{{ entry.audio.gloss }}</dd></template>
                <template v-if="entry.audio.freeTranslation"><dt>Translation</dt><dd>{{ entry.audio.freeTranslation }}</dd></template>
              </dl>
            </article>
          </section>

          <div v-if="backgroundAudioUrl" class="reader-ambience-controls">
            <span>{{ activeBackgroundAudio.title || 'Background ambience' }}<small v-if="activeBackgroundAudio.sourceLabel"> · {{ activeBackgroundAudio.sourceLabel }}</small></span>
            <label>Ambience volume <input v-model.number="ambienceVolume" aria-label="Ambience volume" type="range" min="0" max="100"/> {{ ambienceVolume }}%</label>
          </div>

          <audio
            ref="audioRef"
            data-reader-voice
            :src="currentAudioUrl ?? ''"
            preload="auto"
            style="display:none"
            @ended="onAudioEnded"
            @play="onAudioPlay"
            @pause="onAudioPause"
            @timeupdate="onAudioTimeUpdate"
            @loadedmetadata="onAudioLoadedMetadata"
            @canplay="onAudioCanPlay"
          />

          <div v-if="$slots.actions" class="reader-actions-bar">
            <slot name="actions" />
          </div>

          <audio
            ref="backgroundAudioRef"
            data-reader-ambience
            :src="backgroundAudioUrl ?? ''"
            preload="auto"
            :loop="activeBackgroundAudio?.loop ?? true"
            style="display:none"
            @play="ambiencePlaying = true"
            @pause="ambiencePlaying = false"
          />

          <div class="reader-footer">
            <template v-if="viewMode === 'scene'">
              <span>← → navigate</span>
              <span>Space: {{ currentAudioUrl ? "play/pause" : "autoplay" }}</span>
              <span>F: fullscreen</span>
              <span>Esc: back to storyboard</span>
            </template>
            <template v-else>
              <span>Click a scene to open it</span>
              <span>Esc: close</span>
            </template>
          </div>

        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.reader-glossary { padding: 12px 20px; border-top: 1.5px solid var(--border); overflow-y: auto; max-height: 28vh; flex-shrink: 0; }
.reader-glossary__heading, .reader-ambience-controls { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
.reader-glossary h3 { margin: 0; font-size: 0.9rem; }
.reader-glossary__heading label, .reader-ambience-controls label { display: flex; flex-direction: row; align-items: center; gap: 8px; min-width: 0; max-width: 100%; }
.reader-glossary__heading select { min-width: 0; width: auto; max-width: 100%; }
.reader-glossary__entry { padding: 10px; margin-top: 8px; border-radius: 10px; background: var(--surface); }
.reader-glossary__entry--active { box-shadow: inset 3px 0 var(--primary); }
.reader-glossary dl { margin: 0; display: grid; grid-template-columns: 100px minmax(0, 1fr); gap: 6px 12px; font-size: 0.85rem; }
.reader-glossary dt { font-weight: 700; color: var(--text-soft); }
.reader-glossary dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.reader-ambience-controls { padding: 10px 20px; border-top: 1.5px solid var(--border); font-size: 0.78rem; flex-shrink: 0; }
.reader-ambience-controls input { width: 100px; }

.reader-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1400;
  background: rgba(30, 8, 18, 0.55);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.reader {
  position: relative;
  width: min(1320px, 96vw);
  max-height: min(94vh, 1040px);
  display: flex;
  flex-direction: column;
  background: var(--surface-alt);
  border-radius: 24px;
  border: 3px solid var(--text);
  box-shadow: 8px 8px 0 var(--text);
  overflow: hidden;
}

.reader--fullscreen {
  width: 100vw;
  max-width: 100vw;
  height: 100vh;
  max-height: 100vh;
  border-radius: 0;
  border: none;
  box-shadow: none;
}

.reader-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 18px 20px 12px;
  border-bottom: 1.5px solid var(--border);
  flex-shrink: 0;
}

.reader-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--primary);
  font: inherit;
  font-size: 0.76rem;
  font-weight: 800;
  cursor: pointer;
  transition: color 140ms ease;
}
.reader-back:hover { color: var(--primary-strong); }

.reader-header__eyebrow {
  margin: 0 0 2px;
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--primary);
}

.reader-header__title {
  margin: 0 0 2px;
  font-size: 1.15rem;
  font-weight: 900;
  color: var(--text);
  letter-spacing: -0.01em;
}

.reader-header__meta {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-soft);
}

.reader-header__actions {
  flex-wrap: wrap;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.reader-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.45rem 0.95rem;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: var(--surface);
  color: var(--text-soft);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.reader-btn:hover {
  background: var(--accent-green);
  border-color: var(--primary);
  color: var(--text);
}

.reader-btn--active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.reader-btn--active:hover {
  background: var(--primary-strong);
  border-color: var(--primary-strong);
  color: #fff;
}

.reader-btn--primary {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}
.reader-btn--primary:hover { background: var(--primary-strong); border-color: var(--primary-strong); color: #fff; }

.reader-ambience-btn {
  gap: 5px;
}

.reader-ambience-btn__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.45;
}

.reader-ambience-btn__dot--playing {
  opacity: 1;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.18);
}

.reader-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1.5px solid var(--border);
  background: var(--surface);
  color: var(--text-soft);
  cursor: pointer;
  transition: all 0.15s;
}

.reader-icon-btn:hover {
  background: var(--text);
  color: var(--surface-alt);
  border-color: var(--text);
}

.reader-progress-bar {
  display: flex;
  gap: 3px;
  padding: 10px 20px 8px;
  flex-shrink: 0;
}

.reader-progress-bar__segment {
  flex: 1;
  height: 3px;
  border-radius: 999px;
  background: var(--border);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: background 0.2s;
}

.reader-progress-bar__segment:hover {
  background: var(--accent-green);
}

.reader-progress-bar__segment--done {
  background: var(--primary);
  opacity: 0.55;
}

.reader-progress-bar__segment--active {
  background: var(--border);
}

.reader-progress-bar__fill {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  background: var(--primary);
  border-radius: 999px;
  transition: width 0.2s linear;
}

.reader-stage {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  min-height: 0;
  padding: 12px 0;
}

.reader-stage--grid {
  align-items: stretch;
  overflow-y: auto;
  padding: 0;
}

.reader-grid-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  padding: 18px 20px 22px;
}

.reader-grid-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reader-grid-count {
  font-size: 0.78rem;
  font-weight: 800;
  color: var(--text-soft);
}

.reader-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-auto-rows: 95px;
  grid-auto-flow: dense;
  gap: 8px;
}

.reader-grid-card {
  position: relative;
  display: block;
  min-height: 60px;
  border: 0;
  border-radius: 12px;
  padding: 0;
  background: var(--accent-green);
  cursor: pointer;
  overflow: hidden;
  transition: box-shadow 160ms ease, transform 120ms ease, opacity 220ms ease;
  text-align: left;
}

.reader-grid-card:hover {
  box-shadow: 0 0 0 3px var(--primary);
  transform: translateY(-1px);
}

.reader-grid--spotlight .reader-grid-card {
  opacity: 0.32;
}

.reader-grid--spotlight .reader-grid-card:hover {
  opacity: 0.55;
}

.reader-grid-card--active,
.reader-grid--spotlight .reader-grid-card--active {
  opacity: 1;
  box-shadow: 0 0 0 3px var(--primary), 0 10px 26px rgba(30,8,18,0.22);
}

.reader-grid-card--active:hover,
.reader-grid--spotlight .reader-grid-card--active:hover {
  opacity: 1;
}

.reader-grid-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: var(--accent-green);
  display: block;
}

.reader-grid-card__num {
  position: absolute;
  top: 7px;
  left: 7px;
  z-index: 1;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(30,8,18,0.68);
  color: #fff;
  font-size: 0.6rem;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.reader-grid-card__audio {
  position: absolute;
  top: 7px;
  right: 7px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 999px;
  background: var(--primary);
  color: #fff;
  cursor: pointer;
  transition: background 140ms ease, transform 120ms ease;
}

.reader-grid-card__audio:hover {
  background: var(--primary-strong);
  transform: scale(1.08);
}

.reader-grid-card__audio--playing {
  background: #8B3010;
}

.reader-grid-card__audio .reader-audio-bars {
  height: 11px;
}

.reader-grid-card__title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
  padding: 14px 10px 7px;
  background: linear-gradient(180deg, rgba(30,8,18,0) 0%, rgba(30,8,18,0.75) 100%);
  font-size: 0.72rem;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.reader-scene {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.reader-scene__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 8px;
  display: block;
}

.reader-scene__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-soft);
}

.reader-scene__placeholder p {
  margin: 0;
  font-size: 0.85rem;
}

.reader-scene__label {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px;
  background: rgba(30, 8, 18, 0.72);
  backdrop-filter: blur(8px);
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  white-space: nowrap;
}

.reader-scene__counter {
  font-size: 0.75rem;
  font-weight: 800;
  color: rgba(255, 244, 236, 0.65);
}

.reader-scene__title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #FFF4EC;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.reader-scene__audio-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.2);
  background: rgba(30, 8, 18, 0.62);
  backdrop-filter: blur(8px);
  color: rgba(255, 244, 236, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.reader-scene__audio-btn:hover {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.reader-scene__audio-btn--playing {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.reader-audio-bars {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 14px;
}

.reader-audio-bars span {
  width: 3px;
  border-radius: 2px;
  background: currentColor;
  animation: audio-bar 0.8s ease-in-out infinite;
}

.reader-audio-bars span:nth-child(1) { animation-delay: 0s; height: 60%; }
.reader-audio-bars span:nth-child(2) { animation-delay: 0.15s; height: 100%; }
.reader-audio-bars span:nth-child(3) { animation-delay: 0.3s; height: 45%; }

@keyframes audio-bar {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}

.reader-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.2);
  background: rgba(30, 8, 18, 0.55);
  backdrop-filter: blur(8px);
  color: rgba(255, 244, 236, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.reader-nav--prev { left: 16px; }
.reader-nav--next { right: 16px; }

.reader-nav:hover:not(:disabled) {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
  transform: translateY(-50%) scale(1.08);
}

.reader-nav:disabled {
  opacity: 0.2;
  cursor: not-allowed;
}

.reader-loader, .reader-error, .reader-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  color: var(--text-soft);
  font-size: 0.88rem;
}

.reader-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* Optional actions bar (e.g. accept/decline an invite) */
.reader-actions-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}

.reader-footer {
  display: flex;
  justify-content: center;
  gap: 20px;
  padding: 10px 20px 14px;
  border-top: 1.5px solid var(--border);
  flex-shrink: 0;
}

.reader-footer span {
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--text-soft);
  letter-spacing: 0.04em;
}

.reader-fade-enter-active, .reader-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.reader-fade-enter-from, .reader-fade-leave-to {
  opacity: 0;
  transform: scale(0.97);
}

.scene-slide-enter-active, .scene-slide-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.scene-slide-enter-from {
  opacity: 0;
  transform: translateX(24px);
}
.scene-slide-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}

@media (max-width: 600px) {
  .reader-backdrop { padding: 0; }
  .reader { border-radius: 0; max-height: 100vh; width: 100vw; }
  .reader-header { position: relative; flex-direction: column; gap: 10px; padding: 12px; }
  .reader-header__info { width: 100%; min-width: 0; padding-right: 40px; }
  .reader-header__actions { width: 100%; flex-shrink: 1; gap: 6px; }
  .reader-header__actions [title="Close (Esc)"] { position: absolute; top: 12px; right: 12px; }
  .reader-header__actions .reader-btn { padding-inline: 0.65rem; }
  .reader-glossary { padding-inline: 12px; }
  .reader-glossary__heading label { width: 100%; }
  .reader-glossary__heading select { flex: 1; }
  .reader-footer { gap: 10px; flex-wrap: wrap; }
  .reader-footer span { font-size: 0.6rem; }
  .reader-nav { width: 36px; height: 36px; }
  .reader-nav--prev { left: 8px; }
  .reader-nav--next { right: 8px; }
  .reader-grid { grid-auto-rows: 34px; }
  .reader-ambience-btn { padding-inline: 0.7rem; }
  .reader-ambience-btn__dot { display: none; }
}
</style>
