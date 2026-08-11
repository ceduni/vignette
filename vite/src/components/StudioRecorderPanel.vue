<script setup>
import {computed, ref} from "vue";

const props = defineProps({
  selectedThumb: {type: Object, default: null},
  selectedAudios: {type: Array, default: () => []},
  selectedVoiceId: {type: [String, Number], default: null},
  selectedSceneNumber: {type: [Number, String], default: 0},
  selectedSpeaker: {type: String, default: "A"},
  quickRecordingThumbId: {type: [String, Number], default: null},
  recordingTargetLabel: {type: String, default: ""},
  recordingStatusLabel: {type: String, default: ""},
  canRecord: {type: Boolean, default: false},
  playbackQueueLength: {type: Number, default: 0},
  recordingTrimOpen: {type: Boolean, default: false},
  trimStart: {type: [Number, String], default: 0},
  trimEnd: {type: [Number, String], default: 100},
  trimPreviewPlaying: {type: Boolean, default: false},
});

const emit = defineEmits([
  "update:selectedSpeaker",
  "update:recordingTrimOpen",
  "update:trimStart",
  "update:trimEnd",
  "toggle-record",
  "stop-recording",
  "restart",
  "open-layout",
  "open-audio-drafts",
  "audio-file-change",
  "select-voice",
  "select-speaker-slot",
  "preview-trim",
  "reset-trim",
  "apply-trim",
]);

const fileInput = ref(null);
const audioSettingsOpen = ref(false);
const isRecording = computed(() => props.quickRecordingThumbId !== null && props.quickRecordingThumbId !== undefined);

const activeAudioIndex = computed(() => {
  const index = props.selectedAudios.findIndex((audio) => String(audio.id) === String(props.selectedVoiceId));
  return index >= 0 ? index : (props.selectedAudios.length ? 0 : -1);
});
const activeAudio = computed(() => activeAudioIndex.value >= 0 ? props.selectedAudios[activeAudioIndex.value] : null);
const nextSpeaker = computed(() => nextSpeakerForAudios(props.selectedAudios));
const activeSpeaker = computed(() => activeAudio.value ? speakerForAudio(activeAudio.value, activeAudioIndex.value) : props.selectedSpeaker);
const sceneLabel = computed(() => {
  return props.selectedThumb?.title || (props.selectedSceneNumber ? `Scene ${props.selectedSceneNumber}` : "Select a scene");
});
const targetNote = computed(() => {
  if (!props.selectedThumb) return "Choose a scene";
  if (props.quickRecordingThumbId != null) return "Recording now";
  if (activeAudio.value?.isDraft) return "No audio yet";
  if (activeAudio.value) return "Audio ready";
  return "No audio yet";
});
const micActionLabel = computed(() => {
  if (props.quickRecordingThumbId != null) return "Stop recording";
  if (activeAudio.value && !activeAudio.value.isDraft) return "Tap to replace";
  return "Tap to record";
});
const micLabel = computed(() => {
  const take = takeLabel(activeSpeaker.value);
  if (isRecording.value) return `Stop recording ${take}`;
  if (activeAudio.value && !activeAudio.value.isDraft) return `Replace ${take}`;
  return `Record ${take}`;
});
function takeLabel(speaker = "A") {
  return `Take ${String(speaker || "A").toUpperCase()}`;
}

function speakerForAudio(audio, index) {
  return audio?.speaker || speakerForIndex(index);
}

function displayTitle(audio, index) {
  const speaker = speakerForAudio(audio, index);
  const title = String(audio?.title || "").trim();
  if (/^Voice\s+[A-Z](\b|$)/i.test(title)) {
    return title.replace(/^Voice\s+[A-Z]/i, takeLabel(speaker));
  }
  return title || takeLabel(speaker);
}

function speakerClass(audio, index) {
  return String(speakerForAudio(audio, index)).toLowerCase();
}

function speakerForIndex(index) {
  const normalized = Math.max(0, Number(index) || 0);
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  return alphabet[normalized] || `A${normalized - alphabet.length + 1}`;
}

function nextSpeakerForAudios(audios) {
  const used = new Set((audios || []).map((audio, index) => speakerForAudio(audio, index)));
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  return alphabet.split("").find((speaker) => !used.has(speaker)) || speakerForIndex(used.size);
}

function openAudioFile() {
  fileInput.value?.click?.();
}

function onFileChange(event) {
  const file = event.target.files?.[0] ?? null;
  if (file) emit("audio-file-change", file);
  event.target.value = "";
}
</script>

