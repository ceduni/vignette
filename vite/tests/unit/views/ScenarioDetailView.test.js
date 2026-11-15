import {nextTick} from "vue";
import {mountWithRouter} from "../../helpers/mountWithRouter";
import ScenarioDetailView from "@/views/ScenarioDetailView.vue";

const localStorageValues = new Map();
Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
        getItem: (key) => localStorageValues.get(key) ?? null,
        setItem: (key, value) => localStorageValues.set(key, String(value)),
        removeItem: (key) => localStorageValues.delete(key),
        clear: () => localStorageValues.clear(),
    },
});

Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    writable: true,
    value: vi.fn(() => "blob:scenario-detail-test"),
});

Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    writable: true,
    value: vi.fn(),
});

const apiMocks = vi.hoisted(() => ({
    loadMe: vi.fn(),

    fetchScenario: vi.fn(),
    fetchScenarioBackgroundAudios: vi.fn(),
    fetchScenarioThumbnails: vi.fn(),
    fetchThumbnailAudios: vi.fn(),
    fetchAccreditationRequests: vi.fn(),
    fetchLanguage: vi.fn(),
    deleteAudio: vi.fn(),
    deleteScenario: vi.fn(),
    deleteThumbnail: vi.fn(),
    publishScenario: vi.fn(),
    selectScenarioBackgroundAudio: vi.fn(),
    replaceAudioContent: vi.fn(),
    reorderScenarioThumbnails: vi.fn(),
    updateAudioGloss: vi.fn(),
    updateScenarioMetadata: vi.fn(),
    updateScenarioStoryboard: vi.fn(),
    updateThumbnailLayout: vi.fn(),
    updateThumbnailTitle: vi.fn(),
    uploadScenarioThumbnail: vi.fn(),
    uploadScenarioBackgroundAudio: vi.fn(),
    uploadThumbnailAudio: vi.fn(),

    toastSuccess: vi.fn(),
    toastError: vi.fn(),
    toastInfo: vi.fn(),
}));

const autoplayApi = vi.hoisted(() => ({
    currentIndex: {value: -1},
    currentItem: {value: null},
    currentTime: {value: 0},
    duration: {value: 0},
    progressPercent: 0,
    isPlaying: false,
    isPaused: false,
    isLoading: false,
    autoContinue: {value: true},
    loopScenario: {value: false},
    playFromStart: vi.fn(),
    playFromIndex: vi.fn(async (index) => {
        autoplayApi.currentIndex.value = index;
    }),
    replayCurrent: vi.fn(),
    resume: vi.fn(),
    pause: vi.fn(),
    stop: vi.fn(),
    next: vi.fn(),
    previous: vi.fn(),
    seekToPercent: vi.fn(),
    seekToSeconds: vi.fn(),
    formatTime: vi.fn((value) => `t:${value}`),
    toggleAutoContinue: vi.fn(() => {
        autoplayApi.autoContinue.value = !autoplayApi.autoContinue.value;
    }),
    toggleLoopScenario: vi.fn(() => {
        autoplayApi.loopScenario.value = !autoplayApi.loopScenario.value;
    }),
}));

vi.mock("@/api/languages", () => ({
    fetchLanguage: apiMocks.fetchLanguage,
}));

vi.mock("@/api/scenarios", () => ({
    deleteAudio: apiMocks.deleteAudio,
    deleteScenario: apiMocks.deleteScenario,
    deleteThumbnail: apiMocks.deleteThumbnail,
    fetchScenario: apiMocks.fetchScenario,
    fetchScenarioBackgroundAudios: apiMocks.fetchScenarioBackgroundAudios,
    fetchScenarioThumbnails: apiMocks.fetchScenarioThumbnails,
    fetchThumbnailAudios: apiMocks.fetchThumbnailAudios,
    publishScenario: apiMocks.publishScenario,
    selectScenarioBackgroundAudio: apiMocks.selectScenarioBackgroundAudio,
    replaceAudioContent: apiMocks.replaceAudioContent,
    reorderScenarioThumbnails: apiMocks.reorderScenarioThumbnails,
    updateAudioGloss: apiMocks.updateAudioGloss,
    updateScenarioMetadata: apiMocks.updateScenarioMetadata,
    updateScenarioStoryboard: apiMocks.updateScenarioStoryboard,
    updateThumbnailLayout: apiMocks.updateThumbnailLayout,
    updateThumbnailTitle: apiMocks.updateThumbnailTitle,
    uploadScenarioBackgroundAudio: apiMocks.uploadScenarioBackgroundAudio,
    uploadScenarioThumbnail: apiMocks.uploadScenarioThumbnail,
    uploadThumbnailAudio: apiMocks.uploadThumbnailAudio,
}));

