<script setup>
import {computed, onMounted, ref} from "vue";
import AdminWorkspaceShell from "../components/admin/AdminWorkspaceShell.vue";
import {
  fetchAccreditationRequests,
  fetchAccreditations,
  grantAccreditation,
  reviewAccreditationRequest,
} from "../api/community";

const loading = ref(false);
const error = ref("");
const success = ref("");

const permissionType = ref("COMMUNITY_REVIEW");
const requests = ref([]);
const accreditations = ref([]);

const availablePermissions = [
  "COMMUNITY_REVIEW",
  "LANGUAGE_EDIT",
  "SCENARIO_EDIT",
  "SCENARIO_MODERATE",
];

const grantForm = ref({
  userId: "",
  permissionType: "COMMUNITY_REVIEW",
  note: "",
});

const pendingCount = computed(() =>
    requests.value.filter((req) => req.status === "PENDING").length
);

function statusTone(status) {
  if (status === "APPROVED") return "badge--success";
  if (status === "REJECTED") return "badge--danger";
  return "badge--info";
}

async function loadAll() {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    requests.value = await fetchAccreditationRequests(
        permissionType.value,
        "GLOBAL",
        ""
    );

    accreditations.value = await fetchAccreditations(
        permissionType.value,
        "GLOBAL",
        ""
    );
  } catch (e) {
    error.value = e.message || "Failed to load admin community data.";
  } finally {
    loading.value = false;
  }
}

async function reviewRequest(requestId, approved) {
  error.value = "";
  success.value = "";

  try {
    await reviewAccreditationRequest(requestId, {
      approved,
      reviewNote: approved ? "Approved by admin." : "Rejected by admin.",
    });
    success.value = approved ? "Request approved." : "Request rejected.";
    await loadAll();
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
      scopeType: "GLOBAL",
      targetId: null,
      note: grantForm.value.note,
    });

    success.value = "Accreditation granted.";
    grantForm.value.userId = "";
    grantForm.value.note = "";
    await loadAll();
  } catch (e) {
    error.value = e.message || "Failed to grant accreditation.";
  }
}

onMounted(loadAll);
</script>

<template>
  <AdminWorkspaceShell
      title="Community"
      subtitle="Review requests and manage grants."
  >
      <div class="section-heading">
        <div>
          <h2>Community</h2>
          <p class="muted">
            Global moderation for accreditation requests and direct grants.
          </p>
        </div>

        <div class="toolbar admin-toolbar">
          <label class="field-group field-group--compact">
            <span class="field-group__label">Permission filter</span>
            <select v-model="permissionType" @change="loadAll">
              <option v-for="permission in availablePermissions" :key="permission" :value="permission">
                {{ permission }}
              </option>
            </select>
          </label>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div v-if="loading" class="loader-block">
        <span class="loader-spinner"></span>
        <span class="muted">Loading admin community data...</span>
      </div>

      <template v-else>
        <div class="card-grid admin-community-stats">
          <section class="card">
            <h2>Filtered requests</h2>
            <p class="text">{{ requests.length }}</p>
          </section>

          <section class="card">
            <h2>Pending</h2>
            <p class="text">{{ pendingCount }}</p>
          </section>

          <section class="card">
            <h2>Granted</h2>
            <p class="text">{{ accreditations.length }}</p>
          </section>
        </div>

        <div class="admin-community-layout">
          <section class="card admin-community-panel">
            <div class="admin-community-panel__header">
              <div>
                <h2>Accreditation requests</h2>
                <p class="muted">
                  Review requests for the currently selected permission.
                </p>
              </div>
            </div>

            <div v-if="requests.length" class="admin-community-list">
              <article v-for="req in requests" :key="req.id" class="community-item">
                <div class="community-item__top">
                  <div>
                    <h3>#{{ req.id }} · {{ req.requesterUsername }}</h3>
                    <p class="muted">
                      {{ req.permissionType }} · {{ req.scopeType }}
                      <span v-if="req.targetId"> · target {{ req.targetId }}</span>
                    </p>
                  </div>

                  <span class="badge" :class="statusTone(req.status)">
                    {{ req.status }}
                  </span>
                </div>

                <p class="text">{{ req.motivation || "No motivation provided." }}</p>

                <div v-if="req.status === 'PENDING'" class="toolbar">
                  <button class="btn btn--primary" @click="reviewRequest(req.id, true)">
                    Approve
                  </button>
                  <button class="btn btn--ghost" @click="reviewRequest(req.id, false)">
                    Reject
                  </button>
                </div>
              </article>
            </div>

            <div v-else class="empty-state empty-state--embedded">
              <h3>No request found</h3>
              <p class="muted">No accreditation request matches this filter.</p>
            </div>
          </section>

          <section class="card admin-community-panel">
            <div class="admin-community-panel__header">
              <div>
                <h2>Granted accreditations</h2>
                <p class="muted">
                  Current grants for the selected permission.
                </p>
              </div>
            </div>

            <div v-if="accreditations.length" class="admin-community-list">
              <article v-for="acc in accreditations" :key="acc.id" class="community-item">
                <div class="community-item__top">
                  <div>
                    <h3>#{{ acc.id }} · {{ acc.username }}</h3>
                    <p class="muted">
                      {{ acc.permissionType }} · {{ acc.scopeType }}
                      <span v-if="acc.targetId"> · target {{ acc.targetId }}</span>
                    </p>
                  </div>

                  <span class="badge badge--success">Granted</span>
                </div>

                <p class="muted">{{ acc.note || "No note." }}</p>
              </article>
            </div>

            <div v-else class="empty-state empty-state--embedded">
              <h3>No accreditation found</h3>
              <p class="muted">No grant matches this filter.</p>
            </div>

            <hr class="separator"/>

            <div class="admin-community-panel__header">
              <div>
                <h2>Grant manually</h2>
                <p class="muted">
                  Create a direct accreditation without a request flow.
                </p>
              </div>
            </div>

            <div class="form-grid">
              <label>
                User ID
                <input v-model="grantForm.userId" type="number" min="1"/>
              </label>

              <label>
                Permission
                <select v-model="grantForm.permissionType">
                  <option v-for="permission in availablePermissions" :key="permission" :value="permission">
                    {{ permission }}
                  </option>
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
          </section>
        </div>
      </template>
  </AdminWorkspaceShell>
</template>

<style scoped>
.admin-toolbar {
  align-items: center;
}

.field-group__label {
  display: block;
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--text);
}

.field-group--compact {
  min-width: 220px;
}

.admin-community-stats {
  margin-bottom: 18px;
}

.admin-community-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
}

.admin-community-panel {
  border-radius: 22px;
  padding: 24px 26px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.admin-community-panel__header {
  margin-bottom: 14px;
}

.admin-community-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.community-item {
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface-alt);
  padding: 16px 18px;
}

.community-item__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 10px;
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

.badge--danger {
  background: var(--vignette-danger-bg);
  border-color: rgba(138, 31, 45, 0.18);
  color: var(--vignette-danger-text);
}

.badge--info {
  background: var(--vignette-primary-soft);
  border-color: var(--border);
  color: var(--primary);
}

.separator {
  border: none;
  border-top: 1px solid var(--border);
  margin: 18px 0;
}

.empty-state--embedded {
  margin-top: 8px;
}

.field--full {
  grid-column: 1 / -1;
}

@media (max-width: 980px) {
  .admin-community-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .admin-community-panel {
    padding: 18px;
  }

  .community-item__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .field-group--compact {
    min-width: 0;
    width: 100%;
  }
}
</style>
