<script setup>
import {computed, onBeforeUnmount, onMounted, reactive, ref} from "vue";
import {Circle, CircleCheck, LoaderCircle} from "lucide-vue-next";
import AdminWorkspaceShell from "../components/admin/AdminWorkspaceShell.vue";
import {
  fetchGlottologAdminSettings,
  fetchGlottologNotifications,
  fetchGlottologPreview,
  fetchGlottologUpdateHistory,
  fetchGlottologUpdateRequests,
  startGlottologUpdateJob,
  updateGlottologAdminSettings,
} from "../api/admin";

const loading = ref(false);
const error = ref("");

const glottologPreview = ref(null);
const glottologSettings = ref(null);
const glottologHistory = ref([]);
const glottologNotifications = ref([]);
const glottologJob = ref(null);

const glottologError = ref("");
const glottologSuccess = ref("");
const glottologSettingsSaving = ref(false);
const glottologSettingsForm = reactive({
  autoUpdateEnabled: false,
  frequencyDays: 30,
  notificationEmail: "",
});
/** True seulement pendant une mise à jour lancée depuis ce dashboard. */
const glottologAwaitingResult = ref(false);
/** Manual request id while waiting for the Python worker. */
const pendingRequestId = ref(null);

let glottologPollHandle = null;
let glottologActivityPollHandle = null;

const isLegacyPipeline = computed(() => !!glottologSettings.value?.legacyPipelineEnabled);

const pipelineStatus = computed(() => glottologSettings.value?.pipelineStatus || "");
const pipelineMessage = computed(
    () => glottologSettings.value?.pipelineStatusMessage || ""
);

const PYTHON_ACTIVE_STATUSES = new Set([
  "CHECKING_REMOTE",
  "DOWNLOADING",
  "VALIDATING",
  "TRANSFORMING",
  "IMPORTING",
  "LOCKED",
  "RETRY_SCHEDULED",
]);

const latestSuccessfulHistoryEntry = computed(() =>
    glottologHistory.value.find((entry) => entry.status === "SUCCEEDED") ?? null
);

const latestHistoryEntry = computed(() => glottologHistory.value[0] ?? null);

const isGlottologJobRunning = computed(() => {
  if (isLegacyPipeline.value) {
    return glottologJob.value?.state === "RUNNING";
  }
  if (glottologAwaitingResult.value || pendingRequestId.value != null) {
    return true;
  }
  const status = pipelineStatus.value;
  if (!status || String(status).startsWith("FAILED")) return false;
  return PYTHON_ACTIVE_STATUSES.has(status);
});

const isGlottologSourceUnchanged = computed(() => {
  if (isLegacyPipeline.value) {
    return !!glottologJob.value?.result?.sourceUnchanged;
  }
  return pipelineStatus.value === "NO_CHANGE";
});

/** Progress 0–100 derived from Python pipeline stage (or legacy job %). */
const glottologProgressPercent = computed(() => {
  if (isLegacyPipeline.value) {
    return Number(glottologJob.value?.progressPercent ?? 0);
  }
  if (!isGlottologJobRunning.value) {
    return latestSuccessfulHistoryEntry.value || isGlottologSourceUnchanged.value ? 100 : 0;
  }
  const status = pipelineStatus.value;
  const map = {
    IDLE: 5,
    WAITING: 5,
    AUTO_DISABLED: 5,
    LOCKED: 8,
    CHECKING_REMOTE: 20,
    DOWNLOADING: 40,
    VALIDATING: 55,
    TRANSFORMING: 70,
    IMPORTING: 85,
    NO_CHANGE: 100,
    COMPLETED: 100,
  };
  if (String(status).startsWith("FAILED")) return map.CHECKING_REMOTE;
  return map[status] ?? 10;
});

const glottologProgressWidth = computed(() => `${glottologProgressPercent.value}%`);

const glottologNextRunLabel = computed(() => {
  // Use saved settings (not the unsaved form toggle) so this matches the worker.
  const autoOn = !!glottologSettings.value?.autoUpdateEnabled;
  if (!autoOn) {
    return "Not scheduled (auto-update off)";
  }
  const next = glottologSettings.value?.nextScheduledUpdateAt;
  if (!next) {
    return "No date yet — start/restart the Python worker";
  }
  const nextDate = new Date(next);
  if (Number.isNaN(nextDate.getTime())) return "—";
  if (nextDate.getTime() <= Date.now()) {
    return `Due since ${formatDateTime(next)}`;
  }
  return formatDateTime(next);
});

const glottologScheduleAnchorLabel = computed(() => {
  // Prefer the most recent successful run (full import OR checksum NO_CHANGE).
  // lastSuccessAt alone stays stale when Zenodo is unchanged.
  const candidates = [
    glottologSettings.value?.lastSuccessAt,
    glottologSettings.value?.lastCheckAt,
    latestSuccessfulHistoryEntry.value?.finishedAt,
  ].filter(Boolean);
  if (!candidates.length) return "No successful sync yet";
  const latest = candidates
      .map((value) => ({ value, t: new Date(value).getTime() }))
      .filter((row) => !Number.isNaN(row.t))
      .sort((a, b) => b.t - a.t)[0];
  return latest ? formatDateTime(latest.value) : "No successful sync yet";
});