vi.mock("@/api/community", () => ({
    fetchAccreditationRequests: apiMocks.fetchAccreditationRequests,
}));

vi.mock("@/composables/useAuth", () => ({
    useAuth: () => ({
        currentUser: {
            value: {
                id: 50,
                username: "ownerUser",
            },
        },
        isAuthenticated: {value: true},
        loadMe: apiMocks.loadMe,
    }),
}));

vi.mock("@/composables/useToast", () => ({
    useToast: () => ({
        success: apiMocks.toastSuccess,
        error: apiMocks.toastError,
        info: apiMocks.toastInfo,
    }),
}));

vi.mock("@/composables/useScenarioAutoplay", () => ({
    useScenarioAutoplay: () => autoplayApi,
}));

vi.mock("@/components/ThumbnailCard.vue", () => ({
    default: {
        name: "ThumbnailCard",
        props: ["thumb", "audios", "selected", "highlighted", "quickRecording"],
        emits: ["select"],
        template: `
          <div class="thumbnail-card-stub-wrap">
            <button
                class="thumbnail-card-stub"
                :data-id="thumb.id"
                :data-selected="selected ? 'true' : 'false'"
                @click="$emit('select', thumb)"
            >
              {{ thumb.title || thumb.id }}
            </button>
          </div>
        `,
    },
}));

vi.mock("@/components/StudioRecorderPanel.vue", () => ({
    default: {
        name: "StudioRecorderPanel",
        props: ["selectedThumb", "selectedAudios", "selectedVoiceId"],
        emits: ["select-voice", "open-layout"],
        template: `
          <div class="studio-recorder-panel-stub">
            <span class="studio-panel-thumb">{{ selectedThumb?.id ?? 'none' }}</span>
            <span class="studio-panel-audios">{{ selectedAudios.length }}</span>
            <span class="studio-panel-selected">{{ selectedVoiceId ?? 'none' }}</span>
            <button class="studio-panel-select" @click="$emit('select-voice', selectedAudios[0] || null)">select</button>
            <button class="studio-panel-layout" @click="$emit('open-layout')">layout</button>
          </div>
        `,
    },
}));

vi.mock("@/components/AudioPanel.vue", () => ({
    default: {
        name: "AudioPanel",
        props: ["selectedThumb", "audios", "activeAudioId", "activeAudioTitle", "playerState", "isOwner"],
        emits: ["uploaded", "play-audio"],
        template: `
          <div class="audio-panel-stub">
            <span class="audio-panel-thumb">{{ selectedThumb?.id ?? 'none' }}</span>
            <span class="audio-panel-audios">{{ audios.length }}</span>
            <button class="audio-panel-uploaded" @click="$emit('uploaded')">uploaded</button>
            <button
                class="audio-panel-play"
                @click="$emit('play-audio', audios[0] || null)"
            >
              play
            </button>
          </div>
        `,
    },
}));

vi.mock("@/components/ui/BasePageHeader.vue", () => ({
    default: {
        name: "BasePageHeader",
        props: ["title", "subtitle"],
        template: `
          <section class="base-page-header-stub">
            <h1>{{ title }}</h1>
            <p>{{ subtitle }}</p>
            <div>
              <slot/>
            </div>
            <div>
              <slot name="actions"/>
            </div>
          </section>
        `,
    },
}));

