import {ref} from "vue";

const BACKEND_AUDIO_TYPES = new Set([
    "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave",
    "audio/ogg", "audio/webm", "audio/mp4", "audio/aac", "audio/flac",
    "audio/x-flac", "audio/x-m4a", "audio/opus", "audio/aiff", "audio/x-aiff",
]);

function normalizedAudioMime(file) {
    return String(file?.type || "").toLowerCase().split(";")[0].trim();
}

async function decodeToWav(file, fileBaseName) {
    const arrayBuffer = await file.arrayBuffer();
    const AudioContextCtor = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextCtor) {
        throw new Error("Audio decoding is not available in this browser.");
    }
    const ctx = new AudioContextCtor();
    let audioBuffer;
    try {
        audioBuffer = await ctx.decodeAudioData(arrayBuffer);
    } finally {
        await ctx.close();
    }
    const numCh = audioBuffer.numberOfChannels;
    const sr = audioBuffer.sampleRate;
    const bps = 2;
    const dataLen = audioBuffer.length * numCh * bps;
    const wav = new ArrayBuffer(44 + dataLen);
    const v = new DataView(wav);
    const str = (off, s) => { for (let i = 0; i < s.length; i++) v.setUint8(off + i, s.charCodeAt(i)); };
    str(0, "RIFF"); v.setUint32(4, 36 + dataLen, true);
    str(8, "WAVE"); str(12, "fmt ");
    v.setUint32(16, 16, true); v.setUint16(20, 1, true);
    v.setUint16(22, numCh, true); v.setUint32(24, sr, true);
    v.setUint32(28, sr * numCh * bps, true); v.setUint16(32, numCh * bps, true);
    v.setUint16(34, 16, true); str(36, "data"); v.setUint32(40, dataLen, true);
    let off = 44;
    for (let i = 0; i < audioBuffer.length; i++) {
        for (let ch = 0; ch < numCh; ch++) {
            const s = Math.max(-1, Math.min(1, audioBuffer.getChannelData(ch)[i]));
            v.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
            off += 2;
        }
    }
    const baseName = fileBaseName(file, "audio-import");
    return new File([new Blob([wav], {type: "audio/wav"})], `${baseName}.wav`, {type: "audio/wav"});
}

export function useAudioFileImport(options = {}) {
    const {
        selectedThumb,
        audioMap,
        toast,
        studioSandboxMode,
        getScenarioId,
        deleteAudio,
        uploadThumbnailAudio,
        fetchThumbnailAudios,
        fileBaseName,
        audioImportErrorMessage,
        ensureVoiceForRecording,
        targetVoiceForThumb,
        speakerForVoice,
        selectedSpeaker,
        voiceDisplayTitle,
        takeLabel,
        addLocalAudioClip,
        isLocalAudioId,
        firstVoiceForThumb,
        nextSpeakerForThumb,
        selectedVoiceId,
    } = options;

    const recordingFileInput = ref(null);

    function openRecordingAudioFile() {
        recordingFileInput.value?.click?.();
    }

    function onRecordingAudioFileChange(event) {
        const file = event.target.files?.[0] ?? null;
        importRecordingAudioFile(file);
        event.target.value = "";
    }

    async function importRecordingAudioFile(file) {
        if (!file || !selectedThumb.value) return;
        const targetThumb = selectedThumb.value;
        const {voice} = ensureVoiceForRecording(targetThumb, targetVoiceForThumb(targetThumb));
        if (!voice) return;
        const speaker = speakerForVoice(voice, targetThumb) || selectedSpeaker.value;
        const title = voiceDisplayTitle(voice, targetThumb) || fileBaseName(file, "") || takeLabel(speaker);

        try {
            const uploadFile = await prepareAudioUploadFile(file);

            if (!studioSandboxMode.value && !String(getScenarioId()).startsWith("emergency-")) {
                await uploadVoiceAudioFile(targetThumb, voice, uploadFile, title, uploadFile.name || file.name || "recording-upload");
                toast.success("Audio imported.");
                return;
            }

            addLocalAudioClip(targetThumb, {
                title,
                previewUrl: URL.createObjectURL(uploadFile),
                speaker,
            });

            toast.success("Audio imported.");
        } catch (e) {
            toast.error(audioImportErrorMessage(e, "import this take audio"));
        }
    }

    async function prepareAudioUploadFile(file) {
        if (BACKEND_AUDIO_TYPES.has(normalizedAudioMime(file))) {
            return file;
        }
        return decodeToWav(file, fileBaseName);
    }

    async function uploadVoiceAudioFile(targetThumb, voice, audioFile, title, fileName = "recording.webm") {
        if (!targetThumb?.id || !audioFile) throw new Error("No take selected.");

        const thumbId = targetThumb.id;
        const replacingExistingAudio = !!voice && !voice.isDraft && !isLocalAudioId(voice.id);
        let deletedForReplace = false;
        const previousAudioIds = new Set(
            (audioMap.value[thumbId] || [])
                .filter((audio) => !audio.isDraft)
                .map((audio) => String(audio.id))
        );

        try {
            if (replacingExistingAudio) {
                await deleteAudio(voice.id);
                deletedForReplace = true;
                previousAudioIds.delete(String(voice.id));
            }

            const fd = new FormData();
            fd.append("title", title || voice?.title || "");
            if (voice?.idx) fd.append("idx", String(voice.idx));
            fd.append("audio", audioFile, fileName);

            const response = await uploadThumbnailAudio(thumbId, fd);
            const freshAudios = await fetchThumbnailAudios(thumbId);
            audioMap.value = {...audioMap.value, [thumbId]: freshAudios};

            const newAudio =
                freshAudios.find((audio) => String(audio.id) === String(response?.id)) ??
                freshAudios.find((audio) => !previousAudioIds.has(String(audio.id))) ??
                freshAudios[freshAudios.length - 1] ??
                null;

            if (newAudio) {
                if (String(selectedThumb.value?.id ?? "") !== String(thumbId)) {
                    selectedThumb.value = targetThumb;
                }
                selectedVoiceId.value = newAudio.id;
                selectedSpeaker.value = speakerForVoice(newAudio, targetThumb) || selectedSpeaker.value;
            }

            return newAudio;
        } catch (e) {
            if (deletedForReplace) {
                try {
                    const freshAudios = await fetchThumbnailAudios(thumbId);
                    audioMap.value = {...audioMap.value, [thumbId]: freshAudios};
                    const fallback = firstVoiceForThumb(targetThumb);
                    selectedVoiceId.value = fallback?.id ?? null;
                    selectedSpeaker.value = fallback ? speakerForVoice(fallback, targetThumb) : nextSpeakerForThumb(targetThumb);
                } catch {
                }
            }
            throw e;
        }
    }

    return {
        recordingFileInput,
        openRecordingAudioFile,
        onRecordingAudioFileChange,
        importRecordingAudioFile,
        prepareAudioUploadFile,
        uploadVoiceAudioFile,
    };
}
