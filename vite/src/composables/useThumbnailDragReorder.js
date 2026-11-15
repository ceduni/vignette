import {computed, ref} from "vue";
import {clamp} from "../utils/scenarioStoryboard";

export function useThumbnailDragReorder(sortedThumbnailsRef, options = {}) {
    const applyOrder = options.applyOrder ?? (async () => {});
    const thumbnailContentUrl = options.thumbnailContentUrl ?? (() => "");
    const onDragStart = options.onDragStart ?? (() => {});

    const tileDragId = ref(null);
    const tileDragOverId = ref(null);
    const tileDragPreviewIds = ref([]);
    const tileDragGhost = ref(null);
    let tilePointerDrag = null;

    const visualThumbnails = computed(() => {
        if (!tileDragId.value || !tileDragPreviewIds.value.length) {
            return sortedThumbnailsRef.value;
        }

        const byId = new Map(sortedThumbnailsRef.value.map((thumb) => [String(thumb.id), thumb]));
        const ordered = tileDragPreviewIds.value
            .map((id) => byId.get(String(id)))
            .filter(Boolean);

        return ordered.length === sortedThumbnailsRef.value.length
            ? ordered
            : sortedThumbnailsRef.value;
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

    function orderedIdsFromThumbnails(items = sortedThumbnailsRef.value) {
        return items.map((thumb) => String(thumb.id));
    }

    function thumbnailsFromIdOrder(ids, fallback = sortedThumbnailsRef.value) {
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

    function cleanupTilePointerDrag() {
        window.removeEventListener("pointermove", onTilePointerDragMove);
        window.removeEventListener("pointerup", onTilePointerDragEnd);
        window.removeEventListener("pointercancel", onTilePointerDragCancel);

        if (tilePointerDrag?.handleEl && tilePointerDrag?.pointerId != null) {
            try {
                tilePointerDrag.handleEl.releasePointerCapture?.(tilePointerDrag.pointerId);
            } catch {
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
        onDragStart(thumb);
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

        if (!isSameOrder(originalIds, previewIds) && nextOrdered.length === sortedThumbnailsRef.value.length) {
            await applyOrder(nextOrdered);
        }
    }

    function onTilePointerDragCancel(event) {
        if (tilePointerDrag && event.pointerId !== tilePointerDrag.pointerId) return;
        cleanupTilePointerDrag();
    }

    function isTileDragging(thumb) {
        return String(thumb.id) === String(tileDragId.value);
    }

    function isTileDragTarget(thumb) {
        return String(thumb.id) === String(tileDragOverId.value);
    }

    return {
        tileDragId,
        tileDragOverId,
        tileDragGhost,
        tileDragGhostStyle,
        visualThumbnails,
        isTileDragging,
        isTileDragTarget,
        onTilePointerReorderStart,
    };
}
