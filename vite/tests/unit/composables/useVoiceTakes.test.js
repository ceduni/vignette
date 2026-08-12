import {computed, ref} from "vue";
import {useVoiceTakes} from "@/composables/useVoiceTakes";
import {buildSelectedAudios} from "@/utils/scenarioStoryboard.js";

function makeToast() {
    return {success: vi.fn(), error: vi.fn(), info: vi.fn()};
}

class FakeMediaRecorder {
    constructor(stream, opts) {
        this.stream = stream;
        this.mimeType = opts?.mimeType || "audio/webm";
        this.state = "recording";
        this.ondataavailable = null;
        this.onstop = null;
    }
    start() {}
    stop() {
        this.state = "inactive";
        this.ondataavailable?.({data: new Blob(["x"])});
        this.onstop?.();
    }
    static isTypeSupported() { return true; }
}

function setup(overrides = {}) {
    const selectedThumb = ref(null);
    const thumbnails = ref([{id: 1, title: "Scene 1"}, {id: 2, title: "Scene 2"}]);
    const sortedThumbnails = ref(thumbnails.value);
    const audioMap = ref({});
    const scenario = ref({id: 42});
    const activeAudioId = ref(null);
    const glossTranscription = ref("");
    const glossGloss = ref("");
    const glossFreeTranslation = ref("");

    const selectedAudios = computed(() => buildSelectedAudios(audioMap.value, selectedThumb.value));
    const selectedVoice = computed(() => {
        if (!selectedAudios.value.length) return null;
        return selectedAudios.value.find((audio) => String(audio.id) === String(api.selectedVoiceId.value)) ?? selectedAudios.value[0];
    });

    const deps = {
        selectedThumb,
        thumbnails,
        sortedThumbnails,
        audioMap,
        scenario,
        activeAudioId,
        selectedVoice,
        selectedAudios,
        toast: makeToast(),
        currentUser: ref({username: "lea"}),
        route: {query: {}},
        studioSandboxMode: ref(false),
        getScenarioId: () => 42,
        deleteAudio: vi.fn(async () => {}),
        replaceAudioContent: vi.fn(async (audioId) => ({id: audioId})),
        uploadThumbnailAudio: vi.fn(async () => ({id: 99})),
        fetchThumbnailAudios: vi.fn(async () => []),
        glossTranscription,
        glossGloss,
        glossFreeTranslation,
        selectThumb: vi.fn((thumb) => { selectedThumb.value = thumb; }),
        openRecorderForSelection: vi.fn(),
        useStudioSandbox: vi.fn(),
        openUploadDialog: vi.fn(),
        setActiveAudio: vi.fn(async () => {}),
        getSelectedSceneNumber: () => 1,
        autoplayStop: vi.fn(),
        autoplayPause: vi.fn(),
        isAutoplayPlaying: () => false,
        autoplayCurrentItem: () => null,
        fileBaseName: (file, fallback) => file?.name?.replace(/\.[^.]+$/, "") || fallback,
        audioImportErrorMessage: (e, action) => `Could not ${action}: ${e?.message || e}`,
        ...overrides,
    };

    const api = useVoiceTakes(deps);
    return {api, deps, selectedThumb, thumbnails, audioMap, selectedVoice};
}