<template>
  <aside class="studio-audio studio-recorder">
    <div class="rec-panel-title">
      <span>Studio audio</span>
      <strong>{{ sceneLabel }}</strong>
    </div>

    <div class="rec-speakers" role="group" aria-label="Choose a take">
      <button
          v-for="(audio, index) in selectedAudios"
          :key="audio.id"
          type="button"
          :class="{
            active: String(audio.id) === String(selectedVoiceId),
            'rec-speaker--draft': audio.isDraft
          }"
          @click="emit('select-voice', audio)"
      >
        <span>{{ speakerForAudio(audio, index) }}</span>
        <small v-if="audio.isDraft">empty</small>
      </button>

      <button
          v-if="selectedAudios.length < 4 && selectedThumb"
          type="button"
          class="rec-speaker--add"
          :class="{ active: !selectedVoiceId && selectedSpeaker === nextSpeaker }"
          :title="`Add ${takeLabel(nextSpeaker)}`"
          @click="emit('select-speaker-slot', nextSpeaker)"
      >
        <span>{{ nextSpeaker }}</span>
        <small>+ Add</small>
      </button>
    </div>

    <div
        class="rec-target-strip"
        :class="{ recording: isRecording, draft: activeAudio?.isDraft }"
    >
      <span class="fiche-speaker" :class="String(activeSpeaker).toLowerCase()">
        {{ activeSpeaker }}
      </span>
      <div>
        <strong>{{ takeLabel(activeSpeaker) }}</strong>
        <small>{{ targetNote }}</small>
      </div>
    </div>

    <div class="mic-rings rec-mic">
      <div class="mic-ring"></div>
      <div class="mic-ring"></div>
      <div class="mic-ring"></div>
      <button
          type="button"
          class="mic-btn"
          :class="{ rec: isRecording }"
          :disabled="!canRecord || !selectedThumb"
          :title="micLabel"
          :aria-label="micLabel"
          @click="emit('toggle-record')"
      ></button>
    </div>
    <div class="mic-tm">{{ isRecording ? "REC" : "0:00" }}</div>
    <div class="mic-st" :class="{ live: isRecording }">
      {{ micActionLabel }}
    </div>

    <div v-if="selectedAudios.length > 1" class="rec-voice-list">
      <button
          v-for="(audio, index) in selectedAudios"
          :key="audio.id"
          type="button"
          :class="{ active: String(audio.id) === String(activeAudio?.id) }"
          @click="emit('select-voice', audio)"
      >
        <span class="fiche-speaker" :class="speakerClass(audio, index)">
          {{ speakerForAudio(audio, index) }}
        </span>
        <strong>{{ displayTitle(audio, index) }}</strong>
        <small>{{ audio.isDraft ? "empty" : "audio" }}</small>
      </button>
    </div>

    <input ref="fileInput" class="rec-file-input" type="file" accept="audio/*" @change="onFileChange"/>

    <button type="button" class="rec-collapse-toggle" @click="audioSettingsOpen = !audioSettingsOpen">
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true" width="13" height="13">
        <path d="M3 5h14M3 10h14M3 15h14"/>
        <circle cx="7" cy="5" r="1.5" fill="currentColor" stroke="none"/>
        <circle cx="13" cy="10" r="1.5" fill="currentColor" stroke="none"/>
        <circle cx="9" cy="15" r="1.5" fill="currentColor" stroke="none"/>
      </svg>
      Advanced settings
      <svg class="rec-collapse-chevron" :class="{ open: audioSettingsOpen }" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="11" height="11">
        <path d="M4 6l4 4 4-4"/>
      </svg>
    </button>

    <div v-if="audioSettingsOpen" class="side-settings side-settings--collapse">
      <p class="rec-adv-section">Import audio</p>
      <div class="rec-main-actions">
        <button type="button" class="rec-import-btn" :disabled="!canRecord || !selectedThumb" @click="openAudioFile">
          <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" width="13" height="13">
            <path d="M9 3a1 1 0 0 1 2 0v7.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L9 10.586V3Z"/>
            <path d="M3 14a1 1 0 0 1 2 0v1h10v-1a1 1 0 1 1 2 0v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1Z"/>
          </svg>
          Audio file
        </button>
        <button type="button" class="rec-import-btn" :disabled="!canRecord || !selectedThumb" @click="emit('open-audio-drafts')">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" width="13" height="13">
            <path d="M4 5h12M4 10h12M4 15h8"/>
          </svg>
          Audio drafts
        </button>
      </div>

      <p class="rec-adv-section">Cut</p>
      <button
          type="button"
          class="rec-import-btn rec-trim-btn"
          :disabled="!activeAudio || activeAudio.isDraft"
          @click="emit('update:recordingTrimOpen', !recordingTrimOpen)"
      >
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="13" height="13" aria-hidden="true">
          <path d="M3 6h14M3 10h14M3 14h14"/>
          <rect x="6" y="4" width="3" height="4" rx="1" fill="currentColor" stroke="none"/>
          <rect x="11" y="8" width="3" height="4" rx="1" fill="currentColor" stroke="none"/>
        </svg>
        {{ recordingTrimOpen ? "Close cut" : "Cut audio" }}
      </button>

      <div v-if="recordingTrimOpen" class="trim-panel trim-panel--inline">
        <div class="trim-panel__header">
          <small>{{ activeAudio ? displayTitle(activeAudio, activeAudioIndex) : takeLabel(activeSpeaker) }}</small>
          <strong>{{ Number(trimEnd) - Number(trimStart) }}%</strong>
        </div>
        <label>
          <span>Start</span>
          <input :value="trimStart" type="range" min="0" max="95" @input="emit('update:trimStart', Number($event.target.value))"/>
          <strong>{{ trimStart }}%</strong>
        </label>
        <label>
          <span>End</span>
          <input :value="trimEnd" type="range" min="5" max="100" @input="emit('update:trimEnd', Number($event.target.value))"/>
          <strong>{{ trimEnd }}%</strong>
        </label>
        <div class="trim-panel__actions">
          <button type="button" :disabled="!activeAudio || activeAudio.isDraft" @click="emit('preview-trim')">{{ trimPreviewPlaying ? "Stop" : "Preview" }}</button>
          <button type="button" @click="emit('reset-trim')">Reset</button>
          <button type="button" class="primary" :disabled="!activeAudio || activeAudio.isDraft" @click="emit('apply-trim')">Apply</button>
        </div>
      </div>
    </div>
  </aside>
</template>
