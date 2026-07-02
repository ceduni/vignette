<script setup>
import {computed} from "vue";
import {buildApiUrl} from "../api/rest";
import BaseBadge from "./ui/BaseBadge.vue";

const props = defineProps({
  thumb: {type: Object, required: true},
  audios: {type: Array, default: () => []},
  selected: {type: Boolean, default: false},
  highlighted: {type: Boolean, default: false},
  quickRecording: {type: Boolean, default: false},
  canDelete: {type: Boolean, default: false},
});

const emit = defineEmits(["select", "play", "quick-record", "delete"]);

function thumbnailContentUrl(thumb) {
  if (!thumb?.id) return "";
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

const markers = computed(() => {
  return props.audios
      .filter((audio) =>
          audio?.markerX !== null &&
          audio?.markerX !== undefined &&
          audio?.markerY !== null &&
          audio?.markerY !== undefined &&
          audio?.markerX !== "" &&
          audio?.markerY !== ""
      )
      .map((audio) => {
        const x = Number(audio.markerX);
        const y = Number(audio.markerY);

        return {
          ...audio,
          _x: Number.isFinite(x) ? Math.max(0, Math.min(100, x)) : null,
          _y: Number.isFinite(y) ? Math.max(0, Math.min(100, y)) : null,
        };
      })
      .filter((audio) => audio._x !== null && audio._y !== null);
});

function markerStyle(marker) {
  return {
    left: `${marker._x}%`,
    top: `${marker._y}%`,
  };
}

function onPlayClick() {
  emit("select", props.thumb);
  emit("play", props.thumb);
}

function onQuickRecordClick() {
  emit("select", props.thumb);
  emit("quick-record", props.thumb);
}
</script>

<template>
  <article
      class="card thumb-card storyboard-tile"
      :class="{ selected, 'thumb-card--highlighted': highlighted }"
      :data-thumbnail-id="thumb.id"
      @click="emit('select', thumb)"
  >
    <div class="storyboard-tile__stage">
      <img
          :src="thumbnailContentUrl(thumb)"
          :alt="thumb.title || `Thumbnail ${thumb.id}`"
          class="storyboard-tile__image"
      />

      <div class="storyboard-tile__overlay">
        <BaseBadge v-if="selected" variant="success">Selected</BaseBadge>
        <BaseBadge v-else variant="neutral">
          {{ thumb.idx ?? thumb.id }}
        </BaseBadge>
      </div>

      <button
          v-for="marker in markers"
          :key="marker.id"
          type="button"
          class="marker-dot storyboard-tile__marker"
          :style="markerStyle(marker)"
          :title="marker.markerLabel || marker.title || `Audio #${marker.id}`"
          @click.stop="emit('select', thumb, marker)"
      >
        <span class="marker-dot__pulse"></span>
        <span class="marker-dot__core"></span>
      </button>

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

      <button
          type="button"
          class="storyboard-tile__quick-record"
          :class="{ 'storyboard-tile__quick-record--active': quickRecording }"
          :title="quickRecording
      ? `Stop quick recording for ${thumb.title || `thumbnail ${thumb.idx ?? thumb.id}`}`
      : `Quick record on ${thumb.title || `thumbnail ${thumb.idx ?? thumb.id}`}`"
          aria-label="Quick record audio"
          @click.stop="onQuickRecordClick"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 15.5a3.5 3.5 0 0 0 3.5-3.5V7a3.5 3.5 0 1 0-7 0v5a3.5 3.5 0 0 0 3.5 3.5Z"/>
          <path
              d="M6 11.5a1 1 0 1 1 2 0 4 4 0 1 0 8 0 1 1 0 1 1 2 0 6 6 0 0 1-5 5.91V20h2a1 1 0 1 1 0 2H9a1 1 0 1 1 0-2h2v-2.59A6 6 0 0 1 6 11.5Z"/>
        </svg>
      </button>

      <button
          v-if="canDelete"
          type="button"
          class="storyboard-tile__delete"
          :title="`Delete ${thumb.title || `thumbnail ${thumb.idx ?? thumb.id}`}`"
          aria-label="Delete thumbnail"
          @click.stop="emit('delete', thumb)"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M9 3h6l1 1h4v2H4V4h4l1-1Zm-2 5h10l-1 13H8L7 8Zm2 2v9h1v-9H9Zm3 0v9h1v-9h-1Zm3 0v9h1v-9h-1Z"/>
        </svg>
      </button>
    </div>
  </article>
</template>
