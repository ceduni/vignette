<script setup>
import {computed, onMounted, onUnmounted, ref} from "vue";
import {RouterLink, useRouter} from "vue-router";
import {fetchLanguageOptions} from "../api/languages";
import {createScenario} from "../api/scenarios";
import BaseAlert from "../components/ui/BaseAlert.vue";
import {useToast} from "../composables/useToast";
import TagAutocompleteInput from "@/components/TagAutocompleteInput.vue";

const router = useRouter();
const toast = useToast();

const form = ref({title: "", description: "", languageId: "", tags: []});
const languages = ref([]);
const langQuery = ref("");
const selectedLanguage = ref(null);
const comboOpen = ref(false);
const langFieldRef = ref(null);
const attempted = ref(false);
const error = ref("");
const loading = ref(false);
let debounceTimer = null;

const titleError = computed(() => {
  if (!form.value.title.trim()) return "A title is required.";
  if (form.value.title.trim().length < 3) return "Title is too short (3 chars min).";
  return "";
});

const isFormValid = computed(() => !titleError.value && !!selectedLanguage.value);

const descPreview = computed(() => {
  const raw = form.value.description?.trim();
  if (!raw) return null;
  return raw.length > 140 ? raw.slice(0, 137) + "…" : raw;
});

async function loadLanguages(q = "") {
  const params = new URLSearchParams({page: "0", size: "40"});
  if (q.trim()) params.set("q", q.trim());
  const data = await fetchLanguageOptions(params);
  languages.value = data.content ?? [];
}

function onLangInput() {
  selectedLanguage.value = null;
  form.value.languageId = "";
  comboOpen.value = true;
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => loadLanguages(langQuery.value), 220);
}

function selectLanguage(lang) {
  selectedLanguage.value = lang;
  form.value.languageId = lang.id;
  langQuery.value = lang.name;
  comboOpen.value = false;
}

function clearLanguage() {
  selectedLanguage.value = null;
  form.value.languageId = "";
  langQuery.value = "";
  comboOpen.value = true;
}

function onClickOutside(e) {
  if (langFieldRef.value && !langFieldRef.value.contains(e.target)) {
    comboOpen.value = false;
  }
}

async function submit() {
  attempted.value = true;
  if (!isFormValid.value) {
    error.value = titleError.value || "Please select a language.";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const created = await createScenario({
      title: form.value.title.trim(),
      description: form.value.description,
      languageId: form.value.languageId,
      tags: form.value.tags,
    });
    toast.success("Scenario created!");
    router.push(`/scenarios/${created.id}`);
  } catch (e) {
    const msg = e.message || "";
    error.value = msg.toLowerCase().includes("unique") || msg.includes("23505") || msg.includes("duplicate")
        ? "You already have a scenario with this title. Please choose a different name."
        : msg || "Failed to create scenario.";
    toast.error(error.value);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  document.addEventListener("mousedown", onClickOutside);
  try {
    await loadLanguages();
  } catch (e) {
    error.value = e.message;
  }
});

onUnmounted(() => {
  document.removeEventListener("mousedown", onClickOutside);
  clearTimeout(debounceTimer);
});
</script>

