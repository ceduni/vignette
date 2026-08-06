<script setup>
import {computed, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";
import {
  fetchScenario,
  fetchScenarioHistory,
  fetchScenarioThumbnails,
  fetchThumbnailAudios,
  publishScenario,
  updateScenarioMetadata,
  updateScenarioStoryboard,
} from "../api/scenarios";
import {
  fetchAccreditationRequests,
  fetchAccreditations,
  grantAccreditation,
  reviewAccreditationRequest,
} from "../api/community";
import {useAuth} from "../composables/useAuth";
import TagAutocompleteInput from "@/components/TagAutocompleteInput.vue";

const props = defineProps({
  id: {type: String, required: true},
});

const {currentUser, isAdmin, loadMe} = useAuth();

const loading = ref(false);
const error = ref("");
const success = ref("");

const scenario = ref(null);
const thumbnails = ref([]);
const audioMap = ref({});
const history = ref([]);

const isOwner = computed(() =>
    !!currentUser.value &&
    !!scenario.value &&
    currentUser.value.username === scenario.value.authorUsername
);

const canManage = computed(() => isOwner.value || isAdmin.value);

const isPublished = computed(() => scenario.value?.visibilityStatus === "PUBLISHED");
const canEditContent = computed(() => canManage.value && (isAdmin.value || !isPublished.value));

const metadataForm = ref({
  title: "",
  description: "",
  tags: [],
});

const savingMetadata = ref(false);

const storyboardForm = ref({
  layoutMode: "PRESET",
  preset: "GRID_3",
  columns: 3,
});

const savingStoryboard = ref(false);
const publishing = ref(false);

const requestPermissionType = ref("SCENARIO_EDIT");
const pendingRequests = ref([]);
const grantedAccreditations = ref([]);
const loadingGovernance = ref(false);

const grantForm = ref({
  userId: "",
  permissionType: "SCENARIO_EDIT",
  note: "",
});

const totalAudioCount = computed(() =>
    Object.values(audioMap.value).reduce((sum, items) => sum + (items?.length ?? 0), 0)
);

async function loadScenarioData() {
  scenario.value = await fetchScenario(props.id);

  metadataForm.value = {
    title: scenario.value.title ?? "",
    description: scenario.value.description ?? "",
    tags: Array.isArray(scenario.value.tags) ? [...scenario.value.tags] : [],
  };

  storyboardForm.value = {
    layoutMode: scenario.value.storyboardLayoutMode ?? "PRESET",
    preset: scenario.value.storyboardPreset ?? "GRID_3",
    columns: scenario.value.storyboardColumns ?? 3,
  };

  thumbnails.value = await fetchScenarioThumbnails(props.id);
  history.value = await fetchScenarioHistory(props.id).catch(() => []);

  const map = {};
  await Promise.all(
      thumbnails.value.map(async (thumb) => {
        try {
          map[thumb.id] = await fetchThumbnailAudios(thumb.id);
        } catch {
          map[thumb.id] = [];
        }
      })
  );
  audioMap.value = map;
}

async function saveMetadata() {
  if (!canEditContent.value) return;

  savingMetadata.value = true;
  error.value = "";
  success.value = "";

  try {
    scenario.value = await updateScenarioMetadata(props.id, {
      title: metadataForm.value.title,
      description: metadataForm.value.description,
      tags: metadataForm.value.tags,
    });
    success.value = "Scenario metadata saved.";
  } catch (e) {
    error.value = e.message || "Failed to save scenario metadata.";
  } finally {
    savingMetadata.value = false;
  }
}

function formatHistoryWhen(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

async function loadGovernance() {
  if (!canManage.value) return;

  loadingGovernance.value = true;
  try {
    pendingRequests.value = await fetchAccreditationRequests(
        requestPermissionType.value,
        "SCENARIO",
        props.id
    );

    grantedAccreditations.value = await fetchAccreditations(
        requestPermissionType.value,
        "SCENARIO",
        props.id
    );
  } finally {
    loadingGovernance.value = false;
  }
}

async function loadAll() {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    await loadMe();
    await loadScenarioData();
    await loadGovernance();
  } catch (e) {
    error.value = e.message || "Failed to load scenario management.";
  } finally {
    loading.value = false;
  }
}

async function saveStoryboard() {
  if (!canEditContent.value) return;

  savingStoryboard.value = true;
  error.value = "";
  success.value = "";

  try {
    scenario.value = await updateScenarioStoryboard(props.id, {
      layoutMode: String(storyboardForm.value.layoutMode || "PRESET").toUpperCase(),
      preset: String(storyboardForm.value.preset || "GRID_3").toUpperCase(),
      columns: Number(storyboardForm.value.columns || 3),
    });
    success.value = "Storyboard settings saved.";
  } catch (e) {
    error.value = e.message || "Failed to save storyboard settings.";
  } finally {
    savingStoryboard.value = false;
  }
}

async function publishCurrentScenario() {
  publishing.value = true;
  error.value = "";
  success.value = "";

  try {
    scenario.value = await publishScenario(props.id);
    success.value = "Scenario published.";
  } catch (e) {
    error.value = e.message || "Failed to publish scenario.";
  } finally {
    publishing.value = false;
  }
}

async function reviewRequest(requestId, approved) {
  error.value = "";
  success.value = "";

  try {
    await reviewAccreditationRequest(requestId, {
      approved,
      reviewNote: approved ? "Approved from scenario management." : "Rejected from scenario management.",
    });
    success.value = approved ? "Request approved." : "Request rejected.";
    await loadGovernance();
  } catch (e) {
    error.value = e.message || "Failed to review request.";
  }
}

async function submitGrant() {
  error.value = "";
  success.value = "";

  try {
    await grantAccreditation({
      userId: Number(grantForm.value.userId),
      permissionType: grantForm.value.permissionType,
      scopeType: "SCENARIO",
      targetId: String(props.id),
      note: grantForm.value.note,
    });

    success.value = "Accreditation granted.";
    grantForm.value.userId = "";
    grantForm.value.note = "";
    await loadGovernance();
  } catch (e) {
    error.value = e.message || "Failed to grant accreditation.";
  }
}

onMounted(loadAll);
</script>

<template>
  <main class="page">
    <section class="section">
      <div class="section-heading">
        <div>
          <h1>{{ scenario?.title || "Manage scenario" }}</h1>
          <p class="muted">
            Private management view for publication, storyboard and scenario community governance.
          </p>
        </div>

        <div class="toolbar">
          <RouterLink :to="`/scenarios/${id}`" class="btn btn--ghost">
            Open scenario
          </RouterLink>
          <button
              v-if="canManage && scenario?.visibilityStatus !== 'PUBLISHED'"
              class="btn btn--primary"
              :disabled="publishing"
              @click="publishCurrentScenario"
          >
            {{ publishing ? "Publishing..." : "Publish scenario" }}
          </button>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div v-if="loading" class="loader-block">
        <span class="loader-spinner"></span>
        <span class="muted">Loading scenario management...</span>
      </div>

      <template v-else-if="scenario">
        <div class="manage-grid">
          <div class="manage-main">
            <section class="card">
              <h2>Scenario overview</h2>
              <div class="info-grid">
                <div>
                  <h3>Status</h3>
                  <p>{{ scenario.visibilityStatus || "-" }}</p>
                </div>
                <div>
                  <h3>Author</h3>
                  <p>{{ scenario.authorUsername || "-" }}</p>
                </div>
                <div>
                  <h3>Language</h3>
                  <p>{{ scenario.languageId || "-" }}</p>
                </div>
                <div>
                  <h3>Created</h3>
                  <p>{{ scenario.createdAt || "-" }}</p>
                </div>
                <div>
                  <h3>Thumbnails</h3>
                  <p>{{ thumbnails.length }}</p>
                </div>
                <div>
                  <h3>Audios</h3>
                  <p>{{ totalAudioCount }}</p>
                </div>
              </div>
            </section>

            <section v-if="canEditContent" class="card">
              <h2>Scenario metadata</h2>

              <div class="form-grid">
                <label>
                  Title
                  <input v-model="metadataForm.title"/>
                </label>

                <label class="field--full">
                  Description
                  <textarea v-model="metadataForm.description" rows="4"/>
                </label>

                <label class="field--full">
                  <TagAutocompleteInput
                      v-model="metadataForm.tags"
                      label="Tags"
                      placeholder="Add scenario tags"
                  />
                </label>
              </div>

              <div class="toolbar">
                <button class="btn btn--primary" :disabled="savingMetadata" @click="saveMetadata">
                  {{ savingMetadata ? "Saving..." : "Save metadata" }}
                </button>
              </div>
            </section>

            <section v-if="canEditContent" class="card">
              <h2>Storyboard settings</h2>

              <div class="form-grid">
                <label>
                  Layout mode
                  <select v-model="storyboardForm.layoutMode">
                    <option value="PRESET">Preset</option>
                    <option value="CUSTOM">Custom</option>
                  </select>
                </label>

                <label>
                  Preset
                  <select v-model="storyboardForm.preset">
                    <option value="GRID_3">Grid 3</option>
                    <option value="GRID_2">Grid 2</option>
                    <option value="CINEMATIC">Cinematic</option>
                    <option value="MANGA">Manga</option>
                  </select>
                </label>

                <label>
                  Columns
                  <input v-model="storyboardForm.columns" type="number" min="1" max="8"/>
                </label>
              </div>

              <div class="toolbar">
                <button class="btn btn--primary" :disabled="savingStoryboard" @click="saveStoryboard">
                  {{ savingStoryboard ? "Saving..." : "Save storyboard settings" }}
                </button>
              </div>
            </section>

            <section class="card">
              <h2>Media inventory</h2>

              <div v-if="thumbnails.length" class="card-grid">
                <article v-for="thumb in thumbnails" :key="thumb.id" class="card card--nested">
                  <h3>{{ thumb.title || `Thumbnail #${thumb.idx ?? thumb.id}` }}</h3>
                  <p class="muted">Order: {{ thumb.idx ?? "-" }}</p>
                  <p class="muted">Audios: {{ audioMap[thumb.id]?.length ?? 0 }}</p>
                </article>
              </div>

              <div v-else class="empty-state">
                <h3>No thumbnail yet</h3>
                <p class="muted">This scenario still has no uploaded storyboard image.</p>
              </div>
            </section>

            <section class="card">
              <h2>History</h2>

              <div v-if="history.length" class="stack-list">
                <article v-for="entry in history" :key="entry.id" class="card card--nested">
                  <p><strong>{{ entry.actorUsername }}</strong> {{ entry.summary }}</p>
                  <p class="muted">{{ formatHistoryWhen(entry.createdAt) }}</p>
                </article>
              </div>

              <div v-else class="empty-state">
                <h3>No changes recorded yet</h3>
              </div>
            </section>
          </div>

          <aside class="manage-side">
            <section v-if="canManage" class="card">
              <div class="toolbar toolbar--spread">
                <div>
                  <h2>Scenario governance</h2>
                  <p class="muted">Review scenario-scoped accreditation requests.</p>
                </div>

                <label class="compact-field">
                  Permission
                  <select v-model="requestPermissionType" @change="loadGovernance">
                    <option value="SCENARIO_EDIT">SCENARIO_EDIT</option>
                    <option value="SCENARIO_MODERATE">SCENARIO_MODERATE</option>
                    <option value="COMMUNITY_REVIEW">COMMUNITY_REVIEW</option>
                  </select>
                </label>
              </div>

              <div v-if="loadingGovernance" class="loader-block">
                <span class="loader-spinner"></span>
                <span class="muted">Loading governance...</span>
              </div>

              <template v-else>
                <h3>Pending / matching requests</h3>
                <div v-if="pendingRequests.length" class="stack-list">
                  <article v-for="req in pendingRequests" :key="req.id" class="card card--nested">
                    <p><strong>#{{ req.id }}</strong> · {{ req.requesterUsername }}</p>
                    <p class="muted">{{ req.permissionType }} · {{ req.status }}</p>
                    <p class="text">{{ req.motivation || "No motivation provided." }}</p>

                    <div v-if="req.status === 'PENDING'" class="toolbar">
                      <button class="btn btn--primary" @click="reviewRequest(req.id, true)">Approve</button>
                      <button class="btn btn--ghost" @click="reviewRequest(req.id, false)">Reject</button>
                    </div>
                  </article>
                </div>
                <p v-else class="muted">No request found for this filter.</p>

                <hr class="separator"/>

                <h3>Granted accreditations</h3>
                <div v-if="grantedAccreditations.length" class="stack-list">
                  <article v-for="acc in grantedAccreditations" :key="acc.id" class="card card--nested">
                    <p><strong>#{{ acc.id }}</strong> · {{ acc.username }}</p>
                    <p class="muted">{{ acc.permissionType }} · {{ acc.scopeType }} · target {{ acc.targetId }}</p>
                    <p class="muted">{{ acc.note || "No note." }}</p>
                  </article>
                </div>
                <p v-else class="muted">No granted accreditation for this filter.</p>

                <hr class="separator"/>

                <h3>Grant manually</h3>
                <div class="form-grid">
                  <label>
                    User ID
                    <input v-model="grantForm.userId" type="number" min="1"/>
                  </label>

                  <label>
                    Permission
                    <select v-model="grantForm.permissionType">
                      <option value="SCENARIO_EDIT">SCENARIO_EDIT</option>
                      <option value="SCENARIO_MODERATE">SCENARIO_MODERATE</option>
                      <option value="COMMUNITY_REVIEW">COMMUNITY_REVIEW</option>
                    </select>
                  </label>

                  <label class="field--full">
                    Note
                    <textarea v-model="grantForm.note" rows="3"/>
                  </label>
                </div>

                <div class="toolbar">
                  <button class="btn btn--primary" @click="submitGrant">
                    Grant accreditation
                  </button>
                </div>
              </template>
            </section>
          </aside>
        </div>
      </template>
    </section>
  </main>
</template>

<style scoped>
.manage-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.9fr);
  gap: 20px;
}

.manage-main,
.manage-side,
.stack-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card--nested {
  border-radius: 14px;
  background: var(--surface-alt);
}

.separator {
  border: none;
  border-top: 1px solid var(--border);
  margin: 12px 0;
}

.toolbar--spread {
  justify-content: space-between;
  align-items: flex-start;
}

.compact-field {
  min-width: 180px;
}

.field--full {
  grid-column: 1 / -1;
}

@media (max-width: 980px) {
  .manage-grid {
    grid-template-columns: 1fr;
  }
}
</style>