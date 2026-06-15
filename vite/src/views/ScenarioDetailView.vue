<script setup>
import {computed, nextTick, onMounted, ref, watch} from "vue";
import {fetchLanguage} from "../api/languages";
import {apiFetch} from "../api/rest";
import {
  fetchScenario,
  fetchScenarioThumbnails,
  fetchThumbnailAudios,
  publishScenario,
  updateScenarioStoryboard,
  updateThumbnailLayout,
  uploadScenarioThumbnail,
  uploadThumbnailAudio,
} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useAuth} from "../composables/useAuth";
import {useToast} from "../composables/useToast";
import {useScenarioAutoplay} from "../composables/useScenarioAutoplay";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import ThumbnailCard from "../components/ThumbnailCard.vue";
import AudioPanel from "../components/AudioPanel.vue";
import BasePageHeader from "../components/ui/BasePageHeader.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import BaseBadge from "../components/ui/BaseBadge.vue";
import DiscussionThread from "../components/community/DiscussionThread.vue";
import {
  buildPlaybackQueue,
  buildSelectedAudios,
  buildStoryboardItems,
  clamp,
  normalizeMarkers,
  nullableInt,
  safeNumber,
  sortByIdxThenId,
  storyboardItemStyle,
} from "@/utils/scenarioStoryboard.js";
import {createAccreditationRequest, fetchAccreditationRequests} from "../api/community";

const requestingAccreditation = ref(false);
const accreditationRequested = ref(false);
const hasExistingRequest = ref(false);

async function checkExistingRequest() {
  try {
    const requests = await fetchAccreditationRequests(
      "SCENARIO_EDIT", "SCENARIO", props.id
    );
    hasExistingRequest.value = requests.some(
      (r) => r.requesterUsername === currentUser.value?.username
           && (r.status === "PENDING" || r.status === "APPROVED")
    );
  } catch {
    // silencieux
  }
}

import {useRouter} from "vue-router";

const router = useRouter();

const forking = ref(false);

async function forkScenario() {
  forking.value = true;
  try {
    const response = await apiFetch(`/api/scenarios/${props.id}/fork`, {
      method: "POST",
    });
    toast.success("Scenario forked successfully. Redirecting to your copy...");
    await router.push(`/scenarios/${response.id}`);
  } catch (e) {
    toast.error(e.message || "Failed to fork scenario.");
  } finally {
    forking.value = false;
  }
}

async function requestScenarioAccreditation() {
  requestingAccreditation.value = true;
  try {
    await createAccreditationRequest({
      permissionType: "SCENARIO_EDIT",
      scopeType: "SCENARIO",
      targetId: String(props.id),
      motivation: "Requesting contribution access for this scenario.",
    });
    accreditationRequested.value = true;
    hasExistingRequest.value = true;
    toast.success("Contribution request submitted.");
  } catch (e) {
    toast.error(e.message || "Failed to submit request.");
  } finally {
    requestingAccreditation.value = false;
  }
}

const props = defineProps({
  id: {type: String, required: true},
});

const {currentUser, loadMe, isAuthenticated} = useAuth();
const toast = useToast();
const { isLiked, toggleLike, isBookmarked, toggleBookmark, fetchStatus } = useScenarioInteractions();

const scenario = ref(null);
const languageName = ref("");
const thumbnails = ref([]);
const audioMap = ref({});
const selectedThumb = ref(null);
const activeAudioId = ref(null);
const error = ref("");
const uploadError = ref("");
const uploadSuccess = ref("");
const isOwner = ref(false);
const loading = ref(false);
const savingStoryboard = ref(false);
const savingLayout = ref(false);
const publishing = ref(false);

const uploadTitle = ref("");
const uploadFile = ref(null);
const highlightedThumbnailId = ref(null);

const infoDialogOpen = ref(false);

const quickRecordingThumbId = ref(null);
const quickRecordingDialogOpen = ref(false);
const quickRecordingTitle = ref("");
const quickRecordingBlob = ref(null);
const quickRecordingMimeType = ref("audio/webm");
const quickRecordingError = ref("");
const quickRecordingUploading = ref(false);

let quickMediaRecorder = null;
let quickMediaStream = null;
let quickRecordingChunks = [];

function openInfoDialog() {
  infoDialogOpen.value = true;
}

function closeInfoDialog() {
  infoDialogOpen.value = false;
}

const uploadDialogOpen = ref(false);

function openUploadDialog() {
  uploadError.value = "";
  uploadSuccess.value = "";
  uploadDialogOpen.value = true;
}

function closeUploadDialog() {
  uploadDialogOpen.value = false;
}

const storyboardSettingsDialogOpen = ref(false);

function openStoryboardSettingsDialog() {
  storyboardSettingsDialogOpen.value = true;
}

function closeStoryboardSettingsDialog() {
  storyboardSettingsDialogOpen.value = false;
}

const selectedThumbnailPanelOpen = ref(false);
const selectedLayoutPanelOpen = ref(false);

function toggleSelectedThumbnailPanel() {
  selectedThumbnailPanelOpen.value = !selectedThumbnailPanelOpen.value;
}