<template>
  <main class="cs-root">

    <div class="cs-topbar">
      <RouterLink to="/scenarios" class="cs-back">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5M12 5l-7 7 7 7"/>
        </svg>
        My scenarios
      </RouterLink>
      <span class="cs-draft-pill">
        <span class="cs-draft-pill__dot"></span>
        Draft · Private
      </span>
    </div>

    <div class="cs-hero">
      <div class="cs-hero__eyebrow">New scenario</div>
      <h1 class="cs-hero__title">What story are<br>you documenting?</h1>
      <p class="cs-hero__sub">Fill in the basics — you'll land straight in the studio to add scenes and audio.</p>
    </div>

    <div class="cs-body">

      <form class="cs-form" novalidate @submit.prevent="submit">

        <div class="cs-field">
          <label class="cs-label">
            Scenario title
            <span class="cs-req">required</span>
          </label>
          <input
              v-model="form.title"
              class="cs-input cs-input--hero"
              placeholder="e.g. Market scene — morning greetings"
              autocomplete="off"
          />
          <span v-if="form.title && titleError" class="cs-field-err">{{ titleError }}</span>
        </div>

        <div class="cs-field" ref="langFieldRef">
          <label class="cs-label">
            Language
            <span class="cs-req">required</span>
          </label>
          <div class="cs-combo" :class="{ 'cs-combo--open': comboOpen && !selectedLanguage }">
            <div class="cs-combo__input-wrap" :class="{ 'cs-combo__input-wrap--selected': selectedLanguage }">
              <svg class="cs-combo__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
              </svg>
              <input
                  v-model="langQuery"
                  class="cs-combo__input"
                  :placeholder="selectedLanguage ? '' : 'Search 8 000+ languages…'"
                  autocomplete="off"
                  @focus="comboOpen = true"
                  @input="onLangInput"
              />
              <button v-if="selectedLanguage" type="button" class="cs-combo__clear" @click="clearLanguage" title="Change language">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
              </button>
            </div>

            <Transition name="cs-drop">
              <div v-if="comboOpen && !selectedLanguage" class="cs-combo__drop">
                <button
                    v-for="lang in languages"
                    :key="lang.id"
                    type="button"
                    class="cs-combo__opt"
                    @mousedown.prevent="selectLanguage(lang)"
                >
                  <span class="cs-combo__opt-name">{{ lang.name }}</span>
                  <span v-if="lang.glottocode" class="cs-combo__opt-code">{{ lang.glottocode }}</span>
                </button>
                <p v-if="!languages.length" class="cs-combo__empty">No results for "{{ langQuery }}"</p>
              </div>
            </Transition>
          </div>
          <span v-if="attempted && !selectedLanguage" class="cs-field-err">Please select a language.</span>
        </div>

        <div class="cs-field">
          <label class="cs-label">
            Tags
            <span class="cs-opt">optional</span>
          </label>
          <TagAutocompleteInput
              v-model="form.tags"
              placeholder="greeting, daily-life, elicitation…"
          />
        </div>

        <div class="cs-field">
          <label class="cs-label">
            Description
            <span class="cs-opt">optional</span>
          </label>
          <textarea
              v-model="form.description"
              class="cs-textarea"
              rows="4"
              maxlength="500"
              placeholder="Describe the intended scene context and elicitation goals…"
          />
          <span class="cs-char-count">{{ form.description.length }} / 500</span>
        </div>

        <BaseAlert v-if="error" type="error">{{ error }}</BaseAlert>

        <button type="submit" class="cs-submit" :disabled="loading">
          <template v-if="loading">
            <span class="cs-submit__spinner"></span>
            Creating…
          </template>
          <template v-else>
            Create scenario
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </template>
        </button>

      </form>

      <aside class="cs-preview">
        <div class="cs-preview__label">Live preview</div>

        <div class="cs-preview__card">
          <div class="cs-preview__thumb">
            <span class="cs-preview__thumb-glyph">✦</span>
            <span class="cs-preview__thumb-hint">Thumbnail will appear here</span>
          </div>

          <div class="cs-preview__body">
            <h3 class="cs-preview__title">
              {{ form.title.trim() || "Untitled scenario" }}
            </h3>

            <div class="cs-preview__pills">
              <span class="cs-preview__pill cs-preview__pill--lang">
                {{ selectedLanguage ? selectedLanguage.name : "No language" }}
              </span>
              <span class="cs-preview__pill cs-preview__pill--draft">Draft</span>
            </div>

            <div v-if="form.tags.length" class="cs-preview__tags">
              <span v-for="tag in form.tags" :key="tag" class="cs-preview__tag">#{{ tag }}</span>
            </div>

            <p class="cs-preview__desc">
              {{ descPreview || "No description yet." }}
            </p>
          </div>
        </div>

        <div class="cs-preview__next">
          <div class="cs-preview__next-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="3"/>
              <path d="M9 9h6M9 12h6M9 15h4"/>
            </svg>
          </div>
          <div>
            <strong>Next step</strong>
            <p>You'll land in the studio to add scenes, upload images and record audio.</p>
          </div>
        </div>
      </aside>

    </div>
  </main>
</template>

<style scoped>
.cs-root {
  min-height: 100vh;
  padding-bottom: 80px;
}

.cs-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 24px 0;
}

