<script setup>
import {computed, onBeforeUnmount, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {useAuth} from "../composables/useAuth";
import {draftAudioStorageKey, migrateAnonymousDraftAudios, migrateLegacyDraftAudios} from "../utils/draftAudioStorage";

const {currentUser} = useAuth();

migrateLegacyDraftAudios();

const panelOpen = ref(false);
const drafts = ref([]);
const status = ref("idle");
const error = ref("");
const warning = ref("");
const elapsedSeconds = ref(0);

let recorder = null;
let stream = null;
let chunks = [];
let recordingTimer = null;
let recordingStartedAt = 0;

const draftCount = computed(() => drafts.value.length);
const isRecording = computed(() => status.value === "recording");
const isSaving = computed(() => status.value === "saving");
const isRequesting = computed(() => status.value === "requesting");

function loadDrafts() {
  try {
    const key = draftAudioStorageKey(currentUser.value?.username);
    drafts.value = JSON.parse(localStorage.getItem(key) || "[]");
  } catch {
    drafts.value = [];
  }
}

watch(
    () => currentUser.value?.username,
    (username) => {
      if (username) migrateAnonymousDraftAudios(username);
      loadDrafts();
    },
    {immediate: true}
);

function persistDrafts(next) {
  try {
    localStorage.setItem(draftAudioStorageKey(currentUser.value?.username), JSON.stringify(next));
    window.dispatchEvent(new CustomEvent("vignette:draft-audios-changed", {detail: next}));
    return true;
  } catch {
    return false;
  }
}

function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

async function ensureRecorder() {
  if (recorder && stream?.active) return;
  releaseRecorder();
  stream = await navigator.mediaDevices.getUserMedia({audio: true});
  recorder = new MediaRecorder(stream);
  recorder.ondataavailable = (e) => { if (e.data?.size > 0) chunks.push(e.data); };
  recorder.onstop = async (event) => {
    const blob = new Blob(chunks, {type: event.target?.mimeType || "audio/webm"});
    chunks = [];
    status.value = "saving";
    try {
      const dataUrl = await blobToDataUrl(blob);
      const next = [{
        id: `draft-${Date.now()}`,
        title: `Draft ${drafts.value.length + 1}`,
        createdAt: new Date().toISOString(),
        mimeType: blob.type || "audio/webm",
        dataUrl,
      }, ...drafts.value];
      drafts.value = next;
      warning.value = persistDrafts(next)
          ? ""
          : "Storage is full. This recording will be lost on refresh unless you delete an older draft.";
      panelOpen.value = true;
    } catch (e) {
      error.value = e.message || "Could not save audio draft.";
    } finally {
      status.value = "idle";
      stopRecordingTimer();
      releaseRecorder();
    }
  };
}

function startRecordingTimer() {
  recordingStartedAt = Date.now();
  elapsedSeconds.value = 0;
  clearInterval(recordingTimer);
  recordingTimer = setInterval(() => {
    elapsedSeconds.value = Math.floor((Date.now() - recordingStartedAt) / 1000);
  }, 250);
}

function stopRecordingTimer() {
  clearInterval(recordingTimer);
  recordingTimer = null;
}

function releaseRecorder() {
  stream?.getTracks?.().forEach((track) => track.stop());
  stream = null;
  recorder = null;
}

function recordingErrorMessage(e) {
  if (!navigator.mediaDevices?.getUserMedia) return "Audio recording is not supported in this browser.";
  if (e?.name === "NotAllowedError" || e?.name === "SecurityError") {
    return "Microphone access is blocked. Allow microphone access in your browser settings and try again.";
  }
  if (e?.name === "NotFoundError") return "No microphone was found. Connect one and try again.";
  if (e?.name === "NotReadableError") return "Your microphone is busy in another app. Close it there and try again.";
  return e?.message || "Could not start recording.";
}

async function toggleRecording() {
  error.value = "";
  if (isRecording.value) {
    status.value = "saving";
    recorder?.stop();
    return;
  }
  try {
    status.value = "requesting";
    await ensureRecorder();
    chunks = [];
    recorder.start();
    status.value = "recording";
    startRecordingTimer();
  } catch (e) {
    status.value = "idle";
    error.value = recordingErrorMessage(e);
    panelOpen.value = true;
    releaseRecorder();
  }
}

const confirmDeleteId = ref(null);
let confirmTimer = null;

function onDeleteClick(draft) {
  if (confirmDeleteId.value === draft.id) {
    clearTimeout(confirmTimer);
    confirmDeleteId.value = null;
    commitRemoveDraft(draft);
    return;
  }
  confirmDeleteId.value = draft.id;
  clearTimeout(confirmTimer);
  confirmTimer = setTimeout(() => { confirmDeleteId.value = null; }, 3200);
}

function commitRemoveDraft(draft) {
  if (playingId.value === draft.id) stopPlayback();
  const next = drafts.value.filter((d) => d.id !== draft.id);
  drafts.value = next;
  if (!persistDrafts(next)) warning.value = "The draft was removed here, but browser storage could not be updated.";
}

const clearAllConfirmOpen = ref(false);

function openClearAllConfirm() {
  if (!drafts.value.length) return;
  clearAllConfirmOpen.value = true;
}

function cancelClearAllConfirm() {
  clearAllConfirmOpen.value = false;
}

function confirmClearAllDrafts() {
  stopPlayback();
  drafts.value = [];
  if (!persistDrafts([])) warning.value = "Drafts were cleared here, but browser storage could not be updated.";
  clearAllConfirmOpen.value = false;
  confirmDeleteId.value = null;
}

function updateDraftTitle(draft, title) {
  const cleanTitle = title.trim() || "Untitled audio draft";
  const next = drafts.value.map((d) => d.id === draft.id ? {...d, title: cleanTitle} : d);
  drafts.value = next;
  if (!persistDrafts(next)) warning.value = "The title could not be saved because browser storage is full.";
}

function draftScenarioPath(draft) {
  if (currentUser.value?.username) {
    const params = new URLSearchParams({draftAudio: String(draft.id)});
    if (draft.title) params.set("draftTitle", draft.title);
    return `/create-scenario?${params.toString()}`;
  }
  return `/scenarios/emergency-${draft.id}?draftAudio=${draft.id}`;
}

function formatDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) seconds = 0;
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function relativeTime(iso) {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const minutes = Math.floor((Date.now() - then) / 60000);
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString(undefined, {month: "short", day: "numeric"});
}

