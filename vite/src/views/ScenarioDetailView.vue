<script setup>
import {computed, nextTick, onMounted, ref, watch} from "vue";
import {RouterLink, useRoute, useRouter} from "vue-router";
import {fetchLanguage} from "../api/languages";
import {
  deleteAudio,
  deleteThumbnail,
  deleteScenario,
  fetchScenario,
  fetchScenarioThumbnails,
  fetchThumbnailAudios,
  publishScenario,
  reorderScenarioThumbnails,
  updateAudioGloss,
  updateScenarioMetadata,
  updateScenarioStoryboard,
  updateThumbnailLayout,
  uploadScenarioThumbnail,
  uploadThumbnailAudio,
} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useAuth} from "../composables/useAuth";
import {useToast} from "../composables/useToast";
import {useScenarioAutoplay} from "../composables/useScenarioAutoplay";
import ThumbnailCard from "../components/ThumbnailCard.vue";
import StudioRecorderPanel from "../components/StudioRecorderPanel.vue";
import BasePageHeader from "../components/ui/BasePageHeader.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import BaseBadge from "../components/ui/BaseBadge.vue";
import {
  buildPlaybackQueue,
  buildSelectedAudios,
  buildStoryboardItems,
  clamp,
  normalizeMarkers,
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
const {currentUser, loadMe} = useAuth();
const toast = useToast();

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
const savingOrder = ref(false);
const publishing = ref(false);
const resizingThumbnailId = ref(null);
const studioFrontendOnly = false;
const studioSandboxMode = ref(false);

const uploadTitle = ref("");
const uploadFile = ref(null);
const uploadFiles = ref([]);
const uploadPreviewUrl = ref("");
const uploadDragOver = ref(false);
const uploadingProgress = ref(0);
const highlightedThumbnailId = ref(null);
const tileDragId  = ref(null);
const tileDragOverId = ref(null);
const tileDragPreviewIds = ref([]);
const tileDragGhost = ref(null);

const infoDialogOpen = ref(false);
const studioDeleteConfirm = ref(false);
const studioDeleting = ref(false);

const quickRecordingThumbId = ref(null);
const quickRecordingDialogOpen = ref(false);
const quickRecordingTitle = ref("");
const quickRecordingBlob = ref(null);
const quickRecordingMimeType = ref("audio/webm");
const quickRecordingError = ref("");
const quickRecordingUploading = ref(false);
const quickRecordingTargetThumbId = ref(null);
const quickRecordingVoiceId = ref(null);
const quickRecordingCreatedDraft = ref(false);
const selectedSpeaker = ref("A");
const selectedVoiceId = ref(null);
const studioRecorderEl = ref(null);
const recordingFileInput = ref(null);
const recordingTrimOpen = ref(false);
const recordingVolume = ref(80);
const recordingSpeed = ref(100);
const recordingNoiseReduction = ref(true);
const recordingComments = ref(true);
const recordingTranscription = ref(true);
const recordingDownload = ref(false);
const trimStart = ref(0);
const trimEnd = ref(100);
const trimDragging = ref(null);
const trimPreviewPlaying = ref(false);

const glossTranscription = ref("");
const glossGloss = ref("");
const glossFreeTranslation = ref("");
const glossSaving = ref(false);
const glossAudioId = ref(null);
const glossTranscriptionInput = ref(null);

const studioAudioSettingsOpen = ref(false);
const studioTitleEditing = ref(false);
const studioTitleDraft = ref("");
const studioTitleSaving = ref(false);

let quickMediaRecorder = null;
let quickMediaStream = null;
let quickRecordingChunks = [];
let trimPreviewAudio = null;
let tilePointerDrag = null;
const DRAFT_AUDIO_KEY = "vignette:unclaimed-draft-audios";

const trimWaveBars = [
  34, 62, 48, 76, 52, 88, 44, 66, 92, 58, 38, 72,
  84, 46, 64, 96, 54, 74, 42, 68, 86, 50, 78, 60,
];

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
  clearUploadFile();
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

const selectedThumbnailPanelOpen = ref(false);
const selectedLayoutPanelOpen = ref(false);
const playerOpen = ref(false);

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

const selectedLayoutForm = ref({
  gridColumn: "",
  gridRow: "",
  gridColumnSpan: 1,
  gridRowSpan: 1,
});

const sortedThumbnails = computed(() => sortByIdxThenId(thumbnails.value));

const visualThumbnails = computed(() => {
  if (!tileDragId.value || !tileDragPreviewIds.value.length) {
    return sortedThumbnails.value;
  }

  const byId = new Map(sortedThumbnails.value.map((thumb) => [String(thumb.id), thumb]));
  const ordered = tileDragPreviewIds.value
      .map((id) => byId.get(String(id)))
      .filter(Boolean);

  return ordered.length === sortedThumbnails.value.length
      ? ordered
      : sortedThumbnails.value;
});

const selectedAudios = computed(() => {
  return buildSelectedAudios(audioMap.value, selectedThumb.value);
});

const selectedVoice = computed(() => {
  if (!selectedAudios.value.length) return null;
  return selectedAudios.value.find((audio) => String(audio.id) === String(selectedVoiceId.value)) ?? selectedAudios.value[0];
});

const recordingTargetVoice = computed(() => {
  if (!selectedThumb.value) return null;
  return targetVoiceForThumb(selectedThumb.value);
});

const recordingTargetSpeaker = computed(() => {
  return speakerForVoice(recordingTargetVoice.value, selectedThumb.value) || selectedSpeaker.value || nextSelectedSpeaker.value;
});

const selectedAudioMarkers = computed(() => normalizeMarkers(selectedAudios.value));

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

const playerStateLabel = computed(() => {
  if (autoplay.isLoading.value) return "loading";
  if (autoplay.isPlaying.value) return "playing";
  if (autoplay.isPaused.value) return "paused";
  return "idle";
});

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
  const markerCount = Object.values(audioMap.value)
      .flat()
      .filter((audio) => audio?.markerX != null && audio?.markerY != null)
      .length;

  return {
    mode,
    preset,
    columns: storyboardColumns.value,
    audioCount,
    markerCount,
  };
});

const activePresetName = computed(() => {
  const preset = String(storyboardForm.value.preset || "GRID_3").toUpperCase();
  if (preset === "CINEMATIC") return "Cinematic";
  if (preset === "MANGA") return "Manga";
  if (preset === "GRID_2") return "Strip";
  return "Classic";
});

