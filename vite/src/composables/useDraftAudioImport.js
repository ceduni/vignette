import {computed, ref} from "vue";
import {draftAudioStorageKey} from "../utils/draftAudioStorage";

export function useDraftAudioImport(options = {}) {
    const {
        route,
        currentUser,
        scenario,
        sortedThumbnails,
        thumbnails,
        selectedThumb,
        audioMap,
        toast,
        studioSandboxMode,
        getScenarioId,
        useStudioSandbox,
        openUploadDialog,
        audioImportErrorMessage,
        targetVoiceForThumb,
        ensureVoiceForRecording,
        speakerForVoice,
        selectedSpeaker,
        takeLabel,
        addLocalAudioClip,
        selectedVoiceId,
        nextSpeakerForThumb,
        prepareAudioUploadFile,
        uploadVoiceAudioFile,
    } = options;

    const draftImportDialogOpen = ref(false);
    const availableAudioDrafts = ref([]);
    const importingDraftId = ref(null);
    const pendingRouteDraftId = ref(null);
    const pendingRouteDraft = computed(() => readUnclaimedDraftAudio(pendingRouteDraftId.value));

    function readUnclaimedDraftAudio(draftId) {
        if (!draftId) return null;
        try {
            const key = draftAudioStorageKey(currentUser.value?.username);
            const drafts = JSON.parse(localStorage.getItem(key) || "[]");
            return drafts.find((draft) => String(draft.id) === String(draftId)) ?? null;
        } catch {
            return null;
        }
    }

    function readUnclaimedAudioDrafts() {
        try {
            const key = draftAudioStorageKey(currentUser.value?.username);
            const drafts = JSON.parse(localStorage.getItem(key) || "[]");
            return Array.isArray(drafts) ? drafts.filter((draft) => draft?.id && draft?.dataUrl) : [];
        } catch {
            return [];
        }
    }

    function openDraftImportDialog() {
        if (!selectedThumb.value) {
            toast.info("Choose a scene before importing audio.");
            return;
        }
        availableAudioDrafts.value = readUnclaimedAudioDrafts();
        draftImportDialogOpen.value = true;
    }

    function draftAudioFile(draft) {
        const [header = "", payload = ""] = String(draft?.dataUrl || "").split(",");
        const mimeType = draft?.mimeType || header.match(/^data:([^;]+)/)?.[1] || "audio/webm";
        const binary = atob(payload);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const extension = mimeType.includes("ogg")
            ? "ogg"
            : mimeType.includes("mp4") || mimeType.includes("aac")
                ? "m4a"
                : mimeType.includes("wav")
                    ? "wav"
                    : "webm";
        const safeName = String(draft?.title || "audio-draft").replace(/[^\w-]+/g, "-").replace(/^-|-$/g, "") || "audio-draft";
        return new File([bytes], `${safeName}.${extension}`, {type: mimeType});
    }

    async function importAudioDraft(draft, targetThumb = selectedThumb.value, {closeDialog = true} = {}) {
        if (!draft?.dataUrl || !targetThumb?.id || importingDraftId.value) return false;
        const existingDraft = (audioMap.value[targetThumb.id] || []).find(
            (audio) => String(audio.sourceDraftId ?? "") === String(draft.id)
        );
        if (existingDraft) {
            selectedThumb.value = targetThumb;
            selectedVoiceId.value = existingDraft.id;
            if (closeDialog) draftImportDialogOpen.value = false;
            toast.info("That draft is already attached to this scene.");
            return true;
        }

        importingDraftId.value = draft.id;
        try {
            const selectedTarget = targetVoiceForThumb(targetThumb);
            const requestedDraftSlot = selectedTarget?.isDraft ? selectedTarget : null;
            const {voice} = ensureVoiceForRecording(
                targetThumb,
                requestedDraftSlot,
                requestedDraftSlot?.speaker || nextSpeakerForThumb(targetThumb),
                {preferSelected: false}
            );
            if (!voice) throw new Error("No take is available for this scene.");
            const speaker = speakerForVoice(voice, targetThumb) || selectedSpeaker.value;
            const title = draft.title || takeLabel(speaker);

            if (studioSandboxMode.value || String(getScenarioId()).startsWith("emergency-")) {
                selectedThumb.value = targetThumb;
                selectedVoiceId.value = voice.id;
                addLocalAudioClip(targetThumb, {
                    title,
                    previewUrl: draft.dataUrl,
                    speaker,
                    sourceDraftId: draft.id,
                });
            } else {
                const file = await prepareAudioUploadFile(draftAudioFile(draft));
                await uploadVoiceAudioFile(targetThumb, voice, file, title, file.name);
            }

            if (closeDialog) draftImportDialogOpen.value = false;
            pendingRouteDraftId.value = null;
            toast.success(`“${title}” added to ${targetThumb.title || "the scene"}.`);
            return true;
        } catch (e) {
            toast.error(audioImportErrorMessage(e, "import this audio draft"));
            return false;
        } finally {
            importingDraftId.value = null;
        }
    }

    async function applyUnclaimedDraftAudio({targetThumb = null} = {}) {
        const draftId = route.query.draftAudio;
        const draft = readUnclaimedDraftAudio(Array.isArray(draftId) ? draftId[0] : draftId);
        if (!draft?.dataUrl) return;

        if (!scenario.value) {
            useStudioSandbox("");
            scenario.value = {
                ...scenario.value,
                id: getScenarioId(),
                title: draft.title || "Vignette audio",
                description: "Created from an emergency audio draft",
            };
        }

        const destination = targetThumb || sortedThumbnails.value[0] || thumbnails.value[0] || null;
        if (!destination) {
            pendingRouteDraftId.value = draft.id;
            openUploadDialog();
            return;
        }

        await importAudioDraft(draft, destination, {closeDialog: false});
    }

    return {
        draftImportDialogOpen,
        availableAudioDrafts,
        importingDraftId,
        pendingRouteDraftId,
        pendingRouteDraft,
        readUnclaimedDraftAudio,
        readUnclaimedAudioDrafts,
        openDraftImportDialog,
        draftAudioFile,
        importAudioDraft,
        applyUnclaimedDraftAudio,
    };
}
