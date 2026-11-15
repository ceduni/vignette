<script setup>
const props = defineProps({
  entries: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
});

const emit = defineEmits(["close"]);

const ACTION_LABELS = {
  SCENARIO_CREATED: "Created",
  METADATA_UPDATED: "Metadata",
  STORYBOARD_UPDATED: "Storyboard",
  THUMBNAIL_ADDED: "Thumbnail",
  THUMBNAIL_UPDATED: "Thumbnail",
  AUDIO_ADDED: "Audio",
  AUDIO_UPDATED: "Audio",
  AUDIO_DELETED: "Audio",
  PUBLISHED: "Published",
};

function actionLabel(action) {
  return ACTION_LABELS[action] || action;
}

function formatWhen(iso) {
  if (!iso) return "";
  const date = new Date(iso);
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
</script>

<template>
  <div class="hp-card" role="dialog" aria-modal="true" aria-labelledby="history-title">
    <div class="hp-head">
      <div>
        <p class="hp-eyebrow">Activity</p>
        <h2 id="history-title" class="hp-title">History</h2>
      </div>
      <button type="button" class="hp-close" aria-label="Close" @click="emit('close')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18 6 6 18M6 6l12 12"/>
        </svg>
      </button>
    </div>

    <p v-if="loading" class="hp-hint">Loading history…</p>

    <ul v-else class="hp-list">
      <li v-for="entry in props.entries" :key="entry.id" class="hp-entry">
        <span class="hp-badge" :class="`hp-badge--${entry.action.toLowerCase()}`">{{ actionLabel(entry.action) }}</span>
        <div class="hp-entry__body">
          <p class="hp-entry__summary">
            <strong>{{ entry.actorUsername }}</strong> {{ entry.summary }}
          </p>
          <p class="hp-entry__when">{{ formatWhen(entry.createdAt) }}</p>
        </div>
      </li>
      <li v-if="!props.entries.length" class="hp-empty">No changes recorded yet.</li>
    </ul>
  </div>
</template>

<style scoped>
.hp-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(480px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  overflow-y: auto;
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 24px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.hp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.hp-eyebrow {
  margin: 0 0 3px;
  font-size: 0.65rem;
  font-weight: 900;
  color: #485B38;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.hp-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 950;
  color: #1E0812;
  letter-spacing: -0.02em;
}
.hp-close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
}
.hp-close:hover { background: #1E0812; color: #FFF0EE; }
.hp-close svg { width: 14px; height: 14px; }

.hp-hint { margin: 0; font-size: 0.82rem; color: #785068; }

.hp-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hp-entry {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fff;
}

.hp-entry__body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.hp-entry__summary {
  margin: 0;
  font-size: 0.85rem;
  color: #1E0812;
  word-break: break-word;
}
.hp-entry__when { margin: 0; font-size: 0.7rem; color: #785068; }

.hp-badge {
  flex-shrink: 0;
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  background: #D4E5CA;
  color: #1E0812;
  white-space: nowrap;
}
.hp-badge--scenario_created { background: #1E0812; color: #FFF0EE; }
.hp-badge--published { background: #4A6741; color: #FFF0EE; }
.hp-badge--metadata_updated,
.hp-badge--storyboard_updated { background: rgba(120,80,104,0.15); color: #785068; }
.hp-badge--thumbnail_added,
.hp-badge--thumbnail_updated,
.hp-badge--audio_added,
.hp-badge--audio_updated,
.hp-badge--audio_deleted { background: rgba(74,103,65,0.15); color: #4A6741; }

.hp-empty {
  font-size: 0.82rem;
  color: #785068;
  padding: 8px 0;
}
</style>
