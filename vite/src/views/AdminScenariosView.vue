<script setup>
import {computed, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";
import AdminWorkspaceShell from "../components/admin/AdminWorkspaceShell.vue";
import {fetchAdminScenarios, updateAdminScenarioVisibility} from "../api/admin";

const loading = ref(false);
const savingScenarioId = ref(null);
const error = ref("");
const success = ref("");
const scenarios = ref([]);
const query = ref("");

const filteredScenarios = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return scenarios.value;

  return scenarios.value.filter((scenario) => {
    return [
      scenario.title,
      scenario.description,
      scenario.authorUsername,
      scenario.languageId,
      scenario.visibilityStatus,
    ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q));
  });
});

async function loadScenarios() {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    const rows = await fetchAdminScenarios();
    scenarios.value = rows.map((scenario) => ({
      ...scenario,
      draftVisibilityStatus: scenario.visibilityStatus ?? "DRAFT",
    }));
  } catch (e) {
    error.value = e.message || "Failed to load scenarios.";
  } finally {
    loading.value = false;
  }
}

function visibilityChanged(scenario) {
  return scenario.draftVisibilityStatus !== scenario.visibilityStatus;
}

function visibilityTone(status) {
  return status === "PUBLISHED" ? "badge--success" : "badge--muted";
}

async function saveVisibility(scenario) {
  savingScenarioId.value = scenario.id;
  error.value = "";
  success.value = "";

  try {
    const updated = await updateAdminScenarioVisibility(
        scenario.id,
        scenario.draftVisibilityStatus
    );

    const index = scenarios.value.findIndex((s) => s.id === scenario.id);
    if (index >= 0) {
      scenarios.value[index] = {
        ...updated,
        draftVisibilityStatus: updated.visibilityStatus,
      };
    }

    success.value = `Visibility updated for scenario #${updated.id}.`;
  } catch (e) {
    error.value = e.message || "Failed to update scenario visibility.";
  } finally {
    savingScenarioId.value = null;
  }
}

onMounted(loadScenarios);
</script>

<template>
  <AdminWorkspaceShell
      title="Scenarios"
      subtitle="Manage visibility and moderation."
  >
      <div class="section-heading">
        <div>
          <h2>Scenarios</h2>
          <p class="muted">
            Review all scenarios and adjust visibility with a cleaner administration workflow.
          </p>
        </div>

        <div class="toolbar admin-toolbar">
          <input v-model="query" placeholder="Search scenarios..."/>
          <button class="btn btn--ghost" @click="loadScenarios">
            Refresh
          </button>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div v-if="loading" class="loader-block">
        <span class="loader-spinner"></span>
        <span class="muted">Loading scenarios...</span>
      </div>

      <div v-else-if="filteredScenarios.length" class="admin-scenarios-list">
        <article v-for="scenario in filteredScenarios" :key="scenario.id" class="card admin-scenario-card">
          <div class="admin-scenario-card__top">
            <div>
              <h2>{{ scenario.title || "Untitled scenario" }}</h2>
              <p class="muted">
                #{{ scenario.id }} · {{ scenario.authorUsername || "-" }} · {{ scenario.languageId || "-" }}
              </p>
            </div>

            <div class="admin-scenario-card__badges">
              <span class="badge" :class="visibilityTone(scenario.visibilityStatus)">
                {{ scenario.visibilityStatus }}
              </span>
              <span class="badge">
                {{ scenario.storyboardLayoutMode || "PRESET" }}
              </span>
            </div>
          </div>

          <p class="text admin-scenario-card__description">
            {{ scenario.description || "No description provided." }}
          </p>

          <div class="admin-scenario-card__meta">
            <div class="meta-box">
              <span class="meta-box__label">Preset</span>
              <span class="meta-box__value">{{ scenario.storyboardPreset || "-" }}</span>
            </div>

            <div class="meta-box">
              <span class="meta-box__label">Columns</span>
              <span class="meta-box__value">{{ scenario.storyboardColumns ?? "-" }}</span>
            </div>

            <div class="meta-box">
              <span class="meta-box__label">Created</span>
              <span class="meta-box__value">{{ scenario.createdAt || "-" }}</span>
            </div>

            <div class="meta-box">
              <span class="meta-box__label">Published at</span>
              <span class="meta-box__value">{{ scenario.publishedAt || "-" }}</span>
            </div>
          </div>

          <div class="admin-scenario-card__controls">
            <label class="field-group">
              <span class="field-group__label">Visibility</span>
              <select v-model="scenario.draftVisibilityStatus">
                <option value="DRAFT">DRAFT</option>
                <option value="PUBLISHED">PUBLISHED</option>
              </select>
            </label>

            <div class="toolbar">
              <button
                  class="btn btn--primary"
                  :disabled="savingScenarioId === scenario.id || !visibilityChanged(scenario)"
                  @click="saveVisibility(scenario)"
              >
                {{ savingScenarioId === scenario.id ? "Saving..." : "Save visibility" }}
              </button>

              <RouterLink :to="`/scenarios/${scenario.id}`" class="btn btn--ghost">
                Open
              </RouterLink>

              <RouterLink :to="`/scenarios/${scenario.id}/manage`" class="btn btn--ghost">
                Manage
              </RouterLink>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">
        <h3>No scenario found</h3>
        <p class="muted">Try another search term.</p>
      </div>
  </AdminWorkspaceShell>
</template>

<style scoped>
.admin-toolbar {
  align-items: center;
}

.admin-scenarios-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-scenario-card {
  border-radius: 22px;
  padding: 24px 26px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.admin-scenario-card__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.admin-scenario-card__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 0.35rem 0.8rem;
  background: var(--surface-alt);
  font-size: 0.82rem;
  font-weight: 600;
}

.badge--success {
  background: var(--accent-green);
  border-color: rgba(64, 92, 50, 0.2);
  color: var(--vignette-success-text);
}

.badge--muted {
  background: var(--vignette-primary-soft);
  border-color: var(--border);
  color: var(--primary);
}

.admin-scenario-card__description {
  margin-top: 14px;
}

.admin-scenario-card__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.meta-box {
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface-alt);
  padding: 12px 14px;
}

.meta-box__label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-soft);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.meta-box__value {
  font-weight: 600;
  color: var(--text);
}

.admin-scenario-card__controls {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 16px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.field-group {
  min-width: 220px;
}

.field-group__label {
  display: block;
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--text);
}

@media (max-width: 980px) {
  .admin-scenario-card__meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .admin-scenario-card {
    padding: 18px;
  }

  .admin-scenario-card__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .admin-scenario-card__meta {
    grid-template-columns: 1fr;
  }
}
</style>
