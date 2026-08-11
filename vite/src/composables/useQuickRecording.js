import {ref, watch} from "vue";

export function useQuickRecording(options = {}) {
    const {
        selectedThumb,
        thumbnails,
        audioMap,
        toast,
        studioSandboxMode,
        getScenarioId,
        selectThumb,
        ensureVoiceForRecording,
        speakerForVoice,
        voiceDisplayTitle,
        takeLabel,
        firstVoiceForThumb,
        nextSpeakerForThumb,
        selectedSpeaker,
        selectedVoiceId,
        targetVoiceForThumb,
        selectVoice,
        voicesForThumb,
        speakerForAudio,
        recordButtonVoiceForThumb,
        selectedVoice,
        uploadVoiceAudioFile,
        addLocalAudioClip,
    } = options;

    const quickRecordingThumbId = ref(null);
    const quickRecordingDialogOpen = ref(false);
    const quickRecordingTitle = ref("");
    const quickRecordingBlob = ref(null);
    const quickRecordingMimeType = ref("audio/webm");
    const quickRecordingError = ref("");
    const quickRecordingUploading = ref(false);
    const quickRecordingTargetThumbId = ref(null);
    const quickRecordingVoiceId = ref(null);
    const quickRecordingCreatedDraft = ref(false);
    const quickRecordingPreviewUrl = ref("");

    const qrAudioEl = ref(null);
    const qrPlaying = ref(false);
    const qrCurrentTime = ref(0);
    const qrDuration = ref(0);

    let quickMediaRecorder = null;
    let quickMediaStream = null;
    let quickRecordingChunks = [];

    function isRecordingThumb(thumb) {
        return (
            quickRecordingThumbId.value != null &&
            String(quickRecordingThumbId.value) === String(thumb?.id ?? "")
        );
    }

    function closeQuickRecordingDialog() {
        quickRecordingDialogOpen.value = false;
        quickRecordingTitle.value = "";
        quickRecordingBlob.value = null;
        quickRecordingMimeType.value = "audio/webm";
        quickRecordingError.value = "";
        quickRecordingTargetThumbId.value = null;
        quickRecordingVoiceId.value = null;
        quickRecordingCreatedDraft.value = false;

        if (quickRecordingPreviewUrl.value) {
            URL.revokeObjectURL(quickRecordingPreviewUrl.value);
            quickRecordingPreviewUrl.value = "";
        }
    }

    function getSupportedMimeType() {
        const candidates = [
            "audio/webm;codecs=opus",
            "audio/webm",
            "audio/ogg;codecs=opus",
            "audio/ogg",
            "audio/mp4",
        ];
        return candidates.find(t => {
            try { return MediaRecorder.isTypeSupported(t); } catch { return false; }
        }) ?? "";
    }

    async function ensureMediaStream() {
        if (quickMediaStream?.active) return;
        quickMediaStream = await navigator.mediaDevices.getUserMedia({audio: true});
    }

    async function startQuickRecording(thumb, requestedVoice = null, {speaker: requestedSpeaker = null, preferSelected = true} = {}) {
        quickRecordingError.value = "";
        quickRecordingBlob.value = null;

        const {voice, created} = ensureVoiceForRecording(thumb, requestedVoice, requestedSpeaker, {preferSelected});
        if (!voice) return;

        const speaker = speakerForVoice(voice, thumb) || selectedSpeaker.value;
        quickRecordingCreatedDraft.value = created;
        quickRecordingTitle.value = voiceDisplayTitle(voice, thumb) || takeLabel(speaker);

        try {
            await ensureMediaStream();

            quickRecordingChunks = [];
            const mimeType = getSupportedMimeType();
            quickMediaRecorder = mimeType
                ? new MediaRecorder(quickMediaStream, {mimeType})
                : new MediaRecorder(quickMediaStream);

            quickMediaRecorder.ondataavailable = (event) => {
                if (event.data?.size > 0) quickRecordingChunks.push(event.data);
            };

            quickMediaRecorder.onstop = () => {
                const type = quickMediaRecorder.mimeType || "audio/webm";
                quickRecordingBlob.value = new Blob(quickRecordingChunks, {type});
                quickRecordingMimeType.value = type;
                quickRecordingChunks = [];
                quickRecordingDialogOpen.value = true;
                quickRecordingThumbId.value = null;
            };

            quickRecordingThumbId.value = thumb.id;
            quickRecordingTargetThumbId.value = thumb.id;
            quickRecordingVoiceId.value = voice.id;
            quickMediaRecorder.start();
            toast.info(`${takeLabel(speaker)} recording started.`);
        } catch (e) {
            if (created && voice?.isDraft) {
                const voices = audioMap.value[thumb.id] || [];
                audioMap.value = {
                    ...audioMap.value,
                    [thumb.id]: voices.filter((audio) => String(audio.id) !== String(voice.id)),
                };
                const fallback = firstVoiceForThumb(thumb);
                selectedVoiceId.value = fallback?.id ?? null;
                selectedSpeaker.value = fallback ? speakerForVoice(fallback, thumb) : nextSpeakerForThumb(thumb);
            }
            quickRecordingThumbId.value = null;
            quickRecordingTargetThumbId.value = null;
            quickRecordingVoiceId.value = null;
            quickRecordingCreatedDraft.value = false;
            quickRecordingError.value = e.message || "Unable to start quick recording.";
            toast.error(quickRecordingError.value);
        }
    }

    function stopQuickRecording() {
        if (!quickMediaRecorder || quickMediaRecorder.state === "inactive") {
            quickRecordingThumbId.value = null;
            return;
        }

        quickMediaRecorder.stop();
    }

    async function toggleQuickRecording(thumb, requestedVoice = null, {speaker = null, preferSelected = true} = {}) {
        if (String(selectedThumb.value?.id ?? "") !== String(thumb?.id ?? "")) {
            selectThumb(thumb);
        }

        if (
            quickRecordingThumbId.value != null &&
            String(quickRecordingThumbId.value) === String(thumb.id)
        ) {
            stopQuickRecording();
            return;
        }

        if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
            toast.error("Another quick recording is already in progress.");
            return;
        }

        await startQuickRecording(
            thumb,
            requestedVoice ?? (preferSelected ? targetVoiceForThumb(thumb) : null),
            {speaker, preferSelected}
        );
    }

    function toggleSelectedQuickRecording() {
        if (quickRecordingThumbId.value != null) {
            if (
                !selectedThumb.value ||
                String(quickRecordingThumbId.value) !== String(selectedThumb.value.id)
            ) {
                stopQuickRecording();
                return;
            }
        }

        if (selectedThumb.value) {
            toggleQuickRecording(selectedThumb.value, targetVoiceForThumb(selectedThumb.value));
        }
    }

    function restartSelectedRecording() {
        if (!selectedThumb.value) return;

        if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
            stopQuickRecording();
            return;
        }

        quickRecordingBlob.value = null;
        quickRecordingTitle.value = "";
        toggleQuickRecording(selectedThumb.value, targetVoiceForThumb(selectedThumb.value));
    }

    async function addVoiceForThumb(thumb) {
        if (!thumb?.id) return;
        if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") {
            toast.error("Stop the current recording first.");
            return;
        }

        const selectedDraft = String(selectedThumb.value?.id ?? "") === String(thumb.id)
            ? recordButtonVoiceForThumb(thumb)
            : null;

        if (selectedDraft) {
            selectVoice(selectedDraft, thumb);
            await toggleQuickRecording(thumb, selectedDraft);
            return;
        }

        if (voicesForThumb(thumb).length >= 4) {
            toast.error("This scene already has four takes.");
            return;
        }

        selectThumb(thumb);
        await toggleQuickRecording(thumb, null, {
            speaker: nextSpeakerForThumb(thumb),
            preferSelected: false,
        });
    }

    async function confirmQuickRecordingUpload() {
        quickRecordingError.value = "";

        try {
            const targetThumb = thumbnails.value.find(
                (thumb) => String(thumb.id) === String(quickRecordingTargetThumbId.value ?? selectedThumb.value?.id ?? "")
            ) ?? selectedThumb.value;
            if (!targetThumb?.id) throw new Error("No thumbnail selected.");
            if (!quickRecordingBlob.value) throw new Error("No quick recording available.");

            quickRecordingUploading.value = true;

            const extension = quickRecordingMimeType.value.includes("ogg")
                ? "ogg"
                : quickRecordingMimeType.value.includes("mp4") || quickRecordingMimeType.value.includes("aac")
                    ? "mp4"
                    : "webm";

            const fileName = `quick-recording.${extension}`;

            const targetVoice = (audioMap.value[targetThumb.id] || []).find(
                (audio) => String(audio.id) === String(quickRecordingVoiceId.value ?? "")
            ) ?? selectedVoice.value;
            const voiceTitle = quickRecordingTitle.value || voiceDisplayTitle(targetVoice, targetThumb) || takeLabel(selectedSpeaker.value);

            if (studioSandboxMode.value || String(getScenarioId()).startsWith("emergency-")) {
                if (String(selectedThumb.value?.id ?? "") !== String(targetThumb.id)) {
                    selectedThumb.value = targetThumb;
                }
                selectedVoiceId.value = quickRecordingVoiceId.value ?? selectedVoiceId.value;

                addLocalAudioClip(targetThumb, {
                    title: voiceTitle,
                    previewUrl: URL.createObjectURL(quickRecordingBlob.value),
                    speaker: targetVoice?.speaker || selectedSpeaker.value,
                });

                toast.success("Recording added.");
                closeQuickRecordingDialog();
                return;
            }

            await uploadVoiceAudioFile(targetThumb, targetVoice, quickRecordingBlob.value, voiceTitle, fileName);
            toast.success("Quick recording uploaded successfully.");
            closeQuickRecordingDialog();

        } catch (e) {
            quickRecordingError.value = e.message || "Failed to upload quick recording.";
            toast.error(quickRecordingError.value);
        } finally {
            quickRecordingUploading.value = false;
        }
    }

    function discardQuickRecording() {
        const thumbId = quickRecordingTargetThumbId.value;
        const voiceId = quickRecordingVoiceId.value;
        const targetThumb = thumbnails.value.find((thumb) => String(thumb.id) === String(thumbId ?? "")) ?? selectedThumb.value;
        if (thumbId && voiceId && quickRecordingCreatedDraft.value) {
            const voices = audioMap.value[thumbId] || [];
            const target = voices.find(a => String(a.id) === String(voiceId));
            if (target?.isDraft) {
                audioMap.value = {
                    ...audioMap.value,
                    [thumbId]: voices.filter(a => String(a.id) !== String(voiceId)),
                };
                const remaining = voicesForThumb(targetThumb);
                const fallback = remaining.find(a => !a.isDraft) ?? remaining[0] ?? null;
                selectedVoiceId.value = fallback?.id ?? null;
                selectedSpeaker.value = fallback ? speakerForAudio(fallback, Math.max(0, remaining.indexOf(fallback))) : nextSpeakerForThumb(targetThumb);
            }
        } else if (thumbId && voiceId) {
            const target = (audioMap.value[thumbId] || []).find(a => String(a.id) === String(voiceId));
            if (target && targetThumb) selectVoice(target, targetThumb);
        }
        toast.info("Quick recording discarded.");
        closeQuickRecordingDialog();
    }

    function toggleQrPlayback() {
        const el = qrAudioEl.value;
        if (!el) return;
        if (qrPlaying.value) el.pause();
        else el.play().catch(() => {});
    }

    function onQrTimeUpdate() {
        qrCurrentTime.value = qrAudioEl.value?.currentTime ?? 0;
    }

    function onQrLoadedMetadata() {
        const el = qrAudioEl.value;
        if (!el) return;

        if (Number.isFinite(el.duration)) {
            qrDuration.value = el.duration;
            return;
        }

        const recoverDuration = () => {
            el.removeEventListener("timeupdate", recoverDuration);
            qrDuration.value = Number.isFinite(el.duration) ? el.duration : 0;
            el.currentTime = 0;
        };
        el.addEventListener("timeupdate", recoverDuration, {once: true});
        el.currentTime = 1e101;
    }

    function onQrEnded() {
        qrPlaying.value = false;
        qrCurrentTime.value = 0;
    }

    function seekQrPlayback(event) {
        const el = qrAudioEl.value;
        if (!el || !qrDuration.value) return;
        const rect = event.currentTarget.getBoundingClientRect();
        const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
        el.currentTime = ratio * qrDuration.value;
        qrCurrentTime.value = el.currentTime;
    }

    function qrProgressPercent() {
        return qrDuration.value ? Math.min(100, (qrCurrentTime.value / qrDuration.value) * 100) : 0;
    }

    watch(quickRecordingBlob, (blob) => {
        if (quickRecordingPreviewUrl.value) {
            URL.revokeObjectURL(quickRecordingPreviewUrl.value);
            quickRecordingPreviewUrl.value = "";
        }
        qrPlaying.value = false;
        qrCurrentTime.value = 0;
        qrDuration.value = 0;

        if (blob) {
            quickRecordingPreviewUrl.value = URL.createObjectURL(blob);
        }
    });

    watch(
        () => getScenarioId(),
        () => {
            if (quickMediaRecorder && quickMediaRecorder.state !== "inactive") quickMediaRecorder.stop();
            quickRecordingThumbId.value = null;
            closeQuickRecordingDialog();
        }
    );

    return {
        quickRecordingThumbId,
        quickRecordingDialogOpen,
        quickRecordingTitle,
        quickRecordingBlob,
        quickRecordingMimeType,
        quickRecordingError,
        quickRecordingUploading,
        quickRecordingTargetThumbId,
        quickRecordingVoiceId,
        quickRecordingCreatedDraft,
        quickRecordingPreviewUrl,
        qrAudioEl,
        qrPlaying,
        qrCurrentTime,
        qrDuration,
        isRecordingThumb,
        closeQuickRecordingDialog,
        startQuickRecording,
        stopQuickRecording,
        toggleQuickRecording,
        toggleSelectedQuickRecording,
        restartSelectedRecording,
        addVoiceForThumb,
        confirmQuickRecordingUpload,
        discardQuickRecording,
        toggleQrPlayback,
        onQrTimeUpdate,
        onQrLoadedMetadata,
        onQrEnded,
        seekQrPlayback,
        qrProgressPercent,
    };
}