const glottologPipelineStatusLabel = computed(() =>
    pipelineMessage.value || pipelineStatus.value || "—"
);

// Shared timeline for Java stages + Python pipeline statuses.
const glottologFullTimeline = [
  {
    key: "check",
    label: "Zenodo check",
    detailIdle: "Check remote checksum before download",
    detailDone: "Remote checksum compared",
    stages: [
      "QUEUED", "PREPARING_DOWNLOAD", "CHECKING_REMOTE",
      "IDLE", "WAITING", "LOCKED", "AUTO_DISABLED",
    ],
  },
  {
    key: "download",
    label: "Glottolog download",
    detailIdle: "Fetch / convert filtered CSV",
    detailDone: "Download finished",
    stages: ["DOWNLOADING"],
  },
  {
    key: "compare",
    label: "Validate & transform",
    detailIdle: "Validate rows and prepare import",
    detailDone: "Rows validated",
    stages: [
      "COMPARING_SOURCE", "PREPARING_SYNC", "ANALYZING_CSV",
      "VALIDATING", "TRANSFORMING",
    ],
  },
  {
    key: "import",
    label: "Database sync",
    detailIdle: "Inserts and updates in the database",
    detailDone: "Database written",
    stages: ["SYNCING_DATABASE", "WRITING_DATABASE", "IMPORTING"],
  },
  {
    key: "done",
    label: "Finalization",
    detailIdle: "Update result",
    detailDone: "Update finished",
    stages: ["COMPLETED", "SOURCE_UNCHANGED", "NO_CHANGE"],
  },
];

const glottologUnchangedTimeline = [
  {
    key: "check",
    label: "Zenodo check",
    detailDone: "Checksum matches the last imported release",
    stages: ["CHECKING_REMOTE", "PREPARING_DOWNLOAD", "QUEUED", "IDLE", "WAITING"],
  },
  {
    key: "skip-download",
    label: "Download",
    detailDone: "Skipped — same Zenodo source data",
    stages: [],
  },
  {
    key: "skip-sync",
    label: "Database sync",
    detailDone: "Skipped — no database writes needed",
    stages: ["COMPARING_SOURCE", "VALIDATING", "TRANSFORMING"],
  },
  {
    key: "done",
    label: "Result",
    detailDone: "No changes since the last update",
    stages: ["SOURCE_UNCHANGED", "COMPLETED", "NO_CHANGE"],
  },
];

const glottologTimelineStageOrder = computed(() =>
    isGlottologSourceUnchanged.value && !isGlottologJobRunning.value
        ? glottologUnchangedTimeline
        : glottologFullTimeline
);

function resolveActiveStage() {
  if (isLegacyPipeline.value) {
    return glottologJob.value?.stage || "";
  }
  return pipelineStatus.value || "";
}

const glottologActiveTimelineIndex = computed(() => {
  const steps = glottologTimelineStageOrder.value;
  const stage = resolveActiveStage();

  if (isLegacyPipeline.value) {
    const state = glottologJob.value?.state;
    if (state === "SUCCEEDED") return steps.length;
    if (state === "FAILED") {
      const foundIndex = steps.findIndex((item) => item.stages.includes(stage));
      return foundIndex >= 0 ? foundIndex : 0;
    }
    if (state !== "RUNNING") return latestSuccessfulHistoryEntry.value ? steps.length : -1;
    const foundIndex = steps.findIndex((item) => item.stages.includes(stage));
    return foundIndex >= 0 ? foundIndex : 0;
  }

  // Python path
  if (String(stage).startsWith("FAILED")) {
    const foundIndex = steps.findIndex((item) => item.stages.includes(stage));
    // Failures often happen mid-flight; map known failed_* to nearest step via message not stage list.
    if (stage.includes("FETCH") || stage.includes("CHECK")) return 0;
    if (stage.includes("VALIDATION") || stage.includes("TRANSFORM")) return 2;
    if (stage.includes("IMPORT")) return 3;
    return foundIndex >= 0 ? foundIndex : 0;
  }

  if (!isGlottologJobRunning.value) {
    if (stage === "NO_CHANGE" || isGlottologSourceUnchanged.value) return steps.length;
    if (stage === "COMPLETED" || latestSuccessfulHistoryEntry.value) return steps.length;
    return -1;
  }

  // Waiting for worker claim: keep first step active.
  if (!stage || stage === "IDLE" || stage === "WAITING" || stage === "AUTO_DISABLED") {
    return 0;
  }

  const foundIndex = steps.findIndex((item) => item.stages.includes(stage));
  return foundIndex >= 0 ? foundIndex : 0;
});