const tileDragGhostStyle = computed(() => {
  const ghost = tileDragGhost.value;
  if (!ghost) return {};

  const left = Math.round(ghost.x - ghost.offsetX);
  const top = Math.round(ghost.y - ghost.offsetY);

  return {
    width: `${ghost.width}px`,
    height: `${ghost.height}px`,
    transform: `translate3d(${left}px, ${top}px, 0) rotate(-1.5deg)`,
  };
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

const selectedStudioAudios = computed(() => selectedAudios.value.slice(0, 3));
const nextSelectedSpeaker = computed(() => {
  return selectedThumb.value ? nextSpeakerForThumb(selectedThumb.value) : "A";
});

const recordingTargetLabel = computed(() => {
  const scene = selectedThumb.value?.title || `Scene ${selectedSceneNumber.value || 1}`;
  return `${scene} · Voice ${recordingTargetSpeaker.value}`;
});

const recordingStatusLabel = computed(() => {
  if (quickRecordingThumbId.value != null) return `Voice ${recordingTargetSpeaker.value} recording`;
  if (recordingTargetVoice.value?.isDraft) return `Voice ${recordingTargetSpeaker.value} ready to record`;
  if (recordingTargetVoice.value) return `Voice ${recordingTargetSpeaker.value} ready to re-record`;
  return "Voice A ready to record";
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

watch(
    selectedVoice,
    (audio) => {
      glossAudioId.value = audio?.id ?? null;
      glossTranscription.value = audio?.transcription ?? "";
      glossGloss.value = audio?.gloss ?? "";
      glossFreeTranslation.value = audio?.freeTranslation ?? "";
      trimStart.value = audio?.trimStart ?? 0;
      trimEnd.value = audio?.trimEnd ?? 100;
      selectedSpeaker.value = audio ? speakerForAudio(audio, Math.max(0, selectedAudios.value.indexOf(audio))) : nextSelectedSpeaker.value;
    },
    {immediate: true}
);

async function saveGloss() {
  if (!glossAudioId.value) return;
  glossSaving.value = true;
  try {
    const localVoice = selectedVoice.value;
    if (studioFrontendOnly || localVoice?.isDraft || String(localVoice?.id ?? "").startsWith("local-")) {
      updateVoiceFields(selectedThumb.value, localVoice, {
        transcription: glossTranscription.value || "",
        gloss: glossGloss.value || "",
        freeTranslation: glossFreeTranslation.value || "",
      });
      toast.success("Gloss saved.");
      return;
    }

    await updateAudioGloss(glossAudioId.value, {
      transcription: glossTranscription.value || null,
      gloss: glossGloss.value || null,
      freeTranslation: glossFreeTranslation.value || null,
    });
    if (selectedThumb.value) {
      audioMap.value[selectedThumb.value.id] = await fetchThumbnailAudios(selectedThumb.value.id);
    }
    toast.success("Gloss saved.");
  } catch (e) {
    toast.error(e.message || "Failed to save.");
  } finally {
    glossSaving.value = false;
  }
}

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

function focusGloss() {
  nextTick(() => {
    const el = glossTranscriptionInput.value;
    if (!el) return;
    el.scrollIntoView({behavior: "smooth", block: "center"});
    el.focus();
  });
}

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

function addUploadFiles(fileList) {
  const incoming = Array.from(fileList).filter((f) =>
    f.type.startsWith("image/") || f.type === "text/xml" || f.type === "application/xml"
  );
  const entries = incoming.map((file) => ({
    file,
    previewUrl: URL.createObjectURL(file),
    title: file.name.replace(/\.[^.]+$/, ""),
  }));
  uploadFiles.value = [...uploadFiles.value, ...entries];
  uploadFile.value = uploadFiles.value[0]?.file ?? null;
}

function setUploadFile(file) {
  uploadFile.value = file ?? null;
  if (uploadPreviewUrl.value) URL.revokeObjectURL(uploadPreviewUrl.value);
  uploadPreviewUrl.value = file ? URL.createObjectURL(file) : "";
  if (file && !uploadTitle.value) {
    uploadTitle.value = file.name.replace(/\.[^.]+$/, "");
  }
}

function onImageChange(event) {
  addUploadFiles(event.target.files ?? []);
  event.target.value = "";
}

function onUploadDrop(event) {
  uploadDragOver.value = false;
  addUploadFiles(event.dataTransfer?.files ?? []);
}

function removeUploadEntry(index) {
  URL.revokeObjectURL(uploadFiles.value[index]?.previewUrl);
  uploadFiles.value = uploadFiles.value.filter((_, i) => i !== index);
  uploadFile.value = uploadFiles.value[0]?.file ?? null;
}

function clearUploadFile() {
  uploadFiles.value.forEach((e) => URL.revokeObjectURL(e.previewUrl));
  uploadFiles.value = [];
  uploadFile.value = null;
  uploadPreviewUrl.value = "";
  uploadTitle.value = "";
  uploadingProgress.value = 0;
}

function thumbnailContentUrl(thumb) {
  if (thumb?.previewUrl) return thumb.previewUrl;
  if (!thumb?.id) return "";
  return buildApiUrl(`/api/thumbnails/${thumb.id}/content`);
}

function demoImage(label, background, accent) {
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 560">
  <rect width="900" height="560" fill="${background}"/>
  <path d="M0 405 C180 350 250 500 430 415 S705 330 900 410 V560 H0 Z" fill="${accent}" opacity=".68"/>
  <circle cx="690" cy="135" r="74" fill="#f2a154" opacity=".78"/>
  <rect x="86" y="82" width="340" height="85" rx="16" fill="#FFF0E4" stroke="#1E0812" stroke-width="8"/>
  <path d="M130 260 h210 l40 55 h155" fill="none" stroke="#1E0812" stroke-width="12" stroke-linecap="round"/>
  <text x="118" y="140" font-family="Arial, sans-serif" font-size="46" font-weight="800" fill="#1E0812">${label}</text>
</svg>`;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
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
  thumbnails.value = [
    {id: "studio-1", idx: 1, title: "Opening", gridColumnSpan: 6, gridRowSpan: 3, previewUrl: demoImage("Scene 01", "#D4E5CA", "#485B38")},
    {id: "studio-2", idx: 2, title: "Dialogue", gridColumnSpan: 6, gridRowSpan: 3, previewUrl: demoImage("Scene 02", "#E5D0CC", "#8f5f4a")},
    {id: "studio-3", idx: 3, title: "Reaction", gridColumnSpan: 4, gridRowSpan: 3, previewUrl: demoImage("Scene 03", "#F5E7E4", "#485B38")},
    {id: "studio-4", idx: 4, title: "Action", gridColumnSpan: 4, gridRowSpan: 3, previewUrl: demoImage("Scene 04", "#DFE8DA", "#6d8a75")},
    {id: "studio-5", idx: 5, title: "Ending", gridColumnSpan: 4, gridRowSpan: 3, previewUrl: demoImage("Scene 05", "#D4E5CA", "#2f6f73")},
  ];
  audioMap.value = {
    "studio-1": [{id: "audio-1", title: "Narration", idx: 1, markerX: 28, markerY: 34, markerLabel: "Intro"}],
    "studio-2": [{id: "audio-2", title: "Voice A", idx: 1, markerX: 54, markerY: 42, markerLabel: "Dialogue"}],
  };
  selectedThumb.value = thumbnails.value[0] ?? null;
  activeAudioId.value = null;

  if (reason) {
    toast.info("Studio front-end sandbox enabled.");
  }
}

function readUnclaimedDraftAudio(draftId) {
  if (!draftId) return null;
  try {
    const drafts = JSON.parse(localStorage.getItem(DRAFT_AUDIO_KEY) || "[]");
    return drafts.find((draft) => String(draft.id) === String(draftId)) ?? null;
  } catch {
    return null;
  }
}

function applyUnclaimedDraftAudio() {
  const draftId = route.query.draftAudio;
  const draft = readUnclaimedDraftAudio(Array.isArray(draftId) ? draftId[0] : draftId);
  if (!draft?.dataUrl) return;

  if (!scenario.value || String(props.id).startsWith("emergency-")) {
    useStudioSandbox();
    scenario.value = {
      ...scenario.value,
      id: props.id,
      title: draft.title || "Vignette audio",
      description: "Created from an emergency audio draft",
    };
  }

  if (!thumbnails.value.length) {
    thumbnails.value = [
      {
        id: `draft-thumb-${draft.id}`,
        idx: 1,
        title: draft.title || "Audio draft",
        gridColumnSpan: 6,
        gridRowSpan: 3,
        previewUrl: demoImage("Audio 01", "#D4E5CA", "#485B38"),
      },
    ];
  }

  const targetThumb = sortedThumbnails.value[0] ?? thumbnails.value[0];
  const hasDraft = (audioMap.value[targetThumb.id] || []).some((audio) => audio.sourceDraftId === draft.id);
  if (!hasDraft) {
    audioMap.value = {
      ...audioMap.value,
      [targetThumb.id]: [
        {
          id: `draft-audio-${draft.id}`,
          idx: 1,
          title: draft.title || "Voice A",
          speaker: "A",
          previewUrl: draft.dataUrl,
          sourceDraftId: draft.id,
          markerX: null,
          markerY: null,
          markerLabel: "",
          transcription: "",
          gloss: "",
          freeTranslation: "",
        },
        ...(audioMap.value[targetThumb.id] || []),
      ],
    };
  }

  selectedThumb.value = targetThumb;
  selectedVoiceId.value = `draft-audio-${draft.id}`;
  selectedSpeaker.value = "A";
  toast.success("Draft audio imported as Voice A.");
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

function selectThumb(thumb, {openRecorder = true, scrollRecorder = false} = {}) {
  if (!thumb?.id) return;
  selectedThumb.value = thumb;
  activeAudioId.value = null;
  const firstVoice = audioMap.value[thumb.id]?.[0] ?? null;
  selectedVoiceId.value = firstVoice?.id ?? null;
  selectedSpeaker.value = firstVoice ? speakerForAudio(firstVoice, 0) : nextSpeakerForThumb(thumb);
  selectedThumbnailPanelOpen.value = true;
  if (openRecorder) {
    openRecorderForSelection({scroll: scrollRecorder});
  }
}

function selectGlobalThumb(thumb, audio = null) {
  selectThumb(thumb, {openRecorder: true});
  if (audio) {
    selectVoice(audio, thumb, {scrollRecorder: false});
  }
}

function selectRelativeThumb(offset) {
  if (!sortedThumbnails.value.length) return;
  const currentIndex = selectedThumbIndex.value >= 0 ? selectedThumbIndex.value : 0;
  const nextIndex = clamp(currentIndex + offset, 0, sortedThumbnails.value.length - 1);
  selectThumb(sortedThumbnails.value[nextIndex]);
}

function updateSelectedThumbTitle(event) {
  if (!selectedThumb.value) return;
  updateThumbTitle(selectedThumb.value, event);
}

function updateThumbTitle(targetThumb, event) {
  if (!targetThumb?.id) return;
  const title = event.target.value;

  thumbnails.value = thumbnails.value.map((thumb) => {
    if (String(thumb.id) !== String(targetThumb.id)) return thumb;
    return {...thumb, title};
  });

  if (selectedThumb.value && String(selectedThumb.value.id) === String(targetThumb.id)) {
    selectedThumb.value = {
      ...selectedThumb.value,
      title,
    };
  }
}

async function deleteThumb(targetThumb) {
  if (!targetThumb?.id) return;

  const targetId = String(targetThumb.id);
  const isLocal = !isPersistableThumbnailId(targetThumb.id);

  if (!studioFrontendOnly && !studioSandboxMode.value && !isLocal) {
    try {
      await deleteThumbnail(targetThumb.id);
    } catch (e) {
      toast.error(e.message || "Could not delete this image.");
      return;
    }
  }

  const ordered = sortedThumbnails.value;
  const removedIndex = ordered.findIndex((thumb) => String(thumb.id) === targetId);

  thumbnails.value = thumbnails.value.filter((thumb) => String(thumb.id) !== targetId);
  const {[targetThumb.id]: _removedAudios, ...nextAudioMap} = audioMap.value;
  audioMap.value = nextAudioMap;

  if (String(quickRecordingThumbId.value ?? "") === targetId) {
    stopQuickRecording();
  }

  if (selectedThumb.value && String(selectedThumb.value.id) === targetId) {
    const nextThumb = ordered[removedIndex + 1] || ordered[removedIndex - 1] || null;
    selectedThumb.value = nextThumb && String(nextThumb.id) !== targetId ? nextThumb : null;
    activeAudioId.value = null;
    globalRecorderOpen.value = !!selectedThumb.value && storyboardView.value === "global";
  }

  toast.success("Image removed from this board.");
}

function syncSelectedThumbnailFromList(fallbackId = selectedThumb.value?.id) {
  if (fallbackId == null) return;
  const refreshed = thumbnails.value.find((thumb) => String(thumb.id) === String(fallbackId));
  if (refreshed) {
    selectedThumb.value = refreshed;
  }
}

function setOrderedThumbnails(ordered) {
  thumbnails.value = ordered.map((thumb, index) => ({
    ...thumb,
    idx: index + 1,
  }));
  syncSelectedThumbnailFromList();
}

function mergePersistedThumbnailRows(rows) {
  if (!Array.isArray(rows) || !rows.length) return;
  const currentById = new Map(thumbnails.value.map((thumb) => [String(thumb.id), thumb]));
  thumbnails.value = rows.map((row) => ({
    ...(currentById.get(String(row.id)) || {}),
    ...row,
  }));
  syncSelectedThumbnailFromList();
}

function isPersistableThumbnailId(id) {
  return /^\d+$/.test(String(id ?? ""));
}

async function persistThumbnailOrder(ordered) {
  if (studioFrontendOnly || studioSandboxMode.value) return;

  const thumbnailIds = ordered.map((thumb) => thumb.id);
  if (!thumbnailIds.every(isPersistableThumbnailId)) return;

  try {
    const persistedRows = await reorderScenarioThumbnails(
        props.id,
        thumbnailIds.map((id) => Number(id))
    );
    mergePersistedThumbnailRows(persistedRows);
  } catch (e) {
    const message = String(e?.message || "");
    const routeMissing = message.includes("No static resource") || message.includes("HTTP 404");
    if (!routeMissing) {
      throw e;
    }

    console.warn("Thumbnail reorder endpoint is not available on the running backend yet.", e);
  }
}

async function applyThumbnailOrder(nextOrdered, message = "Scene reordered.") {
  if (!nextOrdered.length || savingOrder.value) return;

  const previousThumbnails = thumbnails.value;
  const previousSelectedId = selectedThumb.value?.id;
  setOrderedThumbnails(nextOrdered);
  savingOrder.value = true;

  try {
    await persistThumbnailOrder(nextOrdered);
    toast.success(message);
  } catch (e) {
    thumbnails.value = previousThumbnails;
    syncSelectedThumbnailFromList(previousSelectedId);
    toast.error(e.message || "Could not save image order.");
  } finally {
    savingOrder.value = false;
  }
}

function moveThumbInOrder(ordered, fromIndex, toIndex) {
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return null;

  const nextOrdered = [...ordered];
  const [moved] = nextOrdered.splice(fromIndex, 1);
  nextOrdered.splice(toIndex, 0, moved);
  return nextOrdered;
}

async function reorderThumb(targetThumb, direction) {
  if (!targetThumb?.id) return;

  const ordered = sortedThumbnails.value;
  const currentIndex = ordered.findIndex((thumb) => String(thumb.id) === String(targetThumb.id));
  if (currentIndex < 0) return;

  let nextIndex = currentIndex;
  if (direction === "first") nextIndex = 0;
  if (direction === "last") nextIndex = ordered.length - 1;
  if (direction === "up") nextIndex = Math.max(0, currentIndex - 1);
  if (direction === "down") nextIndex = Math.min(ordered.length - 1, currentIndex + 1);
  if (nextIndex === currentIndex) return;

  const nextOrdered = moveThumbInOrder(ordered, currentIndex, nextIndex);
  if (nextOrdered) {
    await applyThumbnailOrder(nextOrdered);
  }
}

function toggleSelectedQuickRecording() {
  if (quickRecordingThumbId.value != null) {
    if (
        !selectedThumb.value ||
        String(quickRecordingThumbId.value) !== String(selectedThumb.value.id)
    ) {
      stopQuickRecording();
      return;
    }
  }

  if (selectedThumb.value) {
    toggleQuickRecording(selectedThumb.value, targetVoiceForThumb(selectedThumb.value));
  }
}

function scrollToRecorder() {
  nextTick(() => {
    studioRecorderEl.value?.scrollIntoView?.({behavior: "smooth", block: "center"});
  });
}

function isRecordingThumb(thumb) {
  return (
      quickRecordingThumbId.value != null &&
      String(quickRecordingThumbId.value) === String(thumb?.id ?? "")
  );
}

function recordButtonVoiceForThumb(thumb) {
  if (!thumb?.id || String(selectedThumb.value?.id ?? "") !== String(thumb.id)) return null;
  return targetVoiceForThumb(thumb);
}

function recordButtonLabelForThumb(thumb) {
  if (!thumb?.id) return "Record voice";
  const targetVoice = recordButtonVoiceForThumb(thumb);
  if (targetVoice) {
    const speaker = speakerForVoice(targetVoice, thumb);
    return targetVoice.isDraft ? `Record selected Voice ${speaker}` : `Re-record Voice ${speaker}`;
  }
  if (voicesForThumb(thumb).length >= 4) return "Max voices reached";
  return `Record voice ${nextSpeakerForThumb(thumb)}`;
}

async function addVoiceForThumb(thumb) {
  if (!thumb?.id) return;
  if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
    toast.error("Stop the current recording first.");
    return;
  }
  const selectedForCard = String(selectedThumb.value?.id ?? "") === String(thumb.id)
      ? targetVoiceForThumb(thumb)
      : null;

  if (selectedForCard) {
    selectVoice(selectedForCard, thumb);
    await toggleQuickRecording(thumb, selectedForCard);
    return;
  }

  selectThumb(thumb);
  const voice = addDraftVoice(thumb);
  if (!voice) return;
  selectVoice(voice, thumb);
  await toggleQuickRecording(thumb, voice);
}

async function recordNextVoiceForThumb(thumb) {
  if (!thumb?.id) return;
  if (isRecordingThumb(thumb)) {
    stopQuickRecording();
    return;
  }
  if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
    toast.error("Stop the current recording first.");
    return;
  }

  selectGlobalThumb(thumb);
  const voice = addDraftVoice(thumb, nextSpeakerForThumb(thumb));
  if (!voice) return;
  selectVoice(voice, thumb);
  await toggleQuickRecording(thumb, voice);
}

function selectVoice(audio, thumb = selectedThumb.value, {scrollRecorder = true} = {}) {
  if (!audio) return;

  const targetThumb = thumb ?? selectedThumb.value;
  if (targetThumb && String(selectedThumb.value?.id ?? "") !== String(targetThumb.id)) {
    selectedThumb.value = targetThumb;
  }

  selectedVoiceId.value = audio.id;
  selectedSpeaker.value = speakerForAudio(audio, Math.max(0, (audioMap.value[targetThumb?.id] || []).findIndex((item) => String(item.id) === String(audio.id))));
  glossTranscription.value = audio.transcription ?? "";
  glossGloss.value = audio.gloss ?? "";
  glossFreeTranslation.value = audio.freeTranslation ?? "";
  openRecorderForSelection({scroll: scrollRecorder});
}

function selectSpeakerSlot(speaker, thumb = selectedThumb.value) {
  if (!thumb?.id) return;
  const existing = findVoiceBySpeaker(thumb, speaker);

  if (existing) {
    selectVoice(existing, thumb);
    return;
  }

  if ((audioMap.value[thumb.id] || []).length >= 4) {
    toast.error("This scene already has four voices.");
    return;
  }

  const draft = addDraftVoice(thumb, speaker);
  if (draft) {
    selectVoice(draft, thumb);
  }
}

function findVoiceBySpeaker(thumb, speaker) {
  if (!thumb?.id || !speaker) return null;
  return (audioMap.value[thumb.id] || []).find((audio, index) =>
      speakerForAudio(audio, index) === speaker
  ) ?? null;
}

function indexForSpeaker(speaker) {
  const index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(String(speaker || "").toUpperCase());
  return index >= 0 ? index : 0;
}

function nextVoiceIdxForSpeaker(thumb, speaker) {
  const voices = audioMap.value[thumb?.id] || [];
  const used = new Set(voices.map((audio) => Number(audio.idx)).filter(Number.isFinite));
  const preferred = indexForSpeaker(speaker) + 1;
  if (!used.has(preferred)) return preferred;
  return Math.max(0, ...used) + 1;
}

function addDraftVoice(targetThumb, speaker = nextSpeakerForThumb(targetThumb)) {
  if (!targetThumb?.id) return null;
  const voices = audioMap.value[targetThumb.id] || [];
  const assignedSpeaker = speaker || nextSpeakerForThumb(targetThumb);
  const existing = findVoiceBySpeaker(targetThumb, assignedSpeaker);
  if (existing) return existing;

  if (voices.length >= 4) {
    toast.error("This scene already has four voices.");
    return null;
  }

  const draft = {
    id: `draft-${targetThumb.id}-${assignedSpeaker}-${Date.now()}`,
    idx: nextVoiceIdxForSpeaker(targetThumb, assignedSpeaker),
    title: `Voice ${assignedSpeaker}`,
    speaker: assignedSpeaker,
    isDraft: true,
    markerX: null,
    markerY: null,
    markerLabel: "",
    transcription: "",
    gloss: "",
    freeTranslation: "",
  };

  audioMap.value = {
    ...audioMap.value,
    [targetThumb.id]: [...voices, draft],
  };

  return draft;
}

function ensureVoiceForRecording(thumb, requestedVoice = null, speaker = null) {
  if (!thumb?.id) return {voice: null, created: false};

  if (String(selectedThumb.value?.id ?? "") !== String(thumb.id)) {
    selectThumb(thumb);
  }

  const voices = voicesForThumb(thumb);
  const requested = requestedVoice
      ? voices.find((audio) => String(audio.id) === String(requestedVoice.id))
      : null;
  const selected = targetVoiceForThumb(thumb);
  const target = requested ?? selected;

  if (target) {
    selectVoice(target, thumb);
    return {voice: target, created: false};
  }

  const targetSpeaker = speaker || selectedSpeaker.value || nextSpeakerForThumb(thumb);
  const existing = findVoiceBySpeaker(thumb, targetSpeaker);
  if (existing) {
    selectVoice(existing, thumb);
    return {voice: existing, created: false};
  }

  const draft = addDraftVoice(thumb, targetSpeaker);
  if (draft) selectVoice(draft, thumb);

  return {voice: draft, created: !!draft?.isDraft};
}

async function removeVoice(audio, thumb = selectedThumb.value) {
  if (!audio || !thumb?.id) return;

  if (isAudioPlaying(audio, thumb)) {
    autoplay.stop();
  }

  if (
      quickRecordingThumbId.value != null &&
      String(quickRecordingThumbId.value) === String(thumb.id) &&
      String(quickRecordingVoiceId.value ?? "") === String(audio.id)
  ) {
    stopQuickRecording();
  }

  try {
    const isLocalVoice = audio.isDraft || String(audio.id).startsWith("local-") || String(audio.id).startsWith("draft-");
    if (!studioFrontendOnly && !isLocalVoice) {
      await deleteAudio(audio.id);
    }

    const currentVoices = audioMap.value[thumb.id] || [];
    const removedIndex = currentVoices.findIndex((item) => String(item.id) === String(audio.id));
    const remaining = currentVoices.filter((item) => String(item.id) !== String(audio.id));
    audioMap.value = {
      ...audioMap.value,
      [thumb.id]: remaining,
    };

    const nextVoice = remaining[Math.min(Math.max(removedIndex, 0), remaining.length - 1)] ?? remaining[0] ?? null;
    selectedVoiceId.value = nextVoice?.id ?? null;
    selectedSpeaker.value = nextVoice ? speakerForAudio(nextVoice, Math.max(0, remaining.indexOf(nextVoice))) : nextSpeakerForThumb(thumb);
    activeAudioId.value = String(activeAudioId.value ?? "") === String(audio.id) ? null : activeAudioId.value;
    toast.success("Voice removed.");
  } catch (e) {
    toast.error(e.message || "Could not remove this voice.");
  }
}

function hasVoiceAudio(audio) {
  return !!audio && !audio.isDraft && !!(audio.previewUrl || audio.id);
}

async function removeVoiceAudio(audio, thumb = selectedThumb.value) {
  if (!audio || !thumb?.id || audio.isDraft) return;

  if (isAudioPlaying(audio, thumb)) {
    autoplay.stop();
  }

  if (
      quickRecordingThumbId.value != null &&
      String(quickRecordingThumbId.value) === String(thumb.id) &&
      String(quickRecordingVoiceId.value ?? "") === String(audio.id)
  ) {
    stopQuickRecording();
  }

  try {
    const isLocalVoice = String(audio.id).startsWith("local-") || String(audio.id).startsWith("draft-");
    if (!studioFrontendOnly && !isLocalVoice) {
      await deleteAudio(audio.id);
    }

    const speaker = speakerForAudio(audio, Math.max(0, (audioMap.value[thumb.id] || []).findIndex((item) => String(item.id) === String(audio.id))));
    const draftVoice = {
      ...audio,
      id: `draft-${thumb.id}-${speaker}-${Date.now()}`,
      speaker,
      title: /^Voice\s+[A-Z]/.test(audio.title || "") ? `Voice ${speaker}` : audio.title,
      isDraft: true,
      previewUrl: null,
      sourceDraftId: null,
      markerX: null,
      markerY: null,
      markerLabel: "",
    };

    audioMap.value = {
      ...audioMap.value,
      [thumb.id]: (audioMap.value[thumb.id] || []).map((item) =>
          String(item.id) === String(audio.id) ? draftVoice : item
      ),
    };

    selectedVoiceId.value = draftVoice.id;
    selectedSpeaker.value = speaker;
    activeAudioId.value = String(activeAudioId.value ?? "") === String(audio.id) ? null : activeAudioId.value;
    toast.success("Audio removed, voice kept.");
  } catch (e) {
    toast.error(e.message || "Could not remove this audio.");
  }
}

function speakerForAudio(audio, index) {
  return audio?.speaker || speakerForIndex(index);
}

function speakerClass(audio, index) {
  return String(speakerForAudio(audio, index)).toLowerCase();
}

function voicesForThumb(thumb) {
  return thumb?.id ? (audioMap.value[thumb.id] || []) : [];
}

function voiceIndexInThumb(audio, thumb) {
  if (!audio || !thumb?.id) return -1;
  return voicesForThumb(thumb).findIndex((item) => String(item.id) === String(audio.id));
}

function speakerForVoice(audio, thumb) {
  if (!audio) return "";
  const index = voiceIndexInThumb(audio, thumb);
  return speakerForAudio(audio, Math.max(0, index));
}

function voiceDisplayTitle(audio, thumb = selectedThumb.value) {
  const speaker = speakerForVoice(audio, thumb) || selectedSpeaker.value || "A";
  const title = String(audio?.title || "").trim();
  if (/^Voice\s+[A-Z](\b|$)/i.test(title)) {
    return title.replace(/^Voice\s+[A-Z]/i, `Voice ${speaker}`);
  }
  return title || `Voice ${speaker}`;
}

function selectedVoiceForThumb(thumb) {
  if (!thumb?.id || String(selectedThumb.value?.id ?? "") !== String(thumb.id)) return null;
  return voicesForThumb(thumb).find((audio) => String(audio.id) === String(selectedVoiceId.value ?? "")) ?? null;
}

function targetVoiceForThumb(thumb) {
  if (!thumb?.id || String(selectedThumb.value?.id ?? "") !== String(thumb.id)) return null;
  return selectedVoiceForThumb(thumb) ?? selectedVoice.value ?? null;
}

function isLocalAudioId(id) {
  return String(id ?? "").startsWith("local-") || String(id ?? "").startsWith("draft-");
}

function speakerForIndex(index) {
  const normalized = Math.max(0, Number(index) || 0);
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  if (normalized < alphabet.length) {
    return alphabet[normalized];
  }

  return `A${normalized - alphabet.length + 1}`;
}

function nextSpeakerForThumb(thumb) {
  if (!thumb?.id) return "A";
  const used = new Set((audioMap.value[thumb.id] || []).map((audio, index) => speakerForAudio(audio, index)));
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  return alphabet.split("").find((speaker) => !used.has(speaker)) || speakerForIndex(used.size);
}

function isAudioPlaying(audio, thumb = selectedThumb.value) {
  const item = autoplay.currentItem.value;
  return (
      autoplay.isPlaying.value &&
      String(activeAudioId.value ?? "") === String(audio?.id ?? "") &&
      String(item?.audioId ?? "") === String(audio?.id ?? "") &&
      String(item?.thumbnailId ?? "") === String(thumb?.id ?? "")
  );
}

function isThumbPlaying(thumb) {
  const item = autoplay.currentItem.value;
  return (
      autoplay.isPlaying.value &&
      String(item?.thumbnailId ?? "") === String(thumb?.id ?? "")
  );
}

async function toggleThumbPlayback(thumb) {
  if (!thumb) return;

  if (isThumbPlaying(thumb)) {
    autoplay.pause();
    return;
  }

  selectGlobalThumb(thumb);
  await playAllFromContext();
}

async function toggleAudioPlayback(audio, thumb = selectedThumb.value) {
  if (!audio || !thumb) {
    autoplay.stop();
    activeAudioId.value = null;
    return;
  }

  selectVoice(audio, thumb);

  if (audio.isDraft) {
    return;
  }

  if (isAudioPlaying(audio, thumb)) {
    autoplay.pause();
    return;
  }

  if (String(selectedThumb.value?.id ?? "") !== String(thumb.id)) {
    selectedThumb.value = thumb;
    selectedSpeaker.value = nextSpeakerForThumb(thumb);
  }

  await setActiveAudio(audio);
}

async function toggleSelectedAudioPlayback() {
  if (selectedVoice.value) {
    await toggleAudioPlayback(selectedVoice.value, selectedThumb.value);
    return;
  }

  if (autoplay.isPlaying.value) {
    autoplay.pause();
    return;
  }

  if (autoplay.isPaused.value) {
    await autoplay.resume();
    return;
  }

  await playAllFromContext();
}

function addLocalAudioClip(targetThumb, {title, previewUrl, speaker}) {
  if (!targetThumb?.id || !previewUrl) return;
  const currentVoice = selectedVoice.value && String(selectedThumb.value?.id ?? "") === String(targetThumb.id)
      ? selectedVoice.value
      : null;
  const assignedSpeaker = speaker || currentVoice?.speaker || nextSpeakerForThumb(targetThumb);
  const nextAudio = {
    id: currentVoice?.isDraft ? currentVoice.id : `local-audio-${Date.now()}-${Math.round(Math.random() * 1000)}`,
    idx: currentVoice?.idx ?? ((audioMap.value[targetThumb.id]?.length ?? 0) + 1),
    title: title || currentVoice?.title || `Voice ${assignedSpeaker}`,
    speaker: assignedSpeaker,
    previewUrl,
    isDraft: false,
    markerX: currentVoice?.markerX ?? null,
    markerY: currentVoice?.markerY ?? null,
    markerLabel: currentVoice?.markerLabel ?? "",
    transcription: currentVoice?.transcription ?? "",
    gloss: currentVoice?.gloss ?? "",
    freeTranslation: currentVoice?.freeTranslation ?? "",
  };

  const existing = audioMap.value[targetThumb.id] || [];
  const replaceExisting = currentVoice && existing.some((audio) => String(audio.id) === String(currentVoice.id));

  audioMap.value = {
    ...audioMap.value,
    [targetThumb.id]: replaceExisting
        ? existing.map((audio) => String(audio.id) === String(currentVoice.id) ? nextAudio : audio)
        : [...existing, nextAudio],
  };

  selectedVoiceId.value = nextAudio.id;
  selectedSpeaker.value = assignedSpeaker;
}

function openRecordingAudioFile() {
  recordingFileInput.value?.click?.();
}

function onRecordingAudioFileChange(event) {
  const file = event.target.files?.[0] ?? null;
  importRecordingAudioFile(file);
  event.target.value = "";
}

async function importRecordingAudioFile(file) {
  if (!file || !selectedThumb.value) return;
  const targetThumb = selectedThumb.value;
  const {voice} = ensureVoiceForRecording(targetThumb, targetVoiceForThumb(targetThumb));
  if (!voice) return;
  const speaker = speakerForVoice(voice, targetThumb) || selectedSpeaker.value;
  const title = voiceDisplayTitle(voice, targetThumb) || file.name.replace(/\.[^.]+$/, "") || `Voice ${speaker}`;

  try {
    if (!studioFrontendOnly) {
      await uploadVoiceAudioFile(targetThumb, voice, file, title, file.name || "recording-upload");
      toast.success("Audio imported.");
      return;
    }

    addLocalAudioClip(targetThumb, {
      title,
      previewUrl: URL.createObjectURL(file),
      speaker,
    });

    toast.success("Audio imported.");
  } catch (e) {
    toast.error(e.message || "Could not import this audio.");
  }
}

async function uploadVoiceAudioFile(targetThumb, voice, audioFile, title, fileName = "recording.webm") {
  if (!targetThumb?.id || !audioFile) throw new Error("No voice selected.");

  const thumbId = targetThumb.id;
  const replacingExistingAudio = !!voice && !voice.isDraft && !isLocalAudioId(voice.id);
  let deletedForReplace = false;
  const previousAudioIds = new Set(
      (audioMap.value[thumbId] || [])
          .filter((audio) => !audio.isDraft)
          .map((audio) => String(audio.id))
  );

  try {
    if (replacingExistingAudio) {
      await deleteAudio(voice.id);
      deletedForReplace = true;
      previousAudioIds.delete(String(voice.id));
    }

    const fd = new FormData();
    fd.append("title", title || voice?.title || "");
    if (voice?.idx) fd.append("idx", String(voice.idx));
    if (voice?.markerX != null) fd.append("markerX", String(voice.markerX));
    if (voice?.markerY != null) fd.append("markerY", String(voice.markerY));
    if (voice?.markerLabel) fd.append("markerLabel", voice.markerLabel);
    fd.append("audio", audioFile, fileName);

    const response = await uploadThumbnailAudio(thumbId, fd);
    const freshAudios = await fetchThumbnailAudios(thumbId);
    audioMap.value = {...audioMap.value, [thumbId]: freshAudios};

    const newAudio =
        freshAudios.find((audio) => String(audio.id) === String(response?.id)) ??
        freshAudios.find((audio) => !previousAudioIds.has(String(audio.id))) ??
        freshAudios[freshAudios.length - 1] ??
        null;

    if (newAudio) {
      if (String(selectedThumb.value?.id ?? "") !== String(thumbId)) {
        selectedThumb.value = targetThumb;
      }
      selectedVoiceId.value = newAudio.id;
      selectedSpeaker.value = speakerForAudio(newAudio, freshAudios.indexOf(newAudio));
    }

    return newAudio;
  } catch (e) {
    if (deletedForReplace) {
      try {
        const freshAudios = await fetchThumbnailAudios(thumbId);
        audioMap.value = {...audioMap.value, [thumbId]: freshAudios};
        const fallback = freshAudios[0] ?? null;
        selectedVoiceId.value = fallback?.id ?? null;
        selectedSpeaker.value = fallback ? speakerForAudio(fallback, 0) : nextSpeakerForThumb(targetThumb);
      } catch {
        // Keep the original error as the user-facing failure.
      }
    }
    throw e;
  }
}

function restartSelectedRecording() {
  if (!selectedThumb.value) return;

  if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
    stopQuickRecording();
    return;
  }

  quickRecordingBlob.value = null;
  quickRecordingTitle.value = "";
  toggleQuickRecording(selectedThumb.value, targetVoiceForThumb(selectedThumb.value));
}

function toggleTrimEditor() {
  recordingTrimOpen.value = !recordingTrimOpen.value;
}

function clampTrimValue(value, min = 0, max = 100) {
  return Math.max(min, Math.min(max, Math.round(Number(value) || 0)));
}

function updateTrimStart(value) {
  trimStart.value = clampTrimValue(value, 0, Math.max(0, Number(trimEnd.value) - 1));
}

function updateTrimEnd(value) {
  trimEnd.value = clampTrimValue(value, Math.min(100, Number(trimStart.value) + 1), 100);
}

function trimPercentFromEvent(event) {
  const rect = event.currentTarget.getBoundingClientRect();
  return clampTrimValue(((event.clientX - rect.left) / rect.width) * 100);
}

function setNearestTrimHandle(event) {
  const value = trimPercentFromEvent(event);
  const startDistance = Math.abs(value - Number(trimStart.value));
  const endDistance = Math.abs(value - Number(trimEnd.value));

  if (startDistance <= endDistance) {
    updateTrimStart(value);
    trimDragging.value = "start";
  } else {
    updateTrimEnd(value);
    trimDragging.value = "end";
  }

  window.addEventListener("pointermove", updateTrimFromEvent);
  window.addEventListener("pointerup", stopTrimDrag, {once: true});
}

function beginTrimDrag(handle, event) {
  trimDragging.value = handle;
  updateTrimFromEvent(event);
  window.addEventListener("pointermove", updateTrimFromEvent);
  window.addEventListener("pointerup", stopTrimDrag, {once: true});
}

function updateTrimFromEvent(event) {
  if (!trimDragging.value) return;
  const track = document.querySelector(".trim-editor__wave");
  if (!track) return;
  const rect = track.getBoundingClientRect();
  const value = clampTrimValue(((event.clientX - rect.left) / rect.width) * 100);

  if (trimDragging.value === "start") {
    updateTrimStart(value);
  } else {
    updateTrimEnd(value);
  }
}

function stopTrimDrag() {
  trimDragging.value = null;
  window.removeEventListener("pointermove", updateTrimFromEvent);
}

function resetTrimSelection() {
  trimStart.value = 0;
  trimEnd.value = 100;
}

function selectedVoiceAudioUrl() {
  if (!selectedVoice.value || selectedVoice.value.isDraft) return "";
  return selectedVoice.value.previewUrl || buildApiUrl(`/api/audios/${selectedVoice.value.id}/content`);
}

function stopTrimPreview() {
  if (trimPreviewAudio) {
    trimPreviewAudio.pause();
    trimPreviewAudio = null;
  }
  trimPreviewPlaying.value = false;
}

function previewTrimSelection() {
  const src = selectedVoiceAudioUrl();
  if (!src) return;

  if (trimPreviewPlaying.value) {
    stopTrimPreview();
    return;
  }

  stopTrimPreview();
  trimPreviewAudio = new Audio(src);
  trimPreviewAudio.addEventListener("loadedmetadata", () => {
    const duration = Number.isFinite(trimPreviewAudio.duration) ? trimPreviewAudio.duration : 0;
    const startSeconds = duration * (Number(trimStart.value) / 100);
    const endSeconds = duration * (Number(trimEnd.value) / 100);
    trimPreviewAudio.currentTime = startSeconds;
    trimPreviewAudio.playbackRate = Number(recordingSpeed.value) / 100;
    trimPreviewAudio.volume = Math.max(0, Math.min(1, Number(recordingVolume.value) / 100));
    trimPreviewAudio.play().then(() => {
      trimPreviewPlaying.value = true;
    }).catch(() => {
      trimPreviewPlaying.value = false;
    });

    trimPreviewAudio.addEventListener("timeupdate", () => {
      if (trimPreviewAudio && trimPreviewAudio.currentTime >= endSeconds) {
        stopTrimPreview();
      }
    });
  });
  trimPreviewAudio.addEventListener("ended", stopTrimPreview);
}

function applyTrimSelection() {
  if (!selectedVoice.value || !selectedThumb.value) return;
  stopTrimPreview();
  updateVoiceFields(selectedThumb.value, selectedVoice.value, {
    trimStart: trimStart.value,
    trimEnd: trimEnd.value,
  });
  recordingTrimOpen.value = false;
  toast.success(`Trim applied: ${trimStart.value}% → ${trimEnd.value}%.`);
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
  },
  onEndedAll: () => {
    activeAudioId.value = null;
    toast.success("Automatic playback finished.");
  },
});

function toggleAutoContinue() {
  autoplay.toggleAutoContinue();
  toast.info(
      autoplay.autoContinue.value
          ? "Auto-continue enabled."
          : "Auto-continue disabled."
  );
}

function toggleLoopScenario() {
  autoplay.toggleLoopScenario();
  toast.info(
      autoplay.loopScenario.value
          ? "Loop scenario enabled."
          : "Loop scenario disabled."
  );
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

async function playSelectedAudioPreview() {
  await toggleSelectedAudioPlayback();
}

const playerCurrentThumb = computed(() => {
  const item = autoplay.currentItem.value;
  if (!item) return selectedThumb.value || sortedThumbnails.value[0] || null;
  return sortedThumbnails.value.find(t => String(t.id) === String(item.thumbnailId)) ?? null;
});

const playerCurrentSceneNumber = computed(() => {
  const thumb = playerCurrentThumb.value;
  if (!thumb) return 1;
  const idx = sortedThumbnails.value.findIndex(t => String(t.id) === String(thumb.id));
  return idx >= 0 ? idx + 1 : 1;
});

function openPlayer() {
  playerOpen.value = true;
  if (!autoplay.isPlaying.value && !autoplay.isPaused.value) {
    playAllFromContext();
  }
}

function closePlayer() {
  playerOpen.value = false;
}

function playerJumpToScene(thumb) {
  const idx = playbackQueue.value.findIndex(q => String(q.thumbnailId) === String(thumb.id));
  if (idx >= 0) autoplay.playFromIndex(idx);
}

function playerTogglePlay() {
  if (autoplay.isPlaying.value) {
    autoplay.pause();
  } else if (autoplay.isPaused.value) {
    autoplay.resume();
  } else {
    playAllFromContext();
  }
}

function onPlayerKey(e) {
  if (e.key === "Escape") { closePlayer(); return; }
  if (e.key === " " || e.code === "Space") { e.preventDefault(); playerTogglePlay(); return; }
  if (e.key === "ArrowRight") { e.preventDefault(); autoplay.next(); return; }
  if (e.key === "ArrowLeft") { e.preventDefault(); autoplay.previous(); }
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

  const selectedVoices = selectedThumb.value?.id ? (audioMap.value[selectedThumb.value.id] || []) : [];
  const selectedVoiceStillExists = selectedVoices.some((audio) => String(audio.id) === String(selectedVoiceId.value ?? ""));
  if (!selectedVoiceStillExists) {
    const firstVoice = selectedVoices[0] ?? null;
    selectedVoiceId.value = firstVoice?.id ?? null;
    selectedSpeaker.value = firstVoice ? speakerForAudio(firstVoice, 0) : nextSpeakerForThumb(selectedThumb.value);
  }
}

async function loadAll() {
  loading.value = true;
  error.value = "";
  studioSandboxMode.value = false;

  try {
    if (String(props.id).startsWith("emergency-")) {
      applyUnclaimedDraftAudio();
      return;
    }

    await loadMe();
    await loadScenario();

    isOwner.value =
        !!currentUser.value &&
        currentUser.value.username === scenario.value.authorUsername;
    await Promise.all([loadThumbs(), checkExistingRequest()]);
    applyUnclaimedDraftAudio();
  } catch (e) {
    if (studioFrontendOnly) {
      useStudioSandbox(e.message);
      applyUnclaimedDraftAudio();
    } else {
      error.value = e.message;
    }
  } finally {
    loading.value = false;
  }
}

async function uploadImage() {
  uploadError.value = "";
  uploadSuccess.value = "";

  const entries = uploadFiles.value.length ? uploadFiles.value : (uploadFile.value ? [{file: uploadFile.value, previewUrl: uploadPreviewUrl.value, title: uploadTitle.value}] : []);
  if (!entries.length) { uploadError.value = "No image selected."; return; }

  uploadingProgress.value = 0;

  try {
    if (studioFrontendOnly) {
      const added = [];
      entries.forEach((entry, i) => {
        const nextIndex = thumbnails.value.length + added.length + 1;
        added.push({
          id: `local-${Date.now()}-${i}`,
          idx: nextIndex,
          title: entry.title || `Scene ${nextIndex}`,
          gridColumnSpan: 4,
          gridRowSpan: 3,
          previewUrl: URL.createObjectURL(entry.file),
        });
        uploadingProgress.value = Math.round(((i + 1) / entries.length) * 100);
      });
      thumbnails.value = [...thumbnails.value, ...added];
      selectedThumb.value = added[added.length - 1];
      toast.success(`${added.length} scene${added.length > 1 ? "s" : ""} added.`);
      closeUploadDialog();
      return;
    }

    for (let i = 0; i < entries.length; i++) {
      const entry = entries[i];
      const fd = new FormData();
      fd.append("scenarioId", String(scenario.value.id));
      fd.append("title", entry.title || "");
      fd.append("image", entry.file);
      await uploadScenarioThumbnail(props.id, fd);
      uploadingProgress.value = Math.round(((i + 1) / entries.length) * 100);
    }

    toast.success(`${entries.length} scene${entries.length > 1 ? "s" : ""} uploaded.`);
    await loadThumbs();
    closeUploadDialog();
  } catch (e) {
    uploadError.value = e.message;
    toast.error(e.message || "Upload failed.");
  } finally {
    uploadingProgress.value = 0;
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
    await updateScenarioStoryboard(props.id, {layoutMode: "PRESET", preset, columns: cols});
    applyLocalStoryboardState({layoutMode: "PRESET", preset, columns: cols});
    toast.success(`${preset.replace("_", " ")} preset applied.`);
  } catch (e) {
    toast.error(e.message || "Failed to apply preset.");
  } finally {
    savingStoryboard.value = false;
  }
}

async function saveSelectedThumbnailLayout() {
  if (!selectedThumb.value || savingLayout.value) return;

  savingLayout.value = true;
  try {
    const nextLayout = {
      gridColumn: nullableInt(selectedLayoutForm.value.gridColumn),
      gridRow: nullableInt(selectedLayoutForm.value.gridRow),
      gridColumnSpan: nullableInt(selectedLayoutForm.value.gridColumnSpan) ?? 1,
      gridRowSpan: nullableInt(selectedLayoutForm.value.gridRowSpan) ?? 1,
    };

    await updateThumbnailLayout(selectedThumb.value.id, nextLayout);
    applyLocalStoryboardState({layoutMode: "CUSTOM"});

    thumbnails.value = thumbnails.value.map((thumb) => {
      if (String(thumb.id) !== String(selectedThumb.value.id)) return thumb;
      return {...thumb, ...nextLayout};
    });

    const updated = thumbnails.value.find((thumb) => String(thumb.id) === String(selectedThumb.value.id));
    if (updated) selectedThumb.value = updated;

    toast.success("Layout saved.");
  } catch (e) {
    toast.error(e.message || "Failed to save layout.");
  } finally {
    savingLayout.value = false;
  }
}

async function beginThumbnailResize({thumb, direction, event}) {
  if (!thumb?.id || !event) return;

  const gridEl = event.currentTarget?.closest?.(".storyboard-grid");
  if (!gridEl) return;

  event.preventDefault();
  event.stopPropagation();

  applyLocalStoryboardState({layoutMode: "CUSTOM"});

  selectedThumb.value = thumb;
  resizingThumbnailId.value = thumb.id;

  const gap = 8;
  const colWidth = (gridEl.clientWidth - gap * 11) / 12 + gap;
  const rowHeight = 64;
  const startX = event.clientX;
  const startY = event.clientY;
  const savedColumnSpan = safeNumber(thumb.gridColumnSpan, 0);
  const savedRowSpan = safeNumber(thumb.gridRowSpan, 0);
  const startColumnSpan = clamp(savedColumnSpan > 1 ? savedColumnSpan : (thumb._layout?.columnSpan ?? 4), 2, 12);
  const startRowSpan = clamp(savedRowSpan > 1 ? savedRowSpan : (thumb._layout?.rowSpan ?? 3), 2, 10);

  let latestColumnSpan = startColumnSpan;
  let latestRowSpan = startRowSpan;

  function updateLocalLayout(columnSpan, rowSpan) {
    thumbnails.value = thumbnails.value.map((item) => {
      if (String(item.id) !== String(thumb.id)) return item;
      return {
        ...item,
        gridColumnSpan: columnSpan,
        gridRowSpan: rowSpan,
      };
    });

    if (selectedThumb.value && String(selectedThumb.value.id) === String(thumb.id)) {
      selectedThumb.value = {
        ...selectedThumb.value,
        gridColumnSpan: columnSpan,
        gridRowSpan: rowSpan,
      };
    }
  }

  function onMove(moveEvent) {
    let nextColumnSpan = startColumnSpan;
    let nextRowSpan = startRowSpan;

    if (direction === "right" || direction === "corner") {
      nextColumnSpan = clamp(startColumnSpan + Math.round((moveEvent.clientX - startX) / colWidth), 2, 12);
    }

    if (direction === "bottom" || direction === "corner") {
      nextRowSpan = clamp(startRowSpan + Math.round((moveEvent.clientY - startY) / rowHeight), 2, 10);
    }

    if (nextColumnSpan !== latestColumnSpan || nextRowSpan !== latestRowSpan) {
      latestColumnSpan = nextColumnSpan;
      latestRowSpan = nextRowSpan;
      updateLocalLayout(latestColumnSpan, latestRowSpan);
    }
  }

  async function onUp() {
    document.removeEventListener("mousemove", onMove);
    document.removeEventListener("mouseup", onUp);
    resizingThumbnailId.value = null;

    const updated = thumbnails.value.find((item) => String(item.id) === String(thumb.id));
    if (updated) {
      selectedThumb.value = updated;
      selectedLayoutForm.value = {
        gridColumn: updated.gridColumn ?? "",
        gridRow: updated.gridRow ?? "",
        gridColumnSpan: updated.gridColumnSpan ?? latestColumnSpan,
        gridRowSpan: updated.gridRowSpan ?? latestRowSpan,
      };
    }
  }

  document.addEventListener("mousemove", onMove);
  document.addEventListener("mouseup", onUp);
}

async function refreshAudios() {
  await loadThumbs();
}


function orderedIdsFromThumbnails(items = sortedThumbnails.value) {
  return items.map((thumb) => String(thumb.id));
}

function thumbnailsFromIdOrder(ids, fallback = sortedThumbnails.value) {
  const byId = new Map(fallback.map((thumb) => [String(thumb.id), thumb]));
  return ids.map((id) => byId.get(String(id))).filter(Boolean);
}

function thumbnailIdFromPoint(clientX, clientY) {
  const element = document.elementFromPoint?.(clientX, clientY);
  return element?.closest?.("[data-thumbnail-id]")?.dataset?.thumbnailId ?? null;
}

function orderedTileRects(excludeId = null) {
  const grid = document.querySelector(".storyboard-grid");
  if (!grid) return [];

  return Array.from(grid.querySelectorAll("[data-thumbnail-id]"))
      .map((el) => ({
        id: el.dataset.thumbnailId,
        rect: el.getBoundingClientRect(),
      }))
      .filter((item) =>
          item.id &&
          String(item.id) !== String(excludeId ?? "") &&
          item.rect.width > 0 &&
          item.rect.height > 0
      )
      .sort((a, b) => {
        const sameRow = Math.abs(a.rect.top - b.rect.top) < 24;
        return sameRow ? a.rect.left - b.rect.left : a.rect.top - b.rect.top;
      });
}

function insertionIndexFromPoint(clientX, clientY, fromId) {
  const tiles = orderedTileRects(fromId);
  if (!tiles.length) return 0;

  const rows = [];
  for (const tile of tiles) {
    const row = rows.find((candidate) => Math.abs(candidate.top - tile.rect.top) < 24);
    if (row) {
      row.items.push(tile);
      row.top = Math.min(row.top, tile.rect.top);
      row.bottom = Math.max(row.bottom, tile.rect.bottom);
    } else {
      rows.push({
        top: tile.rect.top,
        bottom: tile.rect.bottom,
        items: [tile],
      });
    }
  }

  rows.forEach((row) => {
    row.items.sort((a, b) => a.rect.left - b.rect.left);
  });

  const rowDistances = rows.map((row, rowIndex) => {
    const distance = clientY < row.top
        ? row.top - clientY
        : clientY > row.bottom
            ? clientY - row.bottom
            : 0;
    return {row, rowIndex, distance};
  });

  rowDistances.sort((a, b) => a.distance - b.distance || a.row.top - b.row.top);
  const selectedRow = rowDistances[0].row;
  const beforeRowCount = rows
      .filter((row) => row.top < selectedRow.top)
      .reduce((count, row) => count + row.items.length, 0);

  for (let i = 0; i < selectedRow.items.length; i += 1) {
    const tile = selectedRow.items[i];
    if (clientX < tile.rect.left + tile.rect.width / 2) {
      return beforeRowCount + i;
    }
  }

  return beforeRowCount + selectedRow.items.length;
}

function moveIdInOrder(ids, fromId, targetId) {
  if (!fromId || !targetId || String(fromId) === String(targetId)) return ids;

  const nextIds = [...ids];
  const fromIdx = nextIds.findIndex((id) => String(id) === String(fromId));
  const toIdx = nextIds.findIndex((id) => String(id) === String(targetId));
  if (fromIdx < 0 || toIdx < 0 || fromIdx === toIdx) return ids;

  const [moved] = nextIds.splice(fromIdx, 1);
  nextIds.splice(toIdx, 0, moved);
  return nextIds;
}

function moveIdToIndex(ids, fromId, targetIndex) {
  if (!fromId) return ids;

  const normalizedFromId = String(fromId);
  const withoutDragged = ids.filter((id) => String(id) !== normalizedFromId);
  if (withoutDragged.length === ids.length) return ids;

  const nextIds = [...withoutDragged];
  const boundedIndex = clamp(targetIndex, 0, nextIds.length);
  nextIds.splice(boundedIndex, 0, normalizedFromId);
  return nextIds;
}

function isSameOrder(a, b) {
  return a.length === b.length && a.every((id, index) => String(id) === String(b[index]));
}

function startTileDragPreviewOrder(fromId) {
  const ids = orderedIdsFromThumbnails();
  tileDragPreviewIds.value = ids.includes(String(fromId)) ? ids : [];
}

function previewDraggedThumbAtIndex(targetIndex) {
  if (!tilePointerDrag?.fromId) return;

  const currentIds = tileDragPreviewIds.value.length
      ? tileDragPreviewIds.value
      : orderedIdsFromThumbnails();
  const nextIds = moveIdToIndex(currentIds, tilePointerDrag.fromId, targetIndex);

  if (!isSameOrder(currentIds, nextIds)) {
    tileDragPreviewIds.value = nextIds;
    if (navigator.vibrate) {
      navigator.vibrate(6);
    }
  }
}

function updateTileDragGhost(event) {
  if (!tileDragGhost.value) return;
  tileDragGhost.value = {
    ...tileDragGhost.value,
    x: event.clientX,
    y: event.clientY,
  };
}

function autoScrollDuringTileDrag(clientY) {
  const edge = 78;
  const maxSpeed = 18;
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
  if (!viewportHeight) return;

  if (clientY < edge) {
    window.scrollBy({top: -maxSpeed, behavior: "auto"});
  } else if (viewportHeight - clientY < edge) {
    window.scrollBy({top: maxSpeed, behavior: "auto"});
  }
}

async function moveDraggedThumbToTarget(fromId, targetId) {
  if (!fromId || !targetId || String(fromId) === String(targetId)) return;

  const sorted = [...sortedThumbnails.value];
  const fromIdx = sorted.findIndex((thumb) => String(thumb.id) === String(fromId));
  const toIdx = sorted.findIndex((thumb) => String(thumb.id) === String(targetId));
  if (fromIdx < 0 || toIdx < 0) return;

  const nextOrdered = moveThumbInOrder(sorted, fromIdx, toIdx);
  if (nextOrdered) {
    await applyThumbnailOrder(nextOrdered);
  }
}

function onTileDragStart(e, thumb) {
  if (!e?.dataTransfer || !thumb?.id) return;

  tileDragId.value = thumb.id;
  e.dataTransfer.effectAllowed = "move";
  e.dataTransfer.setData("text/plain", String(thumb.id));
}

function onTileDragOver(e, thumb) {
  if (!tileDragId.value) return;

  e.preventDefault();
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = "move";
  }
  if (String(thumb.id) !== String(tileDragId.value)) {
    tileDragOverId.value = thumb.id;
  }
}

function onTileDragLeave(e) {
  // Only clear if leaving the tile entirely (not entering a child)
  if (!e.currentTarget.contains(e.relatedTarget)) {
    tileDragOverId.value = null;
  }
}

function onTileDragEnd() {
  const el = document.querySelector(`[data-thumbnail-id="${tileDragId.value}"]`);
  if (el) el.style.opacity = "";
  tileDragId.value = null;
  tileDragOverId.value = null;
}

async function onTileDrop(e, targetThumb) {
  e.preventDefault();
  const fromId = tileDragId.value;
  if (!fromId || String(fromId) === String(targetThumb.id)) {
    onTileDragEnd();
    return;
  }

  onTileDragEnd();
  await moveDraggedThumbToTarget(fromId, targetThumb.id);
}

function cleanupTilePointerDrag() {
  window.removeEventListener("pointermove", onTilePointerDragMove);
  window.removeEventListener("pointerup", onTilePointerDragEnd);
  window.removeEventListener("pointercancel", onTilePointerDragCancel);

  if (tilePointerDrag?.handleEl && tilePointerDrag?.pointerId != null) {
    try {
      tilePointerDrag.handleEl.releasePointerCapture?.(tilePointerDrag.pointerId);
    } catch {
      // Some browsers release capture automatically before pointercancel.
    }
  }

  document.body.classList.remove("storyboard-drag-active");
  tilePointerDrag = null;
  tileDragId.value = null;
  tileDragOverId.value = null;
  tileDragPreviewIds.value = [];
  tileDragGhost.value = null;
}

function onTilePointerReorderStart(event, thumb) {
  if (!thumb?.id) return;
  if (event.pointerType === "mouse" && event.button !== 0) return;

  event.preventDefault();
  event.stopPropagation();

  if (tilePointerDrag) {
    cleanupTilePointerDrag();
  }

  const tileEl = event.currentTarget?.closest?.("[data-thumbnail-id]");
  const rect = tileEl?.getBoundingClientRect?.();
  const ghostWidth = rect?.width || 180;
  const ghostHeight = rect?.height || 160;

  tilePointerDrag = {
    fromId: thumb.id,
    pointerId: event.pointerId,
    handleEl: event.currentTarget,
  };

  startTileDragPreviewOrder(thumb.id);
  tileDragGhost.value = {
    id: thumb.id,
    title: thumb.title || `Scene ${thumb.idx ?? thumb.id}`,
    imageUrl: thumbnailContentUrl(thumb),
    x: event.clientX,
    y: event.clientY,
    width: ghostWidth,
    height: ghostHeight,
    offsetX: rect ? clamp(event.clientX - rect.left, 20, Math.max(20, ghostWidth - 20)) : Math.min(60, ghostWidth / 2),
    offsetY: rect ? clamp(event.clientY - rect.top, 20, Math.max(20, ghostHeight - 20)) : Math.min(60, ghostHeight / 2),
  };

  tileDragId.value = thumb.id;
  tileDragOverId.value = null;
  selectedThumb.value = thumb;
  document.body.classList.add("storyboard-drag-active");

  if (navigator.vibrate) {
    navigator.vibrate(10);
  }

  event.currentTarget?.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", onTilePointerDragMove, {passive: false});
  window.addEventListener("pointerup", onTilePointerDragEnd, {once: true});
  window.addEventListener("pointercancel", onTilePointerDragCancel, {once: true});
}

function onTilePointerDragMove(event) {
  if (!tilePointerDrag || event.pointerId !== tilePointerDrag.pointerId) return;

  event.preventDefault();

  updateTileDragGhost(event);
  autoScrollDuringTileDrag(event.clientY);

  const targetIndex = insertionIndexFromPoint(event.clientX, event.clientY, tilePointerDrag.fromId);
  previewDraggedThumbAtIndex(targetIndex);

  const targetId = thumbnailIdFromPoint(event.clientX, event.clientY);
  tileDragOverId.value =
      targetId && String(targetId) !== String(tilePointerDrag.fromId)
          ? targetId
          : null;
}

async function onTilePointerDragEnd(event) {
  if (!tilePointerDrag || event.pointerId !== tilePointerDrag.pointerId) return;

  event.preventDefault();
  updateTileDragGhost(event);

  const fromId = tilePointerDrag.fromId;
  const endTargetId = tileDragOverId.value || thumbnailIdFromPoint(event.clientX, event.clientY);
  let previewIds = tileDragPreviewIds.value.length
      ? [...tileDragPreviewIds.value]
      : orderedIdsFromThumbnails();
  const originalIds = orderedIdsFromThumbnails();

  if (isSameOrder(originalIds, previewIds) && endTargetId && String(endTargetId) !== String(fromId)) {
    previewIds = moveIdInOrder(originalIds, fromId, endTargetId);
  }

  if (isSameOrder(originalIds, previewIds)) {
    previewIds = moveIdToIndex(
        originalIds,
        fromId,
        insertionIndexFromPoint(event.clientX, event.clientY, fromId)
    );
  }

  const nextOrdered = thumbnailsFromIdOrder(previewIds);
  cleanupTilePointerDrag();

  if (!isSameOrder(originalIds, previewIds) && nextOrdered.length === sortedThumbnails.value.length) {
    await applyThumbnailOrder(nextOrdered);
  }
}

function onTilePointerDragCancel(event) {
  if (tilePointerDrag && event.pointerId !== tilePointerDrag.pointerId) return;
  cleanupTilePointerDrag();
}

function isTileDragging(thumb)  { return String(thumb.id) === String(tileDragId.value);   }
function isTileDragTarget(thumb){ return String(thumb.id) === String(tileDragOverId.value); }

const storyboardItems = computed(() => {
  return buildStoryboardItems({
    thumbnails: visualThumbnails.value,
    layoutMode: storyboardForm.value.layoutMode ?? "PRESET",
    preset: storyboardForm.value.preset ?? "GRID_3",
    columns: storyboardColumns.value,
  });
});

function closeQuickRecordingDialog() {
  quickRecordingDialogOpen.value = false;
  quickRecordingTitle.value = "";
  quickRecordingBlob.value = null;
  quickRecordingMimeType.value = "audio/webm";
  quickRecordingError.value = "";
  quickRecordingTargetThumbId.value = null;
  quickRecordingVoiceId.value = null;
  quickRecordingCreatedDraft.value = false;

  if (quickRecordingPreviewUrl.value) {
    URL.revokeObjectURL(quickRecordingPreviewUrl.value);
    quickRecordingPreviewUrl.value = "";
  }
}

function getSupportedMimeType() {
  const candidates = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/ogg;codecs=opus",
    "audio/ogg",
    "audio/mp4",
  ];
  return candidates.find(t => {
    try { return MediaRecorder.isTypeSupported(t); } catch { return false; }
  }) ?? "";
}

async function ensureMediaStream() {
  if (quickMediaStream?.active) return;
  quickMediaStream = await navigator.mediaDevices.getUserMedia({audio: true});
}

async function startQuickRecording(thumb, requestedVoice = null) {
  quickRecordingError.value = "";
  quickRecordingBlob.value = null;

  const {voice, created} = ensureVoiceForRecording(thumb, requestedVoice);
  if (!voice) return;

  const speaker = speakerForVoice(voice, thumb) || selectedSpeaker.value;
  quickRecordingCreatedDraft.value = created;
  quickRecordingTitle.value = voiceDisplayTitle(voice, thumb) || `Voice ${speaker}`;

  try {
    await ensureMediaStream();

    // Always create a fresh MediaRecorder each session to avoid state confusion
    quickRecordingChunks = [];
    const mimeType = getSupportedMimeType();
    quickMediaRecorder = mimeType
        ? new MediaRecorder(quickMediaStream, {mimeType})
        : new MediaRecorder(quickMediaStream);

    quickMediaRecorder.ondataavailable = (event) => {
      if (event.data?.size > 0) quickRecordingChunks.push(event.data);
    };

    quickMediaRecorder.onstop = () => {
      const type = quickMediaRecorder.mimeType || "audio/webm";
      quickRecordingBlob.value = new Blob(quickRecordingChunks, {type});
      quickRecordingMimeType.value = type;
      quickRecordingChunks = [];
      quickRecordingDialogOpen.value = true;
      quickRecordingThumbId.value = null;
    };

    quickRecordingThumbId.value = thumb.id;
    quickRecordingTargetThumbId.value = thumb.id;
    quickRecordingVoiceId.value = voice.id;
    quickMediaRecorder.start();
    toast.info(`Voice ${speaker} recording started.`);
  } catch (e) {
    if (created && voice?.isDraft) {
      const voices = audioMap.value[thumb.id] || [];
      audioMap.value = {
        ...audioMap.value,
        [thumb.id]: voices.filter((audio) => String(audio.id) !== String(voice.id)),
      };
      const fallback = audioMap.value[thumb.id]?.[0] ?? null;
      selectedVoiceId.value = fallback?.id ?? null;
      selectedSpeaker.value = fallback ? speakerForAudio(fallback, 0) : nextSpeakerForThumb(thumb);
    }
    quickRecordingThumbId.value = null;
    quickRecordingTargetThumbId.value = null;
    quickRecordingVoiceId.value = null;
    quickRecordingCreatedDraft.value = false;
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

async function toggleQuickRecording(thumb, requestedVoice = null) {
  if (String(selectedThumb.value?.id ?? "") !== String(thumb?.id ?? "")) {
    selectThumb(thumb);
  }

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

  await startQuickRecording(thumb, requestedVoice ?? targetVoiceForThumb(thumb));
}

async function confirmQuickRecordingUpload() {
  quickRecordingError.value = "";

  try {
    const targetThumb = thumbnails.value.find(
        (thumb) => String(thumb.id) === String(quickRecordingTargetThumbId.value ?? selectedThumb.value?.id ?? "")
    ) ?? selectedThumb.value;
    if (!targetThumb?.id) throw new Error("No thumbnail selected.");
    if (!quickRecordingBlob.value) throw new Error("No quick recording available.");

    quickRecordingUploading.value = true;

    const extension = quickRecordingMimeType.value.includes("ogg")
        ? "ogg"
        : quickRecordingMimeType.value.includes("mp4") || quickRecordingMimeType.value.includes("aac")
            ? "mp4"
            : "webm";

    const fileName = `quick-recording.${extension}`;

    const targetVoice = (audioMap.value[targetThumb.id] || []).find(
        (audio) => String(audio.id) === String(quickRecordingVoiceId.value ?? "")
    ) ?? selectedVoice.value;
    const voiceTitle = quickRecordingTitle.value || voiceDisplayTitle(targetVoice, targetThumb) || `Voice ${selectedSpeaker.value}`;

    if (studioFrontendOnly) {
      if (String(selectedThumb.value?.id ?? "") !== String(targetThumb.id)) {
        selectedThumb.value = targetThumb;
      }
      selectedVoiceId.value = quickRecordingVoiceId.value ?? selectedVoiceId.value;

      addLocalAudioClip(targetThumb, {
        title: voiceTitle,
        previewUrl: URL.createObjectURL(quickRecordingBlob.value),
        speaker: targetVoice?.speaker || selectedSpeaker.value,
      });

      toast.success("Recording added.");
      closeQuickRecordingDialog();
      return;
    }

    await uploadVoiceAudioFile(targetThumb, targetVoice, quickRecordingBlob.value, voiceTitle, fileName);
    toast.success("Quick recording uploaded successfully.");
    closeQuickRecordingDialog();

  } catch (e) {
    quickRecordingError.value = e.message || "Failed to upload quick recording.";
    toast.error(quickRecordingError.value);
  } finally {
    quickRecordingUploading.value = false;
  }
}

function discardQuickRecording() {
  const thumbId = quickRecordingTargetThumbId.value;
  const voiceId = quickRecordingVoiceId.value;
  const targetThumb = thumbnails.value.find((thumb) => String(thumb.id) === String(thumbId ?? "")) ?? selectedThumb.value;
  if (thumbId && voiceId && quickRecordingCreatedDraft.value) {
    const voices = audioMap.value[thumbId] || [];
    const target = voices.find(a => String(a.id) === String(voiceId));
    if (target?.isDraft) {
      audioMap.value = {
        ...audioMap.value,
        [thumbId]: voices.filter(a => String(a.id) !== String(voiceId)),
      };
      const remaining = audioMap.value[thumbId] || [];
      const fallback = remaining.find(a => !a.isDraft) ?? remaining[0] ?? null;
      selectedVoiceId.value = fallback?.id ?? null;
      selectedSpeaker.value = fallback ? speakerForAudio(fallback, Math.max(0, remaining.indexOf(fallback))) : nextSpeakerForThumb(targetThumb);
    }
  } else if (thumbId && voiceId) {
    const target = (audioMap.value[thumbId] || []).find(a => String(a.id) === String(voiceId));
    if (target && targetThumb) selectVoice(target, targetThumb);
  }
  toast.info("Quick recording discarded.");
  closeQuickRecordingDialog();
}

const quickRecordingPreviewUrl = ref("");

watch(quickRecordingBlob, (blob) => {
  if (quickRecordingPreviewUrl.value) {
    URL.revokeObjectURL(quickRecordingPreviewUrl.value);
    quickRecordingPreviewUrl.value = "";
  }

  if (blob) {
    quickRecordingPreviewUrl.value = URL.createObjectURL(blob);
  }
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

            <!-- Close -->
            <button type="button" class="si-close" aria-label="Close" @click="closeInfoDialog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 6 6 18M6 6l12 12"/>
              </svg>
            </button>

            <!-- Title + status -->
            <div class="si-hero">
              <span class="si-status" :class="isPublished ? 'si-status--pub' : 'si-status--draft'">
                <span class="si-status__dot"></span>
                {{ isPublished ? "Published" : "Draft" }}
              </span>
              <h2 id="scenario-info-title" class="si-title">{{ scenario.title || "Untitled scenario" }}</h2>
            </div>

            <!-- Stats strip -->
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
                <span class="si-stat__num si-stat__num--lang">{{ languageName || "—" }}</span>
                <span class="si-stat__lbl">language</span>
              </div>
              <div class="si-stat__div"></div>
              <div class="si-stat">
                <span class="si-stat__num">{{ scenario.authorUsername || "—" }}</span>
                <span class="si-stat__lbl">author</span>
              </div>
            </div>

            <!-- Tags -->
            <div v-if="scenario.tags?.length" class="si-tags">
              <span v-for="tag in scenario.tags" :key="tag" class="si-tag">#{{ tag }}</span>
            </div>

            <!-- Description -->
            <div class="si-desc">
              <p class="si-desc__label">Description</p>
              <p class="si-desc__text">{{ scenario.description?.trim() || "No description." }}</p>
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
                      <p class="ud-eyebrow">New scene</p>
                      <h2 id="thumbnail-upload-title" class="ud-title">Add a scene image</h2>
                    </div>
                    <button type="button" class="ud-close" aria-label="Close" @click="closeUploadDialog">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 6 6 18M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>

                  <label
                      v-if="!uploadFiles.length"
                      class="ud-dropzone"
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
                    <p class="ud-dropzone__label">Drop images, SVG or XML here</p>
                    <p class="ud-dropzone__sub">or click to browse · PNG, JPG, WebP, SVG, XML · multiple files</p>
                    <input type="file" accept="image/*,.svg,.xml,text/xml,application/xml" multiple class="ud-file-input" @change="onImageChange"/>
                  </label>

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
                          <small>{{ isPublished ? "Visible to the community" : "Only you can see this" }}</small>
                        </div>
                      </div>
                      <button
                          v-if="isOwner && !isPublished"
                          type="button"
                          class="ss-pub__btn"
                          :disabled="publishing"
                          @click="publishCurrentScenario"
                      >
                        {{ publishing ? "Publishing…" : "Publish →" }}
                      </button>
                    </div>
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
                      <h2 id="quick-recording-title" class="qr-title">Review & add voice</h2>
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
                        Voice {{ selectedSpeaker }}
                      </span>
                    </div>
                  </div>

                  <div class="qr-player">
                    <div class="qr-wave" aria-hidden="true">
                      <span v-for="i in 24" :key="i"></span>
                    </div>
                    <audio
                        v-if="quickRecordingPreviewUrl"
                        ref="qrAudio"
                        class="qr-audio"
                        controls
                        autoplay
                        :src="quickRecordingPreviewUrl"
                    />
                  </div>

                  <div class="qr-field">
                    <label class="qr-label" for="qr-title-input">Voice name</label>
                    <input
                        id="qr-title-input"
                        v-model="quickRecordingTitle"
                        class="qr-input"
                        placeholder="e.g. Voice A, Speaker 1…"
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

              <div v-if="storyboardItems.length" class="vg-root">
                <div class="vg-nav">
                  <div class="vg-brand-block">
                    <div class="vg-brand studio-breadcrumb">
                      <span class="vg-brand-dot"></span>
                      <RouterLink to="/scenarios" class="vg-brand-back">My scenarios</RouterLink>
                      <strong>/ {{ scenario.title || "New scenario" }}</strong>
                    </div>
                    <Transition name="vg-kicker" mode="out-in">
                      <span :key="storyboardView" class="vg-kicker">
                        {{ storyboardView === "studio" ? "Studio" : "Storyboard" }}
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
                        type="button"
                        class="vg-tab"
                        :class="{ active: storyboardView === 'studio' }"
                        @click="setStoryboardView('studio')"
                    >
                      Studio
                    </button>
                  </div>

                  <div class="vg-actions">
                    <button
                        v-if="isOwner"
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
                        v-if="isOwner"
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
                        :class="{ 'vg-play-btn--playing': autoplay.isPlaying.value || autoplay.isPaused.value }"
                        :title="playbackQueue.length ? 'Open scenario player' : 'No audio clips yet — record some to enable the player'"
                        :disabled="!playbackQueue.length"
                        @click="openPlayer"
                    >
                      <svg viewBox="0 0 24 24" fill="currentColor" width="13" height="13">
                        <polygon points="5 3 19 12 5 21 5 3"/>
                      </svg>
                      Play
                    </button>
                    <button
                        v-if="isOwner && !isPublished"
                        type="button"
                        class="vg-pub"
                        :disabled="publishing"
                        @click="publishCurrentScenario"
                    >
                      {{ publishing ? "Publishing…" : "Publish →" }}
                    </button>
                    <span v-if="isPublished" class="vg-status vg-status--pub">Published</span>
                    <span v-else-if="!isOwner" class="vg-status">Read-only</span>
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
                      <span class="bd-tlbl">Layout</span>

                      <div class="bd-presets">
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
                            ? "Drag handles to resize · " + storyboardSummary.audioCount + " audio · " + storyboardSummary.markerCount + " marker(s)"
                            : storyboardSummary.audioCount + " audio · " + storyboardSummary.markerCount + " marker(s)"
                        }}
                      </span>
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
                            :can-record="studioFrontendOnly || isOwner"
                            :can-resize="studioFrontendOnly || isOwner"
                            :can-delete="studioFrontendOnly || isOwner"
                            :can-reorder="studioFrontendOnly || isOwner"
                            :active-audio-id="activeAudioId"
                            :player-state="playerStateLabel"
                            :col-span="item._layout?.columnSpan ?? 1"
                            :row-span="item._layout?.rowSpan ?? 1"
                            :class="{
                              'storyboard-tile--resizing': resizingThumbnailId === item.id,
                              'storyboard-tile--is-dragging': isTileDragging(item),
                              'storyboard-tile--drag-target': isTileDragTarget(item),
                            }"
                            :style="storyboardItemStyle(item)"
                            @select="selectGlobalThumb"
                            @play="toggleThumbPlayback"
                            @quick-record="recordNextVoiceForThumb"
                            @delete="deleteThumb"
                            @reorder="({ thumb, direction }) => reorderThumb(thumb, direction)"
                            @resize-start="beginThumbnailResize"
                            @drag-start="(thumb, e) => onTileDragStart(e, thumb)"
                            @drag-over="(thumb, e) => onTileDragOver(e, thumb)"
                            @drag-leave="(thumb, e) => onTileDragLeave(e)"
                            @drag-end="onTileDragEnd"
                            @tile-drop="(thumb, e) => onTileDrop(e, thumb)"
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
                      v-model:recording-trim-open="recordingTrimOpen"
                      v-model:recording-volume="recordingVolume"
                      v-model:recording-speed="recordingSpeed"
                      v-model:recording-noise-reduction="recordingNoiseReduction"
                      v-model:trim-start="trimStart"
                      v-model:trim-end="trimEnd"
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
                      :preview-playing="selectedVoice ? isAudioPlaying(selectedVoice, selectedThumb) : autoplay.isPlaying.value"
                      @select-voice="(voice) => selectVoice(voice, selectedThumb, { scrollRecorder: false })"
                      @select-speaker-slot="selectSpeakerSlot"
                      @toggle-record="toggleSelectedQuickRecording"
                      @stop-recording="stopQuickRecording"
                      @replay="playSelectedAudioPreview"
                      @restart="restartSelectedRecording"
                      @open-layout="openStoryboardSettingsDialog"
                      @audio-file-change="importRecordingAudioFile"
                  />
                </div>

                <div v-else key="studio" class="vg-view active">
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
                                <!-- Wave: only when audio exists or actively recording this voice -->
                                <span
                                    v-if="!audio.isDraft || (isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id))"
                                    class="fiche-wave"
                                    :class="{ 'fiche-wave--live': isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) }"
                                    aria-hidden="true"
                                >
                                  <i></i><i></i><i></i><i></i><i></i><i></i>
                                </span>
                                <!-- Play/stop: only for recorded voices or the voice currently being recorded -->
                                <button
                                    v-if="!audio.isDraft || (isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id))"
                                    type="button"
                                    class="fiche-play"
                                    :class="{ 'fiche-play--pause': isAudioPlaying(audio, item) }"
                                    :title="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) ? 'Stop recording' : (isAudioPlaying(audio, item) ? 'Pause' : 'Play')"
                                    :aria-label="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) ? 'Stop recording' : (isAudioPlaying(audio, item) ? 'Pause playback' : 'Play voice')"
                                    @click.stop="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id) ? stopQuickRecording() : toggleAudioPlayback(audio, item)"
                                >
                                  <span v-if="isRecordingThumb(item) && String(selectedVoiceId ?? '') === String(audio.id)" class="pause-icon" aria-hidden="true"></span>
                                  <span v-else-if="isAudioPlaying(audio, item)" class="pause-icon" aria-hidden="true"></span>
                                  <span v-else aria-hidden="true">▶</span>
                                </button>
                                <button
                                    type="button"
                                    class="fiche-delete-voice"
                                    title="Remove voice"
                                    aria-label="Remove voice"
                                    @click.stop="removeVoice(audio, item)"
                                >
                                  ×
                                </button>
                              </div>

                              <div v-if="!(audioMap[item.id] || []).length" class="fiche-voice fiche-voice--empty">
                                <span class="fiche-speaker" :class="nextSpeakerForThumb(item).toLowerCase()">
                                  {{ nextSpeakerForThumb(item) }}
                                </span>
                                <span class="fiche-voice-title">Voice {{ nextSpeakerForThumb(item) }} to record</span>
                                <span class="fiche-empty-dot"></span>
                              </div>

                              <button
                                  type="button"
                                  class="fiche-add-voice"
                                  :class="{ 'fiche-add-voice--rec': isRecordingThumb(item) }"
                                  :disabled="!(studioFrontendOnly || isOwner) || (!isRecordingThumb(item) && !recordButtonVoiceForThumb(item) && (audioMap[item.id] || []).length >= 4)"
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
                            v-if="studioFrontendOnly || isOwner"
                            type="button"
                            class="fiche-add-card"
                            @click="openUploadDialog"
                        >
                          +
                        </button>
                      </div>
                    </main>

                    <aside ref="studioRecorderEl" class="studio-audio studio-recorder">
                      <div class="rec-panel-title">
                        <span>Studio audio</span>
                        <strong>{{ recordingTargetLabel }}</strong>
                      </div>

                      <div class="rec-speakers" role="group" aria-label="Choose a voice">
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
                          <small>{{ audio.isDraft ? 'to record' : 'recorded' }}</small>
                        </button>

                        <button
                            v-if="selectedAudios.length < 4 && selectedThumb"
                            type="button"
                            class="rec-speaker--add"
                            :title="`Record as Voice ${nextSelectedSpeaker}`"
                            @click="selectSpeakerSlot(nextSelectedSpeaker)"
                        >
                          <span>{{ nextSelectedSpeaker }}</span>
                          <small>+ Add</small>
                        </button>
                      </div>

                      <div
                          class="rec-target-strip"
                          :class="{ recording: quickRecordingThumbId != null, draft: recordingTargetVoice?.isDraft }"
                      >
                        <span class="fiche-speaker" :class="String(recordingTargetSpeaker).toLowerCase()">
                          {{ recordingTargetSpeaker }}
                        </span>
                        <div>
                          <strong>{{ quickRecordingThumbId != null ? `Recording Voice ${recordingTargetSpeaker}` : `Voice ${recordingTargetSpeaker} selected` }}</strong>
                          <small>{{ recordingStatusLabel }}</small>
                        </div>
                      </div>

                      <div class="mic-rings rec-mic">
                        <div class="mic-ring"></div>
                        <div class="mic-ring"></div>
                        <div class="mic-ring"></div>
                        <button
                            type="button"
                            class="mic-btn"
                            :class="{ rec: quickRecordingThumbId != null }"
                            :disabled="!(studioFrontendOnly || isOwner) || !selectedThumb"
                            :title="quickRecordingThumbId != null ? `Stop recording Voice ${recordingTargetSpeaker}` : `Record Voice ${recordingTargetSpeaker}`"
                            :aria-label="quickRecordingThumbId != null ? `Stop recording Voice ${recordingTargetSpeaker}` : `Record Voice ${recordingTargetSpeaker}`"
                            @click="toggleSelectedQuickRecording"
                        ></button>
                      </div>
                      <div class="mic-tm">{{ quickRecordingThumbId != null ? "REC" : "0:00" }}</div>
                      <div class="mic-st" :class="{ live: quickRecordingThumbId != null }">
                        {{ quickRecordingThumbId != null ? "Recording" : "Tap to record" }}
                      </div>

                      <div class="rec-quick-player">
                        <template v-if="selectedAudios.length">
                          <span class="fiche-speaker" :class="speakerClass(selectedVoice, selectedAudios.indexOf(selectedVoice))">
                            {{ speakerForAudio(selectedVoice, selectedAudios.indexOf(selectedVoice)) }}
                          </span>
                          <div>
                            <strong>{{ selectedVoice ? voiceDisplayTitle(selectedVoice, selectedThumb) : "Selected voice" }}</strong>
                            <small>{{ recordingStatusLabel }}</small>
                          </div>
                          <span v-if="!selectedVoice?.isDraft" class="fiche-wave">
                            <i></i><i></i><i></i><i></i><i></i><i></i>
                          </span>
                          <span v-else class="fiche-empty-dot"></span>
                          <button
                              type="button"
                              :disabled="selectedVoice?.isDraft"
                              :title="selectedVoice?.isDraft ? 'Record or import this voice' : (isAudioPlaying(selectedVoice, selectedThumb) ? 'Pause' : 'Play')"
                              :aria-label="isAudioPlaying(selectedVoice, selectedThumb) ? 'Pause' : 'Play selected voice'"
                              @click="playSelectedAudioPreview"
                          >
                            <span v-if="isAudioPlaying(selectedVoice, selectedThumb)" class="pause-icon" aria-hidden="true"></span>
                            <span v-else aria-hidden="true">▶</span>
                          </button>
                        </template>
                        <template v-else>
                          <span class="fiche-speaker" :class="String(selectedSpeaker).toLowerCase()">
                            {{ selectedSpeaker }}
                          </span>
                          <div>
                            <strong>Voice {{ selectedSpeaker }}</strong>
                            <small>{{ recordingTargetLabel }}</small>
                          </div>
                          <span class="fiche-empty-dot"></span>
                        </template>
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
                        <div class="rec-main-actions rec-main-actions--split">
                          <button type="button" class="rec-import-btn" @click="openRecordingAudioFile">
                            <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" width="13" height="13">
                              <path d="M9 3a1 1 0 0 1 2 0v7.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L9 10.586V3Z"/>
                              <path d="M3 14a1 1 0 0 1 2 0v1h10v-1a1 1 0 1 1 2 0v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1Z"/>
                            </svg>
                            Audio file
                          </button>
                        </div>
                        <p class="rec-adv-section">Trim</p>
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
                          {{ recordingTrimOpen ? 'Close trim' : 'Trim audio' }}
                        </button>
                        <div v-if="recordingTrimOpen" class="trim-panel trim-panel--inline">
                          <div class="trim-panel__header">
                            <small>{{ selectedVoice ? voiceDisplayTitle(selectedVoice, selectedThumb) : `Voice ${selectedSpeaker}` }}</small>
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
                            <button type="button" class="trim-editor__handle trim-editor__handle--start" :style="{ left: `${trimStart}%` }" aria-label="Trim start" @pointerdown.stop.prevent="beginTrimDrag('start', $event)"><span></span></button>
                            <button type="button" class="trim-editor__handle trim-editor__handle--end" :style="{ left: `${trimEnd}%` }" aria-label="Trim end" @pointerdown.stop.prevent="beginTrimDrag('end', $event)"><span></span></button>
                          </div>
                          <div class="trim-panel__actions">
                            <button type="button" :disabled="!selectedVoice || selectedVoice.isDraft" @click="previewTrimSelection">{{ trimPreviewPlaying ? "Stop" : "Preview" }}</button>
                            <button type="button" @click="resetTrimSelection">Reset</button>
                            <button type="button" class="primary" :disabled="!selectedVoice || selectedVoice.isDraft" @click="applyTrimSelection">Apply</button>
                          </div>
                        </div>
                        <p class="rec-adv-section">Playback</p>
                        <label>
                          <span>Volume</span>
                          <input v-model="recordingVolume" type="range" min="0" max="100"/>
                          <strong>{{ recordingVolume }}%</strong>
                        </label>
                        <label>
                          <span>Vitesse</span>
                          <input v-model="recordingSpeed" type="range" min="50" max="150"/>
                          <strong>{{ (recordingSpeed / 100).toFixed(1) }}x</strong>
                        </label>
                        <label class="switch-row">
                          <span>Nettoyage</span>
                          <input v-model="recordingNoiseReduction" type="checkbox"/>
                          <strong>{{ recordingNoiseReduction ? "On" : "Off" }}</strong>
                        </label>
                      </div>

                      <div class="side-settings">
                        <div class="side-settings__title">Linguistic gloss</div>
                        <template v-if="selectedVoice && !selectedVoice.isDraft">
                          <div class="voice-editor-head">
                            <span class="fiche-speaker" :class="String(selectedSpeaker).toLowerCase()">
                              {{ selectedSpeaker }}
                            </span>
                            <div>
                              <strong>{{ voiceDisplayTitle(selectedVoice, selectedThumb) }}</strong>
                              <small>{{ selectedVoice.isDraft ? "Not yet recorded" : "Audio ready" }}</small>
                            </div>
                          </div>
                          <label class="side-gloss-label">
                            <span>Transcription</span>
                            <input
                                ref="glossTranscriptionInput"
                                v-model="glossTranscription"
                                class="side-note-input"
                                placeholder="Orthographic or phonemic transcription"
                                :disabled="!isOwner"
                                @blur="saveGloss"
                            />
                          </label>
                          <label class="side-gloss-label">
                            <span>Gloss</span>
                            <input
                                v-model="glossGloss"
                                class="side-note-input side-note-input--mono"
                                placeholder="ex. 1SG-PRES-like-FV"
                                :disabled="!isOwner"
                                @blur="saveGloss"
                            />
                          </label>
                          <label class="side-gloss-label">
                            <span>Free translation</span>
                            <input
                                v-model="glossFreeTranslation"
                                class="side-note-input"
                                placeholder="Translation in a reference language"
                                :disabled="!isOwner"
                                @blur="saveGloss"
                            />
                          </label>
                          <button
                              v-if="isOwner"
                              type="button"
                              class="gloss-save-btn"
                              :disabled="glossSaving"
                              @click="saveGloss"
                          >
                            {{ glossSaving ? "Saving…" : "Save gloss" }}
                          </button>
                        </template>
                        <p v-else-if="selectedVoice?.isDraft" class="side-gloss-empty">Record this voice first to annotate the gloss.</p>
                        <p v-else class="side-gloss-empty">Select a scene to annotate the gloss.</p>
                      </div>

                    </aside>
                  </div>
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
                    You'll then be able to record voices and annotate the whole thing.
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
                      <span>Record voices</span>
                    </div>
                    <div class="sb-empty__step-arrow">→</div>
                    <div class="sb-empty__step">
                      <span class="sb-empty__step-num">3</span>
                      <span>Publish the vignette</span>
                    </div>
                  </div>
                </div>
              </div>

      <!-- ============ CINEMATIC SCENARIO PLAYER ============ -->
      <Teleport to="body">
        <Transition name="sp">
          <div
              v-if="playerOpen"
              class="sp-overlay"
              tabindex="0"
              @keydown="onPlayerKey"
          >
            <!-- Blurred background from current scene -->
            <div class="sp-bg" aria-hidden="true">
              <img
                  v-if="playerCurrentThumb"
                  :src="thumbnailContentUrl(playerCurrentThumb)"
                  class="sp-bg__img"
              />
            </div>

            <!-- Top bar -->
            <div class="sp-topbar">
              <button type="button" class="sp-close" aria-label="Close player" @click="closePlayer">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
              </button>

              <div class="sp-topbar__center">
                <span class="sp-scenario-title">{{ scenario?.title || "Scenario" }}</span>
                <span class="sp-scene-counter" v-if="playbackQueue.length">
                  Scene {{ playerCurrentSceneNumber }} / {{ sortedThumbnails.length }}
                  &nbsp;·&nbsp;
                  Track {{ autoplay.currentIndex.value >= 0 ? autoplay.currentIndex.value + 1 : 0 }} / {{ playbackQueue.length }}
                </span>
              </div>

              <div class="sp-toggles">
                <button type="button" class="sp-toggle" :class="{ active: autoplay.autoContinue.value }" @click="toggleAutoContinue">Auto</button>
                <button type="button" class="sp-toggle" :class="{ active: autoplay.loopScenario.value }" @click="toggleLoopScenario">Loop</button>
              </div>
            </div>

            <!-- Main stage -->
            <div class="sp-stage">
              <!-- Scene image -->
              <div class="sp-scene-wrap">
                <div class="sp-scene">
                  <img
                      v-if="playerCurrentThumb"
                      :src="thumbnailContentUrl(playerCurrentThumb)"
                      :alt="playerCurrentThumb.title || 'Scene'"
                      class="sp-scene__img"
                  />
                  <div v-else class="sp-scene__empty">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="40" height="40" opacity="0.3">
                      <rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/>
                    </svg>
                    <span>No scene</span>
                  </div>

                  <!-- Info gradient overlay -->
                  <div class="sp-scene__overlay">
                    <span class="sp-scene__big-num">{{ String(playerCurrentSceneNumber).padStart(2, "0") }}</span>
                    <div class="sp-scene__meta">
                      <p class="sp-scene__name">{{ playerCurrentThumb?.title || `Scene ${playerCurrentSceneNumber}` }}</p>
                      <p class="sp-scene__audio-name">
                        {{ autoplay.currentItem.value?.audioTitle?.trim() || (autoplay.currentIndex.value >= 0 ? "Playing…" : "—") }}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Animated waveform -->
              <div class="sp-wave" aria-hidden="true" :class="{ 'sp-wave--on': autoplay.isPlaying.value }">
                <span v-for="i in 28" :key="i"></span>
              </div>

              <!-- Progress -->
              <div class="sp-progress">
                <span class="sp-time">{{ autoplay.formatTime(autoplay.currentTime.value) }}</span>
                <div class="sp-bar">
                  <div class="sp-bar__track"></div>
                  <div class="sp-bar__fill" :style="{ width: autoplay.progressPercent.value + '%' }"></div>
                  <input
                      type="range" min="0" max="100" step="0.1"
                      :value="autoplay.progressPercent.value"
                      class="sp-bar__input"
                      @input="autoplay.seekToPercent($event.target.value)"
                  />
                </div>
                <span class="sp-time">{{ autoplay.formatTime(autoplay.duration.value) }}</span>
              </div>

              <!-- Transport controls -->
              <div class="sp-transport">
                <button type="button" class="sp-btn" title="Previous (←)" :disabled="!playbackQueue.length" @click="autoplay.previous()">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                    <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z"/>
                  </svg>
                </button>

                <button type="button" class="sp-btn sp-btn--play" :disabled="!playbackQueue.length || autoplay.isLoading.value" @click="playerTogglePlay">
                  <svg v-if="autoplay.isLoading.value" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" width="22" height="22" class="sp-spin">
                    <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
                  </svg>
                  <svg v-else-if="autoplay.isPlaying.value" viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                    <rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                  </svg>
                </button>

                <button type="button" class="sp-btn" title="Next (→)" :disabled="!playbackQueue.length" @click="autoplay.next()">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                    <path d="M6 18 14.5 12 6 6v12zM16 6v12h2V6h-2z"/>
                  </svg>
                </button>

                <button type="button" class="sp-btn sp-btn--stop" title="Stop" :disabled="autoplay.currentIndex.value < 0" @click="autoplay.stop()">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15">
                    <rect x="4" y="4" width="16" height="16" rx="2"/>
                  </svg>
                </button>
              </div>
            </div>

            <!-- Filmstrip of all scenes -->
            <div class="sp-filmstrip">
              <div class="sp-filmstrip__inner">
                <button
                    v-for="(item, i) in storyboardItems"
                    :key="item.id"
                    type="button"
                    class="sp-film"
                    :class="{ 'sp-film--active': playerCurrentThumb?.id === item.id }"
                    :title="item.title || `Scene ${i + 1}`"
                    @click="playerJumpToScene(item)"
                >
                  <div class="sp-film__frame">
                    <img :src="thumbnailContentUrl(item)" :alt="item.title || `Scene ${i + 1}`" loading="lazy"/>
                    <span class="sp-film__num">{{ String(item._sceneNumber ?? i + 1).padStart(2, "0") }}</span>
                    <div class="sp-film__bar"></div>
                  </div>
                  <span class="sp-film__label">{{ item.title || `Scene ${i + 1}` }}</span>
                </button>
              </div>
            </div>

          </div>
        </Transition>
      </Teleport>

    </template>
  </main>
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
.si-status--draft { background: rgba(72,91,56,0.12); color: #485B38; }
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

.si-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.si-tag {
  border-radius: 999px;
  padding: 4px 12px;
  background: #D4E5CA;
  color: #1E0812;
  font-size: 0.78rem;
  font-weight: 700;
}

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
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 22px;
  background: #1E0812;
  color: #FFF0EE;
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
  color: #FFF0EE;
  letter-spacing: -0.02em;
}

.qr-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 999px;
  background: transparent;
  color: rgba(255,248,240,0.45);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 140ms ease, color 140ms ease;
}

.qr-close:hover { background: rgba(255,255,255,0.08); color: #FFF0EE; }
.qr-close svg { width: 13px; height: 13px; }

.qr-scene {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px;
  background: rgba(255,255,255,0.04);
}

.qr-scene__thumb {
  width: 56px;
  height: 42px;
  object-fit: cover;
  border-radius: 8px;
  border: 1.5px solid rgba(255,255,255,0.1);
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
  color: rgba(255,248,240,0.35);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.qr-scene__name {
  font-size: 0.88rem;
  font-weight: 800;
  color: #FFF0EE;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qr-scene__speaker {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  color: rgba(255,248,240,0.45);
}

.qr-scene__speaker .fiche-speaker {
  width: 18px;
  height: 18px;
  font-size: 0.6rem;
}

.qr-player {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qr-wave {
  display: flex;
  align-items: center;
  gap: 2.5px;
  height: 36px;
  padding: 0 2px;
}

.qr-wave span {
  flex: 1;
  border-radius: 2px;
  background: #485B38;
  opacity: 0.6;
  animation: qr-wave-idle 1.2s ease-in-out infinite;
}

.qr-wave span:nth-child(odd)  { animation-delay: 0s; }
.qr-wave span:nth-child(even) { animation-delay: 0.3s; }
.qr-wave span:nth-child(3n)   { animation-delay: 0.15s; }
.qr-wave span:nth-child(4n)   { animation-delay: 0.45s; }

@keyframes qr-wave-idle {
  0%, 100% { height: 20%; opacity: 0.4; }
  50%       { height: 85%; opacity: 0.75; }
}

.qr-wave span:nth-child(1)  { animation-duration: 1.1s; }
.qr-wave span:nth-child(5)  { animation-duration: 0.9s; }
.qr-wave span:nth-child(9)  { animation-duration: 1.3s; }
.qr-wave span:nth-child(13) { animation-duration: 1.0s; }
.qr-wave span:nth-child(17) { animation-duration: 1.4s; }
.qr-wave span:nth-child(21) { animation-duration: 0.95s; }

.qr-audio {
  width: 100%;
  height: 32px;
  border-radius: 8px;
  filter: invert(1) hue-rotate(180deg) brightness(0.75);
}

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
  color: rgba(255,248,240,0.4);
}

.qr-input {
  border: 1.5px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  padding: 11px 14px;
  background: rgba(255,255,255,0.05);
  color: #FFF0EE;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 700;
  outline: none;
  transition: border-color 160ms ease;
}

.qr-input:focus { border-color: #485B38; }
.qr-input::placeholder { color: rgba(255,248,240,0.2); font-weight: 400; }

.qr-actions {
  display: flex;
  gap: 10px;
}

.qr-discard {
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  padding: 0 18px;
  height: 48px;
  background: transparent;
  color: rgba(255,248,240,0.5);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
  flex-shrink: 0;
}

.qr-discard:hover:not(:disabled) { background: rgba(200,50,50,0.15); color: #ff6b6b; border-color: rgba(200,50,50,0.3); }
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
  border: 2px solid rgba(255,248,240,0.3);
  border-top-color: #FFF0EE;
  border-radius: 999px;
  animation: qr-spin 0.7s linear infinite;
}

@keyframes qr-spin { to { transform: rotate(360deg); } }


.bd-page {
  position: relative;
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

.ud-add-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
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

/* ──────────────────────────────────────────────────────────
   PLAY BUTTON IN NAV
────────────────────────────────────────────────────────── */
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

/* ──────────────────────────────────────────────────────────
   CINEMATIC SCENARIO PLAYER
────────────────────────────────────────────────────────── */
.sp-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  background: #100810;
  overflow: hidden;
  outline: none;
}

/* Blurred background */
.sp-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.sp-bg__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(64px) brightness(0.2) saturate(0.5);
  transform: scale(1.15);
  transition: opacity 600ms ease;
}

/* Top bar */
.sp-topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  background: linear-gradient(to bottom, rgba(0,0,0,0.55), transparent);
  flex-shrink: 0;
}
.sp-close {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.14);
  background: rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.75);
  cursor: pointer;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  transition: background 150ms, color 150ms;
}
.sp-close:hover { background: rgba(255,255,255,0.16); color: #fff; }
.sp-close svg { width: 14px; height: 14px; }

.sp-topbar__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.sp-scenario-title {
  font-size: 0.85rem;
  font-weight: 700;
  color: rgba(255,255,255,0.85);
  letter-spacing: 0.04em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.sp-scene-counter {
  font-size: 0.7rem;
  color: rgba(255,255,255,0.35);
  letter-spacing: 0.06em;
  font-variant-numeric: tabular-nums;
}

.sp-toggles {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.sp-toggle {
  padding: 5px 13px;
  border-radius: 999px;
  border: 1px solid rgba(255,255,255,0.15);
  background: rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.4);
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: all 150ms;
}
.sp-toggle.active {
  background: rgba(72, 91, 56, 0.35);
  border-color: rgba(72, 91, 56, 0.6);
  color: #B8D4A8;
}

/* Main stage */
.sp-stage {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 0 24px 12px;
  overflow: hidden;
  min-height: 0;
}

/* Scene image container */
.sp-scene-wrap {
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 800px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sp-scene {
  position: relative;
  width: 100%;
  height: 100%;
  max-height: 100%;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 28px 80px rgba(0,0,0,0.75), 0 0 0 1px rgba(255,255,255,0.06);
}
.sp-scene__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  background: #100810;
}
.sp-scene__empty {
  width: 100%;
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(255,255,255,0.03);
  color: rgba(255,255,255,0.25);
  font-size: 0.85rem;
}
.sp-scene__overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: flex-end;
  gap: 14px;
  padding: 36px 18px 16px;
  background: linear-gradient(to top, rgba(0,0,0,0.72) 0%, transparent 100%);
  pointer-events: none;
}
.sp-scene__big-num {
  font-size: 3rem;
  font-weight: 900;
  color: rgba(255,255,255,0.12);
  line-height: 1;
  letter-spacing: -0.06em;
  flex-shrink: 0;
}
.sp-scene__meta { flex: 1; min-width: 0; }
.sp-scene__name {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: rgba(255,255,255,0.92);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sp-scene__audio-name {
  margin: 3px 0 0;
  font-size: 0.75rem;
  color: rgba(255,255,255,0.45);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Waveform */
.sp-wave {
  display: flex;
  align-items: center;
  gap: 2.5px;
  height: 30px;
  flex-shrink: 0;
}
.sp-wave span {
  display: block;
  width: 3px;
  height: 4px;
  border-radius: 2px;
  background: rgba(72, 91, 56, 0.35);
  transition: height 150ms ease, background 150ms ease;
}
.sp-wave--on span {
  animation: sp-bar 0.9s ease-in-out infinite alternate;
  background: rgba(72, 91, 56, 0.75);
}
.sp-wave--on span:nth-child(1)  { animation-delay: 0ms;   animation-duration: 0.85s; }
.sp-wave--on span:nth-child(2)  { animation-delay: 40ms;  animation-duration: 0.72s; }
.sp-wave--on span:nth-child(3)  { animation-delay: 80ms;  animation-duration: 0.95s; }
.sp-wave--on span:nth-child(4)  { animation-delay: 20ms;  animation-duration: 0.68s; }
.sp-wave--on span:nth-child(5)  { animation-delay: 110ms; animation-duration: 0.82s; }
.sp-wave--on span:nth-child(6)  { animation-delay: 60ms;  animation-duration: 0.90s; }
.sp-wave--on span:nth-child(7)  { animation-delay: 30ms;  animation-duration: 0.76s; }
.sp-wave--on span:nth-child(8)  { animation-delay: 90ms;  animation-duration: 1.0s;  }
.sp-wave--on span:nth-child(9)  { animation-delay: 15ms;  animation-duration: 0.65s; }
.sp-wave--on span:nth-child(10) { animation-delay: 55ms;  animation-duration: 0.88s; }
.sp-wave--on span:nth-child(11) { animation-delay: 75ms;  animation-duration: 0.78s; }
.sp-wave--on span:nth-child(12) { animation-delay: 35ms;  animation-duration: 0.92s; }
.sp-wave--on span:nth-child(13) { animation-delay: 100ms; animation-duration: 0.70s; }
.sp-wave--on span:nth-child(14) { animation-delay: 50ms;  animation-duration: 0.86s; }
.sp-wave--on span:nth-child(15) { animation-delay: 25ms;  animation-duration: 0.98s; }
.sp-wave--on span:nth-child(16) { animation-delay: 70ms;  animation-duration: 0.73s; }
.sp-wave--on span:nth-child(17) { animation-delay: 45ms;  animation-duration: 0.84s; }
.sp-wave--on span:nth-child(18) { animation-delay: 120ms; animation-duration: 0.67s; }
.sp-wave--on span:nth-child(19) { animation-delay: 10ms;  animation-duration: 0.94s; }
.sp-wave--on span:nth-child(20) { animation-delay: 65ms;  animation-duration: 0.80s; }
.sp-wave--on span:nth-child(21) { animation-delay: 85ms;  animation-duration: 0.75s; }
.sp-wave--on span:nth-child(22) { animation-delay: 32ms;  animation-duration: 0.88s; }
.sp-wave--on span:nth-child(23) { animation-delay: 95ms;  animation-duration: 0.71s; }
.sp-wave--on span:nth-child(24) { animation-delay: 18ms;  animation-duration: 0.96s; }
.sp-wave--on span:nth-child(25) { animation-delay: 58ms;  animation-duration: 0.82s; }
.sp-wave--on span:nth-child(26) { animation-delay: 42ms;  animation-duration: 0.79s; }
.sp-wave--on span:nth-child(27) { animation-delay: 105ms; animation-duration: 0.91s; }
.sp-wave--on span:nth-child(28) { animation-delay: 78ms;  animation-duration: 0.69s; }
@keyframes sp-bar {
  from { height: 3px; opacity: 0.5; }
  to   { height: 24px; opacity: 1; }
}

/* Progress bar */
.sp-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 640px;
  flex-shrink: 0;
}
.sp-time {
  font-size: 0.72rem;
  color: rgba(255,255,255,0.38);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
  min-width: 28px;
}
.sp-bar {
  position: relative;
  flex: 1;
  height: 4px;
  border-radius: 999px;
}
.sp-bar__track {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: rgba(255,255,255,0.1);
}
.sp-bar__fill {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(to right, #344228, #485B38);
  transition: width 80ms linear;
  pointer-events: none;
}
.sp-bar__input {
  position: absolute;
  left: 0;
  right: 0;
  top: -10px;
  width: 100%;
  height: calc(100% + 20px);
  margin: 0;
  opacity: 0;
  cursor: pointer;
  z-index: 2;
}

/* Transport controls */
.sp-transport {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.sp-btn {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.12);
  background: rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.65);
  cursor: pointer;
  transition: all 150ms;
}
.sp-btn:hover:not(:disabled) {
  background: rgba(255,255,255,0.14);
  border-color: rgba(255,255,255,0.22);
  color: #fff;
}
.sp-btn:disabled { opacity: 0.25; cursor: default; }
.sp-btn--play {
  width: 60px;
  height: 60px;
  background: #485B38;
  border-color: #485B38;
  color: #fff;
  box-shadow: 0 0 24px rgba(72, 91, 56, 0.45);
}
.sp-btn--play:hover:not(:disabled) {
  background: #566b44;
  border-color: #566b44;
  box-shadow: 0 0 32px rgba(72, 91, 56, 0.65);
  transform: scale(1.04);
}
.sp-btn--stop {
  background: rgba(255,255,255,0.04);
  color: rgba(255,255,255,0.4);
}
.sp-spin {
  animation: sp-spin 0.9s linear infinite;
}
@keyframes sp-spin {
  to { transform: rotate(360deg); }
}

/* Filmstrip */
.sp-filmstrip {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  padding: 8px 20px 16px;
  background: linear-gradient(to top, rgba(0,0,0,0.5), transparent);
}
.sp-filmstrip__inner {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  scroll-snap-type: x mandatory;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.15) transparent;
}
.sp-filmstrip__inner::-webkit-scrollbar { height: 3px; }
.sp-filmstrip__inner::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 2px; }

.sp-film {
  flex-shrink: 0;
  width: 76px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  scroll-snap-align: start;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.sp-film__frame {
  position: relative;
  width: 76px;
  height: 52px;
  border-radius: 7px;
  overflow: hidden;
  border: 2px solid transparent;
  transition: border-color 160ms, box-shadow 160ms;
}
.sp-film--active .sp-film__frame {
  border-color: #485B38;
  box-shadow: 0 0 12px rgba(72, 91, 56, 0.5);
}
.sp-film:not(.sp-film--active):hover .sp-film__frame {
  border-color: rgba(255,255,255,0.28);
}
.sp-film__frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.sp-film__num {
  position: absolute;
  top: 4px;
  left: 5px;
  font-size: 0.65rem;
  font-weight: 800;
  color: rgba(255,255,255,0.9);
  text-shadow: 0 1px 4px rgba(0,0,0,0.9);
  letter-spacing: 0.02em;
}
.sp-film__bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #485B38;
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 160ms;
}
.sp-film--active .sp-film__bar {
  transform: scaleX(1);
}
.sp-film__label {
  font-size: 0.62rem;
  color: rgba(255,255,255,0.35);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 76px;
  text-align: center;
  transition: color 160ms;
}
.sp-film--active .sp-film__label { color: rgba(255,255,255,0.75); }
.sp-film:not(.sp-film--active):hover .sp-film__label { color: rgba(255,255,255,0.55); }

/* Player enter/leave transition */
.sp-enter-active { transition: opacity 280ms ease, transform 280ms cubic-bezier(0.16, 1, 0.3, 1); }
.sp-leave-active { transition: opacity 200ms ease, transform 200ms ease; }
.sp-enter-from { opacity: 0; transform: scale(1.04); }
.sp-leave-to   { opacity: 0; transform: scale(0.97); }
</style>
