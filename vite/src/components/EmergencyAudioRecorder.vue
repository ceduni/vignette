<script setup>
import {computed, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";

const DRAFT_AUDIO_KEY = "vignette:unclaimed-draft-audios";

const panelOpen = ref(false);
const drafts = ref([]);
const status = ref("idle");
const error = ref("");

let recorder = null;
let stream = null;
let chunks = [];

const draftCount = computed(() => drafts.value.length);
const isRecording = computed(() => status.value === "recording");
const isSaving = computed(() => status.value === "saving");

function loadDrafts() {
  try {
    drafts.value = JSON.parse(localStorage.getItem(DRAFT_AUDIO_KEY) || "[]");
  } catch {
    drafts.value = [];
  }
}

function saveDrafts() {
  localStorage.setItem(DRAFT_AUDIO_KEY, JSON.stringify(drafts.value));
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
  if (recorder && stream) return;
  stream = await navigator.mediaDevices.getUserMedia({audio: true});
  recorder = new MediaRecorder(stream);
  recorder.ondataavailable = (e) => { if (e.data?.size > 0) chunks.push(e.data); };
  recorder.onstop = async () => {
    const blob = new Blob(chunks, {type: recorder.mimeType || "audio/webm"});
    chunks = [];
    status.value = "saving";
    try {
      const dataUrl = await blobToDataUrl(blob);
      drafts.value = [{
        id: `draft-${Date.now()}`,
        title: `Draft ${drafts.value.length + 1}`,
        createdAt: new Date().toISOString(),
        mimeType: blob.type || "audio/webm",
        dataUrl,
      }, ...drafts.value];
      saveDrafts();
      panelOpen.value = true;
    } catch (e) {
      error.value = e.message || "Could not save audio draft.";
    } finally {
      status.value = "idle";
    }
  };
}

async function toggleRecording() {
  error.value = "";
  if (isRecording.value) { recorder?.stop(); return; }
  try {
    await ensureRecorder();
    chunks = [];
    recorder.start();
    status.value = "recording";
  } catch (e) {
    error.value = e.message || "Could not start recording.";
    panelOpen.value = true;
  }
}

function removeDraft(draft) {
  drafts.value = drafts.value.filter((d) => d.id !== draft.id);
  saveDrafts();
}

function updateDraftTitle(draft, title) {
  drafts.value = drafts.value.map((d) => d.id === draft.id ? {...d, title} : d);
  saveDrafts();
}

function draftScenarioPath(draft) {
  return `/scenarios/emergency-${draft.id}?draftAudio=${draft.id}`;
}

onMounted(loadDrafts);
</script>

<template>
  <div class="er-root">

    <Transition name="er-badge">
      <button
          v-if="draftCount > 0 || panelOpen"
          type="button"
          class="er-badge"
          :class="{ 'er-badge--open': panelOpen }"
          :aria-label="`${draftCount} audio drafts`"
          @click="panelOpen = !panelOpen"
      >
        <span class="er-badge__num">{{ draftCount > 99 ? '99+' : draftCount }}</span>
        <span class="er-badge__lbl">{{ draftCount === 1 ? 'draft' : 'drafts' }}</span>
        <svg class="er-badge__arrow" :class="{ 'er-badge__arrow--open': panelOpen }"
             viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="m18 15-6-6-6 6"/>
        </svg>
      </button>
    </Transition>

    <button
        type="button"
        class="er-btn"
        :class="{ 'er-btn--rec': isRecording, 'er-btn--saving': isSaving }"
        :disabled="isSaving"
        :aria-label="isRecording ? 'Stop recording' : 'Start recording'"
        @click="toggleRecording"
    >
      <span class="er-btn__ring"></span>
      <span class="er-btn__ring er-btn__ring--2"></span>

      <span class="er-dot" :class="{ 'er-dot--live': isRecording }"></span>
      <span class="er-btn__label">{{ isSaving ? "···" : isRecording ? "STOP" : "REC" }}</span>
    </button>

    <Transition name="er-panel">
      <section v-if="panelOpen" class="er-panel" aria-label="Emergency audio drafts">

        <div class="er-panel__head">
          <div>
            <p class="er-panel__eyebrow">Emergency audio</p>
            <h2 class="er-panel__title">
              {{ draftCount }} {{ draftCount === 1 ? 'draft' : 'drafts' }}
            </h2>
          </div>
          <button type="button" class="er-panel__close" aria-label="Close" @click="panelOpen = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 6 6 18M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <p v-if="error" class="er-error">{{ error }}</p>

        <div v-if="drafts.length" class="er-list">
          <article v-for="draft in drafts" :key="draft.id" class="er-item">
            <div class="er-item__top">
              <div class="er-item__wave" aria-hidden="true">
                <span v-for="i in 12" :key="i"></span>
              </div>
              <button type="button" class="er-item__del" title="Delete" @click="removeDraft(draft)">×</button>
            </div>

            <input
                :value="draft.title"
                class="er-item__title"
                aria-label="Draft title"
                @input="updateDraftTitle(draft, $event.target.value)"
            />

            <audio :src="draft.dataUrl" controls class="er-item__audio"></audio>

            <RouterLink :to="draftScenarioPath(draft)" class="er-item__cta">
              Open in studio →
            </RouterLink>
          </article>
        </div>

        <div v-else class="er-empty">
          <span class="er-empty__dot"></span>
          <p>Hit <strong>REC</strong> to capture a voice note — no scenario needed.</p>
        </div>

      </section>
    </Transition>

  </div>
</template>

<style scoped>
.er-root {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 120;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
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

.er-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 16px 6px 14px;
  border: 0;
  border-radius: 999px;
  background: #5B1928;
  cursor: pointer;
  box-shadow:
    0 4px 14px rgba(91,25,40,0.35),
    0 0 0 1px rgba(255,255,255,0.08) inset;
  transition: transform 160ms ease, box-shadow 160ms ease;
  white-space: nowrap;
}

.er-badge:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(91,25,40,0.45), 0 0 0 1px rgba(255,255,255,0.10) inset;
}

