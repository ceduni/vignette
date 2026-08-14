<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from "vue";
import { fetchScenarioThumbnails, fetchThumbnailAudios } from "../../api/scenarios";
import { buildApiUrl } from "../../api/rest";

const props = defineProps({
  scenario: { type: Object, default: null }, // { id, title, authorUsername }
});

const emit = defineEmits(["close"]);

// ── State ──────────────────────────────────────────────────────────────────
const loading = ref(false);
const error = ref("");
const thumbnails = ref([]); // sorted list of thumb objects
const audioMap = ref({});   // { thumbId: [audio, ...] }
const currentIndex = ref(0);
const isAutoplay = ref(false);
const isFullscreen = ref(false);
const audioRef = ref(null);
const containerRef = ref(null);
const autoplayTimer = ref(null);
const audioDuration = ref(0);
const audioCurrentTime = ref(0);
const audioPlaying = ref(false);

// ── Derived ────────────────────────────────────────────────────────────────
const currentThumb = computed(() => thumbnails.value[currentIndex.value] ?? null);

const currentAudios = computed(() => {
  if (!currentThumb.value) return [];
  return audioMap.value[currentThumb.value.id] ?? [];
});

const currentAudioUrl = computed(() => {
  const first = currentAudios.value[0];
  if (!first) return null;
  return buildApiUrl(`/api/audios/${first.id}/content`);
});

const thumbImageUrl = computed(() => {
  if (!currentThumb.value) return null;
  return buildApiUrl(`/api/thumbnails/${currentThumb.value.id}/content`);
});

const progressPercent = computed(() => {
  if (!audioDuration.value) return 0;
  return (audioCurrentTime.value / audioDuration.value) * 100;
});

const totalScenes = computed(() => thumbnails.value.length);
const hasNext = computed(() => currentIndex.value < totalScenes.value - 1);
const hasPrev = computed(() => currentIndex.value > 0);