const glottologTimelineSteps = computed(() =>
    glottologTimelineStageOrder.value.map((step, index) => {
      let status = "pending";
      if (glottologActiveTimelineIndex.value > index) {
        status = "done";
      } else if (
          glottologActiveTimelineIndex.value === index
          && isGlottologJobRunning.value
      ) {
        status = "active";
      } else if (
          glottologActiveTimelineIndex.value === index
          && String(pipelineStatus.value).startsWith("FAILED")
      ) {
        status = "active";
      }

      let detail = step.detailIdle || "Waiting";
      if (status === "active") {
        detail = pipelineMessage.value
            || glottologJob.value?.stageLabel
            || glottologJob.value?.message
            || (pendingRequestId.value != null && (!pipelineStatus.value || ["IDLE", "WAITING", "AUTO_DISABLED"].includes(pipelineStatus.value))
                ? "Waiting for Python worker (same PostgreSQL DB)…"
                : null)
            || step.detailDone
            || detail;
      } else if (status === "done") {
        detail = step.detailDone || "Step completed";
      }

      return {
        ...step,
        status,
        detail,
        icon: status === "done" ? CircleCheck : status === "active" ? LoaderCircle : Circle,
      };
    })
);

const glottologStatusHeadline = computed(() => {
  if (isGlottologJobRunning.value) {
    return pipelineMessage.value
        || glottologJob.value?.message
        || glottologJob.value?.stageLabel
        || (pendingRequestId.value != null ? "Waiting for Python worker…" : "Update in progress…");
  }
  if (isGlottologSourceUnchanged.value) {
    return "No changes since the last update";
  }
  if (glottologJob.value?.state === "SUCCEEDED" || pipelineStatus.value === "COMPLETED") {
    return "Sync completed successfully";
  }
  if (latestSuccessfulHistoryEntry.value) {
    return "Last sync completed";
  }
  return "No recent sync";
});

const isGlottologBusy = computed(
    () => isGlottologJobRunning.value || glottologAwaitingResult.value
);

const glottologHeroStats = computed(() => {
  const live = latestHistoryEntry.value;
  const last = latestSuccessfulHistoryEntry.value;
  const busy = isGlottologJobRunning.value;

  const languagesInDb = formatCount(
      glottologPreview.value?.databaseCount
      ?? glottologJob.value?.databaseCount
      ?? live?.databaseCount
      ?? last?.databaseCount
  );

  // Same labels idle + during update. NO_CHANGE / missing counts → 0 when idle.
  const addedRaw = busy
      ? (glottologJob.value?.inserted ?? live?.inserted)
      : (last?.inserted ?? 0);
  const updatedRaw = busy
      ? (glottologJob.value?.updated ?? live?.updated)
      : (last?.updated ?? 0);

  return [
    { label: "Languages in DB", value: languagesInDb },
    { label: "Added", value: busy && addedRaw == null ? "—" : formatCount(addedRaw ?? 0) },
    { label: "Updated", value: busy && updatedRaw == null ? "—" : formatCount(updatedRaw ?? 0) },
  ];
});

function formatDateTime(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-CA", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatCount(value) {
  if (value == null) return "—";
  return value.toLocaleString();
}

function applyGlottologSettings(settings) {
  if (!settings) return;
  glottologSettingsForm.autoUpdateEnabled = !!settings.autoUpdateEnabled;
  glottologSettingsForm.frequencyDays = settings.frequencyDays || 30;
  glottologSettingsForm.notificationEmail = settings.notificationEmail || "";
}

async function toggleGlottologAutoUpdate() {
  glottologSettingsForm.autoUpdateEnabled = !glottologSettingsForm.autoUpdateEnabled;
  if (glottologSettingsForm.autoUpdateEnabled && !glottologSettingsForm.frequencyDays) {
    glottologSettingsForm.frequencyDays = 30;
  }
  await persistGlottologSchedule(false);
}

async function onGlottologFrequencyChange() {
  await persistGlottologSchedule(false);
}

async function persistGlottologSchedule(verifyImmediately) {
  glottologSettingsSaving.value = true;
  glottologError.value = "";
  glottologSuccess.value = "";

  try {
    const settings = await updateGlottologAdminSettings({
      autoUpdateEnabled: glottologSettingsForm.autoUpdateEnabled,
      frequencyDays: glottologSettingsForm.autoUpdateEnabled
          ? Number(glottologSettingsForm.frequencyDays)
          : glottologSettingsForm.frequencyDays,
      notificationEmail: glottologSettingsForm.notificationEmail,
      verifyImmediately: !!verifyImmediately,
    });
    glottologSettings.value = settings;
    applyGlottologSettings(settings);
    glottologSuccess.value = verifyImmediately
        ? "Schedule saved and manual verification requested."
        : "Schedule updated (Python worker will recalculate next run).";
  } catch (e) {
    glottologError.value = e.message || "Could not update the Glottolog schedule.";
  } finally {
    glottologSettingsSaving.value = false;
  }
}

