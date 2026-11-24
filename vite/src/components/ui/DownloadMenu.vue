<script setup>
import {onBeforeUnmount, ref} from "vue";

defineProps({
  label: {type: String, default: ""},
  title: {type: String, default: "Download"},
  buttonClass: {type: String, default: ""},
  busy: {type: Boolean, default: false},
  align: {type: String, default: "right"},
  placement: {type: String, default: "down"},
});
const emit = defineEmits(["svg", "zip"]);

const open = ref(false);
const root = ref(null);

function onDocumentClick(event) {
  if (root.value && !root.value.contains(event.target)) close();
}

function onKeyDown(event) {
  if (event.key === "Escape") {
    event.stopPropagation();
    close();
  }
}

function toggle() {
  open.value ? close() : show();
}

function show() {
  open.value = true;
  document.addEventListener("click", onDocumentClick, true);
  document.addEventListener("keydown", onKeyDown, true);
}

function close() {
  open.value = false;
  document.removeEventListener("click", onDocumentClick, true);
  document.removeEventListener("keydown", onKeyDown, true);
}

function choose(format) {
  close();
  emit(format);
}

onBeforeUnmount(close);
</script>

<template>
  <div ref="root" class="dl-menu" @click.stop>
    <button
        type="button"
        :class="buttonClass"
        :disabled="busy"
        :title="busy ? 'Downloading…' : title"
        :aria-label="busy ? 'Downloading…' : title"
        aria-haspopup="menu"
        :aria-expanded="open"
        @click="toggle"
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 3v12m0 0-5-5m5 5 5-5M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>
      </svg>
      <span v-if="label">{{ busy ? "Downloading…" : label }}</span>
    </button>
    <div v-if="open" class="dl-menu__list" :class="[`dl-menu__list--${align}`, `dl-menu__list--${placement}`]" role="menu">
      <button type="button" class="dl-menu__item" role="menuitem" @click="choose('svg')">
        <strong>SVG</strong>
        <span>Image only</span>
      </button>
      <button type="button" class="dl-menu__item" role="menuitem" @click="choose('zip')">
        <strong>ZIP</strong>
        <span>Image + audio</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
:where(.dl-menu) {
  position: relative;
}

.dl-menu {
  display: inline-flex;
}

.dl-menu__list {
  position: absolute;
  top: calc(100% + 6px);
  z-index: 30;
  display: flex;
  min-width: 170px;
  flex-direction: column;
  padding: 6px;
  border: 1.5px solid var(--border, #e5d6dc);
  border-radius: 12px;
  background: var(--surface, #fff);
  box-shadow: 0 10px 28px rgba(30, 8, 18, 0.16);
}

.dl-menu__list--up {
  top: auto;
  bottom: calc(100% + 6px);
}

.dl-menu__list--right { right: 0; }
.dl-menu__list--left { left: 0; }

.dl-menu__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text, #2a1220);
  font: inherit;
  font-size: 0.82rem;
  text-align: left;
  cursor: pointer;
}

.dl-menu__item span {
  color: var(--text-soft, #7a5a6a);
  font-size: 0.75rem;
}

.dl-menu__item:hover,
.dl-menu__item:focus-visible {
  background: var(--accent-green, #eef2e8);
}
</style>