vi.mock("@/components/ui/BaseLoader.vue", () => ({
    default: {
        name: "BaseLoader",
        template: `<div class="base-loader-stub"><slot /></div>`,
    },
}));

vi.mock("@/components/ui/BaseAlert.vue", () => ({
    default: {
        name: "BaseAlert",
        props: ["type"],
        template: `
          <div class="base-alert-stub" :data-type="type">
            <slot/>
          </div>`,
    },
}));

vi.mock("@/components/ui/BaseEmptyState.vue", () => ({
    default: {
        name: "BaseEmptyState",
        props: ["title", "message"],
        template: `
          <div class="empty-state-stub">{{ title }} - {{ message }}</div>`,
    },
}));

vi.mock("@/components/ui/BaseBadge.vue", () => ({
    default: {
        name: "BaseBadge",
        props: ["variant"],
        template: `<span class="base-badge-stub" :data-variant="variant"><slot/></span>`,
    },
}));

function baseScenario(overrides = {}) {
    return {
        id: 77,
        title: "Scenario Alpha",
        description: "A useful scenario",
        authorUsername: "ownerUser",
        languageId: 42,
        visibilityStatus: "DRAFT",
        storyboardLayoutMode: "PRESET",
        storyboardPreset: "GRID_3",
        storyboardColumns: 3,
        tags: ["animals", "daily life"],
        ...overrides,
    };
}

function baseThumbnails() {
    return [
        {
            id: 10,
            idx: 2,
            title: "Second",
            imageWidth: 1000,
            imageHeight: 800,
            gridColumn: null,
            gridRow: null,
            gridColumnSpan: 1,
            gridRowSpan: 1,
        },
        {
            id: 5,
            idx: 1,
            title: "First",
            imageWidth: 800,
            imageHeight: 1200,
            gridColumn: 2,
            gridRow: 3,
            gridColumnSpan: 2,
            gridRowSpan: 1,
        },
    ];
}

function audioMapByThumb() {
    return {
        5: [
            {
                id: 501,
                idx: 2,
                title: "First-thumb audio B",
                markerX: 33,
                markerY: 40,
                markerLabel: "B",
            },
            {
                id: 500,
                idx: 1,
                title: "First-thumb audio A",
                markerX: 11,
                markerY: 22,
                markerLabel: "A",
            },
        ],
        10: [
            {
                id: 1000,
                idx: 1,
                title: "Second-thumb audio",
                markerX: null,
                markerY: null,
                markerLabel: null,
            },
        ],
    };
}

async function flushPromises(times = 8) {
    for (let i = 0; i < times; i += 1) {
        await Promise.resolve();
        await nextTick();
    }
}

async function mountScenarioView({
                                     scenario = baseScenario(),
                                     thumbnails = baseThumbnails(),
                                     audioMap = audioMapByThumb(),
                                 } = {}) {
    apiMocks.fetchScenario.mockResolvedValue(scenario);
    apiMocks.fetchScenarioBackgroundAudios.mockResolvedValue([]);
    apiMocks.fetchAccreditationRequests.mockResolvedValue([]);
    apiMocks.fetchLanguage.mockResolvedValue({id: 42, name: "Chuj"});
    apiMocks.fetchScenarioThumbnails.mockResolvedValue(thumbnails);
    apiMocks.fetchThumbnailAudios.mockImplementation(async (thumbId) => audioMap[thumbId] || []);
    apiMocks.publishScenario.mockResolvedValue({
        ...scenario,
        visibilityStatus: "PUBLISHED",
    });
    apiMocks.updateScenarioStoryboard.mockImplementation(async (_, body) => ({
        ...scenario,
        storyboardLayoutMode: body.layoutMode,
        storyboardPreset: body.preset,
        storyboardColumns: body.columns,
    }));
    apiMocks.updateThumbnailLayout.mockResolvedValue({});
    apiMocks.updateAudioGloss.mockResolvedValue({});
    apiMocks.updateScenarioMetadata.mockResolvedValue({});
    apiMocks.reorderScenarioThumbnails.mockResolvedValue({});
    apiMocks.uploadScenarioThumbnail.mockResolvedValue({});
    apiMocks.uploadScenarioBackgroundAudio.mockResolvedValue({});
    apiMocks.uploadThumbnailAudio.mockResolvedValue({});

    const {wrapper, router} = await mountWithRouter(ScenarioDetailView, {
        routes: [
            {
                path: "/scenarios",
                component: {template: "<div />"},
            },
            {
                path: "/scenarios/:id",
                component: ScenarioDetailView,
                props: true,
            },
        ],
        initialRoute: "/scenarios/77",
        mountOptions: {
            props: {
                id: "77",
            },
        },
    });

    await flushPromises(10);

    return {wrapper, router};
}

async function clickButtonByText(wrapper, text) {
    const button = wrapper.findAll("button").find((b) => b.text().includes(text));
    expect(button).toBeTruthy();
    await button.trigger("click");
    await flushPromises();
    await nextTick();
    return button;
}

describe("ScenarioDetailView", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();

        autoplayApi.currentIndex.value = -1;
        autoplayApi.currentItem.value = null;
        autoplayApi.currentTime.value = 0;
        autoplayApi.duration.value = 0;
        autoplayApi.autoContinue.value = true;
        autoplayApi.loopScenario.value = false;
    });

    it("loads scenario, language, thumbnails and audios on mount", async () => {
        const {wrapper} = await mountScenarioView();

        expect(apiMocks.loadMe).toHaveBeenCalled();
        expect(apiMocks.fetchScenario).toHaveBeenCalledWith("77");
        expect(apiMocks.fetchLanguage).toHaveBeenCalledWith(42);
        expect(apiMocks.fetchScenarioThumbnails).toHaveBeenCalledWith("77");
        expect(apiMocks.fetchThumbnailAudios).toHaveBeenCalledTimes(2);
        expect(apiMocks.fetchScenarioBackgroundAudios).toHaveBeenCalledWith("77");

        expect(wrapper.text()).toContain("Scenario Alpha");
        expect(wrapper.text()).toContain("Vignette ambience");
        expect(wrapper.find(".rec-panel-title").text()).toContain("Studio audio");
        expect(wrapper.text()).toContain("First-thumb audio A");
    });

    it("falls back to Unknown language when language fetch fails", async () => {
        apiMocks.fetchLanguage.mockRejectedValueOnce(new Error("boom"));
        const {wrapper} = await mountScenarioView();

        expect(wrapper.text()).toContain("Scenario Alpha");

        const infoButton = wrapper.find('button[title="Scenario info"]');
        await infoButton.trigger("click");
        await flushPromises();
        await nextTick();

        expect(wrapper.text()).toContain("Unknown language");
    });

    it("shows scenario tags in the info dialog", async () => {
        const {wrapper} = await mountScenarioView();

        const infoButton = wrapper.find('button[title="Scenario info"]');
        await infoButton.trigger("click");
        await flushPromises();
        await nextTick();

        expect(wrapper.text()).toContain("#animals");
        expect(wrapper.text()).toContain("#daily life");
    });

    it("shows an error alert when initial loading fails", async () => {
        apiMocks.fetchScenario.mockRejectedValueOnce(new Error("Load failed"));

        const {wrapper} = await mountWithRouter(ScenarioDetailView, {
            routes: [
                {
                    path: "/scenarios",
                    component: {template: "<div />"},
                },
                {
                    path: "/scenarios/:id",
                    component: ScenarioDetailView,
                    props: true,
                },
            ],
            initialRoute: "/scenarios/77",
            mountOptions: {
                props: {id: "77"},
            },
        });

        await flushPromises();

        expect(wrapper.find('.base-alert-stub[data-type="error"]').exists()).toBe(true);
        expect(wrapper.text()).toContain("Load failed");
    });

    it("selects another thumbnail when clicking a thumbnail card", async () => {
        const {wrapper} = await mountScenarioView();

        await clickButtonByText(wrapper, "Storyboard");
        const buttons = wrapper.findAll(".thumbnail-card-stub");
        expect(buttons.map((b) => b.text())).toEqual(["First", "Second"]);

        await buttons[1].trigger("click");
        await flushPromises();
        await nextTick();

        const updatedButtons = wrapper.findAll(".thumbnail-card-stub");
        expect(updatedButtons[1].attributes("data-selected")).toBe("true");
    });

    it("publishes the scenario and updates UI", async () => {
        const {wrapper} = await mountScenarioView();

        const publishButton = wrapper.findAll("button")
            .find((b) => b.text().includes("Publish →"));

        expect(publishButton).toBeTruthy();

        await publishButton.trigger("click");
        await flushPromises();

        expect(apiMocks.publishScenario).toHaveBeenCalledWith("77");
        expect(apiMocks.toastSuccess).toHaveBeenCalledWith("Scenario published.");
        expect(wrapper.text()).toContain("Published");
    });

    it("saves storyboard settings with normalized numeric columns", async () => {
        const {wrapper} = await mountScenarioView();

        await wrapper.find('.vg-tab').trigger("click");
        await flushPromises();

        const openSettingsButton = wrapper.find('button[title="Storyboard settings"]');
        await openSettingsButton.trigger("click");
        await flushPromises();
        await nextTick();

        const saveButton = wrapper.findAll("button")
            .find((b) => b.text().includes("Save settings"));

        await saveButton.trigger("click");
        await flushPromises();

        expect(apiMocks.updateScenarioStoryboard).toHaveBeenCalledWith("77", {
            layoutMode: "PRESET",
            preset: "GRID_3",
            columns: 3,
        });
        expect(apiMocks.toastSuccess).toHaveBeenCalledWith("Storyboard settings saved.");
    });

    it("keeps the publication update reminder after a reload and clears it after confirmation", async () => {
        const publishedScenario = {...baseScenario(), visibilityStatus: "PUBLISHED"};
        const firstMount = await mountScenarioView({scenario: publishedScenario});

        await firstMount.wrapper.find('.vg-tab').trigger("click");
        await flushPromises();
        await firstMount.wrapper.find('button[title="Storyboard settings"]').trigger("click");
        await flushPromises();
        const saveButton = firstMount.wrapper.findAll("button")
            .find((button) => button.text().includes("Save settings"));
        await saveButton.trigger("click");
        await flushPromises();

        expect(localStorage.getItem("vignette:scenario:77:publication-edit")).toBe("1");
        firstMount.wrapper.unmount();

        const secondMount = await mountScenarioView({scenario: publishedScenario});
        await flushPromises();
        expect(secondMount.wrapper.text()).toContain("Confirm revision");

        await clickButtonByText(secondMount.wrapper, "Confirm revision");
        expect(secondMount.wrapper.text()).toContain("Your saved changes are already visible to readers");
        await secondMount.wrapper.find(".ms-confirm__delete").trigger("click");
        await flushPromises();

        expect(localStorage.getItem("vignette:scenario:77:publication-edit")).toBeNull();
        expect(apiMocks.toastSuccess).toHaveBeenCalledWith("Live scenario updated.");
    });

    it("saves custom storyboard settings from the settings dialog", async () => {
        const {wrapper} = await mountScenarioView();

        await wrapper.find('.vg-tab').trigger("click");
        await flushPromises();

        const openSettingsButton = wrapper.find('button[title="Storyboard settings"]');
        await openSettingsButton.trigger("click");
        await flushPromises();
        await nextTick();

        await clickButtonByText(wrapper, "Custom");

        const saveButton = wrapper.findAll("button")
            .find((b) => b.text().includes("Save settings"));

        await saveButton.trigger("click");
        await flushPromises();

        expect(apiMocks.updateScenarioStoryboard).toHaveBeenCalledWith("77", {
            layoutMode: "CUSTOM",
            preset: "GRID_3",
            columns: 3,
        });
        expect(apiMocks.toastSuccess).toHaveBeenCalledWith("Storyboard settings saved.");
    });

    it("opens upload dialog and uploads an image successfully", async () => {
        const {wrapper} = await mountScenarioView();

        const openUploadButton = wrapper.find('button[title="Add a scene"]');
        await openUploadButton.trigger("click");
        await flushPromises();
        await nextTick();

        const fileInput = wrapper.find("input.ud-file-input");
        const file = new File(["fake-image"], "thumb.png", {type: "image/png"});

        Object.defineProperty(fileInput.element, "files", {
            value: [file],
            configurable: true,
        });

        await fileInput.trigger("change");

        const titleInput = wrapper.find("input.ud-grid-title");
        await titleInput.setValue("New thumb");

        const uploadButton = wrapper.findAll("button")
            .find((b) => b.text().includes("Add scene to storyboard"));

        await uploadButton.trigger("click");
        await flushPromises();

        expect(apiMocks.uploadScenarioThumbnail).toHaveBeenCalledTimes(1);
        expect(apiMocks.fetchScenarioThumbnails).toHaveBeenCalledTimes(2);
        expect(apiMocks.toastSuccess).toHaveBeenCalledWith("1 scene uploaded.");
    });

    it("keeps upload disabled when no image is selected", async () => {
        const {wrapper} = await mountScenarioView();

        const openUploadButton = wrapper.find('button[title="Add a scene"]');
        await openUploadButton.trigger("click");
        await flushPromises();
        await nextTick();

        const uploadButton = wrapper.findAll("button")
            .find((b) => b.text().includes("Add scene to storyboard"));

        expect(uploadButton.attributes("disabled")).toBeDefined();
        expect(apiMocks.uploadScenarioThumbnail).not.toHaveBeenCalled();
    });

    it("opens storyboard settings from the storyboard toolbar", async () => {
        const {wrapper} = await mountScenarioView();

        await wrapper.find('.vg-tab').trigger("click");
        await flushPromises();

        await wrapper.find('button[title="Storyboard settings"]').trigger("click");
        await flushPromises();

        expect(wrapper.text()).toContain("Layout & Publication");
    });

    it("selects another take from the studio audio panel", async () => {
        const {wrapper} = await mountScenarioView();

        const voiceButtons = wrapper.findAll(".rec-speakers button");
        const takeBButton = voiceButtons.find((button) => button.text().includes("B"));
        expect(takeBButton).toBeTruthy();
        await takeBButton.trigger("click");
        await flushPromises();

        expect(takeBButton.classes()).toContain("active");
    });

    it("handles audio fetch failure for a thumbnail without crashing", async () => {
        apiMocks.fetchScenario.mockResolvedValue(baseScenario());
        apiMocks.fetchScenarioBackgroundAudios.mockResolvedValue([]);
        apiMocks.fetchAccreditationRequests.mockResolvedValue([]);
        apiMocks.fetchLanguage.mockResolvedValue({id: 42, name: "Chuj"});
        apiMocks.fetchScenarioThumbnails.mockResolvedValue(baseThumbnails());
        apiMocks.fetchThumbnailAudios
            .mockResolvedValueOnce(audioMapByThumb()[5])
            .mockRejectedValueOnce(new Error("audio load failed"));

        const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {
        });

        const {wrapper} = await mountWithRouter(ScenarioDetailView, {
            routes: [
                {
                    path: "/scenarios",
                    component: {template: "<div />"},
                },
                {
                    path: "/scenarios/:id",
                    component: ScenarioDetailView,
                    props: true,
                },
            ],
            initialRoute: "/scenarios/77",
            mountOptions: {
                props: {id: "77"},
            },
        });

        await flushPromises();

        expect(wrapper.text()).toContain("Scenario Alpha");
        expect(consoleSpy).toHaveBeenCalled();
        consoleSpy.mockRestore();
    });
});