const audioEl = ref(null);
const playingId = ref(null);
const currentTime = ref(0);
const durations = ref({});

function primeDuration(draft) {
  if (!draft?.dataUrl || durations.value[draft.id] != null) return;
  const probe = new Audio(draft.dataUrl);
  probe.preload = "metadata";
  probe.addEventListener("loadedmetadata", () => {
    if (Number.isFinite(probe.duration)) {
      durations.value = {...durations.value, [draft.id]: probe.duration};
    }
  }, {once: true});
}

watch(drafts, (list) => list.forEach(primeDuration), {immediate: true});

function stopPlayback() {
  audioEl.value?.pause();
  playingId.value = null;
  currentTime.value = 0;
}

function togglePlay(draft) {
  const el = audioEl.value;
  if (!el) return;
  if (playingId.value === draft.id) {
    stopPlayback();
    return;
  }
  if (el.src !== draft.dataUrl) el.src = draft.dataUrl;
  playingId.value = draft.id;
  currentTime.value = 0;
  el.currentTime = 0;
  el.play().catch(() => { playingId.value = null; });
}

function onTimeUpdate() {
  currentTime.value = audioEl.value?.currentTime ?? 0;
}

function onEnded() {
  playingId.value = null;
  currentTime.value = 0;
}

function progressPercent(draft) {
  if (playingId.value !== draft.id) return 0;
  const dur = durations.value[draft.id] || audioEl.value?.duration || 0;
  return dur ? Math.min(100, (currentTime.value / dur) * 100) : 0;
}

function seek(draft, event) {
  const el = audioEl.value;
  if (!el) return;
  const rect = event.currentTarget.getBoundingClientRect();
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  const applySeek = () => {
    const dur = el.duration || durations.value[draft.id] || 0;
    el.currentTime = ratio * dur;
    currentTime.value = el.currentTime;
  };
  if (playingId.value !== draft.id) {
    if (el.src !== draft.dataUrl) el.src = draft.dataUrl;
    playingId.value = draft.id;
    el.addEventListener("loadedmetadata", applySeek, {once: true});
    el.play().catch(() => { playingId.value = null; });
  } else {
    applySeek();
  }
}

