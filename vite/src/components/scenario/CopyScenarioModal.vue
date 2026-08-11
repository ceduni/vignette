<script setup>
import {ref, watch, nextTick} from "vue";

const props = defineProps({
  scenario: {type: Object, default: null}, // { id, title }
  saving: {type: Boolean, default: false},
  error: {type: String, default: ""},
});

const emit = defineEmits(["close", "confirm"]);

const title = ref("");
const inputEl = ref(null);

watch(() => props.scenario, async (s) => {
  if (!s) return;
  title.value = `Copy of ${s.title || "Untitled scenario"}`;
  await nextTick();
  inputEl.value?.focus();
  inputEl.value?.select();
});

function confirm() {
  const trimmed = title.value.trim();
  if (!trimmed || props.saving) return;
  emit("confirm", trimmed);
}
</script>

<template>
  <Teleport to="body">
    <div v-if="scenario" class="cs-backdrop" @click.self="emit('close')">
      <div class="cs-modal" role="dialog" aria-modal="true" aria-labelledby="copy-scenario-title">
        <div class="cs-head">
          <div>
            <p class="cs-eyebrow">Copy scenario</p>
            <h2 id="copy-scenario-title" class="cs-title">Name your copy</h2>
          </div>
          <button type="button" class="cs-close" aria-label="Close" @click="emit('close')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 6 6 18M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <div class="cs-field">
          <label class="cs-label" for="copy-scenario-input">Title</label>
          <input
              id="copy-scenario-input"
              ref="inputEl"
              v-model="title"
              class="cs-input"
              placeholder="Title for your copy"
              maxlength="200"
              @keydown.enter="confirm"
          />
        </div>

        <p v-if="error" class="cs-error">{{ error }}</p>

        <div class="cs-footer">
          <button type="button" class="cs-cancel" @click="emit('close')">Cancel</button>
          <button type="button" class="cs-confirm" :disabled="saving || !title.trim()" @click="confirm">
            <template v-if="saving"><span class="cs-spin"></span> Copying…</template>
            <template v-else>Make a copy</template>
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.cs-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(30,8,18,0.55);
  backdrop-filter: blur(4px);
}

.cs-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: min(440px, 100%);
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 24px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.cs-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.cs-eyebrow {
  margin: 0 0 3px;
  font-size: 0.62rem;
  font-weight: 900;
  color: #485B38;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.cs-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 950;
  color: #1E0812;
  letter-spacing: -0.02em;
}
.cs-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  transition: background 140ms ease;
}
.cs-close:hover { background: #1E0812; color: #FFF0EE; }
.cs-close svg { width: 13px; height: 13px; }

.cs-field { display: flex; flex-direction: column; gap: 6px; }
.cs-label {
  font-size: 0.65rem;
  font-weight: 900;
  color: #785068;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.cs-input {
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 11px 13px;
  background: #fff;
  color: #1E0812;
  font: inherit;
  font-size: 0.92rem;
  font-weight: 600;
  outline: none;
  transition: border-color 160ms ease;
}
.cs-input:focus { border-color: #485B38; }

.cs-error { margin: 0; font-size: 0.82rem; color: #A8334C; font-weight: 600; }

.cs-footer { display: flex; gap: 10px; }
.cs-cancel {
  border: 1.5px solid #D4E5CA;
  border-radius: 12px;
  padding: 11px 20px;
  background: transparent;
  color: #785068;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
  transition: background 140ms ease;
}
.cs-cancel:hover { background: #D4E5CA; }
.cs-confirm {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 0;
  border-radius: 12px;
  padding: 11px;
  background: #1E0812;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.92rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 160ms ease;
}
.cs-confirm:hover:not(:disabled) { background: #485B38; }
.cs-confirm:disabled { opacity: 0.55; cursor: not-allowed; }

.cs-spin {
  width: 13px;
  height: 13px;
  border: 2px solid rgba(255,240,238,0.3);
  border-top-color: #FFF0EE;
  border-radius: 999px;
  animation: cs-spin 0.7s linear infinite;
  display: inline-block;
}
@keyframes cs-spin { to { transform: rotate(360deg); } }
</style>
