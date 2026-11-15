<script setup>
import {buildApiUrl} from "../api/rest";

const props = defineProps({
  thumb:         {type: Object,  required: true},
  audios:        {type: Array,   default: () => []},
  selected:      {type: Boolean, default: false},
  highlighted:   {type: Boolean, default: false},
  quickRecording:{type: Boolean, default: false},
  canDelete:     {type: Boolean, default: false},
  canResize:     {type: Boolean, default: false},
  canReorder:    {type: Boolean, default: false},
  colSpan:       {type: Number,  default: 1},
  rowSpan:       {type: Number,  default: 1},
});

const emit = defineEmits([
  "select",
  "delete", "reorder", "resize-start",
  "pointer-reorder-start",
]);

function thumbnailContentUrl(thumb) {
  if (thumb?.previewUrl) return thumb.previewUrl;
  if (!thumb?.id) return "";
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

function onDeleteClick() {
  if (!confirm("Delete this scene? This cannot be undone.")) return;
  emit("delete", props.thumb);
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
    @click="emit('select', thumb)"
  >
    <div class="storyboard-tile__stage">
      <img
        :src="thumbnailContentUrl(thumb)"
        :alt="thumb.title || `Thumbnail ${thumb.id}`"
        class="storyboard-tile__image"
      />

      <button
        v-if="canReorder"
        type="button"
        class="storyboard-tile__drag-handle"
        draggable="false"
        title="Drag to move this scene"
        aria-label="Drag to move scene"
        @click.stop
        @pointerdown.stop="emit('pointer-reorder-start', thumb, $event)"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
          <circle cx="8" cy="6" r="1" fill="currentColor"/><circle cx="16" cy="6" r="1" fill="currentColor"/>
          <circle cx="8" cy="12" r="1" fill="currentColor"/><circle cx="16" cy="12" r="1" fill="currentColor"/>
          <circle cx="8" cy="18" r="1" fill="currentColor"/><circle cx="16" cy="18" r="1" fill="currentColor"/>
        </svg>
      </button>

      <button
        v-if="canDelete"
        type="button"
        class="storyboard-tile__delete"
        draggable="false"
        title="Delete scene"
        aria-label="Delete scene"
        @click.stop="onDeleteClick"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
             stroke-linecap="round" stroke-linejoin="round" width="13" height="13">
          <polyline points="3 6 5 6 21 6"/>
          <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
          <path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
        </svg>
      </button>

      <div class="storyboard-tile__caption">
        <span>{{ thumb.title || `Scene ${thumb.idx ?? thumb.id}` }}</span>
        <small class="storyboard-tile__meta">
          {{ audios.length }} {{ audios.length === 1 ? "take" : "takes" }}
        </small>
      </div>
    </div>

    <template v-if="canResize">
      <span class="storyboard-tile__size-badge">{{ colSpan }} × {{ rowSpan }}</span>
      <button
        type="button"
        class="storyboard-tile__handle storyboard-tile__handle--right"
        draggable="false"
        title="Resize width"
        aria-label="Resize scene width"
        @click.stop
        @pointerdown.stop="emit('resize-start', { thumb, direction: 'right', event: $event })"
      ></button>
      <button
        type="button"
        class="storyboard-tile__handle storyboard-tile__handle--bottom"
        draggable="false"
        title="Resize height"
        aria-label="Resize scene height"
        @click.stop
        @pointerdown.stop="emit('resize-start', { thumb, direction: 'bottom', event: $event })"
      ></button>
      <button
        type="button"
        class="storyboard-tile__handle storyboard-tile__handle--corner"
        draggable="false"
        title="Resize width and height"
        aria-label="Resize scene width and height"
        @click.stop
        @pointerdown.stop="emit('resize-start', { thumb, direction: 'corner', event: $event })"
      ></button>
    </template>
  </article>
</template>
