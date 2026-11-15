<script setup>
import {onMounted, ref} from "vue";
import AdminWorkspaceShell from "../components/admin/AdminWorkspaceShell.vue";
import {fetchLanguage, fetchLanguages, updateLanguage} from "../api/languages";

const loading = ref(false);
const error = ref("");
const success = ref("");

const query = ref("");
const results = ref([]);
const selected = ref(null);

const form = ref({
  name: "",
  level: "",
  bookkeeping: "",
  iso639P3code: "",
  latitude: "",
  longitude: "",
});

async function searchLanguages() {
  loading.value = true;
  error.value = "";

  try {
    const params = new URLSearchParams({
      q: query.value || "",
      page: "0",
      size: "25",
    });
    const page = await fetchLanguages(params);
    results.value = page.content ?? [];
  } catch (e) {
    error.value = e.message || "Failed to search languages.";
  } finally {
    loading.value = false;
  }
}

async function openLanguage(id) {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    const lang = await fetchLanguage(id);
    selected.value = lang;
    form.value = {
      name: lang.name ?? "",
      level: lang.level ?? "",
      bookkeeping: lang.bookkeeping ?? "",
      iso639P3code: lang.iso639P3code ?? "",
      latitude: lang.latitude ?? "",
      longitude: lang.longitude ?? "",
    };
  } catch (e) {
    error.value = e.message || "Failed to load language.";
  } finally {
    loading.value = false;
  }
}

async function saveLanguage() {
  if (!selected.value) return;

  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    selected.value = await updateLanguage(selected.value.id, {
      ...selected.value,
      name: form.value.name,
      level: form.value.level,
      bookkeeping: form.value.bookkeeping,
      iso639P3code: form.value.iso639P3code,
      latitude: form.value.latitude === "" ? null : Number(form.value.latitude),
      longitude: form.value.longitude === "" ? null : Number(form.value.longitude),
    });
    success.value = "Language updated.";
  } catch (e) {
    error.value = e.message || "Failed to update language.";
  } finally {
    loading.value = false;
  }
}

onMounted(searchLanguages);
</script>

<template>
  <AdminWorkspaceShell
      title="Languages"
      subtitle="Search and edit language entries."
  >
      <div class="section-heading">
        <div>
          <h2>Languages</h2>
          <p class="muted">
            Search and inspect language entries, with a lightweight admin edit interface.
          </p>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div class="admin-grid">
        <section class="card">
          <h2>Search</h2>

          <div class="toolbar">
            <input v-model="query" placeholder="Search languages..."/>
            <button class="btn btn--primary" @click="searchLanguages">
              Search
            </button>
          </div>

          <div v-if="loading" class="loader-block">
            <span class="loader-spinner"></span>
            <span class="muted">Loading languages...</span>
          </div>

          <div v-else-if="results.length" class="stack-list">
            <article
                v-for="lang in results"
                :key="lang.id"
                class="card card--nested"
            >
              <h3>{{ lang.name }}</h3>
              <p class="muted">{{ lang.id }} · {{ lang.level || "-" }}</p>
              <button class="btn btn--ghost" @click="openLanguage(lang.id)">
                Open
              </button>
            </article>
          </div>

          <p v-else class="muted">No language found.</p>
        </section>

        <section class="card">
          <h2>Selected language</h2>

          <div v-if="selected" class="form-grid">
            <label>
              Name
              <input v-model="form.name"/>
            </label>

            <label>
              Level
              <input v-model="form.level"/>
            </label>

            <label>
              Bookkeeping
              <input v-model="form.bookkeeping"/>
            </label>

            <label>
              ISO 639-3
              <input v-model="form.iso639P3code"/>
            </label>

            <label>
              Latitude
              <input v-model="form.latitude" type="number" step="any"/>
            </label>

            <label>
              Longitude
              <input v-model="form.longitude" type="number" step="any"/>
            </label>

            <div class="toolbar field--full">
              <button class="btn btn--primary" @click="saveLanguage">
                Save
              </button>
            </div>
          </div>

          <p v-else class="muted">
            Select a language from the search results.
          </p>
        </section>
      </div>
  </AdminWorkspaceShell>
</template>

<style scoped>
.admin-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(0, 1.2fr);
  gap: 16px;
}

.stack-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card--nested {
  border-radius: 14px;
  background: var(--surface-alt);
}

.field--full {
  grid-column: 1 / -1;
}

@media (max-width: 900px) {
  .admin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