async function loadGlottologPreview() {
  glottologPreview.value = await fetchGlottologPreview();
}

async function loadGlottologHistory() {
  glottologHistory.value = await fetchGlottologUpdateHistory();
}

async function loadGlottologNotifications() {
  glottologNotifications.value = await fetchGlottologNotifications();
}

async function refreshGlottologSettings({syncForm = true} = {}) {
  const settings = await fetchGlottologAdminSettings();
  glottologSettings.value = settings;
  if (syncForm) {
    applyGlottologSettings(settings);
  }
}

async function saveNotificationEmail() {
  glottologSettingsSaving.value = true;
  glottologError.value = "";
  glottologSuccess.value = "";
  try {
    const settings = await updateGlottologAdminSettings({
      notificationEmail: glottologSettingsForm.notificationEmail.trim(),
    });
    glottologSettings.value = settings;
    applyGlottologSettings(settings);
    glottologSuccess.value = settings.notificationEmail
        ? `Notification email saved: ${settings.notificationEmail}`
        : "Notification email cleared.";
  } catch (e) {
    glottologError.value = e.message || "Could not save the notification email.";
  } finally {
    glottologSettingsSaving.value = false;
  }
}

async function runGlottologUpdate() {
  const confirmed = window.confirm(
      "Request a Glottolog update?\n\n"
      + "This deposits a manual request for the external Python worker.\n"
      + "It does not change the automatic schedule."
  );
  if (!confirmed) return;

  glottologError.value = "";
  glottologSuccess.value = "";
  glottologAwaitingResult.value = true;
  pendingRequestId.value = null;

  try {
    const result = await startGlottologUpdateJob();
    if (result?.state) {
      // Legacy Java job shape
      glottologJob.value = result;
      await loadGlottologHistory();
      await loadGlottologJobStatus();
      if (glottologJob.value?.state === "RUNNING") {
        startGlottologPolling();
      }
    } else {
      pendingRequestId.value = result?.id ?? null;
      await refreshGlottologSettings({syncForm: false});
      await loadGlottologHistory();
      startGlottologPolling();
    }
  } catch (e) {
    glottologAwaitingResult.value = false;
    pendingRequestId.value = null;
    glottologError.value = e.message || "Glottolog update request failed.";
  }
}

async function loadGlottologJobStatus() {
  try {
    await refreshGlottologSettings({syncForm: false});
    if (glottologAwaitingResult.value || pendingRequestId.value != null) {
      await loadGlottologHistory();
    }

    if (pendingRequestId.value != null) {
      const requests = await fetchGlottologUpdateRequests();
      const req = requests.find((row) => row.id === pendingRequestId.value);
      const reqStatus = req?.status;
      if (reqStatus === "SUCCEEDED" || reqStatus === "FAILED") {
        stopGlottologPolling();
        glottologAwaitingResult.value = false;
        pendingRequestId.value = null;
        if (reqStatus === "FAILED") {
          glottologError.value = req?.errorMessage
              || glottologSettings.value?.lastError
              || "Glottolog update failed.";
        } else {
          const status = glottologSettings.value?.pipelineStatus;
          glottologSuccess.value = status === "NO_CHANGE"
              ? "No changes since the last update (Zenodo checksum unchanged)."
              : "Glottolog update completed.";
        }
        await Promise.all([loadGlottologPreview(), loadGlottologHistory()]);
      }
      return;
    }

    const status = glottologSettings.value?.pipelineStatus;
    const failed = status && String(status).startsWith("FAILED");

    if (glottologAwaitingResult.value && (failed || status === "NO_CHANGE" || status === "COMPLETED")) {
      stopGlottologPolling();
      glottologAwaitingResult.value = false;
      if (failed) {
        glottologError.value = glottologSettings.value?.lastError || "Glottolog update failed.";
      } else if (status === "NO_CHANGE") {
        glottologSuccess.value = "No changes since the last update (Zenodo checksum unchanged).";
      } else {
        glottologSuccess.value = "Glottolog update completed.";
      }
      await Promise.all([loadGlottologPreview(), loadGlottologHistory()]);
    }
  } catch (e) {
    stopGlottologPolling();
    glottologAwaitingResult.value = false;
    pendingRequestId.value = null;
    glottologError.value = e.message || "Could not track Glottolog progress.";
  }
}

function stopGlottologPolling() {
  if (glottologPollHandle) {
    window.clearInterval(glottologPollHandle);
    glottologPollHandle = null;
  }
}

function startGlottologPolling() {
  stopGlottologPolling();
  glottologPollHandle = window.setInterval(async () => {
    await loadGlottologJobStatus();
  }, 1000);
}

function stopGlottologActivityPolling() {
  if (glottologActivityPollHandle) {
    window.clearInterval(glottologActivityPollHandle);
    glottologActivityPollHandle = null;
  }
}

