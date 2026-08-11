import {ref, watch} from "vue";
import {buildApiUrl} from "../api/rest";
import {useToast} from "./useToast";

export function useAudioTrimEditor(selectedVoiceRef, selectedThumbRef, options = {}) {
    const updateVoiceFields = options.updateVoiceFields ?? (() => {});

    const toast = useToast();

    const recordingTrimOpen = ref(false);
    const trimStart = ref(0);
    const trimEnd = ref(100);
    const trimDragging = ref(null);
    const trimPreviewPlaying = ref(false);
    let trimPreviewAudio = null;

    const trimWaveBars = [
        34, 62, 48, 76, 52, 88, 44, 66, 92, 58, 38, 72,
        84, 46, 64, 96, 54, 74, 42, 68, 86, 50, 78, 60,
    ];

    watch(selectedVoiceRef, (audio) => {
        trimStart.value = audio?.trimStart ?? 0;
        trimEnd.value = audio?.trimEnd ?? 100;
    }, {immediate: true});

    function toggleTrimEditor() {
        recordingTrimOpen.value = !recordingTrimOpen.value;
    }

    function clampTrimValue(value, min = 0, max = 100) {
        return Math.max(min, Math.min(max, Math.round(Number(value) || 0)));
    }

    function updateTrimStart(value) {
        trimStart.value = clampTrimValue(value, 0, Math.max(0, Number(trimEnd.value) - 1));
    }

    function updateTrimEnd(value) {
        trimEnd.value = clampTrimValue(value, Math.min(100, Number(trimStart.value) + 1), 100);
    }

    function trimPercentFromEvent(event) {
        const rect = event.currentTarget.getBoundingClientRect();
        return clampTrimValue(((event.clientX - rect.left) / rect.width) * 100);
    }

    function setNearestTrimHandle(event) {
        const value = trimPercentFromEvent(event);
        const startDistance = Math.abs(value - Number(trimStart.value));
        const endDistance = Math.abs(value - Number(trimEnd.value));

        if (startDistance <= endDistance) {
            updateTrimStart(value);
            trimDragging.value = "start";
        } else {
            updateTrimEnd(value);
            trimDragging.value = "end";
        }

        window.addEventListener("pointermove", updateTrimFromEvent);
        window.addEventListener("pointerup", stopTrimDrag, {once: true});
    }

    function beginTrimDrag(handle, event) {
        trimDragging.value = handle;
        updateTrimFromEvent(event);
        window.addEventListener("pointermove", updateTrimFromEvent);
        window.addEventListener("pointerup", stopTrimDrag, {once: true});
    }

    function updateTrimFromEvent(event) {
        if (!trimDragging.value) return;
        const track = document.querySelector(".trim-editor__wave");
        if (!track) return;
        const rect = track.getBoundingClientRect();
        const value = clampTrimValue(((event.clientX - rect.left) / rect.width) * 100);

        if (trimDragging.value === "start") {
            updateTrimStart(value);
        } else {
            updateTrimEnd(value);
        }
    }

    function stopTrimDrag() {
        trimDragging.value = null;
        window.removeEventListener("pointermove", updateTrimFromEvent);
    }

    function resetTrimSelection() {
        trimStart.value = 0;
        trimEnd.value = 100;
    }

    function selectedVoiceAudioUrl() {
        const voice = selectedVoiceRef.value;
        if (!voice || voice.isDraft) return "";
        return voice.previewUrl || buildApiUrl(`/api/audios/${voice.id}/content`);
    }

    function stopTrimPreview() {
        if (trimPreviewAudio) {
            trimPreviewAudio.pause();
            trimPreviewAudio = null;
        }
        trimPreviewPlaying.value = false;
    }

    function previewTrimSelection() {
        const src = selectedVoiceAudioUrl();
        if (!src) return;

        if (trimPreviewPlaying.value) {
            stopTrimPreview();
            return;
        }

        stopTrimPreview();
        trimPreviewAudio = new Audio(src);
        trimPreviewAudio.addEventListener("loadedmetadata", () => {
            const duration = Number.isFinite(trimPreviewAudio.duration) ? trimPreviewAudio.duration : 0;
            const startSeconds = duration * (Number(trimStart.value) / 100);
            const endSeconds = duration * (Number(trimEnd.value) / 100);
            trimPreviewAudio.currentTime = startSeconds;
            trimPreviewAudio.play().then(() => {
                trimPreviewPlaying.value = true;
            }).catch(() => {
                trimPreviewPlaying.value = false;
            });

            trimPreviewAudio.addEventListener("timeupdate", () => {
                if (trimPreviewAudio && trimPreviewAudio.currentTime >= endSeconds) {
                    stopTrimPreview();
                }
            });
        });
        trimPreviewAudio.addEventListener("ended", stopTrimPreview);
    }

    function applyTrimSelection() {
        const voice = selectedVoiceRef.value;
        const thumb = selectedThumbRef.value;
        if (!voice || !thumb) return;
        stopTrimPreview();
        updateVoiceFields(thumb, voice, {
            trimStart: trimStart.value,
            trimEnd: trimEnd.value,
        });
        recordingTrimOpen.value = false;
        toast.success(`Cut saved: ${trimStart.value}% to ${trimEnd.value}%.`);
    }

    return {
        recordingTrimOpen,
        trimStart,
        trimEnd,
        trimDragging,
        trimPreviewPlaying,
        trimWaveBars,
        toggleTrimEditor,
        updateTrimStart,
        updateTrimEnd,
        setNearestTrimHandle,
        beginTrimDrag,
        resetTrimSelection,
        previewTrimSelection,
        applyTrimSelection,
        stopTrimPreview,
    };
}