.cs-back {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-soft);
  font-size: 0.88rem;
  font-weight: 600;
  text-decoration: none;
  transition: color 140ms ease;
}

.cs-back:hover {
  color: var(--text);
}

.cs-back svg {
  width: 15px;
  height: 15px;
}

.cs-draft-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #D4E5CA;
  border-radius: 999px;
  padding: 4px 12px;
  background: #FFF0EE;
  color: #785068;
  font-size: 0.78rem;
  font-weight: 700;
}

.cs-draft-pill__dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #485B38;
}

.cs-hero {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px 24px 32px;
}

.cs-hero__eyebrow {
  color: #485B38;
  font-size: 0.75rem;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin-bottom: 10px;
}

.cs-hero__title {
  margin: 0 0 12px;
  font-size: clamp(1.9rem, 4vw, 2.8rem);
  font-weight: 950;
  line-height: 1.1;
  color: #1E0812;
  letter-spacing: -0.02em;
}

.cs-hero__sub {
  margin: 0;
  color: var(--text-soft);
  font-size: 1rem;
  max-width: 520px;
}

.cs-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  align-items: start;
  gap: 32px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
}

@media (max-width: 860px) {
  .cs-body {
    grid-template-columns: 1fr;
  }
}

.cs-form {
  display: flex;
  flex-direction: column;
  gap: 28px;
  background: #ffffff;
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 4px 24px rgba(30, 8, 18, 0.06);
}

.cs-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cs-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.82rem;
  font-weight: 800;
  color: var(--text);
  letter-spacing: 0.01em;
  text-transform: uppercase;
}

.cs-req {
  font-size: 0.68rem;
  font-weight: 700;
  color: #485B38;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.cs-opt {
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--text-soft);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.cs-input {
  border: 1.5px solid var(--border);
  border-radius: 12px;
  padding: 12px 14px;
  font: inherit;
  font-size: 0.95rem;
  color: var(--text);
  background: var(--surface);
  transition: border-color 160ms ease, box-shadow 160ms ease;
  outline: none;
}

.cs-input:focus {
  border-color: #485B38;
  box-shadow: 0 0 0 3px rgba(72,91,56, 0.12);
}

.cs-input--hero {
  font-size: 1.15rem;
  font-weight: 700;
  padding: 14px 16px;
  border-radius: 14px;
}

.cs-textarea {
  border: 1.5px solid var(--border);
  border-radius: 12px;
  padding: 12px 14px;
  font: inherit;
  font-size: 0.95rem;
  color: var(--text);
  background: var(--surface);
  resize: vertical;
  min-height: 110px;
  outline: none;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.cs-textarea:focus {
  border-color: #485B38;
  box-shadow: 0 0 0 3px rgba(72,91,56, 0.12);
}

.cs-char-count {
  align-self: flex-end;
  font-size: 0.75rem;
  color: var(--text-soft);
}

.cs-field-err {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--danger);
}

.cs-combo {
  position: relative;
}

.cs-combo__input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1.5px solid var(--border);
  border-radius: 12px;
  padding: 0 14px;
  background: var(--surface);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.cs-combo--open .cs-combo__input-wrap,
.cs-combo__input-wrap:focus-within {
  border-color: #485B38;
  box-shadow: 0 0 0 3px rgba(72,91,56, 0.12);
}

.cs-combo__input-wrap--selected {
  border-color: #4A6741;
  background: rgba(74,103,65,0.08);
}

.cs-combo__input-wrap--selected .cs-combo__icon {
  color: #4A6741;
}

.cs-combo__icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--text-soft);
}

.cs-combo__input {
  flex: 1;
  border: 0;
  outline: none;
  padding: 13px 0;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text);
  background: transparent;
}

.cs-combo__input::placeholder {
  color: var(--text-soft);
  font-weight: 400;
}

.cs-combo__clear {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border: 0;
  border-radius: 999px;
  background: #D4E5CA;
  color: #785068;
  cursor: pointer;
  padding: 0;
}

.cs-combo__clear svg {
  width: 13px;
  height: 13px;
}

.cs-combo__clear:hover {
  background: #485B38;
  color: white;
}