// ── Load data ──────────────────────────────────────────────────────────────
async function loadScenario(id) {
  loading.value = true;
  error.value = "";
  thumbnails.value = [];
  audioMap.value = {};
  currentIndex.value = 0;

  try {
    const thumbs = await fetchScenarioThumbnails(id);
    const sorted = [...thumbs].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
    thumbnails.value = sorted;

    const map = {};
    await Promise.all(
      sorted.map(async (t) => {
        try { map[t.id] = await fetchThumbnailAudios(t.id); }
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

// ── Navigation ─────────────────────────────────────────────────────────────
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
  currentIndex.value = index;
  audioCurrentTime.value = 0;
  audioDuration.value = 0;
  audioPlaying.value = false;
}

function goNext(keepAutoplay = false) {
  if (hasNext.value) goTo(currentIndex.value + 1, keepAutoplay);
  else isAutoplay.value = false;
}

function goPrev() { goTo(currentIndex.value - 1, false); }

// ── Autoplay ───────────────────────────────────────────────────────────────
const AUTOPLAY_FALLBACK_MS = 3500;

function startAutoplay() {
  isAutoplay.value = true;
  playCurrentScene();
}

function stopAutoplay() {
  isAutoplay.value = false;
  clearTimer();
  if (audioRef.value) {
    audioRef.value.pause();
    audioRef.value.currentTime = 0;
  }
}

function toggleAutoplay() {
  if (isAutoplay.value) stopAutoplay();
  else startAutoplay();
}

// Central function: play current scene during autoplay
function playCurrentScene() {
  if (!isAutoplay.value) return;
  clearTimer();

  if (currentAudioUrl.value && audioRef.value) {
    // Reset and reload audio for this scene
    audioRef.value.pause();
    audioRef.value.currentTime = 0;
    audioRef.value.src = currentAudioUrl.value;
    audioRef.value.load();

    const playPromise = audioRef.value.play();
    if (playPromise !== undefined) {
      playPromise.catch(() => {
        // Autoplay blocked — fall back to timer
        autoplayTimer.value = setTimeout(() => {
          if (isAutoplay.value) goNext(true);
        }, AUTOPLAY_FALLBACK_MS);
      });
    }
  } else {
    // No audio for this scene — advance after delay
    autoplayTimer.value = setTimeout(() => {
      if (isAutoplay.value) goNext(true);
    }, AUTOPLAY_FALLBACK_MS);
  }
}

// Watch for scene changes during autoplay
watch(currentIndex, () => {
  if (isAutoplay.value) {
    nextTick(() => playCurrentScene());
  }
});

function onAudioEnded() {
  audioPlaying.value = false;
  if (isAutoplay.value) {
    autoplayTimer.value = setTimeout(() => {
      if (isAutoplay.value) goNext(true);
    }, 600);
  }
}

function onAudioPlay() { audioPlaying.value = true; }
function onAudioPause() { audioPlaying.value = false; }
function onAudioTimeUpdate() {
  if (audioRef.value) audioCurrentTime.value = audioRef.value.currentTime;
}
function onAudioLoadedMetadata() {
  if (audioRef.value) audioDuration.value = audioRef.value.duration;
}
function onAudioCanPlay() {
  // Not used for autoplay anymore — kept for manual play consistency
}

function toggleAudio() {
  if (!audioRef.value) return;
  if (audioPlaying.value) audioRef.value.pause();
  else audioRef.value.play().catch(() => {});
}

// ── Fullscreen ─────────────────────────────────────────────────────────────
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

// ── Keyboard ───────────────────────────────────────────────────────────────
function onKeyDown(e) {
  if (!props.scenario) return;
  if (e.key === "Escape") { if (isFullscreen.value) document.exitFullscreen?.(); else close(); }
  else if (e.key === "ArrowRight" || e.key === "ArrowDown") { e.preventDefault(); goNext(); }
  else if (e.key === "ArrowLeft" || e.key === "ArrowUp") { e.preventDefault(); goPrev(); }
  else if (e.key === " ") { e.preventDefault(); currentAudioUrl.value ? toggleAudio() : toggleAutoplay(); }
  else if (e.key === "f" || e.key === "F") toggleFullscreen();
}

// ── Lifecycle ──────────────────────────────────────────────────────────────
function close() {
  stopAutoplay();
  if (isFullscreen.value) document.exitFullscreen?.();
  emit("close");
}

watch(() => props.scenario, (s) => {
  if (s?.id) loadScenario(s.id);
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

          <!-- Header -->
          <div class="reader-header">
            <div class="reader-header__info">
              <p class="reader-header__eyebrow">Scenario reader</p>
              <h2 class="reader-header__title">{{ scenario.title ?? "Untitled" }}</h2>
              <p class="reader-header__meta">By {{ scenario.authorUsername ?? "Unknown" }}</p>
            </div>
            <div class="reader-header__actions">
              <!-- Autoplay toggle -->
              <button
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

              <!-- Fullscreen toggle -->
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

              <!-- Close -->
              <button type="button" class="reader-icon-btn" title="Close (Esc)" @click="close">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Progress bar -->
          <div class="reader-progress-bar">
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

          <!-- Main stage -->
          <div class="reader-stage">
            <!-- Loading -->
            <div v-if="loading" class="reader-loader">
              <div class="reader-spinner"></div>
              <p>Loading scenes…</p>
            </div>

            <!-- Error -->
            <div v-else-if="error" class="reader-error">
              <p>{{ error }}</p>
              <button type="button" class="reader-btn" @click="loadScenario(scenario.id)">Retry</button>
            </div>

            <!-- Empty -->
            <div v-else-if="!thumbnails.length" class="reader-empty">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
              </svg>
              <p>This scenario has no scenes yet.</p>
            </div>

            <!-- Scene -->
            <template v-else-if="currentThumb">
              <!-- Nav prev -->
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

              <!-- Image -->
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

                  <!-- Scene label -->
                  <div class="reader-scene__label">
                    <span class="reader-scene__counter">{{ currentIndex + 1 }} / {{ totalScenes }}</span>
                    <span v-if="currentThumb.title" class="reader-scene__title">{{ currentThumb.title }}</span>
                  </div>

                  <!-- Audio indicator -->
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

              <!-- Nav next -->
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

          <!-- Audio element always in DOM — src changes trigger canplay reliably -->
          <audio
            ref="audioRef"
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

          <!-- Optional caller-provided actions (e.g. accept/decline an invite) -->
          <div v-if="$slots.actions" class="reader-actions-bar">
            <slot name="actions" />
          </div>

          <!-- Footer hint -->
          <div class="reader-footer">
            <span>← → navigate</span>
            <span>Space: {{ currentAudioUrl ? 'play/pause' : 'autoplay' }}</span>
            <span>F: fullscreen</span>
            <span>Esc: close</span>
          </div>

        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Backdrop */
.reader-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1400;
  background: rgba(20, 8, 4, 0.72);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

/* Modal */
.reader {
  position: relative;
  width: min(880px, 100%);
  max-height: min(92vh, 900px);
  display: flex;
  flex-direction: column;
  background: linear-gradient(160deg, #2A0614 0%, #1E0812 100%);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255,255,255,0.04);
  overflow: hidden;
}

.reader--fullscreen {
  width: 100vw;
  max-width: 100vw;
  height: 100vh;
  max-height: 100vh;
  border-radius: 0;
  border: none;
}

/* Header */
.reader-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 18px 20px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}

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
  font-size: 1.05rem;
  font-weight: 800;
  color: #FFF4EC;
  letter-spacing: -0.01em;
}

.reader-header__meta {
  margin: 0;
  font-size: 0.75rem;
  color: rgba(255, 244, 236, 0.45);
}

.reader-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* Buttons */
.reader-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
  border: 1.5px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.07);
  color: rgba(255, 244, 236, 0.75);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.reader-btn:hover {
  background: rgba(192, 74, 8, 0.2);
  border-color: var(--primary);
  color: #FFF4EC;
}

.reader-btn--active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.reader-btn--active:hover {
  background: var(--primary-strong);
  border-color: var(--primary-strong);
}

.reader-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1.5px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 244, 236, 0.7);
  cursor: pointer;
  transition: all 0.15s;
}

.reader-icon-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #FFF4EC;
  border-color: rgba(255, 255, 255, 0.25);
}

/* Progress bar */
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
  background: rgba(255, 255, 255, 0.15);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: background 0.2s;
}

