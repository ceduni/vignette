import {ref, watch} from "vue";
import {buildApiUrl} from "../api/rest";
import {trimAudioSourceToWav} from "../utils/audioTrim";
import {useToast} from "./useToast";

export function useAudioTrimEditor(selectedVoiceRef, selectedThumbRef, options = {}) {
    const updateVoiceFields = options.updateVoiceFields ?? (() => {});
    const replaceAudioContent = options.replaceAudioContent ?? null;
    const fetchThumbnailAudios = options.fetchThumbnailAudios ?? null;
    const audioMap = options.audioMap ?? null;
    const studioSandboxMode = options.studioSandboxMode ?? ref(false);
    const getScenarioId = options.getScenarioId ?? (() => "");

    const toast = useToast();

    const recordingTrimOpen = ref(false);
    const trimStart = ref(0);
    const trimEnd = ref(100);
    const trimDragging = ref(null);
    const trimPreviewPlaying = ref(false);
    const trimSaving = ref(false);
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

    function trimmedFileName(voice) {
        const base = String(voice?.title || "audio")
            .trim()
            .replace(/[^\p{L}\p{N}_-]+/gu, "-")
            .replace(/^-+|-+$/g, "") || "audio";
        return `${base}-cut.wav`;
    }

    function isLocalVoice(voice) {
        const id = String(voice?.id || "");
        return studioSandboxMode.value ||
            String(getScenarioId()).startsWith("emergency-") ||
            id.startsWith("local-");
    }

    async function applyTrimSelection() {
        const voice = selectedVoiceRef.value;
        const thumb = selectedThumbRef.value;
        if (!voice || !thumb || voice.isDraft || trimSaving.value) return;
        if (Number(trimStart.value) === 0 && Number(trimEnd.value) === 100) {
            toast.info("Move the start or end handle before applying the cut.");
            return;
        }

        stopTrimPreview();
        trimSaving.value = true;

        try {
            const source = selectedVoiceAudioUrl();
            if (!source) throw new Error("No audio is available to cut.");

            const {blob, durationSeconds} = await trimAudioSourceToWav(
                source,
                trimStart.value,
                trimEnd.value
            );

            if (isLocalVoice(voice)) {
                const previousPreviewUrl = voice.previewUrl;
                const previewUrl = URL.createObjectURL(blob);
                updateVoiceFields(thumb, voice, {previewUrl, trimStart: 0, trimEnd: 100});
                if (String(previousPreviewUrl || "").startsWith("blob:")) {
                    URL.revokeObjectURL(previousPreviewUrl);
                }
            } else {
                if (!replaceAudioContent || !fetchThumbnailAudios || !audioMap) {
                    throw new Error("Audio saving is not available.");
                }

                const formData = new FormData();
                formData.append("title", voice.title || "Audio");
                formData.append("audio", blob, trimmedFileName(voice));
                await replaceAudioContent(voice.id, formData);
                const freshAudios = await fetchThumbnailAudios(thumb.id);
                audioMap.value = {...audioMap.value, [thumb.id]: freshAudios};
            }

            trimStart.value = 0;
            trimEnd.value = 100;
            recordingTrimOpen.value = false;
            toast.success(`Audio cut saved (${durationSeconds.toFixed(1)} s).`);
        } catch (error) {
            toast.error(error?.message || "Could not cut this audio.");
        } finally {
            trimSaving.value = false;
        }
    }

    return {
        recordingTrimOpen,
        trimStart,
        trimEnd,
        trimDragging,
        trimPreviewPlaying,
        trimSaving,
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
