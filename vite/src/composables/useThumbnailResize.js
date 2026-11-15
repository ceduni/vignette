import {ref} from "vue";
import {clamp, nullableInt, safeNumber} from "@/utils/scenarioStoryboard.js";

export function useThumbnailResize(options = {}) {
    const {
        thumbnails,
        selectedThumb,
        studioSandboxMode,
        getScenarioId,
        isPersistableThumbnailId,
        updateThumbnailLayout,
        updateScenarioStoryboard,
        storyboardForm,
        applyLocalStoryboardState,
        syncSelectedThumbnailFromList,
        toast,
    } = options;

    const resizingThumbnailId = ref(null);
    const savingLayout = ref(false);

    function updateThumbnailLayoutLocally(thumbId, nextLayout) {
        thumbnails.value = thumbnails.value.map((thumb) =>
            String(thumb.id) === String(thumbId) ? {...thumb, ...nextLayout} : thumb
        );
        syncSelectedThumbnailFromList(thumbId);
    }

    async function persistThumbnailLayoutChange(thumbId, nextLayout) {
        if (
            studioSandboxMode.value ||
            String(getScenarioId()).startsWith("emergency-") ||
            !isPersistableThumbnailId(thumbId)
        ) return;

        await Promise.all([
            updateThumbnailLayout(thumbId, nextLayout),
            updateScenarioStoryboard(getScenarioId(), {
                layoutMode: "CUSTOM",
                preset: storyboardForm.value.preset || "GRID_3",
                columns: 12,
            }),
        ]);
    }

    async function applySelectedThumbnailSize(columnSpan, rowSpan, label = "Custom size") {
        if (!selectedThumb.value || savingLayout.value) return;
        const thumbId = selectedThumb.value.id;
        const previous = thumbnails.value.find((thumb) => String(thumb.id) === String(thumbId));
        const nextLayout = {
            gridColumn: nullableInt(selectedThumb.value.gridColumn),
            gridRow: nullableInt(selectedThumb.value.gridRow),
            gridColumnSpan: clamp(Math.round(columnSpan), 2, 12),
            gridRowSpan: clamp(Math.round(rowSpan), 2, 10),
        };

        savingLayout.value = true;
        applyLocalStoryboardState({layoutMode: "CUSTOM"});
        updateThumbnailLayoutLocally(thumbId, nextLayout);
        try {
            await persistThumbnailLayoutChange(thumbId, nextLayout);
            toast.success(`${label} applied.`);
        } catch (e) {
            if (previous) updateThumbnailLayoutLocally(thumbId, previous);
            toast.error(e.message || "Could not save scene size.");
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
        const pointerId = event.pointerId;
        const previous = thumbnails.value.find((item) => String(item.id) === String(thumb.id));
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
            if (moveEvent.pointerId != null && moveEvent.pointerId !== pointerId) return;
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

        async function onUp(upEvent) {
            if (upEvent?.pointerId != null && upEvent.pointerId !== pointerId) return;
            document.removeEventListener("pointermove", onMove);
            document.removeEventListener("pointerup", onUp);
            document.removeEventListener("pointercancel", onCancel);
            resizingThumbnailId.value = null;

            const updated = thumbnails.value.find((item) => String(item.id) === String(thumb.id));
            if (updated) {
                selectedThumb.value = updated;
            }

            const nextLayout = {
                gridColumn: updated?.gridColumn ?? null,
                gridRow: updated?.gridRow ?? null,
                gridColumnSpan: latestColumnSpan,
                gridRowSpan: latestRowSpan,
            };
            try {
                await persistThumbnailLayoutChange(thumb.id, nextLayout);
                toast.success(`Scene resized to ${latestColumnSpan} × ${latestRowSpan}.`);
            } catch (e) {
                if (previous) updateThumbnailLayoutLocally(thumb.id, previous);
                toast.error(e.message || "Could not save the new scene size.");
            }
        }

        function onCancel(cancelEvent) {
            if (cancelEvent?.pointerId != null && cancelEvent.pointerId !== pointerId) return;
            document.removeEventListener("pointermove", onMove);
            document.removeEventListener("pointerup", onUp);
            document.removeEventListener("pointercancel", onCancel);
            resizingThumbnailId.value = null;
            if (previous) updateThumbnailLayoutLocally(thumb.id, previous);
        }

        event.currentTarget?.setPointerCapture?.(pointerId);
        document.addEventListener("pointermove", onMove);
        document.addEventListener("pointerup", onUp);
        document.addEventListener("pointercancel", onCancel);
    }

    return {
        resizingThumbnailId,
        savingLayout,
        updateThumbnailLayoutLocally,
        persistThumbnailLayoutChange,
        applySelectedThumbnailSize,
        beginThumbnailResize,
    };
}
