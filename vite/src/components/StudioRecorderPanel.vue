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
  recordingComments: {type: Boolean, default: true},
  recordingTranscription: {type: Boolean, default: true},
  recordingDownload: {type: Boolean, default: false},
  trimStart: {type: [Number, String], default: 0},
  trimEnd: {type: [Number, String], default: 100},
});

const emit = defineEmits([
  "update:selectedSpeaker",
  "update:recordingTrimOpen",
  "update:recordingVolume",
  "update:recordingSpeed",
  "update:recordingNoiseReduction",
  "update:recordingComments",
  "update:recordingTranscription",
  "update:recordingDownload",
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
const isRecording = computed(() => props.quickRecordingThumbId !== null && props.quickRecordingThumbId !== undefined);

const activeAudio = computed(() =>
    props.selectedAudios.find((audio) => String(audio.id) === String(props.selectedVoiceId)) ??
    props.selectedAudios[0] ??
    null
);
const nextSpeaker = computed(() => speakerForIndex(props.selectedAudios.length));

function speakerForAudio(audio, index) {
  return audio?.speaker || speakerForIndex(index);
}

function speakerClass(audio, index) {
  return String(speakerForAudio(audio, index)).toLowerCase();
}

function speakerForIndex(index) {
  const normalized = Math.max(0, Number(index) || 0);
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  return alphabet[normalized] || `A${normalized - alphabet.length + 1}`;
}

function openAudioFile() {
  fileInput.value?.click?.();
}

function onFileChange(event) {
  const file = event.target.files?.[0] ?? null;
  if (file) {
    emit("audio-file-change", file);
  }
  event.target.value = "";
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

    <div class="mic-rings rec-mic">
      <div class="mic-ring"></div>
      <div class="mic-ring"></div>
      <div class="mic-ring"></div>
      <button
          type="button"
          class="mic-btn"
          :class="{ rec: isRecording }"
          :disabled="!canRecord || !selectedThumb"
          :aria-label="isRecording ? 'Stop recording' : 'Record'"
          @click="emit('toggle-record')"
      ></button>
    </div>
    <div class="mic-tm">{{ isRecording ? "REC" : "0:00" }}</div>
    <div class="mic-st" :class="{ live: isRecording }">
      {{ isRecording ? "Recording" : "Tap to record" }}
    </div>

    <div class="rec-quick-player">
      <template v-if="activeAudio">
        <span class="fiche-speaker" :class="speakerClass(activeAudio, 0)">
          {{ speakerForAudio(activeAudio, 0) }}
        </span>
        <div>
          <strong>{{ activeAudio.title || "Audio sélectionné" }}</strong>
          <small>{{ recordingStatusLabel }}</small>
        </div>
        <span class="fiche-wave">
          <i></i><i></i><i></i><i></i><i></i><i></i>
        </span>
        <button
            type="button"
            :title="previewPlaying ? 'Pause' : 'Lire'"
            :aria-label="previewPlaying ? 'Mettre en pause' : 'Lire la voix sélectionnée'"
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
        <strong>{{ audio.title || `Voice ${speakerForAudio(audio, index)}` }}</strong>
        <small>{{ audio.isDraft ? "to record" : "audio" }}</small>
      </button>
    </div>

    <input
        ref="fileInput"
        class="rec-file-input"
        type="file"
        accept="audio/*"
        @change="onFileChange"
    />

    <div class="rec-main-actions">
      <button type="button" @click="openAudioFile">
        Importer un fichier audio
      </button>
    </div>

    <div class="rec-tool-grid">
      <button type="button" :disabled="!isRecording" @click="emit('stop-recording')">Stop</button>
      <button type="button" :disabled="!selectedAudios.length && !playbackQueueLength" @click="emit('replay')">
        {{ previewPlaying ? "Pause" : "Replay" }}
      </button>
      <button
          type="button"
          :disabled="!selectedAudios.length"
          @click="emit('update:recordingTrimOpen', !recordingTrimOpen)"
      >Trim</button>
      <button type="button" :disabled="!canRecord || !selectedThumb" @click="emit('restart')">Restart</button>
    </div>

    <div v-if="recordingTrimOpen" class="trim-panel">
      <div class="side-settings__title">Coupe rapide</div>
      <div class="trim-wave"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
      <label>
        <span>Début</span>
        <input :value="trimStart" type="range" min="0" max="95" @input="emit('update:trimStart', Number($event.target.value))"/>
        <strong>{{ trimStart }}%</strong>
      </label>
      <label>
        <span>Fin</span>
        <input :value="trimEnd" type="range" min="5" max="100" @input="emit('update:trimEnd', Number($event.target.value))"/>
        <strong>{{ trimEnd }}%</strong>
      </label>
    </div>

    <div class="side-settings">
      <div class="side-settings__title">Audio settings</div>
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

    <div class="side-settings">
      <div class="side-settings__title">Translation</div>
      <input class="side-note-input" value="Comment vas-tu ?" readonly/>
      <div class="side-settings__title">Note culturelle</div>
      <input class="side-note-input" value="Contexte ou usage particulier..." readonly/>
    </div>

    <div class="side-settings">
      <div class="side-settings__title">Visibility</div>
      <label class="switch-row">
        <span>Commentaires</span>
        <input :checked="recordingComments" type="checkbox" @change="emit('update:recordingComments', $event.target.checked)"/>
        <strong>{{ recordingComments ? "On" : "Off" }}</strong>
      </label>
      <label class="switch-row">
        <span>Transcription</span>
        <input :checked="recordingTranscription" type="checkbox" @change="emit('update:recordingTranscription', $event.target.checked)"/>
        <strong>{{ recordingTranscription ? "On" : "Off" }}</strong>
      </label>
      <label class="switch-row">
        <span>Téléchargement</span>
        <input :checked="recordingDownload" type="checkbox" @change="emit('update:recordingDownload', $event.target.checked)"/>
        <strong>{{ recordingDownload ? "On" : "Off" }}</strong>
      </label>
      <button type="button" class="visibility-choice"><span></span><strong>Private</strong><small>Visible only to me</small></button>
      <button type="button" class="visibility-choice"><span></span><strong>Group</strong><small>My class or circle</small></button>
      <button type="button" class="visibility-choice active"><span></span><strong>Community</strong><small>The whole platform</small></button>
    </div>
  </aside>
</template>