.cs-combo__drop {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 50;
  max-height: 260px;
  overflow-y: auto;
  border: 1.5px solid #D4E5CA;
  border-radius: 14px;
  background: #FFF0EE;
  box-shadow: 0 16px 40px rgba(30,8,18, 0.14);
  padding: 6px;
}

.cs-combo__opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #1E0812;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: background 120ms ease;
}

.cs-combo__opt:hover {
  background: #D4E5CA;
}

.cs-combo__opt-name {
  font-size: 0.9rem;
  font-weight: 700;
}

.cs-combo__opt-code {
  font-size: 0.72rem;
  font-weight: 600;
  color: #785068;
  font-family: monospace;
  letter-spacing: 0.06em;
}

.cs-combo__empty {
  padding: 14px 12px;
  margin: 0;
  font-size: 0.88rem;
  color: #785068;
  text-align: center;
}

.cs-drop-enter-active,
.cs-drop-leave-active {
  transition: opacity 140ms ease, transform 140ms ease;
}

.cs-drop-enter-from,
.cs-drop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.cs-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 52px;
  border: 0;
  border-radius: 14px;
  padding: 0 28px;
  background: #1E0812;
  color: #FFF0EE;
  font: inherit;
  font-size: 1rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 160ms ease, transform 120ms ease;
}

.cs-submit:hover:not(:disabled) {
  background: #485B38;
  transform: translateY(-1px);
}

.cs-submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}

.cs-submit svg {
  width: 18px;
  height: 18px;
  transition: transform 160ms ease;
}

.cs-submit:hover:not(:disabled) svg {
  transform: translateX(3px);
}

.cs-submit__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 248, 240, 0.35);
  border-top-color: #FFF0EE;
  border-radius: 999px;
  animation: cs-spin 0.7s linear infinite;
}

@keyframes cs-spin {
  to {
    transform: rotate(360deg);
  }
}

.cs-preview {
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cs-preview__label {
  font-size: 0.7rem;
  font-weight: 900;
  color: #785068;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.cs-preview__card {
  overflow: hidden;
  border: 3px solid #1E0812;
  border-radius: 14px;
  background: #FFF0EE;
  box-shadow: 3px 3px 0 #1E0812;
}

.cs-preview__thumb {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 140px;
  background: linear-gradient(135deg, #D4E5CA, #D4E5CA);
  border-bottom: 2px solid #1E0812;
}

.cs-preview__thumb-glyph {
  font-size: 2rem;
  color: #485B38;
  opacity: 0.6;
}

.cs-preview__thumb-hint {
  font-size: 0.7rem;
  font-weight: 700;
  color: #785068;
  letter-spacing: 0.04em;
}

.cs-preview__body {
  padding: 14px 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cs-preview__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 900;
  color: #1E0812;
  line-height: 1.25;
  word-break: break-word;
}

.cs-preview__pills {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.cs-preview__pill {
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 0.7rem;
  font-weight: 800;
}

.cs-preview__pill--lang {
  background: rgba(59,90,107,0.12);
  color: #3B5A6B;
}

.cs-preview__pill--draft {
  background: #D4E5CA;
  color: #485B38;
}

.cs-preview__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.cs-preview__tag {
  font-size: 0.72rem;
  font-weight: 700;
  color: #785068;
}

.cs-preview__desc {
  margin: 0;
  font-size: 0.85rem;
  color: #785068;
  line-height: 1.55;
}

.cs-preview__next {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border: 1px solid #D4E5CA;
  border-radius: 14px;
  padding: 14px;
  background: #FFF0EE;
  color: #1E0812;
}

.cs-preview__next-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  background: #FFF0EE;
  border: 1px solid #D4E5CA;
  color: #485B38;
}

.cs-preview__next-icon svg {
  width: 18px;
  height: 18px;
}

.cs-preview__next strong {
  display: block;
  font-size: 0.82rem;
  font-weight: 800;
  margin-bottom: 3px;
}

.cs-preview__next p {
  margin: 0;
  font-size: 0.78rem;
  color: #785068;
  line-height: 1.5;
}

@media (max-width: 600px) {
  .cs-hero {
    padding: 24px 16px 20px;
  }

  .cs-body {
    padding: 0 16px;
  }

  .cs-form {
    padding: 20px 16px;
    border-radius: 16px;
  }
}
</style>
