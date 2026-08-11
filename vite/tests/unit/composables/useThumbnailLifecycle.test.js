import {ref} from "vue";
import {useThumbnailLifecycle} from "@/composables/useThumbnailLifecycle";

function makeToast() {
    return {success: vi.fn(), error: vi.fn()};
}

function setup(overrides = {}) {
    const thumbnails = ref([{id: 1, idx: 1}, {id: 2, idx: 2}, {id: 3, idx: 3}]);
    const sortedThumbnails = ref(thumbnails.value);
    const selectedThumb = ref(null);
    const audioMap = ref({1: [], 2: [], 3: []});
    const activeAudioId = ref(null);

    const deps = {
        selectedThumb,
        thumbnails,
        audioMap,
        activeAudioId,
        selectedThumbnailPanelOpen: ref(false),
        storyboardView: ref("studio"),
        globalRecorderOpen: ref(false),
        studioSandboxMode: ref(false),
        savingOrder: ref(false),
        sortedThumbnails,
        getScenarioId: () => 42,
        deleteThumbnail: vi.fn(async () => {}),
        reorderScenarioThumbnails: vi.fn(async () => []),
        toast: makeToast(),
        openRecorderForSelection: vi.fn(),
        firstVoiceForThumb: vi.fn(() => null),
        speakerForVoice: vi.fn(() => "A"),
        nextSpeakerForThumb: vi.fn(() => "A"),
        selectVoice: vi.fn(),
        selectedVoiceId: ref(null),
        selectedSpeaker: ref("A"),
        quickRecordingThumbId: ref(null),
        stopQuickRecording: vi.fn(),
        ...overrides,
    };

    const api = useThumbnailLifecycle(deps);
    return {api, deps, thumbnails, selectedThumb, audioMap};
}

describe("useThumbnailLifecycle", () => {
    it("selectThumb selects the thumbnail and its first voice/speaker", () => {
        const {api, deps, selectedThumb} = setup({
            firstVoiceForThumb: vi.fn(() => ({id: "v1"})),
            speakerForVoice: vi.fn(() => "B"),
        });
        const thumb = {id: 2};

        api.selectThumb(thumb);

        expect(selectedThumb.value).toEqual(thumb);
        expect(deps.selectedVoiceId.value).toBe("v1");
        expect(deps.selectedSpeaker.value).toBe("B");
        expect(deps.openRecorderForSelection).toHaveBeenCalled();
    });

    it("selectGlobalThumb selects the thumb and a specific voice", () => {
        const {api, deps} = setup();
        const thumb = {id: 1};
        const audio = {id: "a1"};

        api.selectGlobalThumb(thumb, audio);

        expect(deps.selectVoice).toHaveBeenCalledWith(audio, thumb, {scrollRecorder: false});
    });

    it("updateThumbTitle updates the thumbnail list and the selected thumb in sync", () => {
        const {api, thumbnails, selectedThumb} = setup();
        selectedThumb.value = thumbnails.value[0];

        api.updateThumbTitle(thumbnails.value[0], {target: {value: "New title"}});

        expect(thumbnails.value[0].title).toBe("New title");
        expect(selectedThumb.value.title).toBe("New title");
    });

    it("deleteThumb calls the API, removes the thumb, and reassigns selection", async () => {
        const {api, deps, thumbnails, selectedThumb} = setup();
        selectedThumb.value = thumbnails.value[0];

        await api.deleteThumb(thumbnails.value[0]);

        expect(deps.deleteThumbnail).toHaveBeenCalledWith(1);
        expect(thumbnails.value.map((t) => t.id)).toEqual([2, 3]);
        expect(selectedThumb.value.id).toBe(2);
        expect(deps.toast.success).toHaveBeenCalledWith("Image removed from this board.");
    });

    it("deleteThumb stops an in-progress quick recording for the deleted thumb", async () => {
        const {api, deps, thumbnails} = setup({quickRecordingThumbId: ref(1)});

        await api.deleteThumb(thumbnails.value[0]);

        expect(deps.stopQuickRecording).toHaveBeenCalled();
    });

    it("deleteThumb skips the API call for local (unpersisted) thumbnail ids", async () => {
        const {api, deps, thumbnails} = setup();
        thumbnails.value = [{id: "local-123", idx: 1}];

        await api.deleteThumb(thumbnails.value[0]);

        expect(deps.deleteThumbnail).not.toHaveBeenCalled();
    });

    it("isPersistableThumbnailId distinguishes numeric ids from local/draft ids", () => {
        const {api} = setup();
        expect(api.isPersistableThumbnailId(42)).toBe(true);
        expect(api.isPersistableThumbnailId("local-123")).toBe(false);
    });

    it("applyThumbnailOrder persists via the API and reports success", async () => {
        const {api, deps, thumbnails} = setup();
        const reordered = [thumbnails.value[2], thumbnails.value[0], thumbnails.value[1]];

        await api.applyThumbnailOrder(reordered, "Reordered.");

        expect(deps.reorderScenarioThumbnails).toHaveBeenCalledWith(42, [3, 1, 2]);
        expect(thumbnails.value.map((t) => t.id)).toEqual([3, 1, 2]);
        expect(deps.toast.success).toHaveBeenCalledWith("Reordered.");
    });

    it("applyThumbnailOrder rolls back local state and reports an error on failure", async () => {
        const {api, deps, thumbnails} = setup({
            reorderScenarioThumbnails: vi.fn(async () => { throw new Error("network down"); }),
        });
        const original = [...thumbnails.value];
        const reordered = [thumbnails.value[2], thumbnails.value[0], thumbnails.value[1]];

        await api.applyThumbnailOrder(reordered);

        expect(thumbnails.value).toEqual(original);
        expect(deps.toast.error).toHaveBeenCalledWith("network down");
    });

    it("reorderThumb moves a thumbnail up/down/first/last and persists the new order", async () => {
        const {api, deps, thumbnails} = setup();

        await api.reorderThumb(thumbnails.value[2], "first");

        expect(deps.reorderScenarioThumbnails).toHaveBeenCalledWith(42, [3, 1, 2]);
    });

    it("moveThumbInOrder returns null for a no-op move", () => {
        const {api} = setup();
        expect(api.moveThumbInOrder([{id: 1}, {id: 2}], 0, 0)).toBeNull();
    });
});
