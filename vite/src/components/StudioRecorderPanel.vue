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
  previewPlaying: {type: Boolean, default: false},
  recordingTrimOpen: {type: Boolean, default: false},
  recordingVolume: {type: [Number, String], default: 80},
  recordingSpeed: {type: [Number, String], default: 100},
  recordingNoiseReduction: {type: Boolean, default: true},
  trimStart: {type: [Number, String], default: 0},
  trimEnd: {type: [Number, String], default: 100},
});

const emit = defineEmits([
  "update:selectedSpeaker",
  "update:recordingTrimOpen",
  "update:recordingVolume",
  "update:recordingSpeed",
  "update:recordingNoiseReduction",
  "update:trimStart",
  "update:trimEnd",
  "toggle-record",
  "stop-recording",
  "replay",
  "restart",
  "open-layout",
  "audio-file-change",
  "select-voice",
  "select-speaker-slot",
]);

const fileInput = ref(null);
const videoFileInput = ref(null);
const isExtracting = ref(false);
const extractError = ref("");
const audioSettingsOpen = ref(false);
const isRecording = computed(() => props.quickRecordingThumbId !== null && props.quickRecordingThumbId !== undefined);

const activeAudioIndex = computed(() => {
  const index = props.selectedAudios.findIndex((audio) => String(audio.id) === String(props.selectedVoiceId));
  return index >= 0 ? index : (props.selectedAudios.length ? 0 : -1);
});
const activeAudio = computed(() => activeAudioIndex.value >= 0 ? props.selectedAudios[activeAudioIndex.value] : null);
const nextSpeaker = computed(() => nextSpeakerForAudios(props.selectedAudios));
const activeSpeaker = computed(() => activeAudio.value ? speakerForAudio(activeAudio.value, activeAudioIndex.value) : props.selectedSpeaker);
const targetHeadline = computed(() => props.quickRecordingThumbId != null ? `Recording Voice ${activeSpeaker.value}` : `Voice ${activeSpeaker.value} selected`);
const targetNote = computed(() => {
  if (props.quickRecordingThumbId != null) return props.recordingStatusLabel;
  if (activeAudio.value?.isDraft) return "Ready for a first recording";
  if (activeAudio.value) return "Ready to re-record";
  return props.recordingTargetLabel;
});

function speakerForAudio(audio, index) {
  return audio?.speaker || speakerForIndex(index);
}

