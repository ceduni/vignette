import {ref} from "vue";
import {useThumbnailDragReorder} from "@/composables/useThumbnailDragReorder";

function thumb(id, idx) {
    return {id, idx, title: `Scene ${idx}`};
}

function pointerEvent(type, props) {
    const event = new Event(type);
    Object.assign(event, props);
    return event;
}

function stubGrid(order) {
    const els = order.map((thumb, i) => ({
        dataset: {thumbnailId: String(thumb.id)},
        getBoundingClientRect: () => ({
            top: 0,
            bottom: 100,
            left: i * 100,
            right: i * 100 + 90,
            width: 90,
            height: 100,
        }),
    }));

    document.elementFromPoint = document.elementFromPoint || (() => null);
    vi.spyOn(document, "querySelector").mockImplementation((sel) =>
        sel === ".storyboard-grid" ? {querySelectorAll: () => els} : null
    );
    vi.spyOn(document, "elementFromPoint").mockImplementation((x) => {
        const index = Math.floor(x / 100);
        const el = els[index];
        return el ? {closest: () => el} : null;
    });
}

describe("useThumbnailDragReorder", () => {
    beforeEach(() => {
        window.scrollBy = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("drives visualThumbnails from the sorted list while idle", () => {
        const thumbnails = ref([thumb(1, 1), thumb(2, 2), thumb(3, 3)]);
        const {visualThumbnails, tileDragId} = useThumbnailDragReorder(thumbnails);

        expect(tileDragId.value).toBeNull();
        expect(visualThumbnails.value.map((t) => t.id)).toEqual([1, 2, 3]);
    });

    it("drags the first tile to the last position and persists the new order", async () => {
        const thumbnails = ref([thumb(1, 1), thumb(2, 2), thumb(3, 3)]);
        stubGrid(thumbnails.value);
        const applyOrder = vi.fn();

        const {onTilePointerReorderStart, tileDragId, visualThumbnails} = useThumbnailDragReorder(thumbnails, {
            applyOrder,
        });

        const handleEl = {
            closest: () => ({getBoundingClientRect: () => ({top: 0, left: 0, width: 90, height: 100})}),
            setPointerCapture: vi.fn(),
        };

        onTilePointerReorderStart(
            {
                pointerType: "mouse",
                button: 0,
                pointerId: 1,
                clientX: 10,
                clientY: 10,
                currentTarget: handleEl,
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            },
            thumbnails.value[0]
        );

        expect(tileDragId.value).toBe(1);

        window.dispatchEvent(pointerEvent("pointermove", {pointerId: 1, clientX: 250, clientY: 10}));
        await Promise.resolve();

        expect(visualThumbnails.value.map((t) => t.id)).toEqual([2, 3, 1]);

        window.dispatchEvent(pointerEvent("pointerup", {pointerId: 1, clientX: 250, clientY: 10}));
        await Promise.resolve();

        expect(tileDragId.value).toBeNull();
        expect(applyOrder).toHaveBeenCalledTimes(1);
        expect(applyOrder.mock.calls[0][0].map((t) => t.id)).toEqual([2, 3, 1]);
    });

    it("does not call applyOrder when the drag ends without moving", async () => {
        const thumbnails = ref([thumb(1, 1), thumb(2, 2)]);
        stubGrid(thumbnails.value);
        const applyOrder = vi.fn();

        const {onTilePointerReorderStart} = useThumbnailDragReorder(thumbnails, {applyOrder});

        const handleEl = {
            closest: () => ({getBoundingClientRect: () => ({top: 0, left: 0, width: 90, height: 100})}),
            setPointerCapture: vi.fn(),
        };

        onTilePointerReorderStart(
            {
                pointerType: "mouse",
                button: 0,
                pointerId: 2,
                clientX: 10,
                clientY: 10,
                currentTarget: handleEl,
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            },
            thumbnails.value[0]
        );

        window.dispatchEvent(pointerEvent("pointerup", {pointerId: 2, clientX: 10, clientY: 10}));
        await Promise.resolve();

        expect(applyOrder).not.toHaveBeenCalled();
    });
});
