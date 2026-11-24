import {ref} from "vue";
import {saveScenarioVignette as defaultSaveScenarioVignette} from "../api/vignettes";

const IMAGE_IMPORT_FORMATS_LABEL = "PNG, JPG, WebP, GIF, or SVG";

function isSceneImageFile(file) {
    const type = String(file?.type || "").toLowerCase();
    const name = String(file?.name || "").toLowerCase();
    // Plain XML is refused by the server; SVG is accepted and sanitized there.
    if (/\.xml$/i.test(name)) return false;
    return type.startsWith("image/") || /\.(png|jpe?g|webp|gif|svg)$/i.test(name);
}

function userErrorMessage(error) {
    return String(error?.message || error || "").trim();
}

function imageUploadErrorMessage(error) {
    const message = userErrorMessage(error);
    if (/413|too large|payload too large/i.test(message)) {
        return "This image is too large. Try a smaller image or export it as WebP/JPG.";
    }
    if (/415|unsupported media|content type/i.test(message)) {
        return `This image type is not supported. Try ${IMAGE_IMPORT_FORMATS_LABEL}.`;
    }
    return message || `Could not add this scene image. Try ${IMAGE_IMPORT_FORMATS_LABEL}.`;
}

export function useSceneImageUpload(options = {}) {
    const {
        scenario,
        thumbnails,
        selectedThumb,
        sortedThumbnails,
        studioSandboxMode,
        getScenarioId,
        pendingRouteDraftId,
        applyUnclaimedDraftAudio,
        loadThumbs,
        uploadScenarioThumbnail,
        saveScenarioVignette = defaultSaveScenarioVignette,
        toast,
        fileBaseName,
    } = options;

    const uploadError = ref("");
    const uploadSuccess = ref("");
    const uploadFiles = ref([]);
    const uploadDragOver = ref(false);
    const uploadingProgress = ref(0);
    const uploadDialogOpen = ref(false);
    const vignetteMakerOpen = ref(false);

    function openUploadDialog() {
        uploadError.value = "";
        uploadSuccess.value = "";
        uploadDialogOpen.value = true;
    }

    function clearUploadFile() {
        uploadFiles.value.forEach((e) => URL.revokeObjectURL(e.previewUrl));
        uploadFiles.value = [];
        uploadingProgress.value = 0;
    }

    function closeUploadDialog() {
        uploadDialogOpen.value = false;
        clearUploadFile();
    }

    function onVignetteMakerInsert(blob, makerState = null) {
        vignetteMakerOpen.value = false;
        uploadDialogOpen.value = true;
        const ts = Date.now();
        const file = new File([blob], `vignette-illustration-${ts}.svg`, {type: "image/svg+xml"});
        const previewUrl = URL.createObjectURL(file);
        const baseTitle = scenario.value?.title ? `${scenario.value.title} – scene` : "Vignette scene";
        const usedTitles = new Set(
            [...thumbnails.value, ...uploadFiles.value]
                .map((entry) => String(entry?.title || "").trim().toLocaleLowerCase())
        );
        let sequence = thumbnails.value.length + uploadFiles.value.length + 1;
        while (usedTitles.has(`${baseTitle} ${sequence}`.toLocaleLowerCase())) {
            sequence += 1;
        }
        const defaultTitle = `${baseTitle} ${sequence}`;
        // Keep the drawing so the scene is saved as a vignette that can be reopened later.
        const sceneJson = makerState ? JSON.stringify(makerState) : null;
        uploadFiles.value = [...uploadFiles.value, {file, title: defaultTitle, previewUrl, sceneJson}];
    }

    function addUploadFiles(fileList) {
        uploadError.value = "";
        const files = Array.from(fileList);
        const incoming = files.filter(isSceneImageFile);
        if (files.length && !incoming.length) {
            const message = `No usable scene image found. Try ${IMAGE_IMPORT_FORMATS_LABEL}.`;
            uploadError.value = message;
            toast.error(message);
            return;
        }
        if (files.length > incoming.length) {
            toast.info(`Skipped ${files.length - incoming.length} unsupported file${files.length - incoming.length > 1 ? "s" : ""}.`);
        }
        const entries = incoming.map((file) => ({
            file,
            previewUrl: URL.createObjectURL(file),
            title: fileBaseName(file, "Scene"),
        }));
        uploadFiles.value = [...uploadFiles.value, ...entries];
    }

    function onImageChange(event) {
        addUploadFiles(event.target.files ?? []);
        event.target.value = "";
    }

    function onUploadDrop(event) {
        uploadDragOver.value = false;
        addUploadFiles(event.dataTransfer?.files ?? []);
    }

    function removeUploadEntry(index) {
        URL.revokeObjectURL(uploadFiles.value[index]?.previewUrl);
        uploadFiles.value = uploadFiles.value.filter((_, i) => i !== index);
    }

    async function uploadImage() {
        uploadError.value = "";
        uploadSuccess.value = "";

        const entries = uploadFiles.value;
        if (!entries.length) {
            uploadError.value = `Choose an image first. Supported formats: ${IMAGE_IMPORT_FORMATS_LABEL}.`;
            return;
        }

        uploadingProgress.value = 0;

        try {
            if (studioSandboxMode.value || String(getScenarioId()).startsWith("emergency-")) {
                const added = [];
                entries.forEach((entry, i) => {
                    const nextIndex = thumbnails.value.length + added.length + 1;
                    added.push({
                        id: `local-${Date.now()}-${i}`,
                        idx: nextIndex,
                        title: entry.title || `Scene ${nextIndex}`,
                        gridColumnSpan: 4,
                        gridRowSpan: 3,
                        previewUrl: URL.createObjectURL(entry.file),
                    });
                    uploadingProgress.value = Math.round(((i + 1) / entries.length) * 100);
                });
                thumbnails.value = [...thumbnails.value, ...added];
                selectedThumb.value = pendingRouteDraftId.value ? added[0] : added[added.length - 1];
                if (pendingRouteDraftId.value) {
                    await applyUnclaimedDraftAudio({targetThumb: added[0]});
                }
                toast.success(`${added.length} scene${added.length > 1 ? "s" : ""} added.`);
                closeUploadDialog();
                return;
            }

            for (let i = 0; i < entries.length; i++) {
                const entry = entries[i];
                if (entry.sceneJson) {
                    await saveScenarioVignette(getScenarioId(), {
                        image: entry.file,
                        sceneJson: entry.sceneJson,
                        title: entry.title || "",
                    });
                } else {
                    const fd = new FormData();
                    fd.append("scenarioId", String(scenario.value.id));
                    fd.append("title", entry.title || "");
                    fd.append("image", entry.file);
                    await uploadScenarioThumbnail(getScenarioId(), fd);
                }
                uploadingProgress.value = Math.round(((i + 1) / entries.length) * 100);
            }

            toast.success(`${entries.length} scene${entries.length > 1 ? "s" : ""} uploaded.`);
            await loadThumbs();
            if (pendingRouteDraftId.value) {
                await applyUnclaimedDraftAudio({targetThumb: sortedThumbnails.value[0] ?? thumbnails.value[0]});
            }
            closeUploadDialog();
        } catch (e) {
            const message = imageUploadErrorMessage(e);
            uploadError.value = message;
            toast.error(message);
        } finally {
            uploadingProgress.value = 0;
        }
    }

    return {
        uploadError,
        uploadSuccess,
        uploadFiles,
        uploadDragOver,
        uploadingProgress,
        uploadDialogOpen,
        vignetteMakerOpen,
        openUploadDialog,
        closeUploadDialog,
        onVignetteMakerInsert,
        addUploadFiles,
        onImageChange,
        onUploadDrop,
        removeUploadEntry,
        clearUploadFile,
        uploadImage,
    };
}
