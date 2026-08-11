<script setup>
import DiscussionThread from "./DiscussionThread.vue";

const props = defineProps({
  scenario: {type: Object, default: null},
});

const emit = defineEmits(["close"]);
</script>

<template>
  <div v-if="scenario" class="dialog-backdrop" @click.self="emit('close')">
    <section class="sdm-card" role="dialog" aria-modal="true" aria-labelledby="scenario-discussion-title">
      <div class="sdm-head">
        <div>
          <p class="sdm-eyebrow">Scenario discussion</p>
          <h2 id="scenario-discussion-title" class="sdm-title">{{ scenario.title || "Untitled scenario" }}</h2>
        </div>
        <button type="button" class="sdm-close" aria-label="Close" @click="emit('close')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 6 6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <DiscussionThread
          title="Discussion"
          subtitle="Questions, notes, and feedback about this scenario."
          target-type="SCENARIO"
          :target-id="scenario.id"
          empty-title="No discussion yet"
          empty-message="Start the conversation about this scenario."
      />
    </section>
  </div>
</template>

<style scoped>
.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1300;
  background: rgba(30, 8, 18, 0.38);
  backdrop-filter: blur(4px);
  display: grid;
  place-items: center;
  padding: 20px;
}

@media (max-width: 640px) {
  .dialog-backdrop {
    align-items: stretch;
    padding: 10px;
  }
}
.sdm-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(640px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  overflow-y: auto;
  border: 1.5px solid var(--border);
  border-radius: 20px;
  padding: 24px;
  background: #fff;
  box-shadow: 0 18px 60px rgba(30, 8, 18, 0.22);
}

.sdm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sdm-eyebrow {
  margin: 0 0 3px;
  font-size: 0.65rem;
  font-weight: 900;
  color: var(--primary);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.sdm-title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 900;
  color: var(--text);
  letter-spacing: -0.02em;
}

.sdm-close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 1.5px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: var(--text-soft);
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}
.sdm-close:hover { background: var(--text); color: #fff; }
.sdm-close svg { width: 14px; height: 14px; }

@media (max-width: 640px) {
  .sdm-card {
    width: 100%;
    max-height: calc(100vh - 20px);
    border-radius: 16px;
    padding: 16px;
  }
}
</style>