import {ref} from "vue";
import {useBackgroundAmbience} from "@/composables/useBackgroundAmbience";

const toastMocks = vi.hoisted(() => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
}));

vi.mock("@/composables/useToast", () => ({
    useToast: () => toastMocks,
}));

class FakeAudio {
    constructor() {
        this.src = "";
        this.currentTime = 0;
        this.volume = 1;
        this.loop = false;
        this.preload = "";
        this.play = vi.fn(async () => {});
        this.pause = vi.fn();
        this.load = vi.fn();
        this.removeAttribute = vi.fn();
        this.addEventListener = vi.fn();
    }
}

function setup(overrides = {}) {
    const scenario = ref({id: 42});
    const isOwner = ref(true);
    const studioSandboxMode = ref(false);

    const deps = {
        scenario,
        isOwner,
        studioSandboxMode,
        getScenarioId: () => 42,
        fetchScenarioBackgroundAudios: vi.fn(async () => []),
        uploadScenarioBackgroundAudio: vi.fn(async () => ({id: 99})),
        deleteAudio: vi.fn(async () => {}),
        prepareAudioUploadFile: vi.fn(async (file) => file),
        fileBaseName: (file, fallback) => file?.name?.replace(/\.[^.]+$/, "") || fallback,
        audioImportErrorMessage: (e, action) => `Could not ${action}`,
        ...overrides,
    };

    const api = useBackgroundAmbience(deps);
    return {api, deps, scenario, isOwner, studioSandboxMode};
}

describe("useBackgroundAmbience", () => {
    let OriginalAudio;

    beforeEach(() => {
        OriginalAudio = global.Audio;
        global.Audio = FakeAudio;
        URL.createObjectURL = URL.createObjectURL || (() => "");
        vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:fake");
        toastMocks.success.mockReset();
        toastMocks.error.mockReset();
        toastMocks.info.mockReset();
    });

    afterEach(() => {
        global.Audio = OriginalAudio;
        vi.restoreAllMocks();
    });

    it("defaults to no active ambience", () => {
        const {api} = setup();
        expect(api.hasActiveBackgroundAmbience.value).toBe(false);
        expect(api.selectedAmbiencePreset.value.id).toBe("forest-morning");
        expect(api.backgroundSummaryTitle.value).toBe("Add ambience");
        expect(api.backgroundSummaryNote.value).toBe("");
    });

    it("opens the ambience panel without activating the default preset", () => {
        const {api} = setup();

        api.openAmbiencePanel();

        expect(api.ambiencePanelOpen.value).toBe(true);
        expect(api.ambiencePresetEnabled.value).toBe(false);
        expect(api.hasActiveBackgroundAmbience.value).toBe(false);
        expect(api.backgroundTitle.value).toBe("");
    });

    it("selectAmbiencePreset prepares the preset without marking it as added", () => {
        const {api} = setup();
        const preset = api.ambiencePresets.find((p) => p.id === "rain-window");

        api.selectAmbiencePreset(preset);

        expect(api.ambiencePresetEnabled.value).toBe(false);
        expect(api.selectedBackgroundAudioId.value).toBeNull();
        expect(api.backgroundTitle.value).toBe("Rain on window");
        expect(api.backgroundVolume.value).toBe(preset.volume);
        expect(api.hasActiveBackgroundAmbience.value).toBe(false);
        expect(api.backgroundSummaryTitle.value).toBe("Add ambience");
    });

    it("selectBackgroundAudio disables the preset and selects the real audio", () => {
        const {api} = setup();
        api.backgroundAudios.value = [{id: 5, title: "Field recording"}];

        api.selectBackgroundAudio(api.backgroundAudios.value[0]);

        expect(api.ambiencePresetEnabled.value).toBe(false);
        expect(api.selectedBackgroundAudioId.value).toBe(5);
        expect(api.selectedBackgroundAudio.value.title).toBe("Field recording");
    });

    it("loadBackgroundAudios populates the list and picks a selection", async () => {
        const {api, deps} = setup({
            fetchScenarioBackgroundAudios: vi.fn(async () => [{id: 1, title: "A"}, {id: 2, title: "B"}]),
        });

        await api.loadBackgroundAudios();

        expect(deps.fetchScenarioBackgroundAudios).toHaveBeenCalledWith(42);
        expect(api.backgroundAudios.value).toHaveLength(2);
        expect(api.selectedBackgroundAudioId.value).toBe(1);
    });

    it("loadBackgroundAudios is a no-op in sandbox mode", async () => {
        const {api, deps, studioSandboxMode} = setup();
        studioSandboxMode.value = true;

        await api.loadBackgroundAudios();

        expect(deps.fetchScenarioBackgroundAudios).not.toHaveBeenCalled();
    });

    it("loadBackgroundAudios clears state and does not throw on failure", async () => {
        const {api} = setup({
            fetchScenarioBackgroundAudios: vi.fn(async () => {
                throw new Error("network down");
            }),
        });
        api.backgroundAudios.value = [{id: 1}];

        await expect(api.loadBackgroundAudios()).resolves.toBeUndefined();
        expect(api.backgroundAudios.value).toEqual([]);
        expect(api.selectedBackgroundAudioId.value).toBeNull();
    });

    it("importBackgroundAudioFile uploads via the API and reloads the list", async () => {
        const {api, deps} = setup({
            fetchScenarioBackgroundAudios: vi.fn(async () => [{id: 99, title: "Vignette ambience"}]),
        });
        const file = new File(["x"], "clip.mp3", {type: "audio/mpeg"});

        await api.importBackgroundAudioFile(file);

        expect(deps.uploadScenarioBackgroundAudio).toHaveBeenCalled();
        expect(toastMocks.success).toHaveBeenCalledWith("Background audio added.");
    });

    it("importBackgroundAudioFile stores a local-only entry in sandbox mode", async () => {
        const {api, studioSandboxMode} = setup();
        studioSandboxMode.value = true;
        const file = new File(["x"], "clip.mp3", {type: "audio/mpeg"});

        await api.importBackgroundAudioFile(file);

        expect(api.backgroundAudios.value).toHaveLength(1);
        expect(String(api.backgroundAudios.value[0].id)).toMatch(/^background-local-/);
    });

    it("playBackgroundAudio plays the selected real audio and toggleBackgroundAudio pauses it", async () => {
        const {api} = setup();
        api.backgroundAudios.value = [{id: 7, title: "Clip", previewUrl: "blob:clip"}];
        api.selectBackgroundAudio(api.backgroundAudios.value[0]);

        await api.playBackgroundAudio();
        expect(api.backgroundPlaying.value).toBe(true);

        await api.toggleBackgroundAudio();
        expect(api.backgroundPlaying.value).toBe(false);
    });

    it("onAmbDragOver/onAmbDropZoneClick are no-ops for non-owners", () => {
        const {api, isOwner} = setup();
        isOwner.value = false;

        api.onAmbDragOver();
        expect(api.ambDragOver.value).toBe(false);
    });
});