function toggleSelectedLayoutPanel() {
  selectedLayoutPanelOpen.value = !selectedLayoutPanelOpen.value;
}

const storyboardForm = ref({
  layoutMode: "PRESET",
  preset: "GRID_3",
  columns: 3,
});

const selectedLayoutForm = ref({
  gridColumn: "",
  gridRow: "",
  gridColumnSpan: 1,
  gridRowSpan: 1,
});

const sortedThumbnails = computed(() => sortByIdxThenId(thumbnails.value));

const selectedAudios = computed(() => {
  return buildSelectedAudios(audioMap.value, selectedThumb.value);
});

const selectedAudioMarkers = computed(() => normalizeMarkers(selectedAudios.value));

const playbackQueue = computed(() => {
  return buildPlaybackQueue(
      sortedThumbnails.value,
      audioMap.value,
      (audio) => buildApiUrl(`/api/audios/${audio.id}/content`)
  );
});

const isPublished = computed(() => scenario.value?.visibilityStatus === "PUBLISHED");

const playerStateLabel = computed(() => {
  if (autoplay.isLoading.value) return "loading";
  if (autoplay.isPlaying.value) return "playing";
  if (autoplay.isPaused.value) return "paused";
  return "idle";
});

const storyboardColumns = computed(() => {
  const raw = storyboardForm.value.columns ?? scenario.value?.storyboardColumns ?? 3;
  return clamp(safeNumber(raw, 3), 1, 8);
});

watch(
    () => scenario.value,
    (value) => {
      if (!value) return;
      storyboardForm.value = {
        layoutMode: value.storyboardLayoutMode ?? "PRESET",
        preset: value.storyboardPreset ?? "GRID_3",
        columns: value.storyboardColumns ?? 3,
      };
    },
    {immediate: true}
);

watch(
    () => selectedThumb.value,
    (thumb) => {
      if (!thumb) {
        selectedLayoutForm.value = {gridColumn: "", gridRow: "", gridColumnSpan: 1, gridRowSpan: 1};
        return;
      }
      selectedLayoutForm.value = {
        gridColumn: thumb.gridColumn ?? "",
        gridRow: thumb.gridRow ?? "",
        gridColumnSpan: thumb.gridColumnSpan ?? 1,
        gridRowSpan: thumb.gridRowSpan ?? 1,
      };
    },
    {immediate: true}
);

function ensureSelectedThumbnailPanelOpen() {
  if (selectedThumb.value) {
    selectedThumbnailPanelOpen.value = true;
  }
}

function markerStyle(audio) {
  return {
    left: `${audio._x}%`,
    top: `${audio._y}%`,
  };
}

function isMarkerActive(audio) {
  return String(activeAudioId.value ?? "") === String(audio?.id ?? "");
}

function onImageChange(event) {
  uploadFile.value = event.target.files?.[0] ?? null;
}

