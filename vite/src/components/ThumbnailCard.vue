<script setup>
import {computed} from "vue";
import {buildApiUrl} from "../api/rest";
import BaseBadge from "./ui/BaseBadge.vue";

const props = defineProps({
  thumb:         {type: Object,  required: true},
  audios:        {type: Array,   default: () => []},
  selected:      {type: Boolean, default: false},
  highlighted:   {type: Boolean, default: false},
  quickRecording:{type: Boolean, default: false},
  canRecord:     {type: Boolean, default: false},
  canDelete:     {type: Boolean, default: false},
  canResize:     {type: Boolean, default: false},
  canReorder:    {type: Boolean, default: false},
  activeAudioId: {type: [String, Number], default: null},
  playerState:   {type: String,  default: "idle"},
  colSpan:       {type: Number,  default: 1},
  rowSpan:       {type: Number,  default: 1},
});

const emit = defineEmits([
  "select", "play", "quick-record",
  "delete", "reorder", "resize-start",
  "drag-start", "drag-over", "drag-leave", "drag-end", "tile-drop",
  "pointer-reorder-start",
]);

function thumbnailContentUrl(thumb) {
  if (thumb?.previewUrl) return thumb.previewUrl;
  if (!thumb?.id) return "";
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

const markers = computed(() =>
  props.audios
    .filter(a =>
      a?.markerX !== null && a?.markerX !== undefined &&
      a?.markerY !== null && a?.markerY !== undefined &&
      a?.markerX !== ""  && a?.markerY !== ""
    )
    .map(a => {
      const x = Number(a.markerX);
      const y = Number(a.markerY);
      return {
        ...a,
        _x: Number.isFinite(x) ? Math.max(0, Math.min(100, x)) : null,
        _y: Number.isFinite(y) ? Math.max(0, Math.min(100, y)) : null,
      };
    })
    .filter(a => a._x !== null && a._y !== null)
);

function markerStyle(marker) {
  return { left: `${marker._x}%`, top: `${marker._y}%` };
}

function onPlayClick() {
  emit("select", props.thumb);
  emit("play",   props.thumb);
}

function onQuickRecordClick() {
  emit("select",       props.thumb);
  emit("quick-record", props.thumb);
}
</script>

<template>
  <article
    class="card thumb-card storyboard-tile"
    :class="{
      selected,
      'thumb-card--highlighted': highlighted,
      'storyboard-tile--recording': quickRecording,
    }"
    :data-thumbnail-id="thumb.id"
    draggable="true"
    @click="emit('select', thumb)"
    @dragstart="emit('drag-start', thumb, $event)"
    @dragover="emit('drag-over',  thumb, $event)"
    @dragleave="emit('drag-leave', thumb, $event)"
    @dragend="emit('drag-end')"
    @drop="emit('tile-drop', thumb, $event)"
  >
    <div class="storyboard-tile__stage">
      <img
        :src="thumbnailContentUrl(thumb)"
        :alt="thumb.title || `Thumbnail ${thumb.id}`"
        class="storyboard-tile__image"
      />

      <!-- Badge selected / index -->
      <div class="storyboard-tile__overlay">
        <BaseBadge v-if="selected" variant="success">Selected</BaseBadge>
        <BaseBadge v-else variant="neutral">{{ thumb.idx ?? thumb.id }}</BaseBadge>
      </div>

      <!-- Audio markers -->
      <button
        v-for="marker in markers"
        :key="marker.id"
        type="button"
        class="marker-dot storyboard-tile__marker"
        :style="markerStyle(marker)"
        :title="marker.markerLabel || marker.title || `Audio #${marker.id}`"
        @click.stop="emit('select', thumb)"
      >
        <span class="marker-dot__pulse"></span>
        <span class="marker-dot__core"></span>
      </button>

      <!-- Play button — always visible -->
      <button
        type="button"
        class="storyboard-tile__play"
        :title="`Play from ${thumb.title || `thumbnail ${thumb.idx ?? thumb.id}`}`"
        aria-label="Play thumbnail audio"
        @click.stop="onPlayClick"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M8 6.5v11l9-5.5-9-5.5z"/>
        </svg>
      </button>

      <!-- Quick record — only for owners -->
      <button
        v-if="canRecord"
        type="button"
        class="storyboard-tile__quick-record"
        :class="{ 'storyboard-tile__quick-record--active': quickRecording }"
        :title="quickRecording ? 'Stop recording' : 'Quick record'"
        aria-label="Quick record audio"
        @click.stop="onQuickRecordClick"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 15.5a3.5 3.5 0 0 0 3.5-3.5V7a3.5 3.5 0 1 0-7 0v5a3.5 3.5 0 0 0 3.5 3.5Z"/>
          <path d="M6 11.5a1 1 0 1 1 2 0 4 4 0 1 0 8 0 1 1 0 1 1 2 0 6 6 0 0 1-5 5.91V20h2a1 1 0 1 1 0 2H9a1 1 0 1 1 0-2h2v-2.59A6 6 0 0 1 6 11.5Z"/>
        </svg>
      </button>

      <!-- Reorder handle — only for owners -->
      <button
        v-if="canReorder"
        type="button"
        class="storyboard-tile__reorder-handle"
        title="Drag to reorder"
        aria-label="Reorder scene"
        @click.stop
        @pointerdown.stop="emit('pointer-reorder-start', thumb, $event)"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
          <line x1="3" y1="9"  x2="21" y2="9"/>
          <line x1="3" y1="15" x2="21" y2="15"/>
        </svg>
      </button>

      <!-- Delete button — only for owners -->
      <button
        v-if="canDelete"
        type="button"
        class="storyboard-tile__delete"
        title="Delete scene"
        aria-label="Delete scene"
        @click.stop="emit('delete', thumb)"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
             stroke-linecap="round" stroke-linejoin="round" width="12" height="12">
          <path d="M18 6 6 18M6 6l12 12"/>
        </svg>
      </button>

      <!-- Resize handles — only for owners -->
      <template v-if="canResize">
        <button
          type="button"
          class="storyboard-tile__resize storyboard-tile__resize--right"
          title="Resize width"
          @click.stop
          @mousedown.stop="emit('resize-start', { thumb, direction: 'right', event: $event })"
        ></button>
        <button
          type="button"
          class="storyboard-tile__resize storyboard-tile__resize--bottom"
          title="Resize height"
          @click.stop
          @mousedown.stop="emit('resize-start', { thumb, direction: 'bottom', event: $event })"
        ></button>
        <button
          type="button"
          class="storyboard-tile__resize storyboard-tile__resize--corner"
          title="Resize"
          @click.stop
          @mousedown.stop="emit('resize-start', { thumb, direction: 'corner', event: $event })"
        ></button>
      </template>
    </div>
  </article>
</template>