<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {RouterLink, useRoute, useRouter} from "vue-router";
import {fetchLanguage} from "../api/languages";
import {
  approveFork,
  deleteAudio as apiDeleteAudio,
  deleteThumbnail as apiDeleteThumbnail,
  deleteScenario,
  fetchScenario,
  fetchScenarioBackgroundAudios,
  fetchScenarioHistory,
  fetchScenarioThumbnails,
  fetchThumbnailAudios,
  publishScenario,
  rejectFork,
  replaceAudioContent as apiReplaceAudioContent,
  reorderScenarioThumbnails as apiReorderScenarioThumbnails,
  selectScenarioBackgroundAudio as apiSelectScenarioBackgroundAudio,
  updateAudioGloss as apiUpdateAudioGloss,
  updateScenarioMetadata as apiUpdateScenarioMetadata,
  updateScenarioStoryboard as apiUpdateScenarioStoryboard,
  updateThumbnailLayout as apiUpdateThumbnailLayout,
  updateThumbnailTitle as apiUpdateThumbnailTitle,
  uploadScenarioThumbnail as apiUploadScenarioThumbnail,
  uploadScenarioBackgroundAudio as apiUploadScenarioBackgroundAudio,
  uploadThumbnailAudio as apiUploadThumbnailAudio,
} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {fetchAccreditationRequests} from "../api/community";
import {useAuth} from "../composables/useAuth";
import {useToast} from "../composables/useToast";
import {useScenarioAutoplay} from "../composables/useScenarioAutoplay";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useThumbnailDragReorder} from "../composables/useThumbnailDragReorder";
import {useAudioTrimEditor} from "../composables/useAudioTrimEditor";
import {useAudioGloss} from "../composables/useAudioGloss";
import {useVoiceTakes} from "../composables/useVoiceTakes";
import {useThumbnailLifecycle} from "../composables/useThumbnailLifecycle";
import {useThumbnailResize} from "../composables/useThumbnailResize";
import {useSceneImageUpload} from "../composables/useSceneImageUpload";
import {useBackgroundAmbience} from "../composables/useBackgroundAmbience";
import { useBookmarkCategories } from "../composables/useBookmarkCategories";
import {draftAudioStorageKey} from "../utils/draftAudioStorage";
import ThumbnailCard from "../components/ThumbnailCard.vue";
import StudioRecorderPanel from "../components/StudioRecorderPanel.vue";
import VignetteMakerModal from "../components/VignetteMakerModal.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";
import BasePageHeader from "../components/ui/BasePageHeader.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import BaseBadge from "../components/ui/BaseBadge.vue";
import CollaboratorsPanel from "../components/community/CollaboratorsPanel.vue";
import DiscussionThread from "../components/community/DiscussionThread.vue";
import ScenarioHistoryPanel from "../components/scenario/ScenarioHistoryPanel.vue";
import {useCollaborators} from "../composables/useCollaborators";
import {
  buildPlaybackQueue,
  buildSelectedAudios,
  buildStoryboardItems,
  clamp,
  nullableInt,
  presetColumns,
  safeNumber,
  sortByIdxThenId,
  storyboardItemStyle,
} from "@/utils/scenarioStoryboard.js";

const props = defineProps({
  id: {type: String, required: true},
});

const route = useRoute();
const router = useRouter();
const {currentUser, isAuthenticated, loadMe} = useAuth();
const toast = useToast();
const {activeScenario: readerScenario, openReader, closeReader} = useScenarioReader();
const { isLiked, toggleLike, isBookmarked, toggleBookmark, fetchStatus } = useScenarioInteractions();
const { getCategory, setCategory, removeCategory, categoryList: bookmarkCategoryList, addCategory: addBookmarkCategory } = useBookmarkCategories();

const bookmarkCategoryPickerOpen = ref(false);
const newBookmarkCategoryName = ref("");
const bookmarkPickerEl = ref(null);

function handleBookmarkClick() {
  const wasBookmarked = isBookmarked(props.id);
  if (wasBookmarked) {
    removeCategory(props.id);
    toggleBookmark(props.id);
    bookmarkCategoryPickerOpen.value = false;
  } else {
    toggleBookmark(props.id);
    bookmarkCategoryPickerOpen.value = true;
  }
}

function assignBookmarkCategory(category) {
  setCategory(props.id, category);
  bookmarkCategoryPickerOpen.value = false;
}

function createAndAssignBookmarkCategory() {
  const name = newBookmarkCategoryName.value.trim();
  if (!name) return;
  addBookmarkCategory(name);
  setCategory(props.id, name);
  newBookmarkCategoryName.value = "";
  bookmarkCategoryPickerOpen.value = false;
}

function onDocumentClickForBookmarkPicker(event) {
  if (
      bookmarkCategoryPickerOpen.value &&
      bookmarkPickerEl.value &&
      !bookmarkPickerEl.value.contains(event.target)
  ) {
    bookmarkCategoryPickerOpen.value = false;
  }
}
const likeCount = ref(0);

async function handleToggleLike() {
  const status = await toggleLike(props.id);
  if (status) likeCount.value = status.likeCount;
}

const scenario = ref(null);
const languageName = ref("");
const thumbnails = ref([]);
const audioMap = ref({});
const selectedThumb = ref(null);
const activeAudioId = ref(null);
const error = ref("");
const isOwner = ref(false);

const collaboratorsPanelOpen = ref(false);
const scenarioIdRef = computed(() => props.id);
const authorUsernameRef = computed(() => scenario.value?.authorUsername ?? "");
const collab = useCollaborators(scenarioIdRef, {authorUsername: authorUsernameRef});
const canEditScenario = computed(() => isOwner.value || collab.canEdit.value);
const hasAnyRole = computed(() => isOwner.value || !!collab.myRole.value);

function openCollaboratorsPanel() {
  collaboratorsPanelOpen.value = true;
  collab.load();
  if (isOwner.value) collab.loadInviteLinks();
}

function closeCollaboratorsPanel() {
  collaboratorsPanelOpen.value = false;
}

const historyPanelOpen = ref(false);
const historyEntries = ref([]);
const historyLoading = ref(false);

async function openHistoryPanel() {
  historyPanelOpen.value = true;
  historyLoading.value = true;
  try {
    historyEntries.value = await fetchScenarioHistory(props.id);
  } catch (e) {
    toast.error(e.message || "Failed to load scenario history.");
  } finally {
    historyLoading.value = false;
  }
}

function closeHistoryPanel() {
  historyPanelOpen.value = false;
}

async function handleInvite(username, role) {
  await collab.invite(username, role);
}

async function handleCreateLink(payload) {
  await collab.createLink(payload);
}

const loading = ref(false);
const savingStoryboard = ref(false);
const savingOrder = ref(false);
const publishing = ref(false);
const reviewing = ref(false);
const reviewComment = ref("");
const originalAuthorUsername = ref("");
const studioFrontendOnly = false;
const studioSandboxMode = ref(false);
const hasExistingRequest = ref(false);

const highlightedThumbnailId = ref(null);

const infoDialogOpen = ref(false);
const studioDeleteConfirm = ref(false);
const studioDeleting = ref(false);

const studioAudioSettingsOpen = ref(false);
const studioTitleEditing = ref(false);
const studioTitleDraft = ref("");
const studioTitleSaving = ref(false);

async function saveStudioTitle() {
  const newTitle = studioTitleDraft.value.trim();
  studioTitleEditing.value = false;
  if (!newTitle || newTitle === scenario.value?.title || studioSandboxMode.value) return;
  studioTitleSaving.value = true;
  try {
    await updateScenarioMetadata(props.id, {title: newTitle});
    scenario.value = {...scenario.value, title: newTitle};
    toast.success("Title updated.");
  } catch (e) {
    toast.error(e.message || "Could not save title.");
  } finally {
    studioTitleSaving.value = false;
  }
}

function openInfoDialog() {
  infoDialogOpen.value = true;
  descriptionDraft.value = scenario.value?.description || "";
}

function closeInfoDialog() {
  infoDialogOpen.value = false;
}

const descriptionDraft = ref("");
const descriptionSaving = ref(false);

async function saveScenarioDescription() {
  const next = descriptionDraft.value.trim();
  if (next === (scenario.value?.description || "").trim()) return;
  if (studioSandboxMode.value) {
    scenario.value = {...scenario.value, description: next};
    return;
  }
  descriptionSaving.value = true;
  try {
    await updateScenarioMetadata(props.id, {description: next});
    scenario.value = {...scenario.value, description: next};
  } catch (e) {
    toast.error(e.message || "Could not update description.");
  } finally {
    descriptionSaving.value = false;
  }
}

const newTagInput = ref("");
const tagsSaving = ref(false);

async function persistScenarioTags(nextTags) {
  if (studioSandboxMode.value) {
    scenario.value = {...scenario.value, tags: nextTags};
    return;
  }
  tagsSaving.value = true;
  try {
    await updateScenarioMetadata(props.id, {tags: nextTags});
    scenario.value = {...scenario.value, tags: nextTags};
  } catch (e) {
    toast.error(e.message || "Could not update tags.");
  } finally {
    tagsSaving.value = false;
  }
}

function addScenarioTag() {
  const raw = newTagInput.value.trim().replace(/^#+/, "");
  newTagInput.value = "";
  if (!raw) return;
  const existing = scenario.value?.tags ?? [];
  if (existing.some((t) => t.toLowerCase() === raw.toLowerCase())) return;
  persistScenarioTags([...existing, raw]);
}

function removeScenarioTag(tag) {
  const existing = scenario.value?.tags ?? [];
  persistScenarioTags(existing.filter((t) => t !== tag));
}

const storyboardSettingsDialogOpen = ref(false);
const storyboardView = ref("studio");
const globalRecorderOpen = ref(false);

function openStoryboardSettingsDialog() {
  storyboardSettingsDialogOpen.value = true;
}

function closeStoryboardSettingsDialog() {
  storyboardSettingsDialogOpen.value = false;
}

function setStoryboardView(view) {
  storyboardView.value = view;
  if (view === "global") {
    globalRecorderOpen.value = false;
  }
}

// A "new comment" / "reply" notification links here with ?discussion={messageId}
// so we jump straight to the Discussion tab and scroll to that message.
const highlightMessageId = ref(null);

function applyDiscussionQueryParam() {
  const raw = route.query.discussion;
  const messageId = Array.isArray(raw) ? raw[0] : raw;
  if (!messageId || !isPublished.value) return;
  storyboardView.value = "discussion";
  highlightMessageId.value = messageId;
}

watch(() => route.query.discussion, applyDiscussionQueryParam);

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
  columns: 12,
});

const sortedThumbnails = computed(() => sortByIdxThenId(thumbnails.value));

const {
  tileDragId,
  tileDragOverId,
  tileDragGhost,
  tileDragGhostStyle,
  visualThumbnails,
  isTileDragging,
  isTileDragTarget,
  onTilePointerReorderStart,
} = useThumbnailDragReorder(sortedThumbnails, {
  applyOrder: (nextOrdered) => applyThumbnailOrder(nextOrdered),
  thumbnailContentUrl: (thumb) => thumbnailContentUrl(thumb),
  onDragStart: (thumb) => { selectedThumb.value = thumb; },
});

const selectedAudios = computed(() => {
  return buildSelectedAudios(audioMap.value, selectedThumb.value);
});

const selectedVoice = computed(() => {
  if (!selectedAudios.value.length) return null;
  return selectedAudios.value.find((audio) => String(audio.id) === String(selectedVoiceId.value)) ?? selectedAudios.value[0];
});

const {
  recordingTrimOpen,
  trimStart,
  trimEnd,
  trimDragging,
  trimPreviewPlaying,
  trimSaving,
  trimWaveBars,
  toggleTrimEditor,
  updateTrimStart,
  updateTrimEnd,
  setNearestTrimHandle,
  beginTrimDrag,
  resetTrimSelection,
  previewTrimSelection,
  applyTrimSelection,
  stopTrimPreview,
} = useAudioTrimEditor(selectedVoice, selectedThumb, {
  updateVoiceFields: (thumb, voice, fields) => updateVoiceFields(thumb, voice, fields),
  replaceAudioContent: (...args) => replaceAudioContent(...args),
  fetchThumbnailAudios,
  audioMap,
  studioSandboxMode,
  getScenarioId: () => props.id,
});

const {
  glossTranscription,
  glossGloss,
  glossFreeTranslation,
  glossSaving,
  glossAudioId,
  glossTranscriptionInput,
  saveGloss,
  focusGloss,
} = useAudioGloss(selectedVoice, selectedThumb, {
  updateAudioGloss: (...args) => updateAudioGloss(...args),
  fetchThumbnailAudios,
  updateVoiceFields: (thumb, voice, fields) => updateVoiceFields(thumb, voice, fields),
  audioMap,
  toast,
});

const {
  quickRecordingThumbId,
  quickRecordingDialogOpen,
  quickRecordingTitle,
  quickRecordingBlob,
  quickRecordingMimeType,
  quickRecordingError,
  quickRecordingUploading,
  quickRecordingTargetThumbId,
  quickRecordingVoiceId,
  quickRecordingCreatedDraft,
  selectedSpeaker,
  selectedVoiceId,
  studioRecorderEl,
  recordingFileInput,
  draftImportDialogOpen,
  availableAudioDrafts,
  importingDraftId,
  pendingRouteDraftId,
  pendingRouteDraft,
  recordingTargetVoice,
  recordingTargetSpeaker,
  nextSelectedSpeaker,
  recordingSceneLabel,
  recordingTargetLabel,
  recordingStatusLabel,
  recordingActionLabel,
  recordingMicLabel,
  toggleSelectedQuickRecording,
  scrollToRecorder,
  isRecordingThumb,
  recordButtonVoiceForThumb,
  recordButtonLabelForThumb,
  addVoiceForThumb,
  selectVoice,
  selectSpeakerSlot,
  findVoiceBySpeaker,
  addDraftVoice,
  ensureVoiceForRecording,
  removeVoice,
  speakerForAudio,
  takeLabel,
  normalizedAudioTitle,
  speakerClass,
  voicesForThumb,
  firstVoiceForThumb,
  voiceIndexInThumb,
  speakerForVoice,
  voiceDisplayTitle,
  audioPreviewDetail,
  selectedVoiceForThumb,
  targetVoiceForThumb,
  isLocalAudioId,
  speakerForIndex,
  nextSpeakerForThumb,
  isAudioPlaying,
  toggleAudioPlayback,
  addLocalAudioClip,
  openRecordingAudioFile,
  onRecordingAudioFileChange,
  importRecordingAudioFile,
  prepareAudioUploadFile,
  uploadVoiceAudioFile,
  restartSelectedRecording,
  readUnclaimedDraftAudio,
  readUnclaimedAudioDrafts,
  openDraftImportDialog,
  draftAudioFile,
  importAudioDraft,
  applyUnclaimedDraftAudio,
  closeQuickRecordingDialog,
  startQuickRecording,
  stopQuickRecording,
  toggleQuickRecording,
  confirmQuickRecordingUpload,
  discardQuickRecording,
  quickRecordingPreviewUrl,
  qrAudioEl,
  qrPlaying,
  qrCurrentTime,
  qrDuration,
  toggleQrPlayback,
  onQrTimeUpdate,
  onQrLoadedMetadata,
  onQrEnded,
  seekQrPlayback,
  qrProgressPercent,
} = useVoiceTakes({
  selectedThumb,
  thumbnails,
  sortedThumbnails,
  audioMap,
  scenario,
  activeAudioId,
  selectedVoice,
  selectedAudios,
  toast,
  currentUser,
  route,
  studioSandboxMode,
  getScenarioId: () => props.id,
  deleteAudio: (...args) => deleteAudio(...args),
  replaceAudioContent: (...args) => replaceAudioContent(...args),
  uploadThumbnailAudio: (...args) => uploadThumbnailAudio(...args),
  fetchThumbnailAudios,
  glossTranscription,
  glossGloss,
  glossFreeTranslation,
  selectThumb: (...args) => selectThumb(...args),
  openRecorderForSelection: (...args) => openRecorderForSelection(...args),
  useStudioSandbox: (...args) => useStudioSandbox(...args),
  openUploadDialog: () => openUploadDialog(),
  setActiveAudio: (...args) => setActiveAudio(...args),
  getSelectedSceneNumber: () => selectedSceneNumber.value,
  autoplayStop: () => autoplay.stop(),
  autoplayPause: () => autoplay.pause(),
  isAutoplayPlaying: () => autoplay.isPlaying.value,
  autoplayCurrentItem: () => autoplay.currentItem.value,
  fileBaseName,
  audioImportErrorMessage,
});

const {
  selectThumb,
  selectGlobalThumb,
  updateThumbTitle,
  persistThumbTitle,
  deleteThumb,
  syncSelectedThumbnailFromList,
  setOrderedThumbnails,
  mergePersistedThumbnailRows,
  isPersistableThumbnailId,
  persistThumbnailOrder,
  applyThumbnailOrder,
  moveThumbInOrder,
  reorderThumb,
} = useThumbnailLifecycle({
  selectedThumb,
  thumbnails,
  audioMap,
  activeAudioId,
  selectedThumbnailPanelOpen,
  storyboardView,
  globalRecorderOpen,
  studioSandboxMode,
  savingOrder,
  sortedThumbnails,
  getScenarioId: () => props.id,
  deleteThumbnail: (...args) => deleteThumbnail(...args),
  reloadThumbnails: () => loadThumbs(),
  onThumbnailDeleted: () => markEditedIfPublished(),
  updateThumbnailTitle: (...args) => updateThumbnailTitle(...args),
  reorderScenarioThumbnails: (...args) => reorderScenarioThumbnails(...args),
  toast,
  openRecorderForSelection: (...args) => openRecorderForSelection(...args),
  firstVoiceForThumb,
  speakerForVoice,
  nextSpeakerForThumb,
  selectVoice,
  selectedVoiceId,
  selectedSpeaker,
  quickRecordingThumbId,
  stopQuickRecording,
});

const {
  resizingThumbnailId,
  savingLayout,
  applySelectedThumbnailSize,
  beginThumbnailResize,
} = useThumbnailResize({
  thumbnails,
  selectedThumb,
  studioSandboxMode,
  getScenarioId: () => props.id,
  isPersistableThumbnailId: (...args) => isPersistableThumbnailId(...args),
  updateThumbnailLayout: (...args) => updateThumbnailLayout(...args),
  updateScenarioStoryboard: (...args) => updateScenarioStoryboard(...args),
  storyboardForm,
  applyLocalStoryboardState: (...args) => applyLocalStoryboardState(...args),
  syncSelectedThumbnailFromList: (...args) => syncSelectedThumbnailFromList(...args),
  toast,
});

const {
  uploadError,
  uploadSuccess,
  uploadFiles,
  uploadDragOver,
  uploadingProgress,
  uploadDialogOpen,
  vignetteMakerOpen,
  openUploadDialog,
  closeUploadDialog,
  onVignetteMakerInsert,
  onImageChange,
  onUploadDrop,
  removeUploadEntry,
  uploadImage,
} = useSceneImageUpload({
  scenario,
  thumbnails,
  selectedThumb,
  sortedThumbnails,
  studioSandboxMode,
  getScenarioId: () => props.id,
  pendingRouteDraftId,
  applyUnclaimedDraftAudio: (...args) => applyUnclaimedDraftAudio(...args),
  loadThumbs: (...args) => loadThumbs(...args),
  uploadScenarioThumbnail: (...args) => uploadScenarioThumbnail(...args),
  toast,
  fileBaseName,
});

const {
  ambiencePanelOpen,
  ambienceAdvancedOpen,
  selectedAmbiencePresetId,
  ambiencePresetEnabled,
  backgroundAudios,
  selectedBackgroundAudioId,
  backgroundAudioFileInput,
  ambDragOver,
  backgroundTitle,
  backgroundSourceLabel,
  backgroundSourceUrl,
  backgroundVolume,
  backgroundLoop,
  backgroundUploading,
  presetAudioGenerating,
  backgroundPlaying,
  ambiencePresets,
  ambienceLibraries,
  selectedBackgroundAudio,
  selectedAmbiencePreset,
  ambienceSearchLinks,
  backgroundSummaryTitle,
  backgroundSummaryNote,
  hasActiveBackgroundAmbience,
  openAmbiencePanel,
  closeAmbiencePanel,
  selectAmbiencePreset,
  selectBackgroundAudio,
  openBackgroundAudioFile,
  onBackgroundAudioFileChange,
  onAmbDragOver,
  onAmbDropZoneClick,
  onBackgroundAudioDrop,
  importBackgroundAudioFile,
  useSelectedAmbiencePreset,
  removeBackgroundAudio,
  stopBackgroundPlayback,
  disposeBackgroundPlayback,
  disposePresetAudioCache,
  pauseBackgroundPlayback,
  playBackgroundAudio,
  toggleBackgroundAudio,
  loadBackgroundAudios,
} = useBackgroundAmbience({
  scenario,
  isOwner,
  studioSandboxMode,
  getScenarioId: () => props.id,
  fetchScenarioBackgroundAudios,
  uploadScenarioBackgroundAudio: (...args) => uploadScenarioBackgroundAudio(...args),
  selectScenarioBackgroundAudio: (...args) => selectScenarioBackgroundAudio(...args),
  deleteAudio: (...args) => deleteAudio(...args),
  prepareAudioUploadFile: (file) => prepareAudioUploadFile(file),
  fileBaseName,
  audioImportErrorMessage,
});

const playbackQueue = computed(() => {
  const playableAudioMap = Object.fromEntries(
      Object.entries(audioMap.value).map(([thumbId, audios]) => [
        thumbId,
        (audios || []).filter((audio) => !audio.isDraft),
      ])
  );

  return buildPlaybackQueue(
      sortedThumbnails.value,
      playableAudioMap,
      (audio) => audio.previewUrl || buildApiUrl(`/api/audios/${audio.id}/content`)
  );
});

const isPublished = computed(() => scenario.value?.visibilityStatus === "PUBLISHED");

const hasUnpublishedEdits = ref(false);

function publicationEditKey() {
  return `vignette:scenario:${props.id}:publication-edit`;
}

function setUnpublishedEdits(value) {
  hasUnpublishedEdits.value = value;
  try {
    if (value) localStorage.setItem(publicationEditKey(), "1");
    else localStorage.removeItem(publicationEditKey());
  } catch {
  }
}

watch(
    [() => scenario.value?.id, isPublished],
    ([scenarioId, published]) => {
      if (!scenarioId || !published) {
        hasUnpublishedEdits.value = false;
        return;
      }
      try {
        hasUnpublishedEdits.value = localStorage.getItem(publicationEditKey()) === "1";
      } catch {
        hasUnpublishedEdits.value = false;
      }
    }
);

function markEditedIfPublished() {
  if (isPublished.value) setUnpublishedEdits(true);
}

async function deleteAudio(...args) {
  const result = await apiDeleteAudio(...args);
  markEditedIfPublished();
  return result;
}

async function replaceAudioContent(...args) {
  const result = await apiReplaceAudioContent(...args);
  markEditedIfPublished();
  return result;
}

async function deleteThumbnail(...args) {
  const result = await apiDeleteThumbnail(...args);
  markEditedIfPublished();
  return result;
}

async function reorderScenarioThumbnails(...args) {
  const result = await apiReorderScenarioThumbnails(...args);
  markEditedIfPublished();
  return result;
}

async function updateAudioGloss(...args) {
  const result = await apiUpdateAudioGloss(...args);
  markEditedIfPublished();
  return result;
}

async function updateScenarioMetadata(...args) {
  const result = await apiUpdateScenarioMetadata(...args);
  markEditedIfPublished();
  return result;
}

async function updateScenarioStoryboard(...args) {
  const result = await apiUpdateScenarioStoryboard(...args);
  markEditedIfPublished();
  return result;
}

async function updateThumbnailLayout(...args) {
  const result = await apiUpdateThumbnailLayout(...args);
  markEditedIfPublished();
  return result;
}

async function updateThumbnailTitle(...args) {
  const result = await apiUpdateThumbnailTitle(...args);
  markEditedIfPublished();
  return result;
}

async function uploadScenarioThumbnail(...args) {
  const result = await apiUploadScenarioThumbnail(...args);
  markEditedIfPublished();
  return result;
}

async function uploadScenarioBackgroundAudio(...args) {
  const result = await apiUploadScenarioBackgroundAudio(...args);
  markEditedIfPublished();
  return result;
}

async function selectScenarioBackgroundAudio(...args) {
  const result = await apiSelectScenarioBackgroundAudio(...args);
  markEditedIfPublished();
  return result;
}

async function uploadThumbnailAudio(...args) {
  const result = await apiUploadThumbnailAudio(...args);
  markEditedIfPublished();
  return result;
}

// Discussion is a published-only feature — fall back if the scenario is
// unpublished while that tab happens to be open.
watch(isPublished, (published) => {
  if (!published && storyboardView.value === "discussion") {
    storyboardView.value = "global";
  }
});

const reviewStatus = computed(() => scenario.value?.reviewStatus ?? "NONE");
const isForkPending = computed(() => reviewStatus.value === "PENDING");
const isForkRejected = computed(() => reviewStatus.value === "REJECTED");
const canPublish = computed(() =>
    isOwner.value &&
    !isForkPending.value &&
    !isForkRejected.value &&
    !studioSandboxMode.value &&
    !String(props.id).startsWith("emergency-")
);
const canReviewFork = computed(() =>
    isForkPending.value &&
    !!currentUser.value?.username &&
    currentUser.value.username === originalAuthorUsername.value
);
const isUnsavedEmergencyDraft = computed(() =>
    String(props.id).startsWith("emergency-") && !isAuthenticated.value
);

const storyboardColumns = computed(() => {
  const raw = storyboardForm.value.columns ?? scenario.value?.storyboardColumns ?? 3;
  const preset = storyboardForm.value.preset ?? scenario.value?.storyboardPreset ?? "GRID_3";
  const fallback = clamp(safeNumber(raw, 3), 1, 8);

  if (String(storyboardForm.value.layoutMode || scenario.value?.storyboardLayoutMode || "PRESET").toUpperCase() === "CUSTOM") {
    return 12;
  }

  return presetColumns(preset, fallback);
});

const storyboardSummary = computed(() => {
  const mode = String(storyboardForm.value.layoutMode || scenario.value?.storyboardLayoutMode || "PRESET").toUpperCase();
  const preset = String(storyboardForm.value.preset || scenario.value?.storyboardPreset || "GRID_3").replace("_", " ");
  const audioCount = playbackQueue.value.length;

  return {
    mode,
    preset,
    columns: storyboardColumns.value,
    audioCount,
  };
});

const activePresetName = computed(() => {
  const preset = String(storyboardForm.value.preset || "GRID_3").toUpperCase();
  if (preset === "CINEMATIC") return "Cinematic";
  if (preset === "MANGA") return "Manga";
  if (preset === "GRID_2") return "Strip";
  return "Classic";
});

const selectedSceneNumber = computed(() => {
  if (!selectedThumb.value) return 0;
  const index = sortedThumbnails.value.findIndex((thumb) => String(thumb.id) === String(selectedThumb.value.id));
  return index >= 0 ? index + 1 : selectedThumb.value.idx ?? selectedThumb.value.id;
});

const selectedThumbIndex = computed(() => {
  if (!selectedThumb.value) return -1;
  return sortedThumbnails.value.findIndex((thumb) => String(thumb.id) === String(selectedThumb.value.id));
});