function startGlottologActivityPolling() {
  stopGlottologActivityPolling();
  glottologActivityPollHandle = window.setInterval(async () => {
    try {
      await Promise.all([
        refreshGlottologSettings({syncForm: false}),
        loadGlottologHistory(),
        loadGlottologNotifications(),
      ]);
    } catch {
      /* ignore background refresh errors */
    }
  }, 4000);
}

function formatTriggerLabel(entry) {
  const raw = entry?.triggerType || "";
  if (raw === "AUTO") return "Automatic";
  if (raw === "MANUAL") return "Manual";
  if (raw === "RETRY") return "Retry";
  if (entry?.triggeredByUsername?.startsWith("system:auto")) return "Automatic";
  if (entry?.triggeredByUsername?.startsWith("system:retry")) return "Retry";
  return raw || "—";
}

function formatPipelineLabel(entry) {
  return entry?.pipelineStatus || entry?.status || "—";
}

async function loadAdminDashboard() {
  loading.value = true;
  error.value = "";
  glottologError.value = "";

  try {
    const [
      previewData,
      settingsData,
      historyData,
      notificationData,
    ] = await Promise.all([
      fetchGlottologPreview(),
      fetchGlottologAdminSettings(),
      fetchGlottologUpdateHistory(),
      fetchGlottologNotifications(),
    ]);

    glottologPreview.value = previewData;
    glottologSettings.value = settingsData;
    applyGlottologSettings(settingsData);
    glottologHistory.value = historyData;
    glottologNotifications.value = notificationData;
  } catch (e) {
    error.value = e.message || "Could not load the admin dashboard.";
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await loadAdminDashboard();
  startGlottologActivityPolling();
});

onBeforeUnmount(() => {
  stopGlottologPolling();
  stopGlottologActivityPolling();
});
</script>