.reader-progress-bar__segment:hover {
  background: rgba(255, 255, 255, 0.28);
}

.reader-progress-bar__segment--done {
  background: rgba(192, 74, 8, 0.6);
}

.reader-progress-bar__segment--active {
  background: rgba(255, 255, 255, 0.2);
}

.reader-progress-bar__fill {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  background: var(--primary);
  border-radius: 999px;
  transition: width 0.2s linear;
}

/* Stage */
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

/* Scene */
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
  color: rgba(255, 244, 236, 0.3);
}

.reader-scene__placeholder p {
  margin: 0;
  font-size: 0.85rem;
}

/* Scene label */
.reader-scene__label {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px;
  background: rgba(20, 8, 4, 0.7);
  backdrop-filter: blur(8px);
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
}

.reader-scene__counter {
  font-size: 0.75rem;
  font-weight: 800;
  color: rgba(255, 244, 236, 0.6);
}

.reader-scene__title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #FFF4EC;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Audio button */
.reader-scene__audio-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.2);
  background: rgba(20, 8, 4, 0.6);
  backdrop-filter: blur(8px);
  color: rgba(255, 244, 236, 0.8);
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

/* Audio bars animation */
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

/* Nav arrows */
.reader-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.15);
  background: rgba(20, 8, 4, 0.55);
  backdrop-filter: blur(8px);
  color: rgba(255, 244, 236, 0.8);
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

/* Loader / error / empty */
.reader-loader, .reader-error, .reader-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  color: rgba(255, 244, 236, 0.5);
  font-size: 0.88rem;
}

.reader-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(255, 255, 255, 0.1);
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

/* Footer */
.reader-footer {
  display: flex;
  justify-content: center;
  gap: 20px;
  padding: 10px 20px 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.reader-footer span {
  font-size: 0.68rem;
  font-weight: 600;
  color: rgba(255, 244, 236, 0.28);
  letter-spacing: 0.04em;
}

/* Transitions */
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

/* Responsive */
@media (max-width: 600px) {
  .reader-backdrop { padding: 0; }
  .reader { border-radius: 0; max-height: 100vh; width: 100vw; }
  .reader-footer { gap: 10px; flex-wrap: wrap; }
  .reader-footer span { font-size: 0.6rem; }
  .reader-nav { width: 36px; height: 36px; }
  .reader-nav--prev { left: 8px; }
  .reader-nav--next { right: 8px; }
}
</style>