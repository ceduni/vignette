import {ref} from "vue";
import {useSceneImageUpload} from "@/composables/useSceneImageUpload";

function makeToast() {
    return {success: vi.fn(), error: vi.fn(), info: vi.fn()};
}

function makeFile(name, type = "image/png") {
    return new File(["x"], name, {type});
}

function setup(overrides = {}) {
    const scenario = ref({id: 42, title: "My scenario"});
    const thumbnails = ref([]);
    const selectedThumb = ref(null);
    const sortedThumbnails = ref([]);
    const studioSandboxMode = ref(false);
    const pendingRouteDraftId = ref(null);

    const deps = {
        scenario,
        thumbnails,
        selectedThumb,
        sortedThumbnails,
        studioSandboxMode,
        getScenarioId: () => 42,
        pendingRouteDraftId,
        applyUnclaimedDraftAudio: vi.fn(async () => {}),
        loadThumbs: vi.fn(async () => {}),
        uploadScenarioThumbnail: vi.fn(async () => ({})),
        toast: makeToast(),
        fileBaseName: (file, fallback) => file?.name?.replace(/\.[^.]+$/, "") || fallback,
        ...overrides,
    };

    const api = useSceneImageUpload(deps);
    return {api, deps, scenario, thumbnails, selectedThumb, studioSandboxMode, pendingRouteDraftId};
}

describe("useSceneImageUpload", () => {
    let originalCreateObjectURL, originalRevokeObjectURL;

    beforeEach(() => {
        originalCreateObjectURL = URL.createObjectURL;
        originalRevokeObjectURL = URL.revokeObjectURL;
        URL.createObjectURL = vi.fn(() => "blob:fake");
        URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        URL.createObjectURL = originalCreateObjectURL;
        URL.revokeObjectURL = originalRevokeObjectURL;
    });

    it("openUploadDialog clears errors and opens the dialog", () => {
        const {api} = setup();
        api.uploadError.value = "boom";
        api.openUploadDialog();
        expect(api.uploadDialogOpen.value).toBe(true);
        expect(api.uploadError.value).toBe("");
    });

    it("onImageChange adds supported image files and rejects unsupported ones", () => {
        const {api, deps} = setup();

        api.onImageChange({target: {files: [makeFile("scene.png"), makeFile("notes.txt", "text/plain")], value: "x"}});

        expect(api.uploadFiles.value).toHaveLength(1);
        expect(api.uploadFiles.value[0].title).toBe("scene");
        expect(deps.toast.info).toHaveBeenCalled();
    });

    it("onImageChange reports an error when nothing usable was picked", () => {
        const {api} = setup();

        api.onImageChange({target: {files: [makeFile("notes.txt", "text/plain")], value: "x"}});

        expect(api.uploadFiles.value).toHaveLength(0);
        expect(api.uploadError.value).toMatch(/No usable scene image/);
    });

    it("removeUploadEntry removes the entry and revokes its preview URL", () => {
        const {api} = setup();
        api.onImageChange({target: {files: [makeFile("a.png")], value: "x"}});

        api.removeUploadEntry(0);

        expect(api.uploadFiles.value).toHaveLength(0);
        expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:fake");
    });

    it("onVignetteMakerInsert closes the maker, opens the upload dialog, and queues the generated file", () => {
        const {api} = setup();
        api.vignetteMakerOpen.value = true;

        api.onVignetteMakerInsert(new Blob(["x"], {type: "image/png"}));

        expect(api.vignetteMakerOpen.value).toBe(false);
        expect(api.uploadDialogOpen.value).toBe(true);
        expect(api.uploadFiles.value).toHaveLength(1);
        expect(api.uploadFiles.value[0].title).toBe("My scenario – scene");
    });

    it("uploadImage errors out when no files are queued", async () => {
        const {api} = setup();
        await api.uploadImage();
        expect(api.uploadError.value).toMatch(/Choose an image first/);
    });

    it("uploadImage adds local thumbnails directly in sandbox mode", async () => {
        const {api, deps, thumbnails, studioSandboxMode} = setup();
        studioSandboxMode.value = true;
        api.onImageChange({target: {files: [makeFile("a.png")], value: "x"}});

        await api.uploadImage();

        expect(thumbnails.value).toHaveLength(1);
        expect(deps.uploadScenarioThumbnail).not.toHaveBeenCalled();
        expect(deps.toast.success).toHaveBeenCalledWith("1 scene added.");
        expect(api.uploadDialogOpen.value).toBe(false);
    });

    it("uploadImage uploads via the API and reloads thumbnails otherwise", async () => {
        const {api, deps} = setup();
        api.onImageChange({target: {files: [makeFile("a.png"), makeFile("b.png")], value: "x"}});

        await api.uploadImage();

        expect(deps.uploadScenarioThumbnail).toHaveBeenCalledTimes(2);
        expect(deps.loadThumbs).toHaveBeenCalled();
        expect(deps.toast.success).toHaveBeenCalledWith("2 scenes uploaded.");
        expect(api.uploadDialogOpen.value).toBe(false);
    });

    it("uploadImage applies a pending draft audio to the first uploaded thumbnail", async () => {
        const {api, deps, pendingRouteDraftId} = setup();
        pendingRouteDraftId.value = "draft-1";
        api.onImageChange({target: {files: [makeFile("a.png")], value: "x"}});

        await api.uploadImage();

        expect(deps.applyUnclaimedDraftAudio).toHaveBeenCalled();
    });

    it("uploadImage reports a friendly error message when the upload fails", async () => {
        const {api, deps} = setup({
            uploadScenarioThumbnail: vi.fn(async () => {
                throw new Error("413 too large");
            }),
        });
        api.onImageChange({target: {files: [makeFile("a.png")], value: "x"}});

        await api.uploadImage();

        expect(api.uploadError.value).toMatch(/too large/);
        expect(deps.toast.error).toHaveBeenCalled();
        expect(api.uploadingProgress.value).toBe(0);
    });

    it("closeUploadDialog clears queued files and revokes their preview URLs", () => {
        const {api} = setup();
        api.onImageChange({target: {files: [makeFile("a.png")], value: "x"}});

        api.closeUploadDialog();

        expect(api.uploadDialogOpen.value).toBe(false);
        expect(api.uploadFiles.value).toHaveLength(0);
        expect(URL.revokeObjectURL).toHaveBeenCalled();
    });
});