const selectedStoryboardItem = computed(() => {
  if (!selectedThumb.value) return null;
  return storyboardItems.value.find((item) => String(item.id) === String(selectedThumb.value.id)) ?? selectedThumb.value;
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

function updateVoiceFields(thumb, voice, fields) {
  if (!thumb?.id || !voice?.id) return;

  audioMap.value = {
    ...audioMap.value,
    [thumb.id]: (audioMap.value[thumb.id] || []).map((audio) =>
        String(audio.id) === String(voice.id)
            ? {...audio, ...fields}
            : audio
    ),
  };
}

function ensureSelectedThumbnailPanelOpen() {
  if (selectedThumb.value) {
    selectedThumbnailPanelOpen.value = true;
  }
}

const AUDIO_IMPORT_FORMATS_LABEL = "MP3, WAV, OGG, WebM, M4A, FLAC, AAC, OPUS, or AIFF";

function fileBaseName(file, fallback = "") {
  return String(file?.name || fallback).replace(/\.[^.]+$/, "");
}

function userErrorMessage(error) {
  return String(error?.message || error || "").trim();
}

function audioImportErrorMessage(error, action = "import this audio") {
  const message = userErrorMessage(error);
  if (/413|too large|payload too large/i.test(message)) {
    return "This audio file is too large. Try a shorter clip or compress it, then import again.";
  }
  if (/415|unsupported media|content type/i.test(message)) {
    return `This audio type is not supported. Try ${AUDIO_IMPORT_FORMATS_LABEL}.`;
  }
  if (!message || /decode|decoding|audio data|not available|format/i.test(message)) {
    return `Could not read this audio file. Try ${AUDIO_IMPORT_FORMATS_LABEL}.`;
  }
  return `Could not ${action}: ${message}`;
}

function thumbnailContentUrl(thumb) {
  if (thumb?.previewUrl) return thumb.previewUrl;
  if (!thumb?.id) return "";
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

function useStudioSandbox(reason = "") {
  studioSandboxMode.value = true;
  error.value = "";
  languageName.value = "Studio sandbox";
  scenario.value = {
    id: props.id,
    title: "Vignette Studio",
    description: "Front-end storyboard sandbox",
    authorUsername: currentUser.value?.username ?? "designer",
    visibilityStatus: "DRAFT",
    storyboardLayoutMode: "PRESET",
    storyboardPreset: "GRID_3",
    storyboardColumns: 12,
  };
  isOwner.value = true;
  storyboardForm.value = {
    layoutMode: "PRESET",
    preset: "GRID_3",
    columns: 12,
  };
  thumbnails.value = [];
  audioMap.value = {};
  backgroundAudios.value = [];
  selectedBackgroundAudioId.value = null;
  selectedThumb.value = thumbnails.value[0] ?? null;
  activeAudioId.value = null;

  if (reason) {
    toast.info("Studio front-end sandbox enabled.");
  }
}

function applyLocalStoryboardState(nextState) {
  const layoutMode = String(nextState.layoutMode || storyboardForm.value.layoutMode || "PRESET").toUpperCase();
  const preset = String(nextState.preset || storyboardForm.value.preset || "GRID_3").toUpperCase();
  const columns = 12;

  storyboardForm.value = {
    ...storyboardForm.value,
    layoutMode,
    preset,
    columns,
  };

  if (scenario.value) {
    scenario.value = {
      ...scenario.value,
      storyboardLayoutMode: layoutMode,
      storyboardPreset: preset,
      storyboardColumns: columns,
    };
  }
}

function openRecorderForSelection({scroll = false} = {}) {
  selectedThumbnailPanelOpen.value = true;
  if (storyboardView.value === "global") {
    globalRecorderOpen.value = true;
  }
  if (scroll) {
    scrollToRecorder();
  }
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
  onItemChange: (item) => {
    focusPlaybackItem(item);
  },
  onStop: () => {
    activeAudioId.value = null;
    stopBackgroundPlayback();
  },
  onEndedAll: () => {
    activeAudioId.value = null;
    stopBackgroundPlayback();
    toast.success("Automatic playback finished.");
  },
});

watch(
    () => autoplay.isPlaying.value,
    (playing) => {
      if (playing && hasActiveBackgroundAmbience.value) {
        playBackgroundAudio().catch(() => {});
      } else if (!playing && autoplay.isPaused.value) {
        pauseBackgroundPlayback();
      }
    }
);

watch([hasActiveBackgroundAmbience, selectedBackgroundAudio, selectedAmbiencePreset], () => {
  stopBackgroundPlayback();
  if (autoplay.isPlaying.value && hasActiveBackgroundAmbience.value) {
    playBackgroundAudio().catch(() => {});
  }
});

async function setActiveAudio(audio) {
  if (!audio || !selectedThumb.value) {
    autoplay.stop();
    activeAudioId.value = null;
    return;
  }

  if (isAudioPlaying(audio, selectedThumb.value)) {
    autoplay.pause();
    return;
  }

  selectVoice(audio, selectedThumb.value);

  if (audio.isDraft) return;

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

function openScenarioReader() {
  if (!scenario.value) return;
  openReader(scenario.value);
}

async function loadOriginalAuthorIfFork() {
  originalAuthorUsername.value = "";
  if (!scenario.value?.parentScenarioId || scenario.value.reviewStatus !== "PENDING") return;
  try {
    const parent = await fetchScenario(scenario.value.parentScenarioId);
    originalAuthorUsername.value = parent.authorUsername ?? "";
  } catch {
  }
}

async function approveCurrentFork() {
  if (!scenario.value || reviewing.value) return;
  reviewing.value = true;
  try {
    scenario.value = await approveFork(props.id, reviewComment.value.trim() || undefined);
    reviewComment.value = "";
    toast.success("Fork approved. The author can now publish it.");
  } catch (e) {
    toast.error(e.message || "Failed to approve fork.");
  } finally {
    reviewing.value = false;
  }
}

async function rejectCurrentFork() {
  if (!scenario.value || reviewing.value) return;
  reviewing.value = true;
  try {
    scenario.value = await rejectFork(props.id, reviewComment.value.trim() || undefined);
    reviewComment.value = "";
    toast.success("Fork rejected.");
  } catch (e) {
    toast.error(e.message || "Failed to reject fork.");
  } finally {
    reviewing.value = false;
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
  try {
    const status = await apiFetch(`/api/scenarios/${props.id}/interactions`);
    likeCount.value = status.likeCount ?? 0;
  } catch {
    likeCount.value = 0;
  }
}

async function checkExistingRequest() {
  if (!currentUser.value?.username) {
    hasExistingRequest.value = false;
    return;
  }

  try {
    const requests = await fetchAccreditationRequests("SCENARIO_EDIT", "SCENARIO", props.id);
    hasExistingRequest.value = requests.some(
      r => r.requesterUsername === currentUser.value?.username
        && (r.status === "PENDING" || r.status === "APPROVED")
    );
  } catch {
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

  const selectedVoices = selectedThumb.value?.id ? voicesForThumb(selectedThumb.value) : [];
  const selectedVoiceStillExists = selectedVoices.some((audio) => String(audio.id) === String(selectedVoiceId.value ?? ""));
  if (!selectedVoiceStillExists) {
    const firstVoice = firstVoiceForThumb(selectedThumb.value);
    selectedVoiceId.value = firstVoice?.id ?? null;
    selectedSpeaker.value = firstVoice ? speakerForVoice(firstVoice, selectedThumb.value) : nextSpeakerForThumb(selectedThumb.value);
  }
}

async function loadAll() {
  loading.value = true;
  error.value = "";
  studioSandboxMode.value = false;
  storyboardView.value = "studio";

  try {
    await loadMe();

    if (String(props.id).startsWith("emergency-")) {
      if (isAuthenticated.value) {
        const draftId = route.query.draftAudio;
        const params = new URLSearchParams({draftAudio: String(draftId ?? "")});
        const draft = readUnclaimedDraftAudio(Array.isArray(draftId) ? draftId[0] : draftId);
        if (draft?.title) params.set("draftTitle", draft.title);
        router.replace(`/create-scenario?${params.toString()}`);
        return;
      }
      await applyUnclaimedDraftAudio();
      return;
    }

    await loadScenario();

    isOwner.value =
        !!currentUser.value &&
        currentUser.value.username === scenario.value.authorUsername;
        await collab.load();
    await loadOriginalAuthorIfFork();
    if (!canEditScenario.value) {
      storyboardView.value = "global";
    }
    await Promise.all([loadThumbs(), loadBackgroundAudios(), checkExistingRequest()]);
    await applyUnclaimedDraftAudio();
    applyDiscussionQueryParam();
  } catch (e) {
    if (studioFrontendOnly || studioSandboxMode.value || String(props.id).startsWith("emergency-")) {
      useStudioSandbox(e.message);
      await applyUnclaimedDraftAudio();
    } else {
      error.value = e.message;
    }
  } finally {
    loading.value = false;
  }
}

async function deleteCurrentScenario() {
  if (!scenario.value || studioDeleting.value) return;
  studioDeleting.value = true;
  try {
    await deleteScenario(props.id);
    router.push("/scenarios");
  } catch (e) {
    toast.error(e.message || "Failed to delete scenario.");
    studioDeleteConfirm.value = false;
  } finally {
    studioDeleting.value = false;
  }
}

async function publishCurrentScenario() {
  if (!scenario.value || publishing.value || !canPublish.value) return;
  publishing.value = true;
  try {
    scenario.value = await publishScenario(props.id);
    setUnpublishedEdits(false);
    toast.success("Scenario published.");
  } catch (e) {
    toast.error(e.message || "Failed to publish scenario.");
  } finally {
    publishing.value = false;
  }
}

const updateConfirmOpen = ref(false);

function openUpdateConfirm() {
  if (!hasUnpublishedEdits.value || publishing.value) return;
  updateConfirmOpen.value = true;
}

function cancelUpdateConfirm() {
  updateConfirmOpen.value = false;
}

async function confirmUpdatePublished() {
  if (!scenario.value || publishing.value) return;
  publishing.value = true;
  try {
    scenario.value = await publishScenario(props.id);
    setUnpublishedEdits(false);
    updateConfirmOpen.value = false;
    toast.success("Live scenario updated.");
  } catch (e) {
    toast.error(e.message || "Failed to update the published scenario.");
  } finally {
    publishing.value = false;
  }
}

async function saveStoryboardSettings() {
  if (!scenario.value || savingStoryboard.value) return;

  savingStoryboard.value = true;
  const layoutMode = String(storyboardForm.value.layoutMode || "PRESET").toUpperCase();
  const preset = String(storyboardForm.value.preset || "GRID_3").toUpperCase();

  try {
    const cols = {GRID_2: 2, CINEMATIC: 3, MANGA: 3}[preset] ?? 3;
    await updateScenarioStoryboard(props.id, {layoutMode, preset, columns: cols});
    applyLocalStoryboardState({layoutMode, preset, columns: cols});
    closeStoryboardSettingsDialog();
    toast.success("Storyboard settings saved.");
  } catch (e) {
    toast.error(e.message || "Failed to save storyboard settings.");
  } finally {
    savingStoryboard.value = false;
  }
}

async function applyStoryboardPreset(preset) {
  if (!scenario.value || savingStoryboard.value) return;

  savingStoryboard.value = true;
  try {
    const cols = {GRID_2: 2, CINEMATIC: 3, MANGA: 3}[preset] ?? 3;
    if (!studioFrontendOnly && !studioSandboxMode.value && !String(props.id).startsWith("emergency-")) {
      await updateScenarioStoryboard(props.id, {layoutMode: "PRESET", preset, columns: cols});
    }
    applyLocalStoryboardState({layoutMode: "PRESET", preset, columns: cols});
    toast.success(`${preset.replace("_", " ")} preset applied.`);
  } catch (e) {
    toast.error(e.message || "Failed to apply preset.");
  } finally {
    savingStoryboard.value = false;
  }
}

async function refreshAudios() {
  await loadThumbs();
}


const storyboardItems = computed(() => {
  return buildStoryboardItems({
    thumbnails: visualThumbnails.value,
    layoutMode: storyboardForm.value.layoutMode ?? "PRESET",
    preset: storyboardForm.value.preset ?? "GRID_3",
    columns: storyboardColumns.value,
  });
});

onMounted(loadAll);
onMounted(() => document.addEventListener("click", onDocumentClickForBookmarkPicker));
onBeforeUnmount(() => document.removeEventListener("click", onDocumentClickForBookmarkPicker));
onBeforeUnmount(() => {
  disposeBackgroundPlayback();
  stopTrimPreview();
  backgroundAudios.value.forEach((audio) => {
    if (audio.previewUrl) URL.revokeObjectURL(audio.previewUrl);
  });
  disposePresetAudioCache();
});
</script>

<template>
  <main class="page page--studio">
    <BaseLoader v-if="loading">Loading storyboard...</BaseLoader>

    <BaseAlert v-else-if="error" type="error">
      {{ error }}
    </BaseAlert>

    <template v-else-if="scenario">
                <div
            v-if="infoDialogOpen"
            class="dialog-backdrop"
            @click.self="closeInfoDialog"
        >
          <section class="si-card" role="dialog" aria-modal="true" aria-labelledby="scenario-info-title">

            <button type="button" class="si-close" aria-label="Close" @click="closeInfoDialog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 6 6 18M6 6l12 12"/>
              </svg>
            </button>

            <div class="si-hero">
              <span class="si-status" :class="isPublished ? 'si-status--pub' : 'si-status--draft'">
                <span class="si-status__dot"></span>
                {{ isPublished ? "Published" : "Draft" }}
              </span>
              <span v-if="isForkPending" class="si-status si-status--review">
                <span class="si-status__dot"></span>
                Pending review
              </span>
              <span v-else-if="isForkRejected" class="si-status si-status--rejected">
                <span class="si-status__dot"></span>
                Fork rejected
              </span>
              <h2 id="scenario-info-title" class="si-title">{{ scenario.title || "Untitled scenario" }}</h2>
            </div>

            <div class="si-stats">
              <div class="si-stat">
                <span class="si-stat__num">{{ thumbnails.length }}</span>
                <span class="si-stat__lbl">scenes</span>
              </div>
              <div class="si-stat__div"></div>
              <div class="si-stat">
                <span class="si-stat__num">{{ playbackQueue.length }}</span>
                <span class="si-stat__lbl">audio clips</span>
              </div>
              <div class="si-stat__div"></div>
              <div class="si-stat">
                <span class="si-stat__num si-stat__num--lang">{{ languageName || "Unknown" }}</span>
                <span class="si-stat__lbl">language</span>
              </div>
              <div class="si-stat__div"></div>
              <div class="si-stat">
                <span class="si-stat__num">{{ scenario.authorUsername || "Unknown" }}</span>
                <span class="si-stat__lbl">author</span>
              </div>
              <div class="si-stat__div"></div>
              <div class="si-stat">
                <span class="si-stat__num">{{ likeCount }}</span>
                <span class="si-stat__lbl">likes</span>
              </div>
            </div>

            <div class="si-tags-section">
              <p class="si-desc__label">Tags</p>
              <div class="si-tags si-tags--edit">
                <span v-for="tag in scenario.tags" :key="tag" class="si-tag">
                  #{{ tag }}
                  <button
                      v-if="canEditScenario && !isPublished"
                      type="button"
                      class="si-tag__remove"
                      :aria-label="`Remove tag ${tag}`"
                      :disabled="tagsSaving"
                      @click="removeScenarioTag(tag)"
                  >×</button>
                </span>
                <form
                    v-if="canEditScenario && !isPublished"
                    class="si-tag-add"
                    @submit.prevent="addScenarioTag"
                >
                  <input
                      v-model="newTagInput"
                      class="si-tag-add__input"
                      placeholder="Add tag…"
                      maxlength="32"
                      :disabled="tagsSaving"
                  />
                </form>
                <span v-else-if="!scenario.tags?.length" class="si-tags__empty">No tags.</span>
              </div>
            </div>

            <div class="si-desc">
              <p class="si-desc__label">Description</p>
              <textarea
                  v-if="canEditScenario && !isPublished"
                  v-model="descriptionDraft"
                  class="si-desc__textarea"
                  rows="3"
                  maxlength="500"
                  placeholder="Add a description…"
                  :disabled="descriptionSaving"
                  @blur="saveScenarioDescription"
              ></textarea>
              <p v-else class="si-desc__text">{{ scenario.description?.trim() || "No description." }}</p>
            </div>

            <div v-if="scenario.parentScenarioId && reviewStatus !== 'NONE'" class="si-desc">
              <p class="si-desc__label">Fork review</p>
              <p class="si-desc__text">
                <template v-if="isForkPending">This fork is awaiting review from the original author before it can be published.</template>
                <template v-else-if="isForkRejected">This fork was rejected{{ scenario.reviewComment ? ": " + scenario.reviewComment : "." }}</template>
                <template v-else-if="reviewStatus === 'APPROVED'">This fork was approved{{ scenario.reviewedByUsername ? " by " + scenario.reviewedByUsername : "" }} and can be published.</template>
              </p>
            </div>

          </section>
        </div>

        <div v-if="studioDeleteConfirm" class="dialog-backdrop" @click.self="studioDeleteConfirm = false">
          <div class="ms-confirm" style="z-index:210">
            <p class="ms-confirm__eyebrow">Permanent action</p>
            <h2 class="ms-confirm__title">Delete "{{ scenario?.title || 'this scenario' }}"?</h2>
            <p class="ms-confirm__body">All scenes, audio recordings, and annotations will be permanently removed. This cannot be undone.</p>
            <div class="ms-confirm__actions">
              <button type="button" class="ms-confirm__cancel" @click="studioDeleteConfirm = false">Keep it</button>
              <button type="button" class="ms-confirm__delete" :disabled="studioDeleting" @click="deleteCurrentScenario">
                <template v-if="studioDeleting"><span class="ms-spin"></span> Deleting…</template>
                <template v-else>Yes, delete</template>
              </button>
            </div>
          </div>
        </div>

        <div v-if="updateConfirmOpen" class="dialog-backdrop" @click.self="cancelUpdateConfirm">
          <div class="ms-confirm" style="z-index:210">
            <p class="ms-confirm__eyebrow">Publication update</p>
            <h2 class="ms-confirm__title">Update the live version?</h2>
            <p class="ms-confirm__body">Your changes are saved. Confirm that the current version is ready for the published scenario.</p>
            <div class="ms-confirm__actions">
              <button type="button" class="ms-confirm__cancel" @click="cancelUpdateConfirm">Keep editing</button>
              <button type="button" class="ms-confirm__delete" :disabled="publishing" @click="confirmUpdatePublished">
                <template v-if="publishing"><span class="ms-spin"></span> Updating…</template>
                <template v-else>Yes, update live version</template>
              </button>
            </div>
          </div>
        </div>

        <div
                  v-if="uploadDialogOpen"
                  class="dialog-backdrop"
                  @click.self="closeUploadDialog"
              >
                <section
                    class="ud-card"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="thumbnail-upload-title"
                >
                  <div class="ud-header">
                    <div>
                      <p class="ud-eyebrow">{{ pendingRouteDraft ? 'Audio draft ready' : 'New scene' }}</p>
                      <h2 id="thumbnail-upload-title" class="ud-title">
                        {{ pendingRouteDraft ? 'Give this audio a scene' : 'Add a scene image' }}
                      </h2>
                      <p v-if="pendingRouteDraft" class="ud-context">
                        Choose an image or create one with Vignette. “{{ pendingRouteDraft.title || 'Audio draft' }}”
                        will be attached to it automatically.
                      </p>
                    </div>
                    <button type="button" class="ud-close" aria-label="Close" @click="closeUploadDialog">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>

                  <div v-if="!uploadFiles.length" class="ud-source-row">
                    <label
                        class="ud-dropzone ud-dropzone--half"
                        :class="{ 'ud-dropzone--over': uploadDragOver }"
                        @dragover.prevent="uploadDragOver = true"
                        @dragleave="uploadDragOver = false"
                        @drop.prevent="onUploadDrop"
                    >
                      <div class="ud-dropzone__icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                          <polyline points="17 8 12 3 7 8"/>
                          <line x1="12" y1="3" x2="12" y2="15"/>
                        </svg>
                      </div>
                      <p class="ud-dropzone__label">{{ pendingRouteDraft ? 'Choose a scene image' : 'Drop images here' }}</p>
                      <p class="ud-dropzone__sub">Drop here or click to browse · PNG, JPG, WebP, SVG</p>
                      <input type="file" accept="image/*,.svg,.xml,text/xml,application/xml" multiple class="ud-file-input" @change="onImageChange"/>
                    </label>

                    <button
                        type="button"
                        class="ud-maker-btn"
                        @click="uploadDialogOpen = false; vignetteMakerOpen = true"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" style="width:28px;height:28px;margin-bottom:8px">
                        <circle cx="12" cy="8" r="4"/><path d="M8 14c-3 1-5 3-5 5h18c0-2-2-4-5-5"/><path d="M17 3l2 2-7 7-3-1 1-3 7-5z"/>
                      </svg>
                      <span class="ud-maker-btn__label">Create an image with Vignette</span>
                      <span class="ud-maker-btn__sub">Draw characters &amp; scenes,<br>then insert as a frame image</span>
                    </button>
                  </div>

                  <template v-if="uploadFiles.length">
                    <div class="ud-grid">
                      <div
                          v-for="(entry, index) in uploadFiles"
                          :key="index"
                          class="ud-grid-item"
                      >
                        <div class="ud-grid-thumb">
                          <img :src="entry.previewUrl" :alt="entry.title" class="ud-grid-img"/>
                          <button
                              type="button"
                              class="ud-grid-remove"
                              title="Remove"
                              @click="removeUploadEntry(index)"
                          >×</button>
                        </div>
                        <input
                            v-model="entry.title"
                            class="ud-grid-title"
                            placeholder="Scene title…"
                        />
                      </div>
                    </div>

                    <div class="ud-add-more-row">
                      <label
                          class="ud-add-more"
                          @dragover.prevent="uploadDragOver = true"
                          @dragleave="uploadDragOver = false"
                          @drop.prevent="onUploadDrop"
                          :class="{ 'ud-dropzone--over': uploadDragOver }"
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M12 5v14M5 12h14"/>
                        </svg>
                        Add more images
                        <input type="file" accept="image/*,.svg,.xml,text/xml,application/xml" multiple class="ud-file-input" @change="onImageChange"/>
                      </label>

                      <button
                          type="button"
                          class="ud-add-more ud-add-more--maker"
                          @click="uploadDialogOpen = false; vignetteMakerOpen = true"
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <circle cx="12" cy="8" r="4"/><path d="M8 14c-3 1-5 3-5 5h18c0-2-2-4-5-5"/>
                        </svg>
                        Create another with Vignette
                      </button>
                    </div>
                  </template>

                  <div v-if="uploadingProgress > 0" class="ud-progress">
                    <div class="ud-progress__bar" :style="{ width: uploadingProgress + '%' }"></div>
                  </div>

                  <button
                      class="ud-submit"
                      :disabled="!uploadFiles.length"
                      @click="uploadImage"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 5v14M5 12h14"/>
                    </svg>
                    Add {{ uploadFiles.length > 1 ? uploadFiles.length + ' scenes' : 'scene' }} to storyboard
                  </button>

                  <BaseAlert v-if="uploadSuccess" type="success">{{ uploadSuccess }}</BaseAlert>
                  <BaseAlert v-if="uploadError" type="error">{{ uploadError }}</BaseAlert>
                </section>
              </div>

              <div
                  v-if="draftImportDialogOpen"
                  class="dialog-backdrop"
                  @click.self="draftImportDialogOpen = false"
              >
                <section class="di-card" role="dialog" aria-modal="true" aria-labelledby="draft-import-title">
                  <div class="di-head">
                    <div>
                      <p class="di-eyebrow">Saved on this device</p>
                      <h2 id="draft-import-title" class="di-title">Import an audio draft</h2>
                      <p class="di-sub">
                        Add a quick recording to
                        <strong>{{ selectedThumb?.title || `Scene ${selectedSceneNumber}` }}</strong>.
                      </p>
                    </div>
                    <button type="button" class="di-close" aria-label="Close audio drafts" @click="draftImportDialogOpen = false">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                           stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>

                  <div v-if="availableAudioDrafts.length" class="di-list">
                    <article v-for="draft in availableAudioDrafts" :key="draft.id" class="di-item">
                      <div class="di-item__top">
                        <span class="di-item__icon" aria-hidden="true">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                               stroke-linecap="round" stroke-linejoin="round">
                            <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
                            <path d="M19 10v2a7 7 0 0 1-14 0v-2M12 19v3"/>
                          </svg>
                        </span>
                        <div>
                          <strong>{{ draft.title || "Audio draft" }}</strong>
                          <small>{{ new Date(draft.createdAt).toLocaleString([], {dateStyle: 'medium', timeStyle: 'short'}) }}</small>
                        </div>
                      </div>
                      <audio :src="draft.dataUrl" controls preload="metadata" class="di-item__audio"></audio>
                      <button
                          type="button"
                          class="di-item__import"
                          :disabled="importingDraftId != null"
                          @click="importAudioDraft(draft)"
                      >
                        {{ importingDraftId === draft.id ? "Adding…" : "Add to this scene" }}
                        <span aria-hidden="true">→</span>
                      </button>
                    </article>
                  </div>

                  <div v-else class="di-empty">
                    <span class="di-empty__icon" aria-hidden="true">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                           stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
                        <path d="M19 10v2a7 7 0 0 1-14 0v-2M12 19v3"/>
                      </svg>
                    </span>
                    <strong>No audio drafts yet</strong>
                    <p>Use Quick audio outside the studio, then come back here to attach the recording to a scene.</p>
                  </div>
                </section>
              </div>

              <div
                  v-if="ambiencePanelOpen"
                  class="dialog-backdrop"
                  @click.self="closeAmbiencePanel"
              >
                <section class="amb-card" role="dialog" aria-modal="true" aria-labelledby="ambience-title">
                  <div class="amb-head">
                    <div>
                      <p class="amb-eyebrow">Optional vignette ambience</p>
                      <h2 id="ambience-title" class="amb-title">{{ backgroundSummaryTitle }}</h2>
                    </div>
                    <button type="button" class="amb-close" aria-label="Close" @click="closeAmbiencePanel">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>

                  <input
                      ref="backgroundAudioFileInput"
                      class="rec-file-input"
                      type="file"
                      accept="audio/*"
                      @change="onBackgroundAudioFileChange"
                  />

                  <div class="amb-layout amb-layout--minimal">
                    <section class="amb-current amb-current--minimal">
                      <div class="rec-quick-player ambient-player">
                        <template v-if="selectedBackgroundAudio">
                          <span class="fiche-speaker ambience">BG</span>
                          <div>
                            <strong>{{ selectedBackgroundAudio.title || "Background audio" }}</strong>
                            <small>{{ selectedBackgroundAudio.sourceLabel || selectedBackgroundAudio.sourceUrl || "Vignette-wide audio" }}</small>
                          </div>
                          <span class="fiche-wave">
                            <i></i><i></i><i></i><i></i><i></i><i></i>
                          </span>
                          <button
                              type="button"
                              :title="backgroundPlaying ? 'Pause background audio' : 'Play background audio'"
                              :aria-label="backgroundPlaying ? 'Pause background audio' : 'Play background audio'"
                              @click="toggleBackgroundAudio"
                          >
                            <span v-if="backgroundPlaying" class="pause-icon" aria-hidden="true"></span>
                            <span v-else aria-hidden="true">▶</span>
                          </button>
                        </template>
                        <template v-else>
                          <span class="fiche-speaker ambience">BG</span>
                          <div>
                            <strong>{{ selectedAmbiencePreset.title }}</strong>
                            <small>{{ selectedAmbiencePreset.hint }}</small>
                          </div>
                          <span class="fiche-wave">
                            <i></i><i></i><i></i><i></i><i></i><i></i>
                          </span>
                          <button
                              type="button"
                              :disabled="presetAudioGenerating"
                              :title="backgroundPlaying ? 'Pause preset audio' : 'Play preset audio'"
                              :aria-label="backgroundPlaying ? 'Pause preset audio' : 'Play preset audio'"
                              @click="toggleBackgroundAudio"
                          >
                            <span v-if="backgroundPlaying" class="pause-icon" aria-hidden="true"></span>
                            <span v-else aria-hidden="true">▶</span>
                          </button>
                        </template>
                      </div>

                      <div v-if="backgroundAudios.length" class="ambient-list">
                        <button
                            v-for="audio in backgroundAudios"
                            :key="audio.id"
                            type="button"
                            :class="{ active: String(audio.id) === String(selectedBackgroundAudio?.id) }"
                            @click="selectBackgroundAudio(audio)"
                        >
                          <span>{{ audio.idx ?? "•" }}</span>
                          <strong>{{ audio.title || "Background audio" }}</strong>
                        </button>
                      </div>

                      <div class="side-settings side-settings--ambient amb-mix-row">
                        <label>
                          <span>Volume</span>
                          <input v-model="backgroundVolume" type="range" min="0" max="100"/>
                          <strong>{{ backgroundVolume }}%</strong>
                        </label>
                        <label class="switch-row">
                          <span>Loop</span>
                          <input v-model="backgroundLoop" type="checkbox"/>
                          <strong>{{ backgroundLoop ? "On" : "Off" }}</strong>
                        </label>
                      </div>

                      <div class="amb-core-actions">
                        <div
                            class="amb-drop-zone"
                            :class="{ 'amb-drop-zone--over': ambDragOver, 'amb-drop-zone--busy': backgroundUploading }"
                            :aria-disabled="!(studioFrontendOnly || isOwner)"
                            @dragover.prevent="onAmbDragOver"
                            @dragleave="ambDragOver = false"
                            @drop.prevent="onBackgroundAudioDrop"
                            @click="onAmbDropZoneClick"
                        >
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="18" height="18" aria-hidden="true">
                            <path d="M12 3v13M8 12l4 4 4-4"/>
                            <path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>
                          </svg>
                          <span v-if="backgroundUploading" class="amb-drop-zone__label">
                            <span class="rec-extract-spinner" aria-hidden="true"></span> Adding audio…
                          </span>
                          <span v-else class="amb-drop-zone__label">
                            Drop audio or <u>browse</u>
                          </span>
                          <small class="amb-drop-zone__hint">MP3, WAV, FLAC, OGG, M4A, OPUS</small>
                        </div>

                        <div class="amb-drop-actions amb-drop-actions--compact">
                          <button type="button" class="rec-import-btn rec-import-btn--primary" :disabled="!(studioFrontendOnly || isOwner) || backgroundUploading || presetAudioGenerating" @click="useSelectedAmbiencePreset">
                            <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" width="13" height="13">
                              <path d="M10 2a1 1 0 0 1 .894.553l1.76 3.568 3.938.572a1 1 0 0 1 .554 1.706l-2.849 2.777.672 3.922a1 1 0 0 1-1.451 1.054L10 14.302l-3.518 1.85a1 1 0 0 1-1.451-1.054l.672-3.922-2.849-2.777a1 1 0 0 1 .554-1.706l3.938-.572 1.76-3.568A1 1 0 0 1 10 2Z"/>
                            </svg>
                            {{ presetAudioGenerating ? "Making…" : "Use preset" }}
                          </button>
                          <button type="button" class="rec-import-btn" :disabled="!selectedBackgroundAudio || backgroundUploading" @click="removeBackgroundAudio()">
                            Remove
                          </button>
                        </div>
                      </div>

                      <button
                          type="button"
                          class="rec-collapse-toggle amb-advanced-toggle"
                          :aria-expanded="ambienceAdvancedOpen"
                          @click="ambienceAdvancedOpen = !ambienceAdvancedOpen"
                      >
                        Advanced settings
                        <svg class="rec-collapse-chevron" :class="{ open: ambienceAdvancedOpen }" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="11" height="11">
                          <path d="M4 6l4 4 4-4"/>
                        </svg>
                      </button>

                      <div v-if="ambienceAdvancedOpen" class="amb-advanced-panel">
                        <section class="amb-advanced-section">
                          <div class="amb-section-head">
                            <span>Details</span>
                            <strong>{{ backgroundTitle || selectedAmbiencePreset.title }}</strong>
                          </div>
                          <label class="ambient-field">
                            <span>Title</span>
                            <input v-model="backgroundTitle" class="side-note-input" maxlength="255" placeholder="Forest, market, rain..."/>
                          </label>
                          <label class="ambient-field">
                            <span>Credit</span>
                            <input v-model="backgroundSourceLabel" class="side-note-input" maxlength="180" placeholder="Creator or library"/>
                          </label>
                          <label class="ambient-field">
                            <span>Source</span>
                            <input v-model="backgroundSourceUrl" class="side-note-input" maxlength="512" placeholder="https://..."/>
                          </label>
                        </section>

                        <section class="amb-advanced-section">
                          <div class="amb-section-head">
                            <span>Presets</span>
                            <strong>{{ selectedAmbiencePreset.title }}</strong>
                          </div>
                          <div class="amb-preset-grid">
                            <button
                                v-for="preset in ambiencePresets"
                                :key="preset.id"
                                type="button"
                                :class="{ active: preset.id === selectedAmbiencePreset.id }"
                                @click="selectAmbiencePreset(preset)"
                            >
                              <span>{{ preset.badge }}</span>
                              <strong>{{ preset.title }}</strong>
                              <small>{{ preset.hint }}</small>
                            </button>
                          </div>
                        </section>

                        <section class="amb-advanced-section">
                          <div class="amb-section-head amb-section-head--libraries">
                            <span>Libraries</span>
                            <strong>{{ selectedAmbiencePreset.badge }}</strong>
                          </div>
                          <div class="amb-library-grid">
                            <a
                                v-for="source in ambienceSearchLinks"
                                :key="source.id"
                                :href="source.url"
                                target="_blank"
                                rel="noreferrer"
                            >
                              <span>{{ source.tag }}</span>
                              <strong>{{ source.label }}</strong>
                              <small>{{ source.note }}</small>
                            </a>
                          </div>
                        </section>
                      </div>
                    </section>
                  </div>
                </section>
              </div>

              <div
                  v-if="storyboardSettingsDialogOpen"
                  class="dialog-backdrop"
                  @click.self="closeStoryboardSettingsDialog"
              >
                <section class="ss-card" role="dialog" aria-modal="true" aria-labelledby="storyboard-settings-title">

                  <div class="ss-head">
                    <div>
                      <p class="ss-eyebrow">Studio settings</p>
                      <h2 id="storyboard-settings-title" class="ss-title">Layout & Publication</h2>
                    </div>
                    <button type="button" class="ss-close" aria-label="Close" @click="closeStoryboardSettingsDialog">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>

                  <div class="ss-section">
                    <p class="ss-section__label">Mode</p>
                    <div class="ss-mode-tabs">
                      <button
                          type="button"
                          class="ss-mode-tab"
                          :class="{ active: storyboardForm.layoutMode === 'PRESET' }"
                          @click="storyboardForm.layoutMode = 'PRESET'"
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                          <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
                          <rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
                        </svg>
                        Preset
                        <small>Auto-composed grid</small>
                      </button>
                      <button
                          type="button"
                          class="ss-mode-tab"
                          :class="{ active: storyboardForm.layoutMode === 'CUSTOM' }"
                          @click="storyboardForm.layoutMode = 'CUSTOM'"
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>
                        </svg>
                        Custom
                        <small>Drag &amp; resize freely</small>
                      </button>
                    </div>
                  </div>

                  <Transition name="ss-fade">
                    <div v-if="storyboardForm.layoutMode === 'PRESET'" class="ss-section">
                      <p class="ss-section__label">Layout preset</p>
                      <div class="ss-presets">

                        <button type="button" class="ss-preset"
                            :class="{ active: storyboardForm.preset === 'GRID_3' }"
                            @click="storyboardForm.preset = 'GRID_3'">
                          <div class="ss-preset__preview">
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 4"></span>
                          </div>
                          <span class="ss-preset__name">Classic</span>
                          <span class="ss-preset__desc">3 equal columns</span>
                        </button>

                        <button type="button" class="ss-preset"
                            :class="{ active: storyboardForm.preset === 'GRID_2' }"
                            @click="storyboardForm.preset = 'GRID_2'">
                          <div class="ss-preset__preview">
                            <span style="grid-column:span 6"></span>
                            <span style="grid-column:span 6"></span>
                            <span style="grid-column:span 6"></span>
                            <span style="grid-column:span 6"></span>
                          </div>
                          <span class="ss-preset__name">Strip</span>
                          <span class="ss-preset__desc">2 wide columns</span>
                        </button>

                        <button type="button" class="ss-preset"
                            :class="{ active: storyboardForm.preset === 'CINEMATIC' }"
                            @click="storyboardForm.preset = 'CINEMATIC'">
                          <div class="ss-preset__preview">
                            <span style="grid-column:span 8; grid-row:span 2"></span>
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 4"></span>
                            <span style="grid-column:span 6"></span>
                            <span style="grid-column:span 6"></span>
                          </div>
                          <span class="ss-preset__name">Cinematic</span>
                          <span class="ss-preset__desc">Hero + small panels</span>
                        </button>

                        <button type="button" class="ss-preset"
                            :class="{ active: storyboardForm.preset === 'MANGA' }"
                            @click="storyboardForm.preset = 'MANGA'">
                          <div class="ss-preset__preview">
                            <span style="grid-column:span 4; grid-row:span 2"></span>
                            <span style="grid-column:span 8"></span>
                            <span style="grid-column:span 8"></span>
                            <span style="grid-column:span 12"></span>
                          </div>
                          <span class="ss-preset__name">Manga</span>
                          <span class="ss-preset__desc">Portrait + landscape mix</span>
                        </button>

                      </div>
                    </div>
                  </Transition>

                  <div class="ss-section ss-pub">
                    <p class="ss-section__label">Publication</p>
                    <div class="ss-pub__row">
                      <div class="ss-pub__status">
                        <span class="ss-status-dot" :class="isPublished ? 'ss-status-dot--pub' : 'ss-status-dot--draft'"></span>
                        <div>
                          <strong>{{ isPublished ? "Published" : "Draft (private)" }}</strong>
                          <small>
                            {{ isPublished && hasUnpublishedEdits ? "Changes saved since the last publication update" : isPublished ? "Visible to the community" : "Only you can see this" }}
                          </small>
                        </div>
                      </div>
                      <RouterLink
                          v-if="isUnsavedEmergencyDraft"
                          :to="`/login?redirect=${encodeURIComponent(route.fullPath)}`"
                          class="ss-pub__btn"
                      >
                        Log in to save →
                      </RouterLink>
                      <button
                          v-else-if="canEditScenario && !isPublished && canPublish"
                          type="button"
                          class="ss-pub__btn"
                          :disabled="publishing"
                          @click="publishCurrentScenario"
                      >
                        {{ publishing ? "Publishing…" : "Publish →" }}
                      </button>
                      <button
                          v-else-if="canEditScenario && isPublished && hasUnpublishedEdits"
                          type="button"
                          class="ss-pub__btn ss-pub__btn--update"
                          :disabled="publishing"
                          @click="openUpdateConfirm"
                      >
                        {{ publishing ? "Updating…" : "Update live version →" }}
                      </button>
                    </div>
                    <p v-if="isOwner && !isPublished && isForkPending" class="ss-pub__blocked">
                      This fork must be approved by the original author before it can be published.
                    </p>
                    <p v-else-if="isOwner && !isPublished && isForkRejected" class="ss-pub__blocked">
                      This fork was rejected and cannot be published{{ scenario.reviewComment ? ": " + scenario.reviewComment : "." }}
                    </p>
                  </div>

                  <div v-if="isOwner" class="ss-danger">
                    <p class="ss-section__label">Danger zone</p>
                    <button
                        type="button"
                        class="ss-delete"
                        @click="studioDeleteConfirm = true; closeStoryboardSettingsDialog()"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                        <path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                      </svg>
                      Delete this scenario
                    </button>
                  </div>

                  <button
                      type="button"
                      class="ss-save"
                      :disabled="savingStoryboard"
                      @click="saveStoryboardSettings"
                  >
                    <template v-if="savingStoryboard">
                      <span class="ss-spinner"></span>
                      Saving…
                    </template>
                    <template v-else>
                      Save settings
                    </template>
                  </button>

                </section>
              </div>

              <div
                  v-if="quickRecordingDialogOpen"
                  class="dialog-backdrop"
                  @click.self="discardQuickRecording"
              >
                <section class="qr-card" role="dialog" aria-modal="true" aria-labelledby="quick-recording-title">

                  <div class="qr-top">
                    <div class="qr-top__info">
                      <p class="qr-eyebrow">New recording</p>
                      <h2 id="quick-recording-title" class="qr-title">Review & add take</h2>
                    </div>
                    <button type="button" class="qr-close" aria-label="Discard" @click="discardQuickRecording">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>

                  <div v-if="selectedThumb" class="qr-scene">
                    <img
                        class="qr-scene__thumb"
                        :src="thumbnailContentUrl(selectedThumb)"
                        :alt="selectedThumb.title || 'Scene'"
                    />
                    <div class="qr-scene__info">
                      <span class="qr-scene__label">Adding to</span>
                      <strong class="qr-scene__name">{{ selectedThumb.title || `Scene ${selectedSceneNumber}` }}</strong>
                      <span class="qr-scene__speaker">
                        <span class="fiche-speaker" :class="String(selectedSpeaker).toLowerCase()">{{ selectedSpeaker }}</span>
                        {{ takeLabel(selectedSpeaker) }}
                      </span>
                    </div>
                  </div>

                  <div v-if="quickRecordingPreviewUrl" class="qr-player">
                    <button
                        type="button"
                        class="qr-player__play"
                        :aria-label="qrPlaying ? 'Pause' : 'Play'"
                        @click="toggleQrPlayback"
                    >
                      <svg v-if="qrPlaying" viewBox="0 0 24 24" fill="currentColor">
                        <rect x="6" y="5" width="4" height="14" rx="1"/><rect x="14" y="5" width="4" height="14" rx="1"/>
                      </svg>
                      <svg v-else viewBox="0 0 24 24" fill="currentColor">
                        <path d="M8 5.5v13l11-6.5-11-6.5z"/>
                      </svg>
                    </button>
                    <div class="qr-player__bar" @click="seekQrPlayback">
                      <div class="qr-player__fill" :style="{ width: qrProgressPercent() + '%' }"></div>
                    </div>
                    <span class="qr-player__time">
                      {{ autoplay.formatTime(qrCurrentTime) }} / {{ autoplay.formatTime(qrDuration) }}
                    </span>
                    <audio
                        ref="qrAudioEl"
                        :src="quickRecordingPreviewUrl"
                        class="qr-sr-only"
                        @play="qrPlaying = true"
                        @pause="qrPlaying = false"
                        @ended="onQrEnded"
                        @timeupdate="onQrTimeUpdate"
                        @loadedmetadata="onQrLoadedMetadata"
                    ></audio>
                  </div>

                  <div class="qr-field">
                    <label class="qr-label" for="qr-title-input">Take name</label>
                    <input
                        id="qr-title-input"
                        v-model="quickRecordingTitle"
                        class="qr-input"
                        placeholder="e.g. Narration, response, whisper..."
                        autocomplete="off"
                    />
                  </div>

                  <BaseAlert v-if="quickRecordingError" type="error">{{ quickRecordingError }}</BaseAlert>

                  <div class="qr-actions">
                    <button
                        type="button"
                        class="qr-discard"
                        :disabled="quickRecordingUploading"
                        @click="discardQuickRecording"
                    >
                      Discard
                    </button>
                    <button
                        type="button"
                        class="qr-confirm"
                        :disabled="quickRecordingUploading || !quickRecordingBlob"
                        @click="confirmQuickRecordingUpload"
                    >
                      <template v-if="quickRecordingUploading">
                        <span class="qr-spinner"></span>
                        Adding…
                      </template>
                      <template v-else>
                        Add to scene →
                      </template>
                    </button>
                  </div>

                </section>
              </div>

              <div
                  v-if="collaboratorsPanelOpen"
                  class="dialog-backdrop"
                  @click.self="closeCollaboratorsPanel"
              >
                <CollaboratorsPanel
                    :scenario-id="props.id"
                    :collaborators="collab.collaborators.value"
                    :invite-links="collab.inviteLinks.value"
                    :is-owner="isOwner"
                    :author-username="scenario.authorUsername"
                    @close="closeCollaboratorsPanel"
                    @invite="handleInvite"
                    @remove="collab.remove"
                    @change-role="collab.changeRole"
                    @create-link="handleCreateLink"
                    @revoke-link="collab.revokeLink"
                />
              </div>

              <div
                  v-if="historyPanelOpen"
                  class="dialog-backdrop"
                  @click.self="closeHistoryPanel"
              >
                <ScenarioHistoryPanel
                    :entries="historyEntries"
                    :loading="historyLoading"
                    @close="closeHistoryPanel"
                />
              </div>

              <div v-if="storyboardItems.length" class="vg-root">
                <div class="vg-nav">
                  <div class="vg-brand-block">
                    <div class="vg-brand studio-breadcrumb">
                      <span class="vg-brand-dot"></span>
                      <RouterLink :to="isOwner ? '/workspace' : '/scenarios'" class="vg-brand-back">{{ isOwner ? 'My scenarios' : 'Scenarios' }}</RouterLink>
                      <strong>/ {{ scenario.title || "New scenario" }}</strong>
                    </div>
                    <Transition name="vg-kicker" mode="out-in">
                      <span :key="storyboardView" class="vg-kicker">
                        {{ storyboardView === "studio" ? "Studio" : storyboardView === "discussion" ? "Discussion" : "Storyboard" }}
                      </span>
                    </Transition>
                  </div>

                  <div class="vg-tabs" role="tablist" aria-label="Storyboard views">
                    <button
                        type="button"
                        class="vg-tab"
                        :class="{ active: storyboardView === 'global' }"
                        @click="setStoryboardView('global')"
                    >
                      Storyboard
                    </button>
                    <button
                        v-if="canEditScenario"
                        type="button"
                        class="vg-tab"
                        :class="{ active: storyboardView === 'studio' }"
                        @click="setStoryboardView('studio')"
                    >
                      Studio
                    </button>
                    <button
                        v-if="isPublished"
                        type="button"
                        class="vg-tab"
                        :class="{ active: storyboardView === 'discussion' }"
                        @click="setStoryboardView('discussion')"
                    >
                      Discussion
                    </button>
                  </div>

                  <div class="vg-actions">
                    <button
                        v-if="canEditScenario"
                        type="button"
                        class="vg-icon-btn"
                        title="Add a scene"
                        @click="openUploadDialog"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 5v14M5 12h14"/>
                      </svg>
                    </button>
                    <button
                        v-if="canEditScenario && storyboardView === 'global'"
                        type="button"
                        class="vg-icon-btn"
                        title="Storyboard settings"
                        @click="openStoryboardSettingsDialog"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 3v4M12 17v4M3 12h4M17 12h4m-11.36-6.36 2.83 2.83m4.06 4.06 2.83 2.83M5.64 18.36l2.83-2.83m4.06-4.06 2.83-2.83"/>
                        <circle cx="12" cy="12" r="3"/>
                      </svg>
                    </button>
                    <button
                        type="button"
                        class="vg-icon-btn"
                        title="Vignette ambience"
                        @click="openAmbiencePanel"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M3 15c3 0 3-3 6-3s3 3 6 3 3-3 6-3"/>
                        <path d="M3 9c3 0 3-3 6-3s3 3 6 3 3-3 6-3"/>
                        <path d="M5 20h14"/>
                      </svg>
                    </button>
                    <button
                        type="button"
                        class="vg-icon-btn"
                        title="Scenario info"
                        @click="openInfoDialog"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="9"/>
                        <path d="M12 10v6M12 7h.01"/>
                      </svg>
                    </button>
                    <button
                        type="button"
                        class="vg-play-btn"
                        :class="{ 'vg-play-btn--playing': !!readerScenario }"
                        :title="playbackQueue.length ? 'Preview scenario' : 'No audio clips yet. Record some to enable the preview.'"
                        :disabled="!playbackQueue.length"
                        @click="openScenarioReader"
                    >
                      <svg viewBox="0 0 24 24" fill="currentColor" width="13" height="13">
                        <polygon points="5 3 19 12 5 21 5 3"/>
                      </svg>
                      Play
                    </button>
                    <button
                        v-if="hasAnyRole"
                        type="button"
                        class="vg-icon-btn"
                        title="Collaborators"
                        @click="openCollaboratorsPanel"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                        <circle cx="9" cy="7" r="4"/>
                        <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
                      </svg>
                    </button>
                    <button
                        v-if="hasAnyRole"
                        type="button"
                        class="vg-icon-btn"
                        title="History"
                        @click="openHistoryPanel"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="9"/>
                        <path d="M12 7v5l3 3"/>
                      </svg>
                    </button>
                    <button
                        v-if="isPublished && isAuthenticated"
                        type="button"
                        class="vg-interaction-btn"
                        :class="{ 'vg-interaction-btn--active': isLiked(props.id) }"
                        :title="isLiked(props.id) ? 'Unlike' : 'Like'"
                        @click="handleToggleLike"
                    >
                      <svg width="14" height="14" viewBox="0 0 24 24"
                           :fill="isLiked(props.id) ? 'currentColor' : 'none'"
                           stroke="currentColor" stroke-width="2"
                           stroke-linecap="round" stroke-linejoin="round">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                      </svg>
                      <span class="vg-interaction-btn__count">{{ likeCount }}</span>
                    </button>
                    <div v-if="isPublished && isAuthenticated" ref="bookmarkPickerEl" class="vg-bookmark-wrap">
                      <button
                          type="button"
                          class="vg-interaction-btn"
                          :class="{ 'vg-interaction-btn--active': isBookmarked(props.id) }"
                          :title="isBookmarked(props.id) ? 'Remove bookmark' : 'Bookmark'"
                          @click="handleBookmarkClick"
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24"
                            :fill="isBookmarked(props.id) ? 'currentColor' : 'none'"
                            stroke="currentColor" stroke-width="2"
                            stroke-linecap="round" stroke-linejoin="round">
                          <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                        </svg>
                      </button>

                      <div v-if="bookmarkCategoryPickerOpen" class="vg-bookmark-picker">
                        <p class="vg-bookmark-picker__label">Save to category</p>
                        <button
                            type="button"
                            class="vg-bookmark-picker__item vg-bookmark-picker__item--none"
                            @click="assignBookmarkCategory(null)"
                        >
                          No category
                        </button>
                        <div v-if="bookmarkCategoryList.length" class="vg-bookmark-picker__divider"></div>
                        <button
                            v-for="cat in bookmarkCategoryList"
                            :key="cat"
                            type="button"
                            class="vg-bookmark-picker__item"
                            :class="{ 'vg-bookmark-picker__item--active': getCategory(props.id) === cat }"
                            @click="assignBookmarkCategory(cat)"
                        >
                          {{ cat }}
                          <span v-if="getCategory(props.id) === cat">✓</span>
                        </button>
                        <div class="vg-bookmark-picker__divider"></div>
                        <div class="vg-bookmark-picker__new">
                          <input
                              v-model="newBookmarkCategoryName"
                              class="vg-bookmark-picker__new-input"
                              placeholder="New category…"
                              @keydown.enter="createAndAssignBookmarkCategory"
                          />
                          <button
                              type="button"
                              class="vg-bookmark-picker__new-btn"
                              :disabled="!newBookmarkCategoryName.trim()"
                              @click="createAndAssignBookmarkCategory"
                          >
                            Add
                          </button>
                        </div>
                      </div>
                    </div>
                    <RouterLink
                        v-if="isUnsavedEmergencyDraft"
                        :to="`/login?redirect=${encodeURIComponent(route.fullPath)}`"
                        class="vg-pub"
                    >
                      Log in to save →
                    </RouterLink>
                    <button
                        v-else-if="canEditScenario && !isPublished && canPublish"
                        type="button"
                        class="vg-pub"
                        :disabled="publishing"
                        @click="publishCurrentScenario"
                    >
                      {{ publishing ? "Publishing…" : "Publish →" }}
                    </button>
                    <button
                        v-else-if="canEditScenario && isPublished && hasUnpublishedEdits"
                        type="button"
                        class="vg-pub vg-pub--update"
                        :disabled="publishing"
                        @click="openUpdateConfirm"
                    >
                      {{ publishing ? "Updating…" : "Update live version →" }}
                    </button>
                    <span v-else-if="isPublished" class="vg-status vg-status--pub">Published</span>
                    <span v-else-if="isOwner && isForkPending" class="vg-status vg-status--review">Pending review</span>
                    <span v-else-if="isOwner && isForkRejected" class="vg-status vg-status--rejected">Fork rejected</span>
                    <span v-else-if="!canEditScenario" class="vg-status">Read-only</span>
                  </div>
                </div>

                <div v-if="canReviewFork" class="vg-review-banner">
                  <div class="vg-review-banner__text">
                    <strong>This fork is awaiting your review.</strong>
                    <span>As the original author, you can approve it for publication or reject it.</span>
                  </div>
                  <div class="vg-review-banner__form">
                    <input
                        v-model="reviewComment"
                        class="vg-review-banner__input"
                        maxlength="255"
                        placeholder="Optional comment…"
                    />
                    <button type="button" class="vg-review-banner__reject" :disabled="reviewing" @click="rejectCurrentFork">
                      {{ reviewing ? "…" : "Reject" }}
                    </button>
                    <button type="button" class="vg-review-banner__approve" :disabled="reviewing" @click="approveCurrentFork">
                      {{ reviewing ? "…" : "Approve" }}
                    </button>
                  </div>
                </div>

                <Transition name="vg-view" mode="out-in">
                <div
                    v-if="storyboardView === 'global'"
                    key="global"
                    class="vg-view active vg-view--with-recorder"
                    :class="{ 'vg-view--recorder-open': globalRecorderOpen }"
                >
                  <div class="vg-view__main">
                    <div class="bd-toolbar">
                      <span v-if="canEditScenario" class="bd-tlbl">Layout</span>

                      <div v-if="canEditScenario" class="bd-presets">
                        <button
                            type="button"
                            class="preset-btn"
                            :class="{ active: storyboardForm.preset === 'GRID_3' && storyboardForm.layoutMode !== 'CUSTOM' }"
                            @click="applyStoryboardPreset('GRID_3')"
                        >Classic</button>
                        <button
                            type="button"
                            class="preset-btn"
                            :class="{ active: storyboardForm.preset === 'CINEMATIC' && storyboardForm.layoutMode !== 'CUSTOM' }"
                            @click="applyStoryboardPreset('CINEMATIC')"
                        >Cinematic</button>
                        <button
                            type="button"
                            class="preset-btn"
                            :class="{ active: storyboardForm.preset === 'MANGA' && storyboardForm.layoutMode !== 'CUSTOM' }"
                            @click="applyStoryboardPreset('MANGA')"
                        >Manga</button>
                        <button
                            type="button"
                            class="preset-btn"
                            :class="{ active: storyboardForm.preset === 'GRID_2' && storyboardForm.layoutMode !== 'CUSTOM' }"
                            @click="applyStoryboardPreset('GRID_2')"
                        >Strip</button>
                      </div>

                      <span class="bd-hint">
                        {{ isOwner
                            ? "Drag scenes to move · pull an edge to resize"
                            : storyboardSummary.audioCount + " audio"
                        }}
                      </span>
                    </div>

                    <div v-if="canEditScenario && selectedStoryboardItem" class="bd-scene-tools">
                      <div class="bd-scene-tools__scene">
                        <span class="bd-scene-tools__number">{{ selectedSceneNumber }}</span>
                        <div>
                          <small>Editing scene</small>
                          <strong>{{ selectedThumb?.title || `Scene ${selectedSceneNumber}` }}</strong>
                        </div>
                      </div>

                      <div class="bd-scene-tools__sizes" role="group" aria-label="Scene size">
                        <button
                            type="button"
                            :class="{ active: selectedStoryboardItem._layout?.columnSpan === 4 && selectedStoryboardItem._layout?.rowSpan === 3 }"
                            @click="applySelectedThumbnailSize(4, 3, 'Compact size')"
                        >Compact</button>
                        <button
                            type="button"
                            :class="{ active: selectedStoryboardItem._layout?.columnSpan === 6 && selectedStoryboardItem._layout?.rowSpan === 4 }"
                            @click="applySelectedThumbnailSize(6, 4, 'Medium size')"
                        >Medium</button>
                        <button
                            type="button"
                            :class="{ active: selectedStoryboardItem._layout?.columnSpan === 8 && selectedStoryboardItem._layout?.rowSpan === 4 }"
                            @click="applySelectedThumbnailSize(8, 4, 'Wide size')"
                        >Wide</button>
                        <button
                            type="button"
                            :class="{ active: selectedStoryboardItem._layout?.columnSpan === 4 && selectedStoryboardItem._layout?.rowSpan === 6 }"
                            @click="applySelectedThumbnailSize(4, 6, 'Portrait size')"
                        >Portrait</button>
                        <button
                            type="button"
                            :class="{ active: selectedStoryboardItem._layout?.columnSpan === 12 && selectedStoryboardItem._layout?.rowSpan === 5 }"
                            @click="applySelectedThumbnailSize(12, 5, 'Hero size')"
                        >Hero</button>
                      </div>

                      <div class="bd-scene-tools__move" role="group" aria-label="Move scene">
                        <button
                            type="button"
                            title="Move scene earlier"
                            :disabled="selectedThumbIndex <= 0 || savingOrder"
                            @click="reorderThumb(selectedThumb, 'up')"
                        >←</button>
                        <button
                            type="button"
                            title="Move scene later"
                            :disabled="selectedThumbIndex >= sortedThumbnails.length - 1 || savingOrder"
                            @click="reorderThumb(selectedThumb, 'down')"
                        >→</button>
                      </div>
                    </div>

                    <div class="bd-page" :class="{ 'bd-page--dragging': tileDragId }">

                      <div class="grid-guides" aria-hidden="true">
                        <div
                            v-for="col in 12"
                            :key="col"
                            class="grid-guides__col"
                        >
                          <span class="grid-guides__num">{{ col }}</span>
                        </div>
                      </div>

                      <div
                          class="storyboard-grid bd-grid"
                          :class="{
                            [`storyboard-grid--${storyboardSummary.mode.toLowerCase()}`]: true,
                            'bd-grid--focus-mode': globalRecorderOpen,
                            'bd-grid--dragging': tileDragId,
                          }"
                      >
                        <ThumbnailCard
                            v-for="item in storyboardItems"
                            :key="item.id"
                            :thumb="item"
                            :audios="audioMap[item.id] || []"
                            :selected="selectedThumb?.id === item.id"
                            :highlighted="highlightedThumbnailId === item.id"
                            :quick-recording="String(quickRecordingThumbId ?? '') === String(item.id)"
                            :can-resize="studioFrontendOnly || canEditScenario"
                            :can-delete="studioFrontendOnly || canEditScenario"
                            :can-reorder="studioFrontendOnly || canEditScenario"
                            :col-span="item._layout?.columnSpan ?? 1"
                            :row-span="item._layout?.rowSpan ?? 1"
                            :class="{
                              'storyboard-tile--resizing': resizingThumbnailId === item.id,
                              'storyboard-tile--is-dragging': isTileDragging(item),
                              'storyboard-tile--drag-target': isTileDragTarget(item),
                            }"
                            :style="storyboardItemStyle(item)"
                            @select="selectGlobalThumb"
                            @delete="deleteThumb"
                            @reorder="({ thumb, direction }) => reorderThumb(thumb, direction)"
                            @resize-start="beginThumbnailResize"
                            @pointer-reorder-start="(thumb, e) => onTilePointerReorderStart(e, thumb)"
                        />
                      </div>
                      <Teleport to="body">
                        <div
                            v-if="tileDragGhost"
                            class="storyboard-drag-ghost"
                            :style="tileDragGhostStyle"
                            aria-hidden="true"
                        >
                          <img
                              :src="tileDragGhost.imageUrl"
                              :alt="tileDragGhost.title"
                              class="storyboard-drag-ghost__image"
                          />
                          <span class="storyboard-drag-ghost__label">
                            {{ tileDragGhost.title }}
                          </span>
                        </div>
                      </Teleport>
                    </div>
                  </div>

                  <StudioRecorderPanel
                      v-if="globalRecorderOpen && selectedThumb"
                      v-model:selected-speaker="selectedSpeaker"
                      class="studio-recorder--global"
                      :selected-thumb="selectedThumb"
                      :selected-audios="selectedAudios"
                      :selected-voice-id="selectedVoiceId"
                      :selected-scene-number="selectedSceneNumber"
                      :quick-recording-thumb-id="quickRecordingThumbId"
                      :recording-target-label="recordingTargetLabel"
                      :recording-status-label="recordingStatusLabel"
                      :can-record="studioFrontendOnly || isOwner"
                      :playback-queue-length="playbackQueue.length"
                      :recording-trim-open="recordingTrimOpen"
                      :trim-start="trimStart"
                      :trim-end="trimEnd"
                      :trim-preview-playing="trimPreviewPlaying"
                      :trim-saving="trimSaving"
                      @select-voice="(voice) => selectVoice(voice, selectedThumb, { scrollRecorder: false })"
                      @select-speaker-slot="selectSpeakerSlot"
                      @update:recording-trim-open="recordingTrimOpen = $event"
                      @update:trim-start="updateTrimStart"
                      @update:trim-end="updateTrimEnd"
                      @toggle-record="toggleSelectedQuickRecording"
                      @stop-recording="stopQuickRecording"
                      @restart="restartSelectedRecording"
                      @open-layout="openStoryboardSettingsDialog"
                      @open-audio-drafts="openDraftImportDialog"
                      @audio-file-change="importRecordingAudioFile"
                      @preview-trim="previewTrimSelection"
                      @reset-trim="resetTrimSelection"
                      @apply-trim="applyTrimSelection"
                  />
                </div>

                <div v-else-if="storyboardView === 'studio'" key="studio" class="vg-view active">
                  <div class="studio-wrap studio-wrap--fiches">
                    <main class="studio-fiches">
                      <header class="studio-fiches-head">
                        <span>Vignette</span>
                        <div class="studio-title-row">
                          <input
                              v-if="studioTitleEditing"
                              class="studio-title-input"
                              :value="studioTitleDraft"
                              @input="studioTitleDraft = $event.target.value"
                              @blur="saveStudioTitle"
                              @keydown.enter.prevent="saveStudioTitle"
                              @keydown.escape.prevent="studioTitleEditing = false"
                              @focusin.once="$event.target.select()"
                              maxlength="200"
                              autocomplete="off"
                              autofocus
                          />
                          <h2 v-else @click="isOwner && (studioTitleDraft = scenario.title || '', studioTitleEditing = true)" :class="{ 'studio-title-editable': isOwner }">
                            {{ scenario.title || "Untitled scenario" }}
                            <svg v-if="isOwner" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="13" height="13" class="studio-title-pen" aria-hidden="true">
                              <path d="M11 2l3 3-8 8-4 1 1-4 8-8Z"/>
                            </svg>
                          </h2>
                        </div>
                        <div class="studio-chip-row">
                          <span v-if="languageName">{{ languageName }}</span>
                          <template v-if="scenario.tags?.length">
                            <span v-for="tag in scenario.tags" :key="tag">#{{ tag }}</span>
                          </template>
                        </div>
                      </header>

                      <section class="studio-ambience-strip">
                        <button
                            type="button"
                            class="studio-ambience-main"
                            :class="{ 'studio-ambience-main--empty': !hasActiveBackgroundAmbience }"
                            @click="openAmbiencePanel"
                        >
                          <span class="fiche-speaker ambience">{{ hasActiveBackgroundAmbience ? "BG" : "+" }}</span>
                          <span>
                            <small>Vignette ambience · Optional</small>
                            <strong>{{ backgroundSummaryTitle }}</strong>
                          </span>
                          <em v-if="backgroundSummaryNote">{{ backgroundSummaryNote }}</em>
                        </button>
                        <button
                            type="button"
                            class="studio-ambience-play"
                            :disabled="presetAudioGenerating || !hasActiveBackgroundAmbience"
                            :title="!hasActiveBackgroundAmbience ? 'No ambience to play' : (backgroundPlaying ? 'Pause background audio' : 'Play background audio')"
                            :aria-label="!hasActiveBackgroundAmbience ? 'No ambience to play' : (backgroundPlaying ? 'Pause background audio' : 'Play background audio')"
                            @click="toggleBackgroundAudio"
                        >
                          <span v-if="backgroundPlaying" class="pause-icon" aria-hidden="true"></span>
                          <span v-else aria-hidden="true">▶</span>
                        </button>
                        <button type="button" class="studio-ambience-manage" @click="openAmbiencePanel">
                          {{ hasActiveBackgroundAmbience ? "Manage" : "Add ambience" }}
                        </button>
                      </section>

                      <div class="fiche-stack">
                        <article
                            v-for="item in storyboardItems"
                            :key="item.id"
                            class="fiche-card"
                            :class="{
                              active: selectedThumb?.id === item.id,
                              recording: String(quickRecordingThumbId ?? '') === String(item.id)
                            }"
                            @click="selectThumb(item, { scrollRecorder: true })"
                        >
                          <div class="fiche-image">
                            <span class="fiche-num">{{ String(item._sceneNumber ?? item.idx ?? item.id).padStart(2, "0") }}</span>
                            <img :src="thumbnailContentUrl(item)" :alt="item.title || 'Scene image'"/>
                          </div>

                          <div class="fiche-body">
                            <button type="button" class="fiche-delete" title="Delete scene" aria-label="Delete scene" @click.stop="deleteThumb(item)">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" width="13" height="13">
                                <polyline points="3 6 5 6 21 6"/>
                                <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                                <path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                              </svg>
                            </button>
                            <div class="fiche-top">
                              <input
                                  class="fiche-title"
                                  :value="item.title || `Scene ${item._sceneNumber ?? item.id}`"
                                  @click.stop="selectThumb(item, { scrollRecorder: false })"
                                  @focus="selectThumb(item, { scrollRecorder: false })"
                                  @input="updateThumbTitle(item, $event)"
                                  @change="persistThumbTitle(item)"
                                  maxlength="255"
                              />
                              <div class="fiche-actions">
                                <button type="button" title="Move up" @click.stop="reorderThumb(item, 'up')">↑</button>
                                <button type="button" title="Move down" @click.stop="reorderThumb(item, 'down')">↓</button>
                              </div>
                            </div>

                            <div class="fiche-voices">
                              <div
                                  v-for="(audio, index) in (audioMap[item.id] || [])"
                                  :key="audio.id"
                                  class="fiche-voice"
                                  :class="{
                                    'fiche-voice--selected': selectedThumb?.id === item.id && String(selectedVoiceId ?? '') === String(audio.id),
                                    'fiche-voice--draft': audio.isDraft && !(isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id)),
                                    'fiche-voice--recording': isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id)
                                  }"
                                  @click.stop="selectVoice(audio, item)"
                              >
                                <span class="fiche-speaker" :class="speakerClass(audio, index)">
                                  {{ speakerForAudio(audio, index) }}
                                </span>
                                <span class="fiche-voice-title">
                                  {{ voiceDisplayTitle(audio, item) }}
                                  <span v-if="audio.isDraft && !(isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id))" class="fiche-gloss">to record</span>
                                  <span v-else-if="audio.gloss" class="fiche-gloss">{{ audio.gloss }}</span>
                                </span>
                                <span
                                    v-if="!audio.isDraft || (isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id))"
                                    class="fiche-wave"
                                    :class="{ 'fiche-wave--live': isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) }"
                                    aria-hidden="true"
                                >
                                  <i></i><i></i><i></i><i></i><i></i><i></i>
                                </span>
                                <button
                                    v-if="!audio.isDraft || (isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id))"
                                    type="button"
                                    class="fiche-play"
                                    :class="{ 'fiche-play--pause': isAudioPlaying(audio, item) }"
                                    :title="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) ? 'Stop recording' : (isAudioPlaying(audio, item) ? 'Pause' : 'Play')"
                                    :aria-label="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) ? 'Stop recording' : (isAudioPlaying(audio, item) ? 'Pause playback' : 'Play audio')"
                                    @click.stop="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) ? stopQuickRecording() : toggleAudioPlayback(audio, item)"
                                >
                                  <span v-if="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id)" class="pause-icon" aria-hidden="true"></span>
                                  <span v-else-if="isAudioPlaying(audio, item)" class="pause-icon" aria-hidden="true"></span>
                                  <span v-else aria-hidden="true">▶</span>
                                </button>
                                <button
                                    type="button"
                                    class="fiche-delete-voice"
                                    title="Remove take"
                                    aria-label="Remove take"
                                    @click.stop="removeVoice(audio, item)"
                                >
                                  ×
                                </button>
                              </div>

                              <div v-if="!(audioMap[item.id] || []).length" class="fiche-voice fiche-voice--empty">
                                <span class="fiche-speaker" :class="nextSpeakerForThumb(item).toLowerCase()">
                                  {{ nextSpeakerForThumb(item) }}
                                </span>
                                <span class="fiche-voice-title">{{ takeLabel(nextSpeakerForThumb(item)) }} to record</span>
                                <span class="fiche-empty-dot"></span>
                              </div>

                              <button
                                  type="button"
                                  class="fiche-add-voice"
                                  :class="{ 'fiche-add-voice--rec': isRecordingThumb(item) }"
                                  :disabled="!(studioFrontendOnly || canEditScenario) || (!isRecordingThumb(item) && !recordButtonVoiceForThumb(item) && (audioMap[item.id] || []).length >= 4)"
                                  @click.stop="isRecordingThumb(item) ? stopQuickRecording() : addVoiceForThumb(item)"
                              >
                                <template v-if="isRecordingThumb(item)">
                                  <span class="fiche-rec-dot"></span>
                                  Stop recording
                                </template>
                                <template v-else>
                                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                                       stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M12 2a3 3 0 0 1 3 3v7a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z"/>
                                    <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                                    <line x1="12" y1="19" x2="12" y2="22"/>
                                  </svg>
                                  {{ recordButtonLabelForThumb(item) }}
                                </template>
                              </button>
                            </div>
                          </div>

                        </article>

                        <button
                            v-if="studioFrontendOnly || canEditScenario"
                            type="button"
                            class="fiche-add-card"
                            aria-label="Add a scene image"
                            @click="openUploadDialog"
                        >
                          +
                        </button>
                      </div>
                    </main>

                    <aside ref="studioRecorderEl" class="studio-audio studio-recorder">
                      <div class="rec-panel-title">
                        <span>Studio audio</span>
                        <strong>{{ recordingSceneLabel }}</strong>
                      </div>

                      <div class="rec-speakers" role="group" aria-label="Choose a take">
                        <button
                            v-for="(audio, index) in selectedAudios"
                            :key="audio.id"
                            type="button"
                            :class="{
                              active: String(audio.id) === String(selectedVoiceId),
                              'rec-speaker--draft': audio.isDraft
                            }"
                            @click="selectVoice(audio, selectedThumb, { scrollRecorder: false })"
                        >
                          <span>{{ speakerForAudio(audio, index) }}</span>
                          <small v-if="audio.isDraft">empty</small>
                        </button>

                        <button
                            v-if="selectedAudios.length < 4 && selectedThumb"
                            type="button"
                            class="rec-speaker--add"
                            :title="`Add ${takeLabel(nextSelectedSpeaker)}`"
                            @click="selectSpeakerSlot(nextSelectedSpeaker)"
                        >
                          <span>{{ nextSelectedSpeaker }}</span>
                          <small>+ Add</small>
                        </button>
                      </div>

                      <div class="mic-rings rec-mic">
                        <div class="mic-ring"></div>
                        <div class="mic-ring"></div>
                        <div class="mic-ring"></div>
                        <button
                            type="button"
                            class="mic-btn"
                            :class="{ rec: quickRecordingThumbId != null }"
                            :disabled="!(studioFrontendOnly || canEditScenario) || !selectedThumb"
                            :title="recordingMicLabel"
                            :aria-label="recordingMicLabel"
                            @click="toggleSelectedQuickRecording"
                        ></button>
                      </div>
                      <div class="mic-tm">{{ quickRecordingThumbId != null ? "REC" : "0:00" }}</div>
                      <div class="mic-st" :class="{ live: quickRecordingThumbId != null }">
                        {{ recordingActionLabel }}
                      </div>

                      <input
                          ref="recordingFileInput"
                          class="rec-file-input"
                          type="file"
                          accept="audio/*"
                          @change="onRecordingAudioFileChange"
                      />

                      <button type="button" class="rec-collapse-toggle" @click="studioAudioSettingsOpen = !studioAudioSettingsOpen">
                        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true" width="13" height="13">
                          <path d="M3 5h14M3 10h14M3 15h14"/>
                          <circle cx="7" cy="5" r="1.5" fill="currentColor" stroke="none"/>
                          <circle cx="13" cy="10" r="1.5" fill="currentColor" stroke="none"/>
                          <circle cx="9" cy="15" r="1.5" fill="currentColor" stroke="none"/>
                        </svg>
                        Advanced settings
                        <svg class="rec-collapse-chevron" :class="{ open: studioAudioSettingsOpen }" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="11" height="11">
                          <path d="M4 6l4 4 4-4"/>
                        </svg>
                      </button>
                      <div v-if="studioAudioSettingsOpen" class="side-settings side-settings--collapse">
                        <p class="rec-adv-section">Import audio</p>
                        <div class="rec-main-actions">
                          <button type="button" class="rec-import-btn" :disabled="!(studioFrontendOnly || isOwner) || !selectedThumb" @click="openRecordingAudioFile">
                            <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" width="13" height="13">
                              <path d="M9 3a1 1 0 0 1 2 0v7.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L9 10.586V3Z"/>
                              <path d="M3 14a1 1 0 0 1 2 0v1h10v-1a1 1 0 1 1 2 0v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1Z"/>
                            </svg>
                            Audio file
                          </button>
                          <button type="button" class="rec-import-btn" :disabled="!(studioFrontendOnly || isOwner) || !selectedThumb" @click="openDraftImportDialog">
                            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8"
                                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" width="13" height="13">
                              <path d="M4 5h12M4 10h12M4 15h8"/>
                            </svg>
                            Audio drafts
                          </button>
                        </div>

                        <p class="rec-adv-section">Cut</p>
                        <button
                            type="button"
                            class="rec-import-btn rec-trim-btn"
                            :disabled="!selectedVoice || selectedVoice.isDraft"
                            @click="toggleTrimEditor"
                        >
                          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="13" height="13" aria-hidden="true">
                            <path d="M3 6h14M3 10h14M3 14h14"/>
                            <rect x="6" y="4" width="3" height="4" rx="1" fill="currentColor" stroke="none"/>
                            <rect x="11" y="8" width="3" height="4" rx="1" fill="currentColor" stroke="none"/>
                          </svg>
                          {{ recordingTrimOpen ? "Close cut" : "Cut audio" }}
                        </button>
                        <div v-if="recordingTrimOpen" class="trim-panel trim-panel--inline">
                          <div class="trim-panel__header">
                            <small>{{ selectedVoice ? voiceDisplayTitle(selectedVoice, selectedThumb) : takeLabel(selectedSpeaker) }}</small>
                            <strong>{{ trimEnd - trimStart }}%</strong>
                          </div>
                          <div
                              class="trim-editor__wave"
                              :class="{ dragging: trimDragging }"
                              @pointerdown="setNearestTrimHandle"
                          >
                            <span
                                v-for="(bar, index) in trimWaveBars"
                                :key="index"
                                class="trim-editor__bar"
                                :style="{ height: `${bar}%` }"
                            ></span>
                            <span class="trim-editor__shade trim-editor__shade--left" :style="{ width: `${trimStart}%` }"></span>
                            <span class="trim-editor__shade trim-editor__shade--right" :style="{ left: `${trimEnd}%` }"></span>
                            <span class="trim-editor__selection" :style="{ left: `${trimStart}%`, width: `${trimEnd - trimStart}%` }"></span>
                            <button type="button" class="trim-editor__handle trim-editor__handle--start" :style="{ left: `${trimStart}%` }" aria-label="Cut start" @pointerdown.stop.prevent="beginTrimDrag('start', $event)"><span></span></button>
                            <button type="button" class="trim-editor__handle trim-editor__handle--end" :style="{ left: `${trimEnd}%` }" aria-label="Cut end" @pointerdown.stop.prevent="beginTrimDrag('end', $event)"><span></span></button>
                          </div>
                          <div class="trim-panel__actions">
                            <button type="button" :disabled="trimSaving || !selectedVoice || selectedVoice.isDraft" @click="previewTrimSelection">{{ trimPreviewPlaying ? "Stop" : "Preview" }}</button>
                            <button type="button" :disabled="trimSaving" @click="resetTrimSelection">Reset</button>
                            <button type="button" class="primary" :disabled="trimSaving || !selectedVoice || selectedVoice.isDraft" @click="applyTrimSelection">{{ trimSaving ? "Cutting…" : "Apply" }}</button>
                          </div>
                        </div>
                      </div>

                      <div class="side-settings">
                        <div class="side-settings__title">Linguistic gloss</div>
                        <template v-if="selectedVoice && !selectedVoice.isDraft">
                          <label class="side-gloss-label">
                            <span>Transcription</span>
                            <input
                                ref="glossTranscriptionInput"
                                v-model="glossTranscription"
                                class="side-note-input"
                                placeholder="Orthographic or phonemic transcription"
                                :disabled="!canEditScenario"
                                @blur="saveGloss"
                            />
                          </label>
                          <label class="side-gloss-label">
                            <span>Gloss</span>
                            <input
                                v-model="glossGloss"
                                class="side-note-input side-note-input--mono"
                                placeholder="ex. 1SG-PRES-like-FV"
                                :disabled="!canEditScenario"
                                @blur="saveGloss"
                            />
                          </label>
                          <label class="side-gloss-label">
                            <span>Free translation</span>
                            <input
                                v-model="glossFreeTranslation"
                                class="side-note-input"
                                placeholder="Translation in a reference language"
                                :disabled="!canEditScenario"
                                @blur="saveGloss"
                            />
                          </label>
                          <button
                              v-if="canEditScenario"
                              type="button"
                              class="gloss-save-btn"
                              :disabled="glossSaving"
                              @click="saveGloss"
                          >
                            {{ glossSaving ? "Saving…" : "Save gloss" }}
                          </button>
                        </template>
                        <p v-else-if="selectedVoice?.isDraft" class="side-gloss-empty">Record this take first to annotate the gloss.</p>
                        <p v-else class="side-gloss-empty">Select a scene to annotate the gloss.</p>
                      </div>

                    </aside>
                  </div>
                </div>

                <div v-else-if="storyboardView === 'discussion' && isPublished" key="discussion" class="vg-view active vg-view--discussion">
                  <DiscussionThread
                      title="Discussion"
                      subtitle="Questions, notes, and feedback about this scenario."
                      target-type="SCENARIO"
                      :target-id="props.id"
                      :highlight-message-id="highlightMessageId"
                      empty-title="No discussion yet"
                      empty-message="Start the conversation about this scenario."
                  />
                </div>
                </Transition>
              </div>

              <div v-else class="sb-empty">
                <div class="sb-empty__grid" aria-hidden="true">
                  <div class="sb-empty__tile sb-empty__tile--1">
                    <span>01</span>
                  </div>
                  <div class="sb-empty__tile sb-empty__tile--2">
                    <span>02</span>
                  </div>
                  <div class="sb-empty__tile sb-empty__tile--3">
                    <span>03</span>
                  </div>
                </div>

                <div class="sb-empty__body">
                  <p class="sb-empty__eyebrow">Empty storyboard</p>
                  <h2 class="sb-empty__title">Start with your first scene</h2>
                  <p class="sb-empty__sub">
                    Upload an image to create your first scene.<br>
                    You'll then be able to record audio and annotate the whole thing.
                  </p>

                  <button
                      v-if="isOwner"
                      type="button"
                      class="sb-empty__cta"
                      @click="openUploadDialog"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 5v14M5 12h14"/>
                    </svg>
                    Add a scene
                  </button>

                  <div class="sb-empty__steps">
                    <div class="sb-empty__step">
                      <span class="sb-empty__step-num">1</span>
                      <span>Upload an image</span>
                    </div>
                    <div class="sb-empty__step-arrow">→</div>
                    <div class="sb-empty__step">
                      <span class="sb-empty__step-num">2</span>
                      <span>Record audio</span>
                    </div>
                    <div class="sb-empty__step-arrow">→</div>
                    <div class="sb-empty__step">
                      <span class="sb-empty__step-num">3</span>
                      <span>Publish the vignette</span>
                    </div>
                  </div>
                </div>
              </div>


    </template>
  </main>

  <VignetteMakerModal
      v-if="vignetteMakerOpen"
      :scenario-id="props.id"
      @close="vignetteMakerOpen = false"
      @insert="onVignetteMakerInsert"
  />

  <ScenarioReaderModal :scenario="readerScenario" @close="closeReader" />