.er-badge--open { box-shadow: 0 4px 14px rgba(91,25,40,0.35), 0 0 0 1.5px #F5D4CE; }

.er-badge__num {
  font-size: 1.7rem;
  font-weight: 950;
  color: #F5D4CE;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  min-width: 1.2ch;
}

.er-badge__lbl {
  font-size: 0.65rem;
  font-weight: 800;
  color: rgba(255,245,244,0.45);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  line-height: 1;
}

.er-badge__arrow {
  width: 14px;
  height: 14px;
  color: rgba(255,245,244,0.35);
  transition: transform 220ms ease, color 160ms ease;
  flex-shrink: 0;
}

.er-badge__arrow--open { transform: rotate(180deg); color: rgba(255,245,244,0.75); }
.er-badge:hover .er-badge__arrow { color: rgba(255,245,244,0.65); }

.er-badge-enter-active { transition: opacity 200ms ease, transform 200ms ease; }
.er-badge-leave-active { transition: opacity 150ms ease, transform 150ms ease; }
.er-badge-enter-from   { opacity: 0; transform: translateY(6px) scale(0.95); }
.er-badge-leave-to     { opacity: 0; transform: translateY(4px) scale(0.95); }

.er-panel {
  width: min(380px, calc(100vw - 40px));
  max-height: min(540px, calc(100vh - 110px));
  overflow-y: auto;
  border: 1.5px solid #7A2A3C;
  border-radius: 20px;
  background: #5B1928;
  box-shadow: 0 24px 60px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.06) inset;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.er-panel-enter-active { transition: opacity 180ms ease, transform 180ms ease; }
.er-panel-leave-active { transition: opacity 140ms ease, transform 140ms ease; }
.er-panel-enter-from  { opacity: 0; transform: translateY(10px) scale(0.97); }
.er-panel-leave-to    { opacity: 0; transform: translateY(8px)  scale(0.97); }

.er-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid rgba(255,255,255,0.07);
}

.er-panel__eyebrow {
  margin: 0 0 3px;
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #F5D4CE;
}

.er-panel__title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 950;
  color: #FFF5F4;
}

.er-panel__close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 999px;
  background: transparent;
  color: rgba(255,248,240,0.5);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 160ms ease, color 160ms ease;
}

.er-panel__close:hover { background: rgba(255,255,255,0.08); color: #FFF0EE; }
.er-panel__close svg   { width: 13px; height: 13px; }

.er-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 10px;
}

.er-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid rgba(255,255,255,0.07);
  border-radius: 14px;
  padding: 12px;
  background: rgba(255,255,255,0.03);
  margin-bottom: 8px;
  transition: background 160ms ease;
}

.er-item:hover { background: rgba(255,255,255,0.05); }
.er-item:last-child { margin-bottom: 0; }

.er-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.er-item__wave {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 20px;
}

.er-item__wave span {
  width: 3px;
  border-radius: 2px;
  background: #F5D4CE;
  opacity: 0.5;
  animation: wave-idle 1.4s ease-in-out infinite;
}

.er-item__wave span:nth-child(1)  { height: 6px;  animation-delay: 0s; }
.er-item__wave span:nth-child(2)  { height: 12px; animation-delay: 0.1s; }
.er-item__wave span:nth-child(3)  { height: 18px; animation-delay: 0.2s; }
.er-item__wave span:nth-child(4)  { height: 10px; animation-delay: 0.3s; }
.er-item__wave span:nth-child(5)  { height: 16px; animation-delay: 0.15s; }
.er-item__wave span:nth-child(6)  { height: 8px;  animation-delay: 0.25s; }
.er-item__wave span:nth-child(7)  { height: 14px; animation-delay: 0.35s; }
.er-item__wave span:nth-child(8)  { height: 20px; animation-delay: 0.05s; }
.er-item__wave span:nth-child(9)  { height: 10px; animation-delay: 0.4s; }
.er-item__wave span:nth-child(10) { height: 6px;  animation-delay: 0.2s; }
.er-item__wave span:nth-child(11) { height: 14px; animation-delay: 0.1s; }
.er-item__wave span:nth-child(12) { height: 8px;  animation-delay: 0.3s; }

@keyframes wave-idle {
  0%, 100% { opacity: 0.35; transform: scaleY(0.7); }
  50%       { opacity: 0.65; transform: scaleY(1); }
}

.er-item__del {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  background: transparent;
  color: rgba(255,248,240,0.4);
  cursor: pointer;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  flex-shrink: 0;
  transition: background 140ms ease, color 140ms ease;
}

.er-item__del:hover { background: rgba(220,50,50,0.2); color: #ff6b6b; border-color: rgba(220,50,50,0.3); }

.er-item__title {
  border: 0;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  border-radius: 0;
  padding: 4px 0;
  background: transparent;
  color: #FFF5F4;
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  outline: none;
  transition: border-color 160ms ease;
}

.er-item__title:focus { border-bottom-color: #F5D4CE; }
.er-item__title::placeholder { color: rgba(255,245,244,0.3); }

.er-item__audio {
  width: 100%;
  border-radius: 8px;
  height: 32px;
  filter: invert(1) hue-rotate(180deg) brightness(0.8);
}

.er-item__cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 14px;
  border-radius: 10px;
  background: #F5D4CE;
  color: #5B1928;
  font-size: 0.82rem;
  font-weight: 800;
  text-decoration: none;
  transition: background 160ms ease, transform 120ms ease;
}

.er-item__cta:hover { background: #EBC3BB; transform: translateY(-1px); }

.er-error {
  margin: 0;
  padding: 10px 18px;
  font-size: 0.82rem;
  color: #ff6b6b;
}

.er-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px 20px;
  text-align: center;
}

.er-empty__dot {
  display: block;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: rgba(245,212,206,0.15);
  border: 1.5px solid rgba(245,212,206,0.3);
  position: relative;
}

.er-empty__dot::after {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 999px;
  background: #F5D4CE;
  opacity: 0.7;
}

.er-empty p {
  margin: 0;
  font-size: 0.85rem;
  color: rgba(255,245,244,0.45);
  line-height: 1.55;
  max-width: 240px;
}

.er-empty strong { color: rgba(255,245,244,0.8); }

@media (max-width: 1024px) {
  .er-btn  { width: 76px; height: 76px; }
  .er-root { right: 20px; bottom: calc(20px + env(safe-area-inset-bottom)); }
}

@media (max-width: 640px) {
  .er-root         { right: 14px; bottom: calc(14px + env(safe-area-inset-bottom)); gap: 8px; }
  .er-btn          { width: 70px; height: 70px; }
  .er-badge        { padding: 5px 14px 5px 12px; gap: 8px; }
  .er-badge__num   { font-size: 1.5rem; }
  .er-badge__lbl   { font-size: 0.6rem; }
  .er-panel        { width: min(360px, calc(100vw - 28px)); }
}
</style>
