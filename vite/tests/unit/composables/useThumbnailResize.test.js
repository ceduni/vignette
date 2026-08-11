import {ref} from "vue";
import {useThumbnailResize} from "@/composables/useThumbnailResize";

function makeToast() {
    return {success: vi.fn(), error: vi.fn()};
}

function setup(overrides = {}) {
    const thumbnails = ref([{id: 1, gridColumnSpan: 4, gridRowSpan: 3}]);
    const selectedThumb = ref(thumbnails.value[0]);

    const deps = {
        thumbnails,
        selectedThumb,
        studioSandboxMode: ref(false),
        getScenarioId: () => 42,
        isPersistableThumbnailId: (id) => /^\d+$/.test(String(id ?? "")),
        updateThumbnailLayout: vi.fn(async () => {}),
        updateScenarioStoryboard: vi.fn(async () => {}),
        storyboardForm: ref({layoutMode: "PRESET", preset: "GRID_3", columns: 12}),
        applyLocalStoryboardState: vi.fn(),
        syncSelectedThumbnailFromList: vi.fn(),
        toast: makeToast(),
        ...overrides,
    };

    const api = useThumbnailResize(deps);
    return {api, deps, thumbnails, selectedThumb};
}

describe("useThumbnailResize", () => {
    it("applySelectedThumbnailSize clamps and persists the new layout", async () => {
        const {api, deps, thumbnails} = setup();

        await api.applySelectedThumbnailSize(20, 1, "Huge size");

        expect(thumbnails.value[0].gridColumnSpan).toBe(12);
        expect(thumbnails.value[0].gridRowSpan).toBe(2);
        expect(deps.applyLocalStoryboardState).toHaveBeenCalledWith({layoutMode: "CUSTOM"});
        expect(deps.updateThumbnailLayout).toHaveBeenCalledWith(1, expect.objectContaining({gridColumnSpan: 12, gridRowSpan: 2}));
        expect(deps.toast.success).toHaveBeenCalledWith("Huge size applied.");
        expect(api.savingLayout.value).toBe(false);
    });

    it("applySelectedThumbnailSize is a no-op without a selected thumbnail", async () => {
        const {api, deps, selectedThumb} = setup();
        selectedThumb.value = null;

        await api.applySelectedThumbnailSize(6, 4);

        expect(deps.updateThumbnailLayout).not.toHaveBeenCalled();
    });

    it("applySelectedThumbnailSize reverts local state and reports an error on failure", async () => {
        const {api, deps, thumbnails} = setup({
            updateThumbnailLayout: vi.fn(async () => { throw new Error("network down"); }),
        });

        await api.applySelectedThumbnailSize(8, 5);

        expect(thumbnails.value[0].gridColumnSpan).toBe(4);
        expect(thumbnails.value[0].gridRowSpan).toBe(3);
        expect(deps.toast.error).toHaveBeenCalledWith("network down");
    });

    it("applySelectedThumbnailSize skips the API call in sandbox mode", async () => {
        const {api, deps} = setup({studioSandboxMode: ref(true)});

        await api.applySelectedThumbnailSize(6, 4);

        expect(deps.updateThumbnailLayout).not.toHaveBeenCalled();
        expect(deps.toast.success).toHaveBeenCalled();
    });

    it("beginThumbnailResize does nothing without a grid ancestor element", async () => {
        const {api, deps} = setup();
        const event = {
            currentTarget: {closest: vi.fn(() => null)},
            preventDefault: vi.fn(),
        };

        await api.beginThumbnailResize({thumb: {id: 1}, direction: "right", event});

        expect(event.preventDefault).not.toHaveBeenCalled();
        expect(deps.applyLocalStoryboardState).not.toHaveBeenCalled();
    });

    it("beginThumbnailResize tracks pointer drag and resizes on the right/bottom axes", async () => {
        const {api, deps, thumbnails} = setup();
        const gridEl = {clientWidth: 1200};
        const listeners = {};
        const originalAdd = document.addEventListener;
        const originalRemove = document.removeEventListener;
        document.addEventListener = vi.fn((type, handler) => { listeners[type] = handler; });
        document.removeEventListener = vi.fn();

        const event = {
            currentTarget: {closest: vi.fn(() => gridEl), setPointerCapture: vi.fn()},
            preventDefault: vi.fn(),
            stopPropagation: vi.fn(),
            clientX: 0,
            clientY: 0,
            pointerId: 1,
        };

        await api.beginThumbnailResize({thumb: thumbnails.value[0], direction: "corner", event});

        expect(api.resizingThumbnailId.value).toBe(1);
        expect(event.preventDefault).toHaveBeenCalled();

        listeners.pointermove({pointerId: 1, clientX: 300, clientY: 200});
        expect(thumbnails.value[0].gridColumnSpan).toBeGreaterThan(4);

        await listeners.pointerup({pointerId: 1});

        expect(api.resizingThumbnailId.value).toBeNull();
        expect(deps.updateThumbnailLayout).toHaveBeenCalled();
        expect(deps.toast.success).toHaveBeenCalled();

        document.addEventListener = originalAdd;
        document.removeEventListener = originalRemove;
    });
});