function seekWithKeyboard(draft, event) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  const el = audioEl.value;
  if (!el) return;
  if (playingId.value !== draft.id) {
    el.src = draft.dataUrl;
    playingId.value = draft.id;
  }
  const duration = durations.value[draft.id] || el.duration || 0;
  if (event.key === "Home") el.currentTime = 0;
  else if (event.key === "End") el.currentTime = duration;
  else el.currentTime = Math.max(0, Math.min(duration, (el.currentTime || 0) + (event.key === "ArrowRight" ? 5 : -5)));
  currentTime.value = el.currentTime;
}

watch(panelOpen, (open) => { if (!open) stopPlayback(); });

onBeforeUnmount(() => {
  clearTimeout(confirmTimer);
  stopRecordingTimer();
  stopPlayback();
  if (recorder?.state && recorder.state !== "inactive") recorder.stop();
  releaseRecorder();
});
</script>

<template>
  <div class="er-root">
    <Transition name="er-panel">
      <section v-if="panelOpen" id="emergency-audio-drafts" class="er-panel" aria-label="Emergency audio drafts">
        <div class="er-panel__head">
          <div>
            <p class="er-panel__eyebrow">Quick audio</p>
            <h2 class="er-panel__title">Draft recordings</h2>
            <p class="er-panel__intro">
              {{ draftCount ? `${draftCount} saved on this device` : 'Record without setting up a scenario' }}
            </p>
          </div>
          <div class="er-panel__head-actions">
            <button
                v-if="draftCount"
                type="button"
                class="er-panel__clear-all"
                @click="openClearAllConfirm"
            >
              Clear all
            </button>
            <button type="button" class="er-panel__close" aria-label="Close audio drafts" @click="panelOpen = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                   stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 6 6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>
        </div>

        <p v-if="error" class="er-error">{{ error }}</p>
        <p v-if="warning" class="er-warning">{{ warning }}</p>

        <div v-if="drafts.length" class="er-list">
          <article v-for="draft in drafts" :key="draft.id" class="er-item">
            <div class="er-item__top">
              <div class="er-item__icon" :class="{ 'er-item__icon--live': playingId === draft.id }" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                     stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2M12 19v3"/>
                </svg>
              </div>
              <div class="er-item__meta">
                <input
                    :value="draft.title"
                    class="er-item__title"
                    aria-label="Draft title"
                    maxlength="80"
                    @change="updateDraftTitle(draft, $event.target.value)"
                    @blur="updateDraftTitle(draft, $event.target.value)"
                />
                <span class="er-item__time">{{ relativeTime(draft.createdAt) }}</span>
              </div>
              <button
                  type="button"
                  class="er-item__del"
                  :class="{ 'er-item__del--confirm': confirmDeleteId === draft.id }"
                  :title="confirmDeleteId === draft.id ? 'Click again to delete' : 'Delete draft'"
                  :aria-label="confirmDeleteId === draft.id ? 'Click again to confirm delete' : 'Delete draft'"
                  @click="onDeleteClick(draft)"
              >
                <svg v-if="confirmDeleteId !== draft.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 6h18"/>
                  <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  <path d="M19 6l-.867 12.142A2 2 0 0 1 16.138 20H7.862a2 2 0 0 1-1.995-1.858L5 6"/>
                  <path d="M10 11v6"/>
                  <path d="M14 11v6"/>
                </svg>
                <span v-else class="er-item__del-label">Sure?</span>
              </button>
            </div>

            <div class="er-player">
              <button
                  type="button"
                  class="er-player__play"
                  :aria-label="playingId === draft.id ? 'Pause' : 'Play'"
                  @click="togglePlay(draft)"
              >
                <svg v-if="playingId === draft.id" viewBox="0 0 24 24" fill="currentColor">
                  <rect x="6" y="5" width="4" height="14" rx="1"/><rect x="14" y="5" width="4" height="14" rx="1"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8 5.5v13l11-6.5-11-6.5z"/>
                </svg>
              </button>

              <div
                  class="er-player__bar"
                  role="slider"
                  tabindex="0"
                  aria-label="Audio position"
                  :aria-valuemin="0"
                  :aria-valuemax="Math.round(durations[draft.id] ?? 0)"
                  :aria-valuenow="Math.round(playingId === draft.id ? currentTime : 0)"
                  :aria-valuetext="`${formatDuration(playingId === draft.id ? currentTime : 0)} of ${formatDuration(durations[draft.id] ?? 0)}`"
                  @click="seek(draft, $event)"
                  @keydown="seekWithKeyboard(draft, $event)"
              >
                <div class="er-player__fill" :style="{ width: progressPercent(draft) + '%' }"></div>
              </div>

              <span class="er-player__time">
                {{ formatDuration(playingId === draft.id ? currentTime : 0) }} / {{ formatDuration(durations[draft.id] ?? 0) }}
              </span>
            </div>

            <RouterLink :to="draftScenarioPath(draft)" class="er-item__cta">
              <span>Open in studio</span>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6"/>
              </svg>
            </RouterLink>
          </article>
        </div>

        <div v-else class="er-empty">
          <span class="er-empty__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2M12 19v3"/>
            </svg>
          </span>
          <p>Your quick recordings will appear here, ready to name, review, and move into the studio.</p>
        </div>
      </section>
    </Transition>

    <Teleport to="body">
      <div v-if="clearAllConfirmOpen" class="er-clear-backdrop" @click.self="cancelClearAllConfirm">
        <div class="er-clear-confirm">
          <p class="er-clear-confirm__eyebrow">Not reversible</p>
          <h2 class="er-clear-confirm__title">Delete all {{ draftCount }} draft{{ draftCount === 1 ? '' : 's' }}?</h2>
          <p class="er-clear-confirm__body">
            These recordings only exist on this device. Once cleared, they can't be recovered.
          </p>
          <div class="er-clear-confirm__actions">
            <button type="button" class="er-clear-confirm__cancel" @click="cancelClearAllConfirm">Keep them</button>
            <button type="button" class="er-clear-confirm__delete" @click="confirmClearAllDrafts">Yes, delete all</button>
          </div>
        </div>
      </div>
    </Teleport>

    <div class="er-dock" :class="{ 'er-dock--live': isRecording }">
      <Transition name="er-drafts">
        <button
            v-if="draftCount > 0"
            type="button"
            class="er-drafts"
            :class="{ 'er-drafts--open': panelOpen }"
            :aria-label="`${panelOpen ? 'Hide' : 'Show'} ${draftCount} audio ${draftCount === 1 ? 'draft' : 'drafts'}`"
            :aria-expanded="panelOpen"
            aria-controls="emergency-audio-drafts"
            @click="panelOpen = !panelOpen"
        >
          <span class="er-drafts__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18V5l10-2v13"/>
              <circle cx="6" cy="18" r="3"/><circle cx="16" cy="16" r="3"/>
            </svg>
          </span>
          <span class="er-drafts__copy">
            <strong>{{ draftCount > 99 ? '99+' : draftCount }} audio {{ draftCount === 1 ? 'draft' : 'drafts' }}</strong>
            <small>{{ panelOpen ? "Close saved audio" : "Review saved audio" }}</small>
          </span>
          <svg class="er-drafts__chevron" :class="{ 'er-drafts__chevron--open': panelOpen }"
               viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m6 8 4 4 4-4"/>
          </svg>
        </button>
      </Transition>

      <button
          type="button"
          class="er-btn"
          :class="{ 'er-btn--rec': isRecording, 'er-btn--saving': isSaving || isRequesting }"
          :disabled="isSaving || isRequesting"
          :aria-label="isRequesting ? 'Requesting microphone access' : isSaving ? 'Saving recording' : isRecording ? `Stop recording, ${formatDuration(elapsedSeconds)} elapsed` : 'Start quick audio recording'"
          :aria-pressed="isRecording"
          @click="toggleRecording"
      >
        <span class="er-btn__ring"></span>
        <span class="er-btn__ring er-btn__ring--2"></span>

        <span class="er-dot" :class="{ 'er-dot--live': isRecording }"></span>
        <span class="er-btn__label">{{ isRequesting ? "···" : isSaving ? "···" : isRecording ? "STOP" : "REC" }}</span>
      </button>
    </div>

    <audio
        ref="audioEl"
        class="er-sr-only"
        @timeupdate="onTimeUpdate"
        @ended="onEnded"
    ></audio>

  </div>