<template>
  <AdminWorkspaceShell
      title="Dashboard"
      subtitle="Admin control center for Glottolog sync."
  >
    <div class="admin-dashboard">
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="glottologError" class="error">{{ glottologError }}</p>
      <p v-if="glottologSuccess" class="success">{{ glottologSuccess }}</p>

      <div v-if="loading" class="loader-block">
        <span class="loader-spinner"></span>
        <span>Loading admin panel…</span>
      </div>

      <template v-else>
        <section class="glottolog-hero">
          <div class="glottolog-hero__main">
            <div class="hero-title-row">
              <div class="hero-icon">⌁</div>
              <div>
                <p class="eyebrow">Data source</p>
                <h3>Glottolog Management</h3>
              </div>
            </div>

            <div class="hero-meta">
              <div>
                <span>Current version</span>
                <strong>{{ glottologSettings?.glottologVersion || "—" }}</strong>
              </div>

              <div>
                <span>Last sync</span>
                <strong>{{ formatDateTime(latestSuccessfulHistoryEntry?.finishedAt) }}</strong>
              </div>

              <div>
                <span>Status</span>
                <strong :class="isGlottologBusy ? 'pill pill--running' : 'pill pill--success'">
                  {{ isGlottologBusy ? "Running" : "Completed" }}
                </strong>
              </div>
            </div>

            <div class="sync-state">
              <div class="sync-state__label">
                <span>{{ glottologStatusHeadline }}</span>
                <strong>{{ glottologProgressPercent }}%</strong>
              </div>

              <div class="progress-track">
                <div
                    class="progress-bar"
                    :class="{ 'progress-bar--done': !isGlottologBusy && (latestSuccessfulHistoryEntry || isGlottologSourceUnchanged) }"
                    :style="{ width: isGlottologBusy ? glottologProgressWidth : ((latestSuccessfulHistoryEntry || isGlottologSourceUnchanged) ? '100%' : '0%') }"
                ></div>
              </div>
            </div>

            <div class="hero-stats">
              <div v-for="stat in glottologHeroStats" :key="stat.label">
                <span>{{ stat.label }}</span>
                <strong>{{ stat.value }}</strong>
              </div>
            </div>
          </div>

          <div class="glottolog-visual">
            <button
                type="button"
                class="btn btn--primary update-btn"
                :disabled="isGlottologBusy"
                @click="runGlottologUpdate"
            >
              {{ isGlottologBusy ? "Updating…" : "Update Glottolog" }}
            </button>

            <div class="timeline-card">
              <div
                  v-for="(step, index) in glottologTimelineSteps"
                  :key="step.label"
                  class="timeline-step"
                  :class="`timeline-step--${step.status}`"
              >
                <div class="timeline-step__rail">
                  <component
                      :is="step.icon"
                      :key="`${step.label}-${step.status}`"
                      class="timeline-step__icon"
                      :class="`timeline-step__icon--${step.status}`"
                  />
                  <span
                      v-if="index < glottologTimelineSteps.length - 1"
                      class="timeline-step__line"
                      :class="{
                        'is-complete': glottologActiveTimelineIndex > index,
                        'is-active': glottologActiveTimelineIndex === index && isGlottologBusy,
                      }"
                  ></span>
                </div>

                <div class="timeline-step__content">
                  <strong>{{ step.label }}</strong>
                  <small>{{ step.detail }}</small>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="schedule-card">
          <div>
            <p class="eyebrow">Automation</p>
            <h3>Schedule</h3>
          </div>

          <button
              type="button"
              class="schedule-toggle"
              :class="{ 'is-enabled': glottologSettingsForm.autoUpdateEnabled }"
              :disabled="glottologSettingsSaving"
              @click="toggleGlottologAutoUpdate"
          >
            <span class="schedule-toggle__control"></span>
            <span>
              <strong>Auto update</strong>
              <small>{{ glottologSettingsForm.autoUpdateEnabled ? "Enabled" : "Disabled" }}</small>
            </span>
          </button>

          <label class="schedule-field">
            <span>Frequency (days)</span>
            <input
                type="number"
                min="1"
                :max="glottologSettings?.frequencyDaysMax || 365"
                v-model.number="glottologSettingsForm.frequencyDays"
                :disabled="!glottologSettingsForm.autoUpdateEnabled || glottologSettingsSaving"
                @change="onGlottologFrequencyChange"
            >
          </label>

          <label class="schedule-field">
            <span>Notification email</span>
            <input
                type="email"
                placeholder="you@example.com"
                v-model="glottologSettingsForm.notificationEmail"
                :disabled="glottologSettingsSaving"
                @keydown.enter.prevent="saveNotificationEmail"
            >
            <small class="schedule-hint">
              Change anytime, then Save. Currently saved:
              <strong>{{ glottologSettings?.notificationEmail || "none" }}</strong>.
              SMTP credentials stay in the worker <code>.env</code> (not in Git).
            </small>
          </label>

          <div class="schedule-actions">
            <button
                type="button"
                class="btn btn--primary"
                :disabled="glottologSettingsSaving"
                @click="saveNotificationEmail"
            >
              Save notification email
            </button>
          </div>

          <div class="schedule-meta">
            <p class="schedule-next">
              <span>Based on last successful run</span>
              <strong>{{ glottologScheduleAnchorLabel }}</strong>
            </p>
            <p class="schedule-next">
              <span>Next automatic update (Python)</span>
              <strong>{{ glottologNextRunLabel }}</strong>
            </p>
            <p class="schedule-next">
              <span>Pipeline status</span>
              <strong>{{ glottologPipelineStatusLabel }}</strong>
            </p>
            <p class="schedule-next">
              <span>Last imported checksum</span>
              <strong>{{ glottologSettings?.lastImportedChecksum || "—" }}</strong>
            </p>
            <p v-if="glottologSettings?.lastError" class="schedule-next">
              <span>Last error</span>
              <strong>{{ glottologSettings.lastError }}</strong>
            </p>
          </div>

          <button
              type="button"
              class="btn btn--primary"
              :disabled="glottologSettingsSaving || !glottologSettingsForm.autoUpdateEnabled"
              @click="persistGlottologSchedule(true)"
          >
            Save and verify now
          </button>
        </section>

        <details class="activity-card">
          <summary class="activity-summary">
            <span class="activity-chevron" aria-hidden="true">▼</span>
            <span>
              <span class="eyebrow">Trace</span>
              <strong>Update activity</strong>
              <small>{{ glottologHistory.length }} run(s)</small>
            </span>
          </summary>

          <div v-if="!glottologHistory.length" class="activity-empty">
            No updates yet.
          </div>

          <ul v-else class="activity-list">
            <li
                v-for="entry in glottologHistory"
                :key="entry.id"
                class="activity-row"
            >
              <div class="activity-row__main">
                <strong>#{{ entry.id }} · {{ formatTriggerLabel(entry) }}</strong>
                <span>{{ formatPipelineLabel(entry) }} · {{ entry.status }}</span>
              </div>
              <div class="activity-row__meta">
                <span>{{ entry.triggeredByUsername || "—" }}</span>
                <span>{{ formatDateTime(entry.finishedAt || entry.startedAt) }}</span>
              </div>
              <div class="activity-row__stats">
                <span>DB {{ formatCount(entry.databaseCount) }}</span>
                <span>Added {{ formatCount(entry.inserted ?? 0) }}</span>
                <span>Updated {{ formatCount(entry.updated ?? 0) }}</span>
              </div>
              <p v-if="entry.errorMessage" class="activity-row__error">
                {{ entry.errorMessage }}
              </p>
            </li>
          </ul>
        </details>

        <details class="activity-card">
          <summary class="activity-summary">
            <span class="activity-chevron" aria-hidden="true">▼</span>
            <span>
              <span class="eyebrow">Email</span>
              <strong>Notifications</strong>
              <small>{{ glottologNotifications.length }} sent</small>
            </span>
          </summary>

          <div v-if="!glottologNotifications.length" class="activity-empty">
            No notifications yet.
          </div>

          <ul v-else class="activity-list">
            <li
                v-for="note in glottologNotifications"
                :key="note.id"
                class="activity-row"
            >
              <div class="activity-row__main">
                <strong>{{ note.deliveryStatus }} · {{ note.toEmail }}</strong>
                <span>{{ note.subject }}</span>
              </div>
              <div class="activity-row__meta">
                <span>{{ note.triggerType || "—" }} · {{ note.pipelineStatus || "—" }}</span>
                <span>{{ formatDateTime(note.createdAt) }}</span>
              </div>
              <p class="activity-row__detail">{{ note.deliveryDetail || "—" }}</p>
            </li>
          </ul>
        </details>

        <details v-if="glottologJob?.recentLogLines?.length" class="developer-card">
          <summary>Developer tools · Show logs</summary>
          <pre>{{ glottologJob.recentLogLines.join("\n") }}</pre>
        </details>
      </template>
    </div>
  </AdminWorkspaceShell>
