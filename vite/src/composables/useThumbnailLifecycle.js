export function useThumbnailLifecycle(options = {}) {
    const {
        selectedThumb,
        thumbnails,
        audioMap,
        activeAudioId,
        selectedThumbnailPanelOpen,
        storyboardView,
        globalRecorderOpen,
        studioSandboxMode,
        savingOrder,
        sortedThumbnails,
        getScenarioId,
        deleteThumbnail,
        updateThumbnailTitle,
        reorderScenarioThumbnails,
        toast,
        openRecorderForSelection,
        firstVoiceForThumb,
        speakerForVoice,
        nextSpeakerForThumb,
        selectVoice,
        selectedVoiceId,
        selectedSpeaker,
        quickRecordingThumbId,
        stopQuickRecording,
    } = options;

    function selectThumb(thumb, {openRecorder = true, scrollRecorder = false} = {}) {
        if (!thumb?.id) return;
        selectedThumb.value = thumb;
        activeAudioId.value = null;
        const firstVoice = firstVoiceForThumb(thumb);
        selectedVoiceId.value = firstVoice?.id ?? null;
        selectedSpeaker.value = firstVoice ? speakerForVoice(firstVoice, thumb) : nextSpeakerForThumb(thumb);
        selectedThumbnailPanelOpen.value = true;
        if (openRecorder) {
            openRecorderForSelection({scroll: scrollRecorder});
        }
    }

    function selectGlobalThumb(thumb, audio = null) {
        selectThumb(thumb, {openRecorder: true});
        if (audio) {
            selectVoice(audio, thumb, {scrollRecorder: false});
        }
    }

    function updateThumbTitle(targetThumb, event) {
        if (!targetThumb?.id) return;
        const title = event.target.value;

        thumbnails.value = thumbnails.value.map((thumb) => {
            if (String(thumb.id) !== String(targetThumb.id)) return thumb;
            return {...thumb, title};
        });

        if (selectedThumb.value && String(selectedThumb.value.id) === String(targetThumb.id)) {
            selectedThumb.value = {
                ...selectedThumb.value,
                title,
            };
        }
    }

    async function persistThumbTitle(targetThumb) {
        if (!targetThumb?.id) return;
        const current = thumbnails.value.find((thumb) => String(thumb.id) === String(targetThumb.id));
        if (!current) return;

        if (studioSandboxMode.value || !isPersistableThumbnailId(current.id)) {
            return;
        }

        try {
            const saved = await updateThumbnailTitle(current.id, {title: current.title || ""});
            thumbnails.value = thumbnails.value.map((thumb) =>
                String(thumb.id) === String(current.id) ? {...thumb, ...saved} : thumb
            );
            syncSelectedThumbnailFromList(current.id);
        } catch (e) {
            toast.error(e.message || "Could not save this scene title.");
        }
    }

    async function deleteThumb(targetThumb) {
        if (!targetThumb?.id) return;

        const targetId = String(targetThumb.id);
        const isLocal = !isPersistableThumbnailId(targetThumb.id);

        if (!studioSandboxMode.value && !isLocal) {
            try {
                await deleteThumbnail(targetThumb.id);
            } catch (e) {
                toast.error(e.message || "Could not delete this image.");
                return;
            }
        }

        const ordered = sortedThumbnails.value;
        const removedIndex = ordered.findIndex((thumb) => String(thumb.id) === targetId);

        thumbnails.value = thumbnails.value.filter((thumb) => String(thumb.id) !== targetId);
        const {[targetThumb.id]: _removedAudios, ...nextAudioMap} = audioMap.value;
        audioMap.value = nextAudioMap;

        if (String(quickRecordingThumbId.value ?? "") === targetId) {
            stopQuickRecording();
        }

        if (selectedThumb.value && String(selectedThumb.value.id) === targetId) {
            const nextThumb = ordered[removedIndex + 1] || ordered[removedIndex - 1] || null;
            selectedThumb.value = nextThumb && String(nextThumb.id) !== targetId ? nextThumb : null;
            activeAudioId.value = null;
            globalRecorderOpen.value = !!selectedThumb.value && storyboardView.value === "global";
        }

        toast.success("Image removed from this board.");
    }

    function syncSelectedThumbnailFromList(fallbackId = selectedThumb.value?.id) {
        if (fallbackId == null) return;
        const refreshed = thumbnails.value.find((thumb) => String(thumb.id) === String(fallbackId));
        if (refreshed) {
            selectedThumb.value = refreshed;
        }
    }

    function setOrderedThumbnails(ordered) {
        thumbnails.value = ordered.map((thumb, index) => ({
            ...thumb,
            idx: index + 1,
        }));
        syncSelectedThumbnailFromList();
    }

    function mergePersistedThumbnailRows(rows) {
        if (!Array.isArray(rows) || !rows.length) return;
        const currentById = new Map(thumbnails.value.map((thumb) => [String(thumb.id), thumb]));
        thumbnails.value = rows.map((row) => ({
            ...(currentById.get(String(row.id)) || {}),
            ...row,
        }));
        syncSelectedThumbnailFromList();
    }

    function isPersistableThumbnailId(id) {
        return /^\d+$/.test(String(id ?? ""));
    }

    async function persistThumbnailOrder(ordered) {
        if (studioSandboxMode.value) return;

        const thumbnailIds = ordered.map((thumb) => thumb.id);
        if (!thumbnailIds.every(isPersistableThumbnailId)) return;

        try {
            const persistedRows = await reorderScenarioThumbnails(
                getScenarioId(),
                thumbnailIds.map((id) => Number(id))
            );
            mergePersistedThumbnailRows(persistedRows);
        } catch (e) {
            const message = String(e?.message || "");
            const routeMissing = message.includes("No static resource") || message.includes("HTTP 404");
            if (!routeMissing) {
                throw e;
            }

            console.warn("Thumbnail reorder endpoint is not available on the running backend yet.", e);
        }
    }

    async function applyThumbnailOrder(nextOrdered, message = "Scene reordered.") {
        if (!nextOrdered.length || savingOrder.value) return;

        const previousThumbnails = thumbnails.value;
        const previousSelectedId = selectedThumb.value?.id;
        setOrderedThumbnails(nextOrdered);
        savingOrder.value = true;

        try {
            await persistThumbnailOrder(nextOrdered);
            toast.success(message);
        } catch (e) {
            thumbnails.value = previousThumbnails;
            syncSelectedThumbnailFromList(previousSelectedId);
            toast.error(e.message || "Could not save image order.");
        } finally {
            savingOrder.value = false;
        }
    }

    function moveThumbInOrder(ordered, fromIndex, toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return null;

        const nextOrdered = [...ordered];
        const [moved] = nextOrdered.splice(fromIndex, 1);
        nextOrdered.splice(toIndex, 0, moved);
        return nextOrdered;
    }

    async function reorderThumb(targetThumb, direction) {
        if (!targetThumb?.id) return;

        const ordered = sortedThumbnails.value;
        const currentIndex = ordered.findIndex((thumb) => String(thumb.id) === String(targetThumb.id));
        if (currentIndex < 0) return;

        let nextIndex = currentIndex;
        if (direction === "first") nextIndex = 0;
        if (direction === "last") nextIndex = ordered.length - 1;
        if (direction === "up") nextIndex = Math.max(0, currentIndex - 1);
        if (direction === "down") nextIndex = Math.min(ordered.length - 1, currentIndex + 1);
        if (nextIndex === currentIndex) return;

        const nextOrdered = moveThumbInOrder(ordered, currentIndex, nextIndex);
        if (nextOrdered) {
            await applyThumbnailOrder(nextOrdered);
        }
    }

    return {
        selectThumb,
        selectGlobalThumb,
        updateThumbTitle,
        persistThumbTitle,
        deleteThumb,
        syncSelectedThumbnailFromList,
        setOrderedThumbnails,
        mergePersistedThumbnailRows,
        isPersistableThumbnailId,
        persistThumbnailOrder,
        applyThumbnailOrder,
        moveThumbInOrder,
        reorderThumb,
    };
}