function thumbnailContentUrl(thumb) {
  if (!thumb?.id) return "";
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

function selectThumb(thumb) {
  selectedThumb.value = thumb;
  activeAudioId.value = null;
  selectedThumbnailPanelOpen.value = true;
}

function focusPlaybackItem(item) {
  const thumb = thumbnails.value.find((t) => String(t.id) === String(item.thumbnailId)) ?? null;
  selectedThumb.value = thumb;
  activeAudioId.value = item.audioId ?? null;
  ensureSelectedThumbnailPanelOpen();
  highlightedThumbnailId.value = item.thumbnailId;

  nextTick(() => {
    const el = document.querySelector(`[data-thumbnail-id="${item.thumbnailId}"]`);
    el?.scrollIntoView({behavior: "smooth", block: "center"});
  });

  window.clearTimeout(focusPlaybackItem._highlightTimeout);
  focusPlaybackItem._highlightTimeout = window.setTimeout(() => {
    if (String(highlightedThumbnailId.value) === String(item.thumbnailId)) {
      highlightedThumbnailId.value = null;
    }
  }, 1400);
}

const autoplay = useScenarioAutoplay(playbackQueue, {
  gapMs: 320,
  autoContinue: true,
  loopScenario: false,
  onItemChange: (item) => { focusPlaybackItem(item); },
  onStop: () => { activeAudioId.value = null; },
  onEndedAll: () => {
    activeAudioId.value = null;
    toast.success("Automatic playback finished.");
  },
});

function toggleAutoContinue() {
  autoplay.toggleAutoContinue();
  toast.info(autoplay.autoContinue.value ? "Auto-continue enabled." : "Auto-continue disabled.");
}

function toggleLoopScenario() {
  autoplay.toggleLoopScenario();
  toast.info(autoplay.loopScenario.value ? "Loop scenario enabled." : "Loop scenario disabled.");
}

function findStartIndex() {
  if (!playbackQueue.value.length) return 0;
  if (selectedThumb.value && activeAudioId.value != null) {
    const exactIndex = playbackQueue.value.findIndex(
        (item) =>
            String(item.thumbnailId) === String(selectedThumb.value.id) &&
            String(item.audioId) === String(activeAudioId.value)
    );
    if (exactIndex >= 0) return exactIndex;
  }
  if (selectedThumb.value) {
    const thumbIndex = playbackQueue.value.findIndex(
        (item) => String(item.thumbnailId) === String(selectedThumb.value.id)
    );
    if (thumbIndex >= 0) return thumbIndex;
  }
  return 0;
}

async function playAllFromContext() {
  if (!playbackQueue.value.length) return;
  await autoplay.playFromIndex(findStartIndex());
}

async function setActiveAudio(audio) {
  if (!audio || !selectedThumb.value) {
    autoplay.stop();
    activeAudioId.value = null;
    return;
  }
  const idx = playbackQueue.value.findIndex(
      (item) =>
          String(item.thumbnailId) === String(selectedThumb.value.id) &&
          String(item.audioId) === String(audio.id)
  );
  if (idx >= 0) {
    ensureSelectedThumbnailPanelOpen();
    await autoplay.playFromIndex(idx);
  } else {
    activeAudioId.value = audio?.id ?? null;
  }
}

async function playAudioFromMarker(audio) {
  const idx = playbackQueue.value.findIndex(
      (item) =>
          String(item.thumbnailId) === String(selectedThumb.value?.id) &&
          String(item.audioId) === String(audio.id)
  );
  if (idx >= 0) {
    ensureSelectedThumbnailPanelOpen();
    await autoplay.playFromIndex(idx);
  }
}

async function loadScenario() {
  scenario.value = await fetchScenario(props.id);
  if (scenario.value?.languageId) {
    try {
      const lang = await fetchLanguage(scenario.value.languageId);
      languageName.value = lang.name ?? "Unknown language";
    } catch {
      languageName.value = "Unknown language";
    }
  } else {
    languageName.value = "-";
  }
}

async function loadThumbs() {
  thumbnails.value = await fetchScenarioThumbnails(props.id);
  const map = {};
  await Promise.all(
      thumbnails.value.map(async (t) => {
        try {
          map[t.id] = await fetchThumbnailAudios(t.id);
        } catch (e) {
          console.error(`Failed to load audios for thumbnail ${t.id}`, e);
          map[t.id] = [];
        }
      })
  );
  audioMap.value = map;
  if (!selectedThumb.value && sortedThumbnails.value.length) {
    selectedThumb.value = sortedThumbnails.value[0];
  } else if (
      selectedThumb.value &&
      !thumbnails.value.some((t) => t.id === selectedThumb.value.id)
  ) {
    selectedThumb.value = sortedThumbnails.value[0] || null;
  }
  if (
      activeAudioId.value != null &&
      !selectedAudios.value.some((audio) => String(audio.id) === String(activeAudioId.value))
  ) {
    activeAudioId.value = null;
  }
}

async function loadAll() {
  loading.value = true;
  error.value = "";
  try {
    await loadMe();
    await loadScenario();
    isOwner.value =
        !!currentUser.value &&
        currentUser.value.username === scenario.value.authorUsername;
    await Promise.all([loadThumbs(), checkExistingRequest()]);
    await loadThumbs();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function uploadImage() {
  uploadError.value = "";
  uploadSuccess.value = "";
  try {
    if (!uploadFile.value) throw new Error("No image selected.");
    const fd = new FormData();
    fd.append("scenarioId", String(scenario.value.id));
    fd.append("title", uploadTitle.value || "");
    fd.append("image", uploadFile.value);
    await uploadScenarioThumbnail(props.id, fd);
    uploadTitle.value = "";
    uploadFile.value = null;
    uploadSuccess.value = "Image uploaded successfully.";
    toast.success("Thumbnail uploaded successfully.");
    await loadThumbs();
    closeUploadDialog();
  } catch (e) {
    uploadError.value = e.message;
    toast.error(e.message || "Upload failed.");
  }
}

async function publishCurrentScenario() {
  if (!scenario.value || publishing.value) return;
  publishing.value = true;
  try {
    scenario.value = await publishScenario(props.id);
    toast.success("Scenario published.");
  } catch (e) {
    toast.error(e.message || "Failed to publish scenario.");
  } finally {
    publishing.value = false;
  }
}

async function saveStoryboardSettings() {
  if (!scenario.value || savingStoryboard.value) return;
  savingStoryboard.value = true;
  try {
    const columns = clamp(safeNumber(storyboardForm.value.columns, 3), 1, 8);
    scenario.value = await updateScenarioStoryboard(props.id, {
      layoutMode: String(storyboardForm.value.layoutMode || "PRESET").toUpperCase(),
      preset: String(storyboardForm.value.preset || "GRID_3").toUpperCase(),
      columns,
    });
    storyboardForm.value.columns = columns;
    toast.success("Storyboard settings saved.");
  } catch (e) {
    toast.error(e.message || "Failed to save storyboard settings.");
  } finally {
    savingStoryboard.value = false;
  }
}

async function saveSelectedThumbnailLayout() {
  if (!selectedThumb.value || savingLayout.value) return;
  savingLayout.value = true;
  try {
    await updateThumbnailLayout(selectedThumb.value.id, {
      gridColumn: nullableInt(selectedLayoutForm.value.gridColumn),
      gridRow: nullableInt(selectedLayoutForm.value.gridRow),
      gridColumnSpan: nullableInt(selectedLayoutForm.value.gridColumnSpan) ?? 1,
      gridRowSpan: nullableInt(selectedLayoutForm.value.gridRowSpan) ?? 1,
    });
    await loadThumbs();
    const refreshed = thumbnails.value.find((thumb) => String(thumb.id) === String(selectedThumb.value.id));
    if (refreshed) selectedThumb.value = refreshed;
    toast.success("Thumbnail layout saved.");
  } catch (e) {
    toast.error(e.message || "Failed to save thumbnail layout.");
  } finally {
    savingLayout.value = false;
  }
}

async function refreshAudios() {
  await loadThumbs();
}

const storyboardItems = computed(() => {
  return buildStoryboardItems({
    thumbnails: sortedThumbnails.value,
    layoutMode: scenario.value?.storyboardLayoutMode ?? "PRESET",
    columns: storyboardColumns.value,
  });
});

function closeQuickRecordingDialog() {
  quickRecordingDialogOpen.value = false;
  quickRecordingTitle.value = "";
  quickRecordingBlob.value = null;
  quickRecordingMimeType.value = "audio/webm";
  quickRecordingError.value = "";
  if (quickRecordingPreviewUrl.value) {
    URL.revokeObjectURL(quickRecordingPreviewUrl.value);
    quickRecordingPreviewUrl.value = "";
  }
}

async function ensureQuickRecorder() {
  if (quickMediaRecorder && quickMediaStream) return;
  quickMediaStream = await navigator.mediaDevices.getUserMedia({audio: true});
  quickMediaRecorder = new MediaRecorder(quickMediaStream);
  quickMediaRecorder.ondataavailable = (event) => {
    if (event.data && event.data.size > 0) quickRecordingChunks.push(event.data);
  };
  quickMediaRecorder.onstop = () => {
    quickRecordingBlob.value = new Blob(quickRecordingChunks, {
      type: quickMediaRecorder.mimeType || "audio/webm",
    });
    quickRecordingMimeType.value = quickMediaRecorder.mimeType || "audio/webm";
    quickRecordingChunks = [];
    quickRecordingDialogOpen.value = true;
    quickRecordingThumbId.value = null;
  };
}

async function startQuickRecording(thumb) {
  quickRecordingError.value = "";
  quickRecordingBlob.value = null;
  quickRecordingTitle.value = "";
  try {
    await ensureQuickRecorder();
    quickRecordingChunks = [];
    quickRecordingThumbId.value = thumb.id;
    quickMediaRecorder.start();
    toast.info(`Recording started on ${thumb.title || `thumbnail ${thumb.idx ?? thumb.id}`}.`);
  } catch (e) {
    quickRecordingThumbId.value = null;
    quickRecordingError.value = e.message || "Unable to start quick recording.";
    toast.error(quickRecordingError.value);
  }
}

function stopQuickRecording() {
  if (!quickMediaRecorder || quickMediaRecorder.state === "inactive") {
    quickRecordingThumbId.value = null;
    return;
  }
  quickMediaRecorder.stop();
}

async function toggleQuickRecording(thumb) {
  selectThumb(thumb);
  if (
      quickRecordingThumbId.value != null &&
      String(quickRecordingThumbId.value) === String(thumb.id)
  ) {
    stopQuickRecording();
    return;
  }
  if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
    toast.error("Another quick recording is already in progress.");
    return;
  }
  await startQuickRecording(thumb);
}

async function confirmQuickRecordingUpload() {
  quickRecordingError.value = "";
  try {
    const targetThumb = selectedThumb.value;
    if (!targetThumb?.id) throw new Error("No thumbnail selected.");
    if (!quickRecordingBlob.value) throw new Error("No quick recording available.");
    quickRecordingUploading.value = true;
    const extension = quickRecordingMimeType.value.includes("ogg")
        ? "ogg"
        : quickRecordingMimeType.value.includes("mp4")
            ? "m4a"
            : "webm";
    const fileName = `quick-recording.${extension}`;
    const fd = new FormData();
    fd.append("title", quickRecordingTitle.value || "");
    fd.append("audio", quickRecordingBlob.value, fileName);
    await uploadThumbnailAudio(targetThumb.id, fd);
    toast.success("Quick recording uploaded successfully.");
    closeQuickRecordingDialog();
    await refreshAudios();
  } catch (e) {
    quickRecordingError.value = e.message || "Failed to upload quick recording.";
    toast.error(quickRecordingError.value);
  } finally {
    quickRecordingUploading.value = false;
  }
}

function discardQuickRecording() {
  toast.info("Quick recording discarded.");
  closeQuickRecordingDialog();
}

const quickRecordingPreviewUrl = ref("");

watch(quickRecordingBlob, (blob) => {
  if (quickRecordingPreviewUrl.value) {
    URL.revokeObjectURL(quickRecordingPreviewUrl.value);
    quickRecordingPreviewUrl.value = "";
  }
  if (blob) quickRecordingPreviewUrl.value = URL.createObjectURL(blob);
});

watch(
    () => props.id,
    () => {
      if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") quickMediaRecorder.stop();
      quickRecordingThumbId.value = null;
      closeQuickRecordingDialog();
    }
);

onMounted(loadAll);
</script>

<template>
  <main class="page">
    <BaseLoader v-if="loading">Loading storyboard...</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else-if="scenario">
      <section class="section">
        <BasePageHeader
            :title="scenario.title ?? 'Scenario'"
            subtitle="Storyboard workspace with publication, layout and media controls"
        >
          <template #actions>
            <button
                type="button"
                class="icon-button"
                aria-label="Open scenario information"
                title="Scenario information"
                @click="openInfoDialog"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9"></circle>
                <path d="M12 10v6"></path>
                <path d="M12 7h.01"></path>
              </svg>
            </button>

            <!-- Like & Bookmark -->
            <button
              type="button"
              class="interaction-btn"
              :class="{ 'interaction-btn--active': isLiked(props.id) }"
              :title="isLiked(props.id) ? 'Unlike' : 'Like this scenario'"
              @click="toggleLike(props.id)"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
              {{ isLiked(props.id) ? "Liked" : "Like" }}
            </button>

            <button
              type="button"
              class="interaction-btn"
              :class="{ 'interaction-btn--active': isBookmarked(props.id) }"
              :title="isBookmarked(props.id) ? 'Remove bookmark' : 'Bookmark this scenario'"
              @click="toggleBookmark(props.id)"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
              </svg>
              {{ isBookmarked(props.id) ? "Saved" : "Save" }}
            </button>

            <BaseBadge :variant="isPublished ? 'success' : 'warning'">
              {{ scenario.visibilityStatus }}
            </BaseBadge>

            <BaseBadge :variant="isOwner ? 'success' : 'neutral'">
              {{ isOwner ? "Owner view" : "Read-only" }}
            </BaseBadge>

            <button
                v-if="isOwner && !isPublished"
                class="btn btn--primary"
                :disabled="publishing"
                @click="publishCurrentScenario"
            >
              {{ publishing ? "Publishing..." : "Publish scenario" }}
            </button>

            <button
              v-if="isAuthenticated && !isOwner && isPublished && !hasExistingRequest"
              type="button"
              class="btn btn--ghost"
              :disabled="requestingAccreditation"
              @click="requestScenarioAccreditation"
            >
              {{ requestingAccreditation ? "Requesting..." : "Request to contribute" }}
            </button>

            <button
              v-if="isAuthenticated && !isOwner && isPublished"
              type="button"
              class="btn btn--ghost"
              :disabled="forking"
              @click="forkScenario"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="18" r="3"/>
                <circle cx="6" cy="6" r="3"/>
                <circle cx="18" cy="6" r="3"/>
                <path d="M18 9v1a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V9"/>
                <path d="M12 12v3"/>
              </svg>
              {{ forking ? "Forking..." : "Fork scenario" }}
            </button>

            <span v-if="isAuthenticated && !isOwner && isPublished && hasExistingRequest" class="badge badge--info">
              Contribution requested
            </span>

            <div v-if="infoDialogOpen" class="dialog-backdrop" @click.self="closeInfoDialog">
              <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="scenario-info-title">
                <div class="dialog-card__header">
                  <div>
                    <h2 id="scenario-info-title">Scenario information</h2>
                    <p class="muted">General metadata and description.</p>
                  </div>
                  <button type="button" class="icon-button" aria-label="Close scenario information" title="Close" @click="closeInfoDialog">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M18 6 6 18"></path><path d="m6 6 12 12"></path>
                    </svg>
                  </button>
                </div>
                <div class="dialog-card__body">
                  <div class="card info-grid info-grid--premium">
                    <div><h3>Language</h3><p>{{ languageName }}</p></div>
                    <div><h3>Author</h3><p>{{ scenario.authorUsername ?? "-" }}</p></div>
                    <div><h3>Thumbnails</h3><p>{{ thumbnails.length }}</p></div>
                    <div><h3>Audio clips</h3><p>{{ playbackQueue.length }}</p></div>
                    <div><h3>Status</h3><p>{{ scenario.visibilityStatus }}</p></div>
                  </div>
                  <div class="card">
                    <h3>Tags</h3>
                    <div v-if="scenario.tags?.length" class="scenario-tags">
                      <BaseBadge v-for="tag in scenario.tags" :key="tag" variant="neutral">#{{ tag }}</BaseBadge>
                    </div>
                    <p v-else class="muted">No tags.</p>
                  </div>
                  <section class="card">
                    <h3>Description</h3>
                    <p class="text">{{ scenario.description ?? "No description available." }}</p>
                  </section>
                </div>
              </section>
            </div>
          </template>
        </BasePageHeader>

        <div class="scenario-layout">
          <div class="scenario-layout__main">
            <section class="section">
              <BasePageHeader
                  title="Storyboard"
                  :subtitle="`${thumbnails.length} thumbnail(s) in this scenario.`"
              >
                <template #actions>
                  <button v-if="isOwner" type="button" class="icon-button" aria-label="Open storyboard settings" title="Storyboard settings" @click="openStoryboardSettingsDialog">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 3v4"/><path d="M12 17v4"/><path d="M3 12h4"/><path d="M17 12h4"/>
                      <path d="m5.64 5.64 2.83 2.83"/><path d="m15.53 15.53 2.83 2.83"/>
                      <path d="m5.64 18.36 2.83-2.83"/><path d="m15.53 8.47 2.83-2.83"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  </button>
                  <button v-if="isOwner" type="button" class="icon-button" aria-label="Add a thumbnail" title="Add a thumbnail" @click="openUploadDialog">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 5v14"/><path d="M5 12h14"/>
                    </svg>
                  </button>
                </template>
              </BasePageHeader>

              <!-- Upload dialog -->
              <div v-if="uploadDialogOpen" class="dialog-backdrop" @click.self="closeUploadDialog">
                <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="thumbnail-upload-title">
                  <div class="dialog-card__header">
                    <div>
                      <h2 id="thumbnail-upload-title">Add a thumbnail</h2>
                      <p class="muted">Upload a new image to extend the storyboard.</p>
                    </div>
                    <button type="button" class="icon-button" aria-label="Close upload dialog" title="Close" @click="closeUploadDialog">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
                      </svg>
                    </button>
                  </div>
                  <div class="dialog-card__body">
                    <div class="form-grid">
                      <label>Title<input v-model="uploadTitle" placeholder="Optional image title"/></label>
                      <label>Image file<input type="file" accept="image/*" @change="onImageChange"/></label>
                    </div>
                    <div class="toolbar">
                      <button class="btn btn--primary" @click="uploadImage">Upload image</button>
                    </div>
                    <BaseAlert v-if="uploadSuccess" type="success">{{ uploadSuccess }}</BaseAlert>
                    <BaseAlert v-if="uploadError" type="error">{{ uploadError }}</BaseAlert>
                  </div>
                </section>
              </div>

              <!-- Storyboard settings dialog -->
              <div v-if="storyboardSettingsDialogOpen" class="dialog-backdrop" @click.self="closeStoryboardSettingsDialog">
                <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="storyboard-settings-title">
                  <div class="dialog-card__header">
                    <div>
                      <h2 id="storyboard-settings-title">Publication & storyboard</h2>
                      <p class="muted">Manage publication status and storyboard layout.</p>
                    </div>
                    <button type="button" class="icon-button" aria-label="Close storyboard settings" title="Close" @click="closeStoryboardSettingsDialog">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
                      </svg>
                    </button>
                  </div>
                  <div class="dialog-card__body">
                    <BaseAlert v-if="!isPublished" type="info">This scenario is still private. Public users cannot see it until you publish it.</BaseAlert>
                    <div class="storyboard-settings-grid">
                      <label>Layout mode<select v-model="storyboardForm.layoutMode"><option value="PRESET">Preset</option><option value="CUSTOM">Custom</option></select></label>
                      <label>Preset<select v-model="storyboardForm.preset" :disabled="storyboardForm.layoutMode !== 'PRESET'"><option value="GRID_3">Grid 3</option><option value="GRID_2">Grid 2</option><option value="CINEMATIC">Cinematic</option><option value="MANGA">Manga</option></select></label>
                      <label>Columns<input v-model="storyboardForm.columns" type="number" min="1" max="8"/></label>
                    </div>
                    <div class="toolbar">
                      <button v-if="!isPublished" class="btn btn--ghost" :disabled="publishing" @click="publishCurrentScenario">{{ publishing ? "Publishing..." : "Publish scenario" }}</button>
                      <button class="btn btn--primary" :disabled="savingStoryboard" @click="saveStoryboardSettings">{{ savingStoryboard ? "Saving..." : "Save storyboard settings" }}</button>
                    </div>
                    <p class="muted storyboard-help">Preset mode auto-composes the storyboard. Custom mode uses each thumbnail's saved grid position and spans.</p>
                  </div>
                </section>
              </div>

              <!-- Quick recording dialog -->
              <div v-if="quickRecordingDialogOpen" class="dialog-backdrop" @click.self="discardQuickRecording">
                <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="quick-recording-title">
                  <div class="dialog-card__header">
                    <div>
                      <h2 id="quick-recording-title">Confirm quick recording</h2>
                      <p class="muted">Review and confirm the upload for the selected thumbnail.</p>
                    </div>
                    <button type="button" class="icon-button" aria-label="Close quick recording dialog" title="Close" @click="discardQuickRecording">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
                      </svg>
                    </button>
                  </div>
                  <div class="dialog-card__body">
                    <BaseAlert v-if="quickRecordingError" type="error">{{ quickRecordingError }}</BaseAlert>
                    <label>Audio title<input v-model="quickRecordingTitle" placeholder="Optional title for this quick recording"/></label>
                    <audio v-if="quickRecordingPreviewUrl" class="quick-recording-preview" controls :src="quickRecordingPreviewUrl"/>
                    <div class="toolbar">
                      <button type="button" class="btn btn--ghost" :disabled="quickRecordingUploading" @click="discardQuickRecording">Discard</button>
                      <button type="button" class="btn btn--primary" :disabled="quickRecordingUploading || !quickRecordingBlob" @click="confirmQuickRecordingUpload">{{ quickRecordingUploading ? "Uploading..." : "Confirm upload" }}</button>
                    </div>
                  </div>
                </section>
              </div>

              <div v-if="storyboardItems.length" class="storyboard-grid" :style="{ '--storyboard-columns': storyboardColumns }">
                <ThumbnailCard
                    v-for="item in storyboardItems"
                    :key="item.id"
                    :thumb="item"
                    :audios="audioMap[item.id] || []"
                    :selected="selectedThumb?.id === item.id"
                    :highlighted="highlightedThumbnailId === item.id"
                    :quick-recording="String(quickRecordingThumbId ?? '') === String(item.id)"
                    :style="storyboardItemStyle(item)"
                    @select="selectThumb"
                    @play="async (thumb) => { selectThumb(thumb); await playAllFromContext(); }"
                    @quick-record="toggleQuickRecording"
                />
              </div>
              <BaseEmptyState v-else title="No thumbnails yet" message="Upload an image to start building the storyboard."/>
            </section>
          </div>

          <aside class="scenario-layout__side">
            <section class="card autoplay-panel">
              <div class="autoplay-panel__header">
                <div>
                  <h2>Scenario player</h2>
                  <p class="muted">Automatic playback through all audio clips in thumbnail order.</p>
                </div>
                <BaseBadge variant="info">
                  {{ autoplay.currentIndex >= 0 ? `${autoplay.currentIndex + 1}/${playbackQueue.length}` : `0/${playbackQueue.length}` }}
                </BaseBadge>
              </div>
              <div class="transport-card transport-card--compact">
                <div class="transport-card__top transport-card__top--compact">
                  <div class="transport-card__meta">
                    <p class="transport-card__title">
                      <template v-if="autoplay.currentItem">
                        {{ autoplay.currentItem.audioTitle?.trim() || (autoplay.currentItem.audioId != null ? `Audio #${autoplay.currentItem.audioId}` : "Untitled audio") }}
                      </template>
                      <template v-else>No audio selected</template>
                    </p>
                    <p class="muted transport-card__subtitle">
                      <template v-if="autoplay.currentItem">
                        {{ autoplay.currentItem.thumbnailIdx != null ? `Thumb #${autoplay.currentItem.thumbnailIdx}` : (autoplay.currentItem.thumbnailId != null ? `Thumb #${autoplay.currentItem.thumbnailId}` : "Thumb unknown") }}
                        <span v-if="autoplay.currentItem.audioIdx != null"> · #{{ autoplay.currentItem.audioIdx }}</span>
                        · {{ playerStateLabel }}
                      </template>
                      <template v-else>Idle</template>
                    </p>
                  </div>
                  <div class="transport-toggles transport-toggles--compact">
                    <button type="button" class="btn btn--small" :class="autoplay.autoContinue ? 'btn--primary' : 'btn--ghost'" @click="toggleAutoContinue">Auto</button>
                    <button type="button" class="btn btn--small" :class="autoplay.loopScenario ? 'btn--primary' : 'btn--ghost'" @click="toggleLoopScenario">Loop</button>
                  </div>
                </div>
                <div class="transport-progress">
                  <input type="range" min="0" max="100" step="0.1" :value="autoplay.progressPercent" @input="autoplay.seekToPercent($event.target.value)"/>
                  <div class="transport-progress__times">
                    <span>{{ autoplay.formatTime(autoplay.currentTime) }}</span>
                    <span>{{ autoplay.formatTime(autoplay.duration) }}</span>
                  </div>
                </div>
                <div class="transport-controls transport-controls--compact">
                  <button type="button" class="btn btn--ghost btn--small" :disabled="!playbackQueue.length" @click="autoplay.previous">Prev</button>
                  <button type="button" class="btn btn--ghost btn--small" :disabled="!playbackQueue.length" @click="autoplay.replayCurrent">Replay</button>
                  <button v-if="!autoplay.isPlaying" type="button" class="btn btn--primary btn--small" :disabled="!playbackQueue.length || autoplay.isLoading" @click="autoplay.isPaused ? autoplay.resume() : playAllFromContext()">{{ autoplay.isPaused ? "Resume" : "Play" }}</button>
                  <button v-else type="button" class="btn btn--primary btn--small" @click="autoplay.pause">Pause</button>
                  <button type="button" class="btn btn--ghost btn--small" :disabled="!playbackQueue.length" @click="autoplay.next">Next</button>
                  <button type="button" class="btn btn--ghost btn--small" :disabled="autoplay.currentIndex < 0" @click="autoplay.stop">Stop</button>
                </div>
              </div>
            </section>

            <section v-if="selectedThumb" class="card selected-thumbnail-panel collapsible-card">
              <button type="button" class="collapsible-card__header" @click="toggleSelectedThumbnailPanel">
                <div class="collapsible-card__title-block">
                  <h2 class="collapsible-card__title">Selected thumbnail</h2>
                  <p class="muted collapsible-card__summary">
                    {{ selectedThumb.title || `Thumbnail #${selectedThumb.idx ?? selectedThumb.id}` }}
                    · {{ selectedAudios.length }} audio clip(s)
                    · {{ selectedAudioMarkers.length }} marker(s)
                  </p>
                </div>
                <div class="collapsible-card__header-right">
                  <BaseBadge variant="success">Selected</BaseBadge>
                  <span class="collapsible-card__chevron" :class="{ 'is-open': selectedThumbnailPanelOpen }">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
                  </span>
                </div>
              </button>
              <div v-if="selectedThumbnailPanelOpen" class="collapsible-card__body">
                <div class="selected-thumbnail-panel__header">
                  <div>
                    <h3>{{ selectedThumb.title || `Thumbnail #${selectedThumb.idx ?? selectedThumb.id}` }}</h3>
                    <p class="muted">Index {{ selectedThumb.idx ?? "-" }} · {{ selectedAudios.length }} audio clip(s) · {{ selectedAudioMarkers.length }} marker(s)</p>
                  </div>
                </div>
                <div class="selected-thumbnail-panel__stage">
                  <img :src="thumbnailContentUrl(selectedThumb)" :alt="selectedThumb.title || 'Selected thumbnail'" class="selected-thumbnail-panel__image"/>
                  <button v-for="audio in selectedAudioMarkers" :key="audio.id" type="button" class="marker-dot" :class="{ 'marker-dot--active': isMarkerActive(audio) }" :style="markerStyle(audio)" :title="audio.markerLabel || audio.title || `Audio #${audio.id}`" @click="playAudioFromMarker(audio)">
                    <span class="marker-dot__pulse"></span>
                    <span class="marker-dot__core"></span>
                  </button>
                </div>
              </div>
            </section>

            <section v-if="isOwner && selectedThumb" class="card collapsible-card">
              <button type="button" class="collapsible-card__header" @click="toggleSelectedLayoutPanel">
                <div class="collapsible-card__title-block">
                  <h2 class="collapsible-card__title">Selected thumbnail layout</h2>
                  <p class="muted collapsible-card__summary">Custom grid placement and span settings</p>
                </div>
                <span class="collapsible-card__chevron" :class="{ 'is-open': selectedLayoutPanelOpen }">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
                </span>
              </button>
              <div v-if="selectedLayoutPanelOpen" class="collapsible-card__body">
                <div class="storyboard-settings-grid">
                  <label>Column<input v-model="selectedLayoutForm.gridColumn" type="number" min="1" placeholder="auto"/></label>
                  <label>Row<input v-model="selectedLayoutForm.gridRow" type="number" min="1" placeholder="auto"/></label>
                  <label>Column span<input v-model="selectedLayoutForm.gridColumnSpan" type="number" min="1"/></label>
                  <label>Row span<input v-model="selectedLayoutForm.gridRowSpan" type="number" min="1"/></label>
                </div>
                <div class="toolbar">
                  <button class="btn btn--primary" :disabled="savingLayout" @click="saveSelectedThumbnailLayout">{{ savingLayout ? "Saving..." : "Save thumbnail layout" }}</button>
                </div>
                <p class="muted">In custom mode, these values control the persisted storyboard composition for this thumbnail.</p>
              </div>
            </section>

            <AudioPanel
                :selected-thumb="selectedThumb"
                :audios="selectedAudios"
                :active-audio-id="activeAudioId"
                :active-audio-title="autoplay.currentItem?.audioTitle ?? ''"
                :player-state="playerStateLabel"
                :is-owner="isOwner"
                @uploaded="refreshAudios"
                @play-audio="setActiveAudio"
            />
          </aside>
        </div>
      </section>

      <section v-if="isPublished" class="section">
        <DiscussionThread
          title="Community discussion"
          :subtitle="scenario.title ?? ''"
          target-type="SCENARIO"
          :target-id="props.id"
          empty-title="No messages yet"
          empty-message="Be the first to start a discussion about this scenario."
        />
      </section>
    </template>
  </main>
</template>

<style scoped>
.icon-button {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: #FFFCF7;
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: 160ms ease;
  box-shadow: var(--shadow);
}

.icon-button:hover {
  background: #FFF7EF;
  color: var(--primary);
  border-color: rgba(192, 74, 8, 0.28);
}

.icon-button svg {
  width: 18px;
  height: 18px;
}

/* Like / Bookmark buttons */
.interaction-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.45rem 1rem;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: #FFFCF7;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-soft);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.interaction-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.06);
}

.interaction-btn--active {
  background: rgba(192, 74, 8, 0.10);
  border-color: var(--primary);
  color: var(--primary);
}

.interaction-btn--active svg {
  fill: var(--primary);
}

.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1300;
  background: rgba(42, 21, 0, 0.38);
  backdrop-filter: blur(4px);
  display: grid;
  place-items: center;
  padding: 20px;
}

.dialog-card {
  width: min(760px, 100%);
  max-height: min(88vh, 900px);
  overflow: auto;
  border-radius: 22px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  box-shadow: 0 18px 60px rgba(42, 21, 0, 0.22);
  padding: 20px;
}

.dialog-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.dialog-card__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

@media (max-width: 640px) {
  .dialog-card { padding: 16px; border-radius: 18px; }
}

.scenario-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}
</style>