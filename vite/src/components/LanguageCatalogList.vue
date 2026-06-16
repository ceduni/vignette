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

function normalizeText(value, fallback = "Non renseigné") {
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
        name: normalizeText(item.name, "Langue sans nom"),
        level: normalizeLevel(item.level),
        countryIds: String(item.countryIds ?? ""),
        hasCountryIds: String(item.countryIds ?? "").trim().length > 0,
      }));
});
</script>

<template>
  <div class="language-list">
    <p v-if="!rows.length" class="empty-state">Aucune langue à afficher</p>

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
              aria-label="Voir les informations de la langue"
              title="Informations"
              @click.stop="emit('open-info', item)"
          >
            <Info :size="15"/>
          </button>

          <button
              type="button"
              class="language-action"
              :disabled="!item.hasCountryIds"
              aria-label="Localiser la langue sur la carte"
              title="Localisation"
              @click.stop="emit('focus-location', item)"
          >
            <MapPinned :size="15"/>
          </button>

          <button
              type="button"
              class="language-action language-action--muted"
              aria-label="Prévisualiser l'audio"
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
  border: 1px dashed rgba(148, 163, 184, 0.45);
  border-radius: 1rem;
  color: #50627c;
  background: linear-gradient(180deg, #fbfdff 0%, #f7fbff 100%);
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-radius: 1rem;
  overflow: hidden;
}

.language-item {
  padding: 1rem 1.1rem;
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  cursor: pointer;
  transition:
    background 160ms ease,
    transform 160ms ease,
    border-color 160ms ease;
}

.language-item:last-child {
  border-bottom: none;
}

.language-item:hover {
  background:
    linear-gradient(90deg, rgba(37, 99, 235, 0.06), transparent);
}

.language-item:focus-visible {
  outline: 2px solid rgba(37, 99, 235, 0.35);
  outline-offset: -2px;
}

.language-item.is-selected {
  background:
    linear-gradient(90deg, rgba(15, 63, 117, 0.1), rgba(56, 189, 248, 0.06));
  border-left: 4px solid #2563eb;
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
  color: #102033;
  text-decoration: none;
}

.language-item__name:hover {
  color: #1f4f88;
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
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  color: #173a61;
  cursor: pointer;
  transition:
    transform 160ms ease,
    border-color 160ms ease,
    background 160ms ease,
    color 160ms ease;
}

.language-action:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.45);
  background: #eff6ff;
  color: #1d4ed8;
}

.language-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.language-action--muted {
  color: #4f46e5;
}

.language-badge {
  border-radius: 999px;
  padding: 0.16rem 0.5rem;
  background: rgba(15, 63, 117, 0.08);
  color: #173a61;
  font-weight: 700;
  font-size: 0.74rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.language-badge--language {
  color: #0f5132;
  background: #e8f8ef;
}

.language-badge--dialect {
  color: #5a466f;
  background: #f4effb;
}

.language-badge--family {
  color: #1e3a8a;
  background: #eaf2ff;
}

.language-badge--other {
  color: #314256;
  background: #edf3fa;
}
</style>