</template>

<style scoped>
.admin-dashboard {
  display: grid;
  gap: 1.5rem;
}

.glottolog-hero,
.schedule-card,
.activity-card,
.developer-card {
  border: 1px solid var(--border);
  background: var(--vignette-surface);
  box-shadow: var(--shadow);
}

.glottolog-hero h3 {
  margin: 0;
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.glottolog-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.95fr);
  gap: 1.25rem;
  padding: 1.5rem;
  border-radius: 28px;
  background: linear-gradient(180deg, var(--vignette-surface), var(--vignette-bg));
}

.hero-icon {
  width: 3.5rem;
  height: 3.5rem;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: var(--vignette-primary-soft);
  color: var(--vignette-primary);
  font-size: 1.6rem;
}

.eyebrow {
  margin: 0 0 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--vignette-primary);
}

.hero-meta,
.hero-stats {
  display: grid;
  gap: 1rem;
}

.hero-meta {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 1.5rem;
}

.hero-meta span,
.hero-stats span {
  display: block;
  color: var(--vignette-muted);
  font-size: 0.84rem;
}

.hero-meta strong,
.hero-stats strong {
  display: block;
  margin-top: 0.3rem;
}

.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 2rem;
  padding: 0.2rem 0.75rem;
  border-radius: 999px;
  font-size: 0.86rem;
}

.pill--running {
  background: var(--vignette-primary-soft);
  color: var(--vignette-primary);
}

.pill--success {
  background: var(--vignette-success-bg);
  color: var(--vignette-success-text);
}

.sync-state {
  margin-top: 1.5rem;
}

.sync-state__label {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.65rem;
}

.progress-track {
  height: 12px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(109, 31, 52, 0.08);
}

.progress-bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--vignette-primary-dark), var(--vignette-primary));
  transition: width 0.35s ease;
}

.progress-bar--done {
  background: var(--vignette-success-text);
}

.hero-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 1.5rem;
}

.glottolog-visual {
  display: grid;
  gap: 1rem;
  align-content: start;
}

.update-btn {
  width: 100%;
}

.timeline-card {
  border-radius: 22px;
  border: 1px solid var(--border);
  background: var(--vignette-surface-soft);
  padding: 1rem;
  display: grid;
  gap: 0.35rem;
}

.timeline-step {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 0.85rem;
  align-items: start;
  min-height: 60px;
}

.timeline-step__rail {
  display: grid;
  justify-items: center;
  align-items: start;
}

.timeline-step__icon {
  width: 1.15rem;
  height: 1.15rem;
  margin-top: 0.1rem;
}

.timeline-step__icon--pending {
  color: rgba(47, 23, 34, 0.28);
}

.timeline-step__icon--active {
  color: var(--vignette-primary);
  animation: timeline-spin 1s linear infinite;
}

.timeline-step__icon--done {
  color: var(--vignette-success-text);
  animation: timeline-check-pop 300ms ease-out;
}

.timeline-step__line {
  width: 2px;
  min-height: 40px;
  margin-top: 0.35rem;
  border-radius: 999px;
  background: rgba(47, 23, 34, 0.16);
  transition: background 180ms ease;
}

.timeline-step__line.is-active {
  background: rgba(109, 31, 52, 0.32);
}

.timeline-step__line.is-complete {
  background: rgba(64, 92, 50, 0.42);
}

.timeline-step__content {
  display: grid;
  gap: 0.2rem;
  padding-bottom: 0.9rem;
}

.timeline-step__content strong {
  font-size: 0.98rem;
  color: var(--vignette-text);
  transition: color 180ms ease;
}

.timeline-step__content small {
  color: var(--vignette-muted);
  transition: color 180ms ease;
}

.timeline-step--active .timeline-step__content strong,
.timeline-step--active .timeline-step__content small {
  color: var(--vignette-primary);
}

.timeline-step--done .timeline-step__content strong {
  color: var(--vignette-success-text);
}

.timeline-step--pending .timeline-step__content strong {
  color: rgba(47, 23, 34, 0.58);
}