</template>

<style scoped>
.si-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(500px, calc(100vw - 32px));
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 28px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.si-close {
  position: absolute;
  top: 18px;
  right: 18px;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}
.si-close:hover { background: #1E0812; color: #FFF0EE; }
.si-close svg { width: 14px; height: 14px; }

.si-hero { display: flex; flex-direction: column; gap: 8px; }

.si-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  width: fit-content;
}
.si-status--pub  { background: rgba(74,103,65,0.12);  color: #4A6741; }
.si-status--draft { background: rgba(72,91,56,0.12); color: #8B3010; }
.si-status--review { background: rgba(139,48,16,0.12); color: #8B3010; }
.si-status--rejected { background: rgba(168,51,76,0.12); color: #A8334C; }
.si-status__dot {
  width: 6px; height: 6px;
  border-radius: 999px;
  background: currentColor;
}

.si-title {
  margin: 0;
  font-size: clamp(1.2rem, 3vw, 1.6rem);
  font-weight: 950;
  line-height: 1.15;
  letter-spacing: -0.025em;
  color: #1E0812;
  padding-right: 40px;
}

.si-stats {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 16px 0;
  border-top: 1.5px solid #D4E5CA;
  border-bottom: 1.5px solid #D4E5CA;
  flex-wrap: wrap;
  row-gap: 12px;
}
.si-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 80px;
}
.si-stat__num {
  font-size: 1.3rem;
  font-weight: 950;
  color: #1E0812;
  line-height: 1;
}
.si-stat__num--lang {
  font-size: 0.88rem;
  font-weight: 800;
  text-align: center;
}
.si-stat__lbl {
  font-size: 0.62rem;
  font-weight: 700;
  color: #785068;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.si-stat__div {
  width: 1px;
  height: 32px;
  background: #D4E5CA;
  flex-shrink: 0;
}

.si-tags-section { display: flex; flex-direction: column; gap: 8px; }

.si-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.si-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 4px 12px;
  background: #D4E5CA;
  color: #1E0812;
  font-size: 0.78rem;
  font-weight: 700;
}

.si-tag__remove {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
  border: 0;
  border-radius: 999px;
  background: rgba(30,8,18,0.12);
  color: #1E0812;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}
.si-tag__remove:hover { background: #A8334C; color: #fff; }
.si-tag__remove:disabled { opacity: 0.5; cursor: not-allowed; }

.si-tag-add { display: inline-flex; }
.si-tag-add__input {
  width: 110px;
  border: 1.5px dashed #D4E5CA;
  border-radius: 999px;
  padding: 4px 12px;
  background: transparent;
  color: #1E0812;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  outline: none;
  transition: border-color 160ms ease, width 160ms ease;
}
.si-tag-add__input:focus { border-color: #485B38; border-style: solid; width: 150px; }
.si-tag-add__input::placeholder { color: #A99; font-weight: 600; }
.si-tag-add__input:disabled { opacity: 0.6; }

.si-tags__empty { font-size: 0.82rem; color: #785068; }

.si-desc {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.si-desc__label {
  font-size: 0.65rem;
  font-weight: 900;
  color: #785068;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin: 0;
}
.si-desc__text {
  margin: 0;
  font-size: 0.9rem;
  color: #1E0812;
  line-height: 1.6;
  padding: 12px 14px;
  background: #D4E5CA;
  border-radius: 10px;
  font-style: italic;
}

.si-desc__textarea {
  margin: 0;
  font: inherit;
  font-size: 0.9rem;
  color: #1E0812;
  line-height: 1.6;
  padding: 12px 14px;
  background: #fff;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  resize: vertical;
  min-height: 70px;
  outline: none;
  transition: border-color 160ms ease;
}
.si-desc__textarea:focus { border-color: #485B38; }
.si-desc__textarea:disabled { opacity: 0.6; }

.ms-confirm {
  display: flex; flex-direction: column; gap: 14px;
  width: min(420px, calc(100vw - 32px));
  border: 3px solid #1E0812; border-radius: 18px; padding: 26px;
  background: #FFF0EE; box-shadow: 6px 6px 0 #1E0812;
}
.ms-confirm__eyebrow { margin:0; font-size:.65rem; font-weight:900; color:#A8334C; letter-spacing:.14em; text-transform:uppercase; }
.ms-confirm__title   { margin:0; font-size:1.2rem; font-weight:950; color:#1E0812; letter-spacing:-.02em; }
.ms-confirm__body    { margin:0; font-size:.88rem; color:#785068; line-height:1.55; }
.ms-confirm__actions { display:flex; gap:10px; }
.ms-confirm__cancel {
  flex:1; border:1.5px solid #D4E5CA; border-radius:12px; padding:12px;
  background:transparent; color:#785068; font:inherit; font-weight:700; cursor:pointer;
}
.ms-confirm__cancel:hover { background:#D4E5CA; }
.ms-confirm__delete {
  flex:1; display:flex; align-items:center; justify-content:center; gap:7px;
  border:0; border-radius:12px; padding:12px;
  background:#A8334C; color:#fff; font:inherit; font-size:.92rem; font-weight:800; cursor:pointer;
}
.ms-confirm__delete:hover:not(:disabled) { background:#8b2940; }
.ms-confirm__delete:disabled { opacity:.6; cursor:not-allowed; }
.ms-spin {
  width:13px; height:13px; display:inline-block;
  border:2px solid rgba(255,244,236,.3); border-top-color:#FFF0EE;
  border-radius:999px; animation:ss-spin .7s linear infinite;
}

.ss-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(520px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  overflow-y: auto;
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 24px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.ss-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.ss-eyebrow {
  margin: 0 0 3px;
  font-size: 0.62rem;
  font-weight: 900;
  color: #485B38;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.ss-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 950;
  color: #1E0812;
  letter-spacing: -0.02em;
}
.ss-close {
  display: grid;
  place-items: center;
  width: 32px; height: 32px;
  flex-shrink: 0;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  margin-top: 2px;
  transition: background 140ms ease, color 140ms ease;
}
.ss-close:hover { background: #1E0812; color: #FFF0EE; }
.ss-close svg { width: 14px; height: 14px; }

.ss-section { display: flex; flex-direction: column; gap: 10px; }
.ss-section__label {
  font-size: 0.65rem;
  font-weight: 900;
  color: #785068;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin: 0;
}

.ss-mode-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.ss-mode-tab {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  border: 2px solid #D4E5CA;
  border-radius: 12px;
  background: #fff;
  color: #785068;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 140ms ease, background 140ms ease, color 140ms ease;
}
.ss-mode-tab svg { width: 20px; height: 20px; color: #785068; margin-bottom: 2px; }
.ss-mode-tab small {
  font-size: 0.7rem;
  color: #785068;
  font-weight: 500;
  line-height: 1;
}
.ss-mode-tab:hover { border-color: #485B38; }
.ss-mode-tab.active {
  border-color: #1E0812;
  background: #1E0812;
  color: #FFF0EE;
}
.ss-mode-tab.active svg,
.ss-mode-tab.active small { color: rgba(255,244,236,0.65); }

.ss-presets { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.ss-preset {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 2px solid #D4E5CA;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 140ms ease, box-shadow 140ms ease;
}
.ss-preset:hover { border-color: #485B38; }
.ss-preset.active {
  border-color: #1E0812;
  box-shadow: 3px 3px 0 #1E0812;
}

.ss-preset__preview {
  display: grid;
  grid-template-columns: repeat(12, minmax(0,1fr));
  grid-auto-rows: 8px;
  gap: 2px;
  height: 52px;
  border-radius: 6px;
  overflow: hidden;
  background: #D4E5CA;
  padding: 4px;
}
.ss-preset__preview span {
  border-radius: 3px;
  background: #485B38;
  opacity: 0.7;
}
.ss-preset.active .ss-preset__preview span { opacity: 1; background: #1E0812; }

.ss-preset__name {
  font-size: 0.82rem;
  font-weight: 800;
  color: #1E0812;
}
.ss-preset__desc {
  font-size: 0.7rem;
  color: #785068;
}

.ss-fade-enter-active, .ss-fade-leave-active { transition: opacity 180ms ease, transform 180ms ease; }
.ss-fade-enter-from, .ss-fade-leave-to { opacity: 0; transform: translateY(-6px); }

.ss-pub { border-top: 1.5px solid #D4E5CA; padding-top: 18px; }
.ss-pub__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.ss-pub__status {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ss-status-dot {
  width: 10px; height: 10px;
  border-radius: 999px;
  flex-shrink: 0;
}
.ss-status-dot--pub   { background: #4A6741; box-shadow: 0 0 0 3px rgba(74,103,65,0.15); }
.ss-status-dot--draft { background: #485B38; box-shadow: 0 0 0 3px rgba(72,91,56,0.15); }
.ss-pub__status strong { display: block; font-size: 0.9rem; font-weight: 800; color: #1E0812; }
.ss-pub__status small  { font-size: 0.75rem; color: #785068; }
.ss-pub__btn {
  border: 0;
  border-radius: 10px;
  padding: 10px 20px;
  background: #4A6741;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
  transition: background 160ms ease, transform 120ms ease;
}
.ss-pub__btn:hover:not(:disabled) { background: #3d5534; transform: translateY(-1px); }
.ss-pub__btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ss-pub__btn--update { background: #a8631e; }
.ss-pub__btn--update:hover:not(:disabled) { background: #8f5218; }
.ss-pub__blocked {
  margin: 10px 0 0;
  font-size: 0.8rem;
  color: #A8334C;
  line-height: 1.5;
}

.ss-danger { border-top: 1.5px solid rgba(168,51,76,0.2); padding-top: 16px; }
.ss-delete {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1.5px solid rgba(168,51,76,0.3);
  border-radius: 10px;
  padding: 9px 16px;
  background: transparent;
  color: #A8334C;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease;
}
.ss-delete:hover { background: rgba(168,51,76,0.08); border-color: #A8334C; }
.ss-delete svg { width: 15px; height: 15px; }

.ss-save {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 50px;
  border: 0;
  border-radius: 14px;
  background: #1E0812;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 160ms ease, transform 120ms ease;
}
.ss-save:hover:not(:disabled) { background: #485B38; transform: translateY(-1px); }
.ss-save:disabled { opacity: 0.55; cursor: not-allowed; transform: none; }
.ss-spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255,244,236,0.3);
  border-top-color: #FFF0EE;
  border-radius: 999px;
  animation: ss-spin 0.7s linear infinite;
}
@keyframes ss-spin { to { transform: rotate(360deg); } }

.qr-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: min(420px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  overflow-y: auto;
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 22px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.qr-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.qr-eyebrow {
  margin: 0 0 3px;
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #485B38;
}

.qr-title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 950;
  color: #1E0812;
  letter-spacing: -0.02em;
}

.qr-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 140ms ease, color 140ms ease;
}

.qr-close:hover { background: #1E0812; color: #FFF0EE; }
.qr-close svg { width: 13px; height: 13px; }

.qr-scene {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #D4E5CA;
  border-radius: 12px;
  background: #fff;
}

.qr-scene__thumb {
  width: 56px;
  height: 42px;
  object-fit: cover;
  border-radius: 8px;
  border: 1.5px solid #D4E5CA;
  flex-shrink: 0;
}

.qr-scene__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.qr-scene__label {
  font-size: 0.6rem;
  font-weight: 800;
  color: #785068;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.qr-scene__name {
  font-size: 0.88rem;
  font-weight: 800;
  color: #1E0812;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qr-scene__speaker {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  color: #785068;
}

.qr-scene__speaker .fiche-speaker {
  width: 18px;
  height: 18px;
  font-size: 0.6rem;
}

.qr-player { display: flex; align-items: center; gap: 10px; }
.qr-player__play {
  display: grid; place-items: center; width: 32px; height: 32px; flex-shrink: 0;
  border: 0; border-radius: 999px; background: #485B38; color: #fff; cursor: pointer;
  transition: background 140ms ease, transform 120ms ease;
}
.qr-player__play:hover { background: #344228; transform: scale(1.05); }
.qr-player__play svg { width: 13px; height: 13px; }
.qr-player__bar { position: relative; flex: 1; height: 6px; border-radius: 999px; background: #F0E4DE; cursor: pointer; overflow: hidden; }
.qr-player__fill { height: 100%; border-radius: inherit; background: #485B38; transition: width 100ms linear; }
.qr-player__time { font-size: 0.7rem; font-weight: 700; font-variant-numeric: tabular-nums; color: #785068; white-space: nowrap; flex-shrink: 0; }
.qr-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }

.qr-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.qr-label {
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #785068;
}

.qr-input {
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 11px 14px;
  background: #fff;
  color: #1E0812;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 700;
  outline: none;
  transition: border-color 160ms ease;
}

.qr-input:focus { border-color: #485B38; }
.qr-input::placeholder { color: #A99; font-weight: 400; }

.qr-actions {
  display: flex;
  gap: 10px;
}

.qr-discard {
  border: 1.5px solid #D4E5CA;
  border-radius: 12px;
  padding: 0 18px;
  height: 48px;
  background: transparent;
  color: #785068;
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
  flex-shrink: 0;
}

.qr-discard:hover:not(:disabled) { background: rgba(168,51,76,0.1); color: #A8334C; border-color: #A8334C; }
.qr-discard:disabled { opacity: 0.45; cursor: not-allowed; }

.qr-confirm {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 48px;
  border: 0;
  border-radius: 12px;
  background: #485B38;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 160ms ease, transform 120ms ease;
}

.qr-confirm:hover:not(:disabled) { background: #344228; transform: translateY(-1px); }
.qr-confirm:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }

.qr-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,240,238,0.35);
  border-top-color: #FFF0EE;
  border-radius: 999px;
  animation: qr-spin 0.7s linear infinite;
}

@keyframes qr-spin { to { transform: rotate(360deg); } }


.bd-page {
  position: relative;
}

.bd-scene-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  padding: 9px 16px;
  border-bottom: 1px solid #d7c8c3;
  background: #fffaf7;
  box-shadow: 0 5px 16px rgba(30,8,18,0.05);
}

.bd-scene-tools__scene {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 145px;
}

.bd-scene-tools__number {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 9px;
  background: #485b38;
  color: #fff;
  font-size: 0.72rem;
  font-weight: 900;
}

.bd-scene-tools__scene > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.bd-scene-tools__scene small {
  color: #927b84;
  font-size: 0.57rem;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.bd-scene-tools__scene strong {
  max-width: 150px;
  overflow: hidden;
  color: #1e0812;
  font-size: 0.75rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bd-scene-tools__sizes {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border: 1px solid #ded3ce;
  border-radius: 10px;
  background: #f7f1ed;
}

.bd-scene-tools__sizes button,
.bd-scene-tools__move button {
  min-height: 29px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  font: inherit;
  font-size: 0.64rem;
  font-weight: 800;
}

.bd-scene-tools__sizes button {
  padding: 0 8px;
}

.bd-scene-tools__sizes button:hover,
.bd-scene-tools__sizes button.active {
  border-color: #a9bea0;
  background: #eef4ea;
  color: #344228;
}

.bd-scene-tools__move {
  display: flex;
  gap: 3px;
  margin-left: auto;
}

.bd-scene-tools__move button {
  width: 31px;
  border-color: #d8cbc5;
  background: #fff;
  color: #1e0812;
  font-size: 0.85rem;
}

.bd-scene-tools button:disabled {
  cursor: default;
  opacity: 0.38;
}

@media (max-width: 980px) {
  .bd-scene-tools {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .bd-scene-tools__move { margin-left: auto; }
}

@media (max-width: 620px) {
  .bd-scene-tools__scene { width: 100%; }
  .bd-scene-tools__sizes {
    width: 100%;
    overflow-x: auto;
  }
}

.bd-page--dragging {
  user-select: none;
  cursor: grabbing;
}

.grid-guides {
  position: absolute;
  inset: 16px;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 8px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 240ms ease;
  z-index: 0;
}

.bd-page:hover .grid-guides,
.bd-page--dragging .grid-guides {
  opacity: 1;
}

.grid-guides__col {
  border: 1px solid rgba(30,8,18,0.05);
  border-radius: 4px;
  background: rgba(30,8,18,0.015);
  position: relative;
}

.grid-guides__num {
  position: absolute;
  top: 4px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 9px;
  font-weight: 800;
  color: rgba(30,8,18,0.2);
  letter-spacing: 0.04em;
  line-height: 1;
}

.bd-grid {
  position: relative;
  z-index: 1;
}

.bd-grid--dragging .storyboard-tile {
  transition: transform 170ms ease, opacity 140ms ease, box-shadow 140ms ease, outline-color 140ms ease;
}

.storyboard-tile--is-dragging {
  opacity: 1 !important;
  pointer-events: none;
  transform: scale(1.01) !important;
  outline: 2px dashed rgba(72, 91, 56, 0.9);
  outline-offset: -6px;
  box-shadow: 0 0 0 5px rgba(72, 91, 56, 0.12), 3px 3px 0 #485B38 !important;
}

.storyboard-tile--drag-target {
  outline: 3px solid #485B38;
  outline-offset: 3px;
  box-shadow: 0 0 0 6px rgba(72,91,56,0.15), 3px 3px 0 #485B38 !important;
}

.storyboard-drag-ghost {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 9999;
  overflow: hidden;
  border: 3px solid #1E0812;
  border-radius: 10px;
  background: #FFF0EE;
  box-shadow: 0 18px 34px rgba(30, 8, 18, 0.34), 5px 5px 0 #485B38;
  pointer-events: none;
  touch-action: none;
  will-change: transform;
}

.storyboard-drag-ghost__image {
  display: block;
  width: 100%;
  height: calc(100% - 34px);
  min-height: 96px;
  object-fit: cover;
  background: #D4E5CA;
}

.storyboard-drag-ghost__label {
  display: block;
  height: 34px;
  padding: 8px 10px;
  border-top: 2px solid #1E0812;
  background: #FFF0EE;
  color: #1E0812;
  font-size: 0.75rem;
  font-weight: 900;
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(body.storyboard-drag-active) {
  cursor: grabbing;
  user-select: none;
  -webkit-user-select: none;
  overscroll-behavior: contain;
}

.vg-brand-back {
  color: inherit;
  text-decoration: none;
  opacity: 0.7;
  transition: opacity 140ms ease;
}

.vg-brand-back:hover {
  opacity: 1;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.page--studio {
  padding: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.page--studio .vg-root {
  flex: 1;
  border-radius: 0;
  border-left: 0;
  border-right: 0;
  border-bottom: 0;
}

.vg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.vg-icon-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}

.vg-icon-btn:hover {
  background: rgba(30,8,18, 0.08);
  color: #1E0812;
}

.vg-icon-btn svg {
  width: 16px;
  height: 16px;
}

.vg-status {
  font-size: 0.7rem;
  font-weight: 800;
  color: #785068;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 0 6px;
}

.vg-status--pub {
  color: #4A6741;
}

.vg-status--review {
  color: #8B3010;
}

.vg-status--rejected {
  color: #A8334C;
}

.vg-review-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 20px;
  margin: 0 16px 16px;
  border: 2px solid #8B3010;
  border-radius: 14px;
  background: rgba(139,48,16,0.06);
}

.vg-review-banner__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.vg-review-banner__text strong {
  font-size: 0.9rem;
  color: #1E0812;
}

.vg-review-banner__text span {
  font-size: 0.8rem;
  color: #785068;
}

.vg-review-banner__form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.vg-review-banner__input {
  min-width: 200px;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 8px 12px;
  background: #FFF0EE;
  color: #1E0812;
  font: inherit;
  font-size: 0.85rem;
}

.vg-review-banner__approve,
.vg-review-banner__reject {
  border: 0;
  border-radius: 10px;
  padding: 8px 16px;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}

.vg-review-banner__approve {
  background: #4A6741;
  color: #FFF0EE;
}
.vg-review-banner__approve:hover:not(:disabled) { background: #3d5534; }

.vg-review-banner__reject {
  background: transparent;
  border: 1.5px solid #A8334C;
  color: #A8334C;
}
.vg-review-banner__reject:hover:not(:disabled) { background: rgba(168,51,76,0.08); }

.vg-review-banner__approve:disabled,
.vg-review-banner__reject:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.vg-view--discussion {
  max-width: 760px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 4px 48px;
}

.icon-button {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: 160ms ease;
  box-shadow: var(--shadow);
}

.icon-button:hover {
  background: #fff7ef;
  color: var(--primary);
  border-color: rgba(91, 25, 40, 0.28);
}

.icon-button svg {
  width: 18px;
  height: 18px;
}

.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1300;
  background: rgba(30, 8, 18, 0.38);
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
  background: linear-gradient(180deg, #ffffff 0%, #f7faff 100%);
  box-shadow: 0 18px 60px rgba(30, 8, 18, 0.22);
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
  .dialog-backdrop {
    align-items: stretch;
    padding: 10px;
  }

  .dialog-card {
    width: 100%;
    max-height: calc(100vh - 20px);
    border-radius: 16px;
    padding: 14px;
  }

  .dialog-card__header {
    gap: 10px;
    margin-bottom: 12px;
  }
}

.scenario-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.scenario-meta-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 0.9rem;
  color: var(--text-soft);
}

.scenario-meta-strip__sep {
  color: var(--border);
  font-weight: 700;
}

.sb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  padding: 48px 24px 56px;
}

.sb-empty__grid {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.sb-empty__tile {
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  border: 3px solid #1E0812;
  border-radius: 10px;
  box-shadow: 3px 3px 0 #1E0812;
  font-size: 0.62rem;
  font-weight: 900;
  color: #785068;
  padding: 6px 8px;
  opacity: 0.55;
}

.sb-empty__tile--1 {
  width: 120px;
  height: 90px;
  background: linear-gradient(135deg, #D4E5CA, #D4E5CA);
}

.sb-empty__tile--2 {
  width: 80px;
  height: 110px;
  background: linear-gradient(135deg, #FFF0EE, #D4E5CA);
}

.sb-empty__tile--3 {
  width: 100px;
  height: 76px;
  background: linear-gradient(135deg, #c5d9b8, #afc8a0);
}

.sb-empty__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  text-align: center;
  max-width: 420px;
}

.sb-empty__eyebrow {
  margin: 0;
  color: #485B38;
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.sb-empty__title {
  margin: 0;
  font-size: clamp(1.3rem, 3vw, 1.75rem);
  font-weight: 950;
  line-height: 1.15;
  color: #1E0812;
  letter-spacing: -0.02em;
}

.sb-empty__sub {
  margin: 0;
  font-size: 0.9rem;
  color: #785068;
  line-height: 1.6;
}

.sb-empty__cta {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  border: 0;
  border-radius: 14px;
  padding: 14px 28px;
  background: #1E0812;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 160ms ease, transform 120ms ease;
  margin-top: 4px;
}

.sb-empty__cta:hover {
  background: #485B38;
  transform: translateY(-1px);
}

.sb-empty__cta svg {
  width: 17px;
  height: 17px;
}

.sb-empty__steps {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 6px;
}

.sb-empty__step {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 0.78rem;
  font-weight: 700;
  color: #785068;
}

.sb-empty__step-num {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 2px solid #D4E5CA;
  background: #FFF0EE;
  font-size: 0.65rem;
  font-weight: 900;
  color: #485B38;
}

.sb-empty__step-arrow {
  color: #D4E5CA;
  font-size: 0.88rem;
  font-weight: 700;
}

.side-gloss-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-muted, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.side-note-input--mono {
  font-family: monospace;
  font-size: 0.88rem;
  color: var(--primary, #0d6efd);
}

.gloss-save-btn {
  margin-top: 4px;
  width: 100%;
  padding: 8px 0;
  border-radius: 10px;
  border: none;
  background: var(--primary, #1a7a5e);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 120ms;
}

.gloss-save-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.side-gloss-empty {
  font-size: 0.84rem;
  color: var(--text-muted, #9ca3af);
  margin: 0;
}

.fiche-gloss {
  display: block;
  font-family: monospace;
  font-size: 0.76rem;
  color: var(--primary, #1a7a5e);
  margin-top: 1px;
  opacity: 0.85;
}

.ud-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: min(480px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 24px;
  background: #FFF0EE;
  box-shadow: 6px 6px 0 #1E0812;
}

.ud-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ud-eyebrow {
  margin: 0 0 3px;
  font-size: 0.68rem;
  font-weight: 900;
  color: #485B38;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.ud-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 950;
  color: #1E0812;
  letter-spacing: -0.02em;
}

.ud-context {
  max-width: 370px;
  margin: 8px 0 0;
  color: #785068;
  font-size: 0.76rem;
  line-height: 1.5;
}

.di-card {
  width: min(520px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  border: 2px solid #1e0812;
  border-radius: 20px;
  background: #fffaf7;
  color: #1e0812;
  box-shadow: 6px 6px 0 #1e0812;
}

.di-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 22px 18px;
  border-bottom: 1px solid #e5d8d3;
}

.di-eyebrow {
  margin: 0 0 4px;
  color: #485b38;
  font-size: 0.66rem;
  font-weight: 900;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.di-title {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.35rem;
  line-height: 1.2;
}

.di-sub {
  margin: 7px 0 0;
  color: #785068;
  font-size: 0.78rem;
}

.di-sub strong { color: #1e0812; }

.di-close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border: 1px solid #d8cbc5;
  border-radius: 999px;
  background: #fff;
  color: #785068;
  cursor: pointer;
}

.di-close:hover {
  border-color: #c5a9aa;
  background: #fff0ee;
  color: #5b1928;
}

.di-close svg {
  width: 14px;
  height: 14px;
}

.di-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.di-item {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 11px 14px;
  padding: 13px;
  border: 1px solid #ded3ce;
  border-radius: 14px;
  background: #fff;
}

.di-item__top {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.di-item__top > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.di-item__top strong {
  overflow: hidden;
  color: #1e0812;
  font-size: 0.82rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.di-item__top small {
  margin-top: 2px;
  color: #917c84;
  font-size: 0.65rem;
}

.di-item__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: 10px;
  background: #eef4ea;
  color: #485b38;
}

.di-item__icon svg {
  width: 16px;
  height: 16px;
}

.di-item__audio {
  grid-column: 1 / -1;
  width: 100%;
  height: 32px;
}

.di-item__import {
  grid-column: 1 / -1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  border: 1px solid #a9bea0;
  border-radius: 10px;
  background: #eef4ea;
  color: #344228;
  cursor: pointer;
  font-size: 0.76rem;
  font-weight: 850;
}

.di-item__import:hover:not(:disabled) {
  background: #d4e5ca;
}

.di-item__import:disabled {
  cursor: wait;
  opacity: 0.65;
}

.di-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 34px 24px 38px;
  text-align: center;
}

.di-empty__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  border: 1px solid #cbd8c3;
  border-radius: 999px;
  background: #eef4ea;
  color: #485b38;
}

.di-empty__icon svg {
  width: 21px;
  height: 21px;
}

.di-empty > strong {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1rem;
}

.di-empty p {
  max-width: 320px;
  margin: 7px 0 0;
  color: #785068;
  font-size: 0.76rem;
  line-height: 1.5;
}

@media (max-width: 560px) {
  .di-card {
    width: calc(100vw - 20px);
    max-height: calc(100vh - 20px);
  }

  .di-head { padding: 18px 17px 15px; }
  .di-list { padding: 10px; }
}

.ud-close {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: #FFF0EE;
  color: #785068;
  cursor: pointer;
  margin-top: 2px;
}

.ud-close:hover {
  background: #485B38;
  border-color: #485B38;
  color: white;
}

.ud-close svg {
  width: 15px;
  height: 15px;
}

.ud-source-row {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.ud-dropzone--half {
  flex: 1;
  min-width: 0;
}

.ud-maker-btn {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 180px;
  border: 2.5px solid #D4E5CA;
  border-radius: 14px;
  background: linear-gradient(135deg, #FFF0EE 0%, #DFE8DA 100%);
  cursor: pointer;
  padding: 20px 16px;
  transition: border-color 160ms, background 160ms, transform 120ms;
  text-align: center;
  color: #485B38;
}
.ud-maker-btn:hover {
  border-color: #485B38;
  background: linear-gradient(135deg, #DFE8DA 0%, #D4E5CA 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(72,91,56,.15);
}
.ud-maker-btn__label {
  font-size: 12px;
  font-weight: 700;
  color: #1E0812;
}
.ud-maker-btn__sub {
  font-size: 10px;
  color: #785068;
  line-height: 1.5;
}

.ud-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 200px;
  border: 2.5px dashed #D4E5CA;
  border-radius: 14px;
  background: #FFF0EE;
  cursor: pointer;
  transition: border-color 160ms ease, background 160ms ease;
  overflow: hidden;
  padding: 24px;
}

.ud-dropzone--over {
  border-color: #485B38;
  background: #D4E5CA;
}

.ud-dropzone--filled {
  border-style: solid;
  border-color: #1E0812;
  padding: 0;
  min-height: 240px;
}

.ud-dropzone__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: #FFF0EE;
  border: 1.5px solid #D4E5CA;
  color: #485B38;
}

.ud-dropzone__icon svg {
  width: 24px;
  height: 24px;
}

.ud-dropzone__label {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 800;
  color: #1E0812;
}

.ud-dropzone__sub {
  margin: 0;
  font-size: 0.8rem;
  color: #785068;
}

.ud-preview {
  width: 100%;
  height: 240px;
  object-fit: cover;
  display: block;
}

.ud-file-input {
  display: none;
}

.ud-preview-actions {
  display: flex;
  gap: 10px;
}

.ud-change-btn {
  display: inline-flex;
  align-items: center;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 7px 14px;
  background: #FFF0EE;
  color: #1E0812;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 140ms ease;
}

.ud-change-btn:hover {
  border-color: #485B38;
  color: #485B38;
}

.ud-clear-btn {
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 7px 14px;
  background: transparent;
  color: #785068;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 140ms ease, color 140ms ease;
}

.ud-clear-btn:hover {
  border-color: var(--danger);
  color: var(--danger);
}

.ud-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ud-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.78rem;
  font-weight: 800;
  color: #1E0812;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.ud-opt {
  font-size: 0.68rem;
  font-weight: 600;
  color: #785068;
  text-transform: uppercase;
}

.ud-input {
  border: 1.5px solid #D4E5CA;
  border-radius: 12px;
  padding: 12px 14px;
  background: #FFF0EE;
  color: #1E0812;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 600;
  outline: none;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.ud-input:focus {
  border-color: #485B38;
  box-shadow: 0 0 0 3px rgba(72,91,56, 0.12);
}

.ud-input::placeholder {
  color: #c5b5a5;
  font-weight: 400;
}

.ud-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 50px;
  border: 0;
  border-radius: 14px;
  background: #1E0812;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 160ms ease, transform 120ms ease;
}

.ud-submit:hover:not(:disabled) {
  background: #485B38;
  transform: translateY(-1px);
}

.ud-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ud-submit svg {
  width: 16px;
  height: 16px;
}

.ud-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 10px;
  max-height: 340px;
  overflow-y: auto;
}

.ud-grid-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ud-grid-thumb {
  position: relative;
  border: 2px solid #1E0812;
  border-radius: 10px;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  box-shadow: 2px 2px 0 #1E0812;
}

.ud-grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.ud-grid-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border: 1.5px solid #1E0812;
  border-radius: 6px;
  background: #FFF0EE;
  color: #1E0812;
  font-size: 14px;
  font-weight: 900;
  line-height: 1;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.ud-grid-remove:hover {
  background: #485B38;
  border-color: #485B38;
  color: white;
}

.ud-grid-title {
  border: 1.5px solid #D4E5CA;
  border-radius: 8px;
  padding: 5px 8px;
  background: #FFF0EE;
  color: #1E0812;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 600;
  outline: none;
}

.ud-grid-title:focus {
  border-color: #485B38;
}

.ud-grid-title::placeholder {
  color: #c5b5a5;
  font-weight: 400;
}

.ud-add-more-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ud-add-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex: 1;
  min-width: 160px;
  border: 2px dashed #D4E5CA;
  border-radius: 12px;
  padding: 10px 16px;
  background: transparent;
  color: #785068;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 140ms ease, color 140ms ease, background 140ms ease;
}

.ud-add-more:hover,
.ud-add-more.ud-dropzone--over {
  border-color: #485B38;
  color: #485B38;
  background: #fff3ee;
}

.ud-add-more svg {
  width: 15px;
  height: 15px;
}

.ud-add-more--maker {
  border-color: #D4E5CA;
}

.ud-add-more--maker:hover {
  border-color: #8B3010;
  color: #8B3010;
  background: #FFF0EE;
}

.ud-progress {
  height: 4px;
  border-radius: 999px;
  background: #D4E5CA;
  overflow: hidden;
}

.ud-progress__bar {
  height: 100%;
  background: #485B38;
  border-radius: 999px;
  transition: width 200ms ease;
}

.vg-play-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px 6px 10px;
  border-radius: 999px;
  border: 1.5px solid rgba(72, 91, 56, 0.5);
  background: rgba(72, 91, 56, 0.1);
  color: #485B38;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  letter-spacing: 0.03em;
  transition: all 160ms ease;
}
.vg-play-btn:hover:not(:disabled) {
  background: #485B38;
  border-color: #485B38;
  color: white;
  box-shadow: 0 0 14px rgba(72, 91, 56, 0.3);
}
.vg-play-btn:disabled {
  opacity: 0.35;
  cursor: default;
}
.vg-play-btn--playing {
  background: rgba(72, 91, 56, 0.18);
  border-color: #485B38;
  animation: sp-pulse-btn 2s ease-in-out infinite;
}
@keyframes sp-pulse-btn {
  0%, 100% { box-shadow: 0 0 0 0 rgba(72,91,56,0); }
  50% { box-shadow: 0 0 10px 3px rgba(72,91,56,0.2); }
}

.vg-interaction-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  transition: background 140ms ease, color 140ms ease;
}

.vg-bookmark-wrap {
  position: relative;
  display: inline-flex;
}

.vg-bookmark-picker {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 200;
  min-width: 210px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  border: 1.5px solid var(--border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(42, 21, 0, 0.14);
}

.vg-bookmark-picker__label {
  margin: 2px 6px 4px;
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-soft);
}

.vg-bookmark-picker__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 0.85rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 120ms ease;
}
.vg-bookmark-picker__item:hover { background: var(--surface-alt); }
.vg-bookmark-picker__item--active { color: var(--primary); background: rgba(192, 74, 8, 0.06); }
.vg-bookmark-picker__item--none { color: var(--text-soft); }

.vg-bookmark-picker__divider {
  height: 1px;
  background: var(--border);
  margin: 4px 0;
}

.vg-bookmark-picker__new {
  display: flex;
  gap: 6px;
  padding: 2px;
}

.vg-bookmark-picker__new-input {
  flex: 1;
  min-width: 0;
  border: 1.5px solid var(--border);
  border-radius: 8px;
  padding: 6px 10px;
  font: inherit;
  font-size: 0.82rem;
  outline: none;
  transition: border-color 140ms ease;
}
.vg-bookmark-picker__new-input:focus { border-color: var(--primary); }

.vg-bookmark-picker__new-btn {
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  background: var(--primary);
  color: #fff;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 140ms ease;
}
.vg-bookmark-picker__new-btn:hover:not(:disabled) { background: var(--primary-strong); }
.vg-bookmark-picker__new-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.vg-interaction-btn:hover { background: rgba(30,8,18,0.08); color: #1E0812; }
.vg-interaction-btn--active { color: var(--primary); }
.vg-interaction-btn--active:hover { background: rgba(192,74,8,0.08); }
.vg-interaction-btn__count { font-size: 0.7rem; font-weight: 800; opacity: 0.8; }
</style>