function displayTitle(audio, index) {
  const speaker = speakerForAudio(audio, index);
  const title = String(audio?.title || "").trim();
  if (/^Voice\s+[A-Z](\b|$)/i.test(title)) {
    return title.replace(/^Voice\s+[A-Z]/i, `Voice ${speaker}`);
  }
  return title || `Voice ${speaker}`;
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

function openVideoFile() {
  extractError.value = "";
  videoFileInput.value?.click?.();
}

async function onVideoFileChange(event) {
  const file = event.target.files?.[0] ?? null;
  event.target.value = "";
  if (!file) return;
  isExtracting.value = true;
  extractError.value = "";
  try {
    const arrayBuffer = await file.arrayBuffer();
    const ctx = new AudioContext();
    const audioBuffer = await ctx.decodeAudioData(arrayBuffer);
    await ctx.close();
    const blob = encodeWav(audioBuffer);
    const baseName = file.name.replace(/\.[^.]+$/, "");
    emit("audio-file-change", new File([blob], `${baseName}.wav`, {type: "audio/wav"}));
  } catch {
    extractError.value = "Could not extract audio from this video.";
  } finally {
    isExtracting.value = false;
  }
}

function encodeWav(buffer) {
  const numCh = buffer.numberOfChannels;
  const sr = buffer.sampleRate;
  const bps = 2;
  const dataLen = buffer.length * numCh * bps;
  const wav = new ArrayBuffer(44 + dataLen);
  const v = new DataView(wav);
  const str = (off, s) => { for (let i = 0; i < s.length; i++) v.setUint8(off + i, s.charCodeAt(i)); };
  str(0, "RIFF"); v.setUint32(4, 36 + dataLen, true);
  str(8, "WAVE"); str(12, "fmt ");
  v.setUint32(16, 16, true); v.setUint16(20, 1, true);
  v.setUint16(22, numCh, true); v.setUint32(24, sr, true);
  v.setUint32(28, sr * numCh * bps, true); v.setUint16(32, numCh * bps, true);
  v.setUint16(34, 16, true); str(36, "data"); v.setUint32(40, dataLen, true);
  let off = 44;
  for (let i = 0; i < buffer.length; i++) {
    for (let ch = 0; ch < numCh; ch++) {
      const s = Math.max(-1, Math.min(1, buffer.getChannelData(ch)[i]));
      v.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
      off += 2;
    }
  }
  return new Blob([wav], {type: "audio/wav"});
}
</script>

<template>
  <aside class="studio-audio studio-recorder">
    <div class="rec-panel-title">
      <span>Studio audio</span>
      <strong>{{ recordingTargetLabel }}</strong>
    </div>

    <div class="rec-speakers" role="group" aria-label="Choose a voice">
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
        <small>{{ audio.isDraft ? 'to record' : 'recorded' }}</small>
      </button>

      <button
          v-if="selectedAudios.length < 4 && selectedThumb"
          type="button"
          class="rec-speaker--add"
          :class="{ active: !selectedVoiceId && selectedSpeaker === nextSpeaker }"
          :title="`Record as Voice ${nextSpeaker}`"
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
        <strong>{{ targetHeadline }}</strong>
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
          :title="isRecording ? `Stop recording Voice ${activeSpeaker}` : `Record Voice ${activeSpeaker}`"
          :aria-label="isRecording ? `Stop recording Voice ${activeSpeaker}` : `Record Voice ${activeSpeaker}`"
          @click="emit('toggle-record')"
      ></button>
    </div>
    <div class="mic-tm">{{ isRecording ? "REC" : "0:00" }}</div>
    <div class="mic-st" :class="{ live: isRecording }">
      {{ isRecording ? "Recording" : "Tap to record" }}
    </div>

    <div class="rec-quick-player">
      <template v-if="activeAudio">
        <span class="fiche-speaker" :class="speakerClass(activeAudio, activeAudioIndex)">
          {{ speakerForAudio(activeAudio, activeAudioIndex) }}
        </span>
        <div>
          <strong>{{ displayTitle(activeAudio, activeAudioIndex) }}</strong>
          <small>{{ recordingStatusLabel }}</small>
        </div>
        <span v-if="!activeAudio.isDraft" class="fiche-wave">
          <i></i><i></i><i></i><i></i><i></i><i></i>
        </span>
        <span v-else class="fiche-empty-dot"></span>
        <button
            type="button"
            :disabled="activeAudio.isDraft"
            :title="activeAudio.isDraft ? 'Record this voice first' : (previewPlaying ? 'Pause' : 'Lire')"
            :aria-label="activeAudio.isDraft ? 'Record this voice first' : (previewPlaying ? 'Mettre en pause' : 'Lire la voix sélectionnée')"
            @click="emit('replay')"
        >
          <span v-if="previewPlaying" class="pause-icon" aria-hidden="true"></span>
          <span v-else aria-hidden="true">▶</span>
        </button>
      </template>
      <template v-else>
        <span class="fiche-speaker" :class="String(selectedSpeaker).toLowerCase()">
          {{ selectedSpeaker }}
        </span>
        <div>
          <strong>Voice {{ selectedSpeaker }}</strong>
          <small>{{ recordingTargetLabel }}</small>
        </div>
        <span class="fiche-empty-dot"></span>
      </template>
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
        <small>{{ audio.isDraft ? "to record" : "audio" }}</small>
      </button>
    </div>

    <input ref="fileInput" class="rec-file-input" type="file" accept="audio/*" @change="onFileChange"/>
    <input ref="videoFileInput" class="rec-file-input" type="file" accept="video/*" @change="onVideoFileChange"/>

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
      <div class="rec-main-actions rec-main-actions--split">
        <button type="button" class="rec-import-btn" @click="openAudioFile">
          <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" width="13" height="13">
            <path d="M9 3a1 1 0 0 1 2 0v7.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L9 10.586V3Z"/>
            <path d="M3 14a1 1 0 0 1 2 0v1h10v-1a1 1 0 1 1 2 0v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1Z"/>
          </svg>
          Audio file
        </button>
        <button type="button" class="rec-import-btn" :disabled="isExtracting" @click="openVideoFile">
          <svg v-if="!isExtracting" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" width="13" height="13">
            <path d="M3 5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v1.382l2.553-1.276A1 1 0 0 1 18 6v8a1 1 0 0 1-1.447.894L14 13.618V15a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5Z"/>
          </svg>
          <span v-if="isExtracting" class="rec-extract-spinner" aria-hidden="true"></span>
          {{ isExtracting ? 'Extracting…' : 'From video' }}
        </button>
      </div>
      <p v-if="extractError" class="rec-extract-error">{{ extractError }}</p>
      <p class="rec-adv-section">Trim</p>
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
        {{ recordingTrimOpen ? 'Close trim' : 'Trim audio' }}
      </button>
      <div v-if="recordingTrimOpen" class="trim-panel trim-panel--inline">
        <div class="trim-panel__header">
          <small>{{ activeAudio?.title || "Selected audio" }}</small>
          <strong>{{ trimEnd - trimStart }}%</strong>
        </div>
        <div class="trim-wave trim-wave--compact">
          <i v-for="n in 8" :key="n"></i>
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
      </div>
      <p class="rec-adv-section">Playback</p>
      <label>
        <span>Volume</span>
        <input :value="recordingVolume" type="range" min="0" max="100" @input="emit('update:recordingVolume', Number($event.target.value))"/>
        <strong>{{ recordingVolume }}%</strong>
      </label>
      <label>
        <span>Speed</span>
        <input :value="recordingSpeed" type="range" min="50" max="150" @input="emit('update:recordingSpeed', Number($event.target.value))"/>
        <strong>{{ (Number(recordingSpeed) / 100).toFixed(1) }}x</strong>
      </label>
      <label class="switch-row">
        <span>Noise reduction</span>
        <input :checked="recordingNoiseReduction" type="checkbox" @change="emit('update:recordingNoiseReduction', $event.target.checked)"/>
        <strong>{{ recordingNoiseReduction ? "On" : "Off" }}</strong>
      </label>
    </div>
  </aside>
</template>