</template>

<style scoped>
.er-root {
  position: fixed;
  right: max(24px, env(safe-area-inset-right));
  bottom: max(24px, env(safe-area-inset-bottom));
  z-index: 1010;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.er-dock {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.er-btn {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 82px;
  height: 82px;
  border-radius: 999px;
  border: 0;
  background: #5B1928;
  color: #FFF5F4;
  cursor: pointer;
  box-shadow:
    0 4px 6px rgba(0,0,0,0.15),
    0 14px 32px rgba(91,25,40,0.4),
    0 0 0 1px rgba(255,255,255,0.10) inset;
  transition: transform 160ms ease, box-shadow 300ms ease, background 200ms ease;
}

.er-btn:hover:not(:disabled) {
  transform: scale(1.06);
  box-shadow:
    0 6px 10px rgba(0,0,0,0.18),
    0 20px 40px rgba(0,0,0,0.35),
    0 0 0 1px rgba(255,255,255,0.09) inset;
}

.er-btn:disabled { cursor: not-allowed; opacity: 0.6; }

.er-btn--rec {
  background: #9B2335;
  box-shadow:
    0 4px 6px rgba(0,0,0,0.1),
    0 14px 32px rgba(155,35,53,0.45),
    0 0 0 1px rgba(255,255,255,0.12) inset;
}

.er-btn--saving { background: #5B1928; }

.er-btn__ring {
  position: absolute;
  inset: -6px;
  border-radius: 999px;
  border: 2px solid rgba(155,35,53,0.4);
  opacity: 0;
  pointer-events: none;
}

.er-btn--rec .er-btn__ring {
  animation: ring-expand 2s ease-out infinite;
}

.er-btn--rec .er-btn__ring-2,
.er-btn--rec .er-btn__ring:nth-child(2) {
  animation-delay: 1s;
}

@keyframes ring-expand {
  0%   { transform: scale(1);    opacity: 0.7; }
  100% { transform: scale(1.65); opacity: 0; }
}

.er-dot {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: #F5D4CE;
  flex-shrink: 0;
  transition: background 200ms ease, border-radius 200ms ease;
}

.er-btn--rec .er-dot {
  background: #FFF5F4;
  border-radius: 4px;
  animation: dot-throb 1s ease-in-out infinite;
}

@keyframes dot-throb {
  0%, 100% { transform: scale(1);    opacity: 1; }
  50%       { transform: scale(1.3); opacity: 0.75; }
}

.er-btn__label {
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.16em;
  color: #FFF5F4;
  line-height: 1;
}

.er-btn:focus-visible,
.er-drafts:focus-visible,
.er-panel button:focus-visible,
.er-panel input:focus-visible,
.er-panel a:focus-visible,
.er-player__bar:focus-visible {
  outline: 3px solid #8ba27d;
  outline-offset: 3px;
}

.er-drafts {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 166px;
  height: 58px;
  padding: 7px 11px 7px 8px;
  border: 1px solid #cbd8c3;
  border-radius: 15px;
  background: rgba(255, 250, 247, 0.96);
  color: #485b38;
  cursor: pointer;
  text-align: left;
  box-shadow: 0 10px 28px rgba(30, 18, 18, 0.11);
  transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
}

.er-drafts__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 11px;
  background: #d4e5ca;
  color: #344228;
}

.er-drafts__icon svg {
  width: 18px;
  height: 18px;
}

.er-drafts__copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.er-drafts__copy strong {
  color: #26331e;
  font-size: 0.72rem;
  font-weight: 900;
  line-height: 1.2;
  white-space: nowrap;
}

.er-drafts__copy small {
  color: #7b6870;
  font-size: 0.58rem;
  font-weight: 650;
  line-height: 1.2;
  white-space: nowrap;
}

.er-drafts__chevron {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  color: #84917b;
  transition: transform 180ms ease;
}

.er-drafts__chevron--open {
  transform: rotate(180deg);
}

.er-drafts:hover,
.er-drafts--open {
  transform: translateY(-2px);
  border-color: #9bb18d;
  background: #f6f3ed;
}

.er-drafts-enter-active,
.er-drafts-leave-active { transition: opacity 160ms ease, transform 160ms ease; }
.er-drafts-enter-from,
.er-drafts-leave-to { opacity: 0; transform: translateX(8px); }

.er-panel {
  width: min(400px, calc(100vw - 48px));
  max-height: min(580px, calc(100vh - 104px));
  overflow-y: auto;
  border: 1px solid #d8cfc9;
  border-radius: 18px;
  background: #fffaf7;
  color: #260a12;
  box-shadow: 0 24px 54px rgba(43, 25, 25, 0.17), 0 4px 12px rgba(43, 25, 25, 0.06);
  display: flex;
  flex-direction: column;
}

.er-panel-enter-active { transition: opacity 180ms ease, transform 180ms ease; }
.er-panel-leave-active { transition: opacity 140ms ease, transform 140ms ease; }
.er-panel-enter-from,
.er-panel-leave-to { opacity: 0; transform: translateY(8px) scale(0.98); }

.er-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid #eadfda;
  flex-shrink: 0;
}

.er-panel__head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.er-panel__clear-all {
  padding: 6px 11px;
  border: 1px solid #e3d9d4;
  border-radius: 999px;
  background: #fff;
  color: #8f2031;
  font-size: 0.68rem;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease;
}

.er-panel__clear-all:hover { background: #fff0ee; border-color: #edcbc7; }

.er-panel__eyebrow {
  margin: 0 0 5px;
  font-size: 0.65rem;
  font-weight: 850;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #485b38;
}

.er-panel__title {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.3rem;
  font-weight: 700;
  color: #260a12;
}

.er-panel__intro {
  margin: 5px 0 0;
  color: #7b6870;
  font-size: 0.72rem;
  line-height: 1.4;
}

.er-panel__close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid #ded3ce;
  border-radius: 999px;
  background: #fff;
  color: #775e68;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 160ms ease, color 160ms ease;
}

.er-panel__close:hover { background: #fff0ee; color: #5b1928; border-color: #e2bdb8; }
.er-panel__close svg   { width: 13px; height: 13px; }

.er-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 12px;
}

.er-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border: 1px solid #e3d9d4;
  border-radius: 14px;
  padding: 13px;
  background: #fff;
  margin-bottom: 9px;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.er-item:hover {
  border-color: #cbd8c3;
  box-shadow: 0 4px 12px rgba(43,25,25,0.05);
}
.er-item:last-child { margin-bottom: 0; }

.er-item__top {
  display: flex;
  align-items: center;
  gap: 9px;
}

.er-item__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #eef4ea;
  color: #485b38;
  flex: 0 0 auto;
}

