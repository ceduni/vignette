<script setup>
import {computed} from "vue";
import {RouterLink} from "vue-router";
import {Info, MapPinned, Volume2} from "lucide-vue-next";

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
  selectedLanguageId: {
    type: [String, Number, null],
    default: null,
  },
});

const emit = defineEmits([
  "select-language",
  "open-info",
  "focus-location",
  "preview-audio",
]);

function normalizeText(value, fallback = "Not specified") {
  const text = String(value ?? "").trim();
  return text || fallback;
}

function normalizeLevel(level) {
  const normalized = String(level ?? "").trim().toLowerCase();
  if (normalized.includes("dialect")) return "dialect";
  if (normalized.includes("family")) return "family";
  if (normalized.includes("language")) return "language";
  return "other";
}

const rows = computed(() => {
  return (props.items ?? [])
      .filter((item) => item && item.name)
      .map((item, index) => ({
        key: item.id ?? `${item.name}-${index}`,
        id: item.id,
        name: normalizeText(item.name, "Unnamed language"),
        level: normalizeLevel(item.level),
        countryIds: String(item.countryIds ?? ""),
        hasCountryIds: String(item.countryIds ?? "").trim().length > 0,
      }));
});
</script>

<template>
  <div class="language-list">
    <p v-if="!rows.length" class="empty-state">No languages to display</p>

    <ul v-else class="list" role="list">
      <li
          v-for="item in rows"
          :key="item.key"
          class="language-item"
          :class="{ 'is-selected': String(selectedLanguageId ?? '') === String(item.id ?? '') }"
          tabindex="0"
          @click="emit('select-language', item)"
          @keydown.enter.prevent="emit('select-language', item)"
          @keydown.space.prevent="emit('select-language', item)"
      >
        <div class="language-item__row">
          <RouterLink
              v-if="item.id"
              :to="`/languages/${item.id}`"
              class="language-item__name"
          >
            {{ item.name }}
          </RouterLink>
          <p v-else class="language-item__name">{{ item.name }}</p>

          <span class="language-badge" :class="`language-badge--${item.level}`">{{ item.level }}</span>
        </div>
        <div class="language-item__actions">
          <button
              type="button"
              class="language-action"
              aria-label="View language information"
              title="Information"
              @click.stop="emit('open-info', item)"
          >
            <Info :size="15"/>
          </button>

          <button
              type="button"
              class="language-action"
              :disabled="!item.hasCountryIds"
              aria-label="Locate language on the map"
              title="Location"
              @click.stop="emit('focus-location', item)"
          >
            <MapPinned :size="15"/>
          </button>

          <button
              type="button"
              class="language-action language-action--muted"
              aria-label="Preview audio"
              title="Audio"
              @click.stop="emit('preview-audio', item)"
          >
            <Volume2 :size="15"/>
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.language-list {
  display: block;
}

.empty-state {
  margin: 0;
  padding: 1.2rem 1rem;
  border: 1px dashed rgba(229, 208, 204, 0.98);
  border-radius: 18px;
  color: #785068;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(255, 240, 238, 0.9));
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-radius: 18px;
  overflow: hidden;
}

.language-item {
  padding: 0.92rem 1rem;
  border-bottom: 1px solid rgba(229, 208, 204, 0.82);
  cursor: pointer;
  transition:
    background 160ms ease,
    transform 160ms ease,
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.language-item:last-child {
  border-bottom: none;
}

.language-item:hover {
  background: linear-gradient(180deg, rgba(255, 247, 241, 0.94), rgba(255, 240, 238, 0.88));
  box-shadow: inset 0 0 0 1px rgba(91, 25, 40, 0.08);
}

.language-item:focus-visible {
  outline: 2px solid rgba(91, 25, 40, 0.18);
  outline-offset: -2px;
}

.language-item.is-selected {
  background: linear-gradient(135deg, rgba(91, 25, 40, 0.1), rgba(212, 229, 202, 0.42));
  border-left: 4px solid #5B1928;
  padding-left: calc(1.1rem - 4px);
}

.language-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.language-item__name {
  margin: 0;
  font-size: 1rem;
  font-weight: 750;
  color: #1E0812;
  text-decoration: none;
}

.language-item__name:hover {
  color: #5B1928;
}

.language-item__meta {
  margin-top: 0.35rem;
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  font-size: 0.78rem;
  color: #64748b;
}

.language-item__actions {
  margin-top: 0.7rem;
  display: flex;
  gap: 0.45rem;
}

.language-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid rgba(229, 208, 204, 0.96);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  color: #5B1928;
  cursor: pointer;
  transition:
    transform 160ms ease,
    border-color 160ms ease,
    background 160ms ease,
    color 160ms ease,
    box-shadow 160ms ease;
}

.language-action:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: #5B1928;
  background: #5B1928;
  color: #fff7f1;
  box-shadow: 0 8px 14px rgba(91, 25, 40, 0.16);
}

.language-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.language-action--muted {
  color: #485B38;
}

.language-badge {
  border-radius: 999px;
  padding: 0.2rem 0.58rem;
  background: rgba(255, 240, 238, 0.94);
  color: #5B1928;
  font-weight: 700;
  font-size: 0.74rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border: 1px solid rgba(229, 208, 204, 0.94);
}

.language-badge--language {
  color: #485B38;
  background: rgba(212, 229, 202, 0.76);
  border-color: rgba(72, 91, 56, 0.16);
}

.language-badge--dialect {
  color: #785068;
  background: rgba(245, 212, 206, 0.62);
  border-color: rgba(120, 80, 104, 0.16);
}

.language-badge--family {
  color: #5B1928;
  background: rgba(255, 240, 238, 0.94);
  border-color: rgba(91, 25, 40, 0.12);
}

.language-badge--other {
  color: #314256;
  background: #edf3fa;
}
</style>