describe("useVoiceTakes", () => {
    let originalMediaRecorder, originalGetUserMedia, originalCreateObjectURL, originalRevokeObjectURL;

    beforeEach(() => {
        originalMediaRecorder = global.MediaRecorder;
        global.MediaRecorder = FakeMediaRecorder;
        originalGetUserMedia = global.navigator.mediaDevices;
        Object.defineProperty(global.navigator, "mediaDevices", {
            value: {getUserMedia: vi.fn(async () => ({active: true}))},
            configurable: true,
        });
        originalCreateObjectURL = URL.createObjectURL;
        originalRevokeObjectURL = URL.revokeObjectURL;
        URL.createObjectURL = vi.fn(() => "blob:fake");
        URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        global.MediaRecorder = originalMediaRecorder;
        Object.defineProperty(global.navigator, "mediaDevices", {value: originalGetUserMedia, configurable: true});
        URL.createObjectURL = originalCreateObjectURL;
        URL.revokeObjectURL = originalRevokeObjectURL;
    });

    it("takeLabel/speakerForIndex derive human labels and alphabet slots", () => {
        const {api} = setup();
        expect(api.takeLabel("b")).toBe("Take B");
        expect(api.speakerForIndex(0)).toBe("A");
        expect(api.speakerForIndex(1)).toBe("B");
    });

    it("addDraftVoice creates a draft take and refuses past four takes", () => {
        const {api, deps} = setup();
        const thumb = {id: 1};

        const draft = api.addDraftVoice(thumb, "A");
        expect(draft.isDraft).toBe(true);
        expect(deps.audioMap.value[1]).toHaveLength(1);

        deps.audioMap.value = {1: [{id: "a", speaker: "A"}, {id: "b", speaker: "B"}, {id: "c", speaker: "C"}, {id: "d", speaker: "D"}]};
        const refused = api.addDraftVoice(thumb, "E");

        expect(refused).toBeNull();
        expect(deps.toast.error).toHaveBeenCalledWith("This scene already has four takes.");
    });

    it("selectVoice selects the thumbnail, voice, and syncs gloss fields", () => {
        const {api, selectedThumb, deps} = setup();
        const thumb = {id: 1};
        const audio = {id: "a1", speaker: "A", transcription: "hi", gloss: "g", freeTranslation: "ft"};
        deps.audioMap.value = {1: [audio]};

        api.selectVoice(audio, thumb);

        expect(selectedThumb.value).toEqual(thumb);
        expect(api.selectedVoiceId.value).toBe("a1");
        expect(deps.glossTranscription.value).toBe("hi");
        expect(deps.openRecorderForSelection).toHaveBeenCalled();
    });

    it("selectSpeakerSlot reuses an existing take or creates a draft for an empty slot", () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.audioMap.value = {1: [{id: "a1", speaker: "A"}]};

        api.selectSpeakerSlot("A", thumb);
        expect(api.selectedVoiceId.value).toBe("a1");

        api.selectSpeakerSlot("B", thumb);
        expect(api.selectedVoiceId.value).not.toBe("a1");
        expect(deps.audioMap.value[1]).toHaveLength(2);
    });

    it("ensureVoiceForRecording reuses the target voice without creating a new draft", () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.audioMap.value = {1: [{id: "a1", speaker: "A"}]};
        deps.selectedThumb.value = thumb;

        const {voice, created} = api.ensureVoiceForRecording(thumb, {id: "a1"});

        expect(voice.id).toBe("a1");
        expect(created).toBe(false);
    });

    it("removeVoice deletes a persisted take via the API and reassigns selection", async () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.audioMap.value = {1: [{id: "a1", speaker: "A"}, {id: "a2", speaker: "B"}]};

        await api.removeVoice({id: "a1", speaker: "A"}, thumb);

        expect(deps.deleteAudio).toHaveBeenCalledWith("a1");
        expect(deps.audioMap.value[1]).toEqual([{id: "a2", speaker: "B"}]);
        expect(deps.toast.success).toHaveBeenCalledWith("Take removed.");
    });

    it("removeVoice does not call the API for a local draft take", async () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.audioMap.value = {1: [{id: "draft-1-A-1", speaker: "A", isDraft: true}]};

        await api.removeVoice({id: "draft-1-A-1", speaker: "A", isDraft: true}, thumb);

        expect(deps.deleteAudio).not.toHaveBeenCalled();
    });

    it("addLocalAudioClip appends a sandbox-only clip and selects it", () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.selectedThumb.value = thumb;

        api.addLocalAudioClip(thumb, {title: "My take", previewUrl: "blob:1", speaker: "A"});

        expect(deps.audioMap.value[1]).toHaveLength(1);
        expect(deps.audioMap.value[1][0].title).toBe("My take");
        expect(api.selectedVoiceId.value).toBe(deps.audioMap.value[1][0].id);
    });

    it("replaces a saved take without deleting it first", async () => {
        const refreshedTake = {id: 12, idx: 1, title: "New take", speaker: "A"};
        const {api, deps} = setup({
            fetchThumbnailAudios: vi.fn(async () => [refreshedTake]),
        });
        const thumb = {id: 1};
        const savedTake = {id: 12, idx: 1, title: "Old take", speaker: "A"};
        deps.selectedThumb.value = thumb;
        deps.audioMap.value = {1: [savedTake]};

        const result = await api.uploadVoiceAudioFile(
            thumb,
            savedTake,
            new File(["audio"], "replacement.webm", {type: "audio/webm"}),
            "New take"
        );

        expect(deps.replaceAudioContent).toHaveBeenCalledWith(12, expect.any(FormData));
        expect(deps.uploadThumbnailAudio).not.toHaveBeenCalled();
        expect(deps.deleteAudio).not.toHaveBeenCalled();
        expect(deps.audioMap.value[1]).toEqual([refreshedTake]);
        expect(result).toEqual(refreshedTake);
    });

    it("keeps a saved take when its replacement upload fails", async () => {
        const {api, deps} = setup({
            replaceAudioContent: vi.fn(async () => {
                throw new Error("upload failed");
            }),
        });
        const thumb = {id: 1};
        const savedTake = {id: 12, idx: 1, title: "Old take", speaker: "A"};
        deps.selectedThumb.value = thumb;
        deps.audioMap.value = {1: [savedTake]};

        await expect(api.uploadVoiceAudioFile(
            thumb,
            savedTake,
            new File(["audio"], "replacement.webm", {type: "audio/webm"}),
            "New take"
        )).rejects.toThrow("upload failed");

        expect(deps.deleteAudio).not.toHaveBeenCalled();
        expect(deps.audioMap.value[1]).toEqual([savedTake]);
    });

    it("quick recording: start, stop, and confirm upload a take in normal mode", async () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.selectedThumb.value = thumb;
        deps.audioMap.value = {1: []};

        await api.startQuickRecording(thumb);
        expect(api.quickRecordingThumbId.value).toBe(1);

        api.stopQuickRecording();
        expect(api.quickRecordingBlob.value).toBeInstanceOf(Blob);

        await api.confirmQuickRecordingUpload();

        expect(deps.uploadThumbnailAudio).toHaveBeenCalled();
        expect(deps.toast.success).toHaveBeenCalledWith("Quick recording uploaded successfully.");
        expect(api.quickRecordingDialogOpen.value).toBe(false);
    });

    it("quick recording: discardQuickRecording removes the draft slot it created", async () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        deps.selectedThumb.value = thumb;
        deps.audioMap.value = {1: []};

        await api.startQuickRecording(thumb);
        api.stopQuickRecording();

        const draftId = api.quickRecordingVoiceId.value;
        expect(deps.audioMap.value[1].some((a) => a.id === draftId)).toBe(true);

        api.discardQuickRecording();

        expect(deps.audioMap.value[1].some((a) => a.id === draftId)).toBe(false);
        expect(deps.toast.info).toHaveBeenCalledWith("Quick recording discarded.");
    });

    it("applyUnclaimedDraftAudio does nothing when there's no pending draft", async () => {
        const {api, deps} = setup();
        await api.applyUnclaimedDraftAudio();
        expect(deps.useStudioSandbox).not.toHaveBeenCalled();
    });

    it("importAudioDraft attaches a stored draft to a scene in sandbox mode", async () => {
        const {api, deps} = setup({studioSandboxMode: ref(true)});
        const thumb = {id: 1};
        deps.audioMap.value = {1: []};

        const ok = await api.importAudioDraft(
            {id: "draft-1", title: "My draft", dataUrl: "data:audio/webm;base64,eA=="},
            thumb,
            {closeDialog: false}
        );

        expect(ok).toBe(true);
        expect(deps.audioMap.value[1]).toHaveLength(1);
        expect(deps.audioMap.value[1][0].sourceDraftId).toBe("draft-1");
    });
});