.timeline-step--pending .timeline-step__content small {
  color: rgba(47, 23, 34, 0.45);
}

@keyframes timeline-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes timeline-check-pop {
  0% {
    transform: scale(0.3);
    opacity: 0;
  }
  70% {
    transform: scale(1.2);
    opacity: 1;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.schedule-card {
  display: grid;
  gap: 1rem;
  padding: 1.25rem 1.4rem;
  border-radius: 28px;
}

.schedule-card h3 {
  margin: 0.15rem 0 0;
}

.schedule-toggle {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  width: 100%;
  padding: 0.85rem 1rem;
  border: 1px solid var(--border);
  border-radius: 0.85rem;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.schedule-toggle:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.schedule-toggle__control {
  position: relative;
  width: 2.6rem;
  height: 1.45rem;
  border-radius: 999px;
  background: rgba(47, 23, 34, 0.18);
  flex-shrink: 0;
}

.schedule-toggle__control::after {
  content: "";
  position: absolute;
  top: 0.18rem;
  left: 0.18rem;
  width: 1.1rem;
  height: 1.1rem;
  border-radius: 999px;
  background: white;
  transition: transform 160ms ease;
}

.schedule-toggle.is-enabled .schedule-toggle__control {
  background: var(--vignette-primary);
}

.schedule-toggle.is-enabled .schedule-toggle__control::after {
  transform: translateX(1.15rem);
}

.schedule-toggle strong,
.schedule-toggle small,
.schedule-field span {
  display: block;
}

.schedule-toggle strong {
  font-size: 0.92rem;
}

.schedule-toggle small,
.schedule-field span {
  color: var(--vignette-muted);
  font-size: 0.78rem;
}

.schedule-field {
  display: grid;
  gap: 0.4rem;
}

.schedule-field input[type="number"],
.schedule-field input[type="email"] {
  min-height: 2.4rem;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border);
  border-radius: 0.55rem;
  background: var(--vignette-surface);
  color: inherit;
}

.schedule-hint {
  color: var(--vignette-muted);
  font-size: 0.75rem;
  line-height: 1.35;
}

.schedule-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.activity-card {
  display: grid;
  gap: 1rem;
  padding: 1.25rem 1.4rem;
  border-radius: 28px;
}

.activity-summary {
  list-style: none;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.activity-summary::-webkit-details-marker {
  display: none;
}

.activity-summary .eyebrow {
  display: block;
}

.activity-summary strong {
  display: block;
  margin-top: 0.15rem;
}

.activity-summary small {
  display: block;
  margin-top: 0.2rem;
  color: var(--vignette-muted);
  font-size: 0.82rem;
}

.activity-chevron {
  display: inline-block;
  margin-top: 0.55rem;
  font-size: 0.7rem;
  color: var(--vignette-muted);
  transition: transform 0.15s ease;
}

details.activity-card:not([open]) .activity-chevron {
  transform: rotate(-90deg);
}

.activity-card[open] .activity-summary {
  margin-bottom: 0.35rem;
}

.activity-empty {
  color: var(--vignette-muted);
}

.activity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}

.activity-row {
  display: grid;
  gap: 0.35rem;
  padding: 0.85rem 1rem;
  border: 1px solid var(--border);
  border-radius: 0.85rem;
  background: var(--vignette-surface-soft);
}

.activity-row__main,
.activity-row__meta,
.activity-row__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem 1rem;
  justify-content: space-between;
}

.activity-row__meta,
.activity-row__stats {
  color: var(--vignette-muted);
  font-size: 0.82rem;
}

.activity-row__error {
  margin: 0;
  color: #9b1c1c;
  font-size: 0.82rem;
}

.activity-row__detail {
  margin: 0;
  color: var(--vignette-muted);
  font-size: 0.8rem;
  word-break: break-word;
}

.schedule-field select:disabled,
.schedule-field input:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.schedule-meta {
  display: grid;
  gap: 0.75rem;
}

.schedule-next {
  margin: 0;
  display: grid;
  gap: 0.2rem;
}

.schedule-next span {
  color: var(--vignette-muted);
  font-size: 0.78rem;
}

.schedule-next strong {
  font-size: 0.92rem;
  font-weight: 600;
}

.developer-card {
  padding: 1rem 1.15rem;
  border-radius: 22px;
}

.developer-card summary {
  cursor: pointer;
  color: var(--vignette-text);
}

.developer-card pre {
  margin-top: 1rem;
  padding: 0.9rem;
  border-radius: 14px;
  background: var(--vignette-surface);
  border: 1px solid var(--border);
  color: var(--vignette-text);
  white-space: pre-wrap;
  overflow: auto;
}

@media (max-width: 1200px) {
  .glottolog-hero,
  .hero-meta,
  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .glottolog-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .hero-meta,
  .hero-stats {
    grid-template-columns: 1fr;
  }

  .sync-state__label {
    flex-direction: column;
  }
}
</style>