.er-item__icon svg {
  width: 15px;
  height: 15px;
}

.er-item__icon--live {
  background: #5b1928;
  color: #fff8f5;
}

.er-item__meta {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.er-item__del {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #9b898f;
  cursor: pointer;
  line-height: 1;
  flex-shrink: 0;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease, width 160ms ease;
}

.er-item__del svg { width: 14px; height: 14px; }

.er-item__del:hover { background: #fff0ee; color: #9b2335; border-color: #edcbc7; }

.er-item__del--confirm {
  width: auto;
  padding: 0 10px;
  gap: 4px;
  background: #9b2335;
  color: #fff;
  border-color: #9b2335;
}

.er-item__del--confirm:hover { background: #7e1c2b; color: #fff; }

.er-item__del-label {
  font-size: 0.68rem;
  font-weight: 800;
  white-space: nowrap;
}

.er-item__title {
  border: 0;
  border-bottom: 1px solid transparent;
  border-radius: 0;
  width: 100%;
  padding: 1px 0 2px;
  background: transparent;
  color: #260a12;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 800;
  outline: none;
  transition: border-color 160ms ease;
}

.er-item__title:hover { border-bottom-color: #ded3ce; }
.er-item__title:focus { border-bottom-color: #8ba27d; }
.er-item__title::placeholder { color: #aa999f; }

.er-item__time {
  margin-top: 1px;
  font-size: 0.65rem;
  font-weight: 600;
  color: #9b898f;
}

.er-player {
  display: flex;
  align-items: center;
  gap: 10px;
}

.er-player__play {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid #cbd8c3;
  border-radius: 999px;
  background: #eef4ea;
  color: #344228;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 140ms ease, transform 120ms ease;
}

.er-player__play:hover { background: #d4e5ca; transform: scale(1.04); }
.er-player__play svg { width: 13px; height: 13px; }

.er-player__bar {
  position: relative;
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #e8e1dc;
  cursor: pointer;
  overflow: hidden;
  touch-action: manipulation;
}

.er-player__fill {
  height: 100%;
  border-radius: inherit;
  background: #485b38;
  transition: width 100ms linear;
}

.er-player__time {
  font-size: 0.68rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #8a777f;
  white-space: nowrap;
  flex-shrink: 0;
}

.er-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.er-item__cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 9px 14px;
  border-radius: 10px;
  border: 1px solid #cbd8c3;
  background: #eef4ea;
  color: #344228;
  font-size: 0.82rem;
  font-weight: 800;
  text-decoration: none;
  transition: background 160ms ease, transform 120ms ease;
}

.er-item__cta svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  transition: transform 160ms ease;
}

.er-item__cta:hover { background: #d4e5ca; transform: translateY(-1px); }
.er-item__cta:hover svg { transform: translateX(2px); }

.er-error {
  margin: 0;
  padding: 10px 18px;
  font-size: 0.82rem;
  color: #8f2031;
  background: #fff0ee;
  border-bottom: 1px solid #edcbc7;
}

.er-warning {
  margin: 0;
  padding: 10px 18px;
  font-size: 0.78rem;
  font-weight: 600;
  color: #6c4c13;
  background: #fff8df;
  border-bottom: 1px solid #eadfb9;
}

.er-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 32px 24px 36px;
  text-align: center;
}

.er-empty__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 999px;
  background: #eef4ea;
  border: 1px solid #cbd8c3;
  color: #485b38;
}

.er-empty__icon svg {
  width: 21px;
  height: 21px;
}

.er-empty p {
  margin: 0;
  font-size: 0.85rem;
  color: #806c74;
  line-height: 1.55;
  max-width: 280px;
}

.er-clear-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1020;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(30, 8, 18, 0.45);
}

.er-clear-confirm {
  width: min(360px, 100%);
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 22px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.er-clear-confirm__eyebrow {
  margin: 0 0 4px;
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9b2335;
}

.er-clear-confirm__title {
  margin: 0 0 10px;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.15rem;
  font-weight: 700;
}

.er-clear-confirm__body {
  margin: 0 0 20px;
  font-size: 0.85rem;
  line-height: 1.5;
  color: #4a3238;
}

.er-clear-confirm__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.er-clear-confirm__cancel {
  padding: 9px 16px;
  border: 1px solid #ded3ce;
  border-radius: 999px;
  background: #fff;
  color: #1E0812;
  font-weight: 800;
  font-size: 0.82rem;
  cursor: pointer;
}

.er-clear-confirm__cancel:hover { background: #fff8f7; }

.er-clear-confirm__delete {
  padding: 9px 18px;
  border: 0;
  border-radius: 999px;
  background: #9b2335;
  color: #fff;
  font-weight: 800;
  font-size: 0.82rem;
  cursor: pointer;
}

.er-clear-confirm__delete:hover { background: #7e1c2b; }

@media (max-width: 1024px) {
  .er-root {
    right: max(18px, env(safe-area-inset-right));
    bottom: max(18px, env(safe-area-inset-bottom));
  }
}

@media (max-width: 640px) {
  .er-root {
    right: max(12px, env(safe-area-inset-right));
    bottom: max(12px, env(safe-area-inset-bottom));
  }

  .er-btn {
    width: 70px;
    height: 70px;
  }

  .er-drafts {
    min-width: 148px;
    height: 54px;
    padding-right: 9px;
  }

  .er-drafts__icon {
    width: 34px;
    height: 34px;
  }

  .er-drafts__copy strong {
    font-size: 0.68rem;
  }

  .er-drafts__copy small {
    font-size: 0.54rem;
  }

  .er-panel {
    position: fixed;
    right: 12px;
    bottom: calc(112px + env(safe-area-inset-bottom));
    width: calc(100vw - 24px);
    max-height: min(70vh, 580px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .er-btn,
  .er-drafts,
  .er-panel,
  .er-item {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
