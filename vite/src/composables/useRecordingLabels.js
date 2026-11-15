import {computed} from "vue";

export function useRecordingLabels(options = {}) {
    const {
        selectedThumb,
        getSelectedSceneNumber,
        targetVoiceForThumb,
        speakerForVoice,
        selectedSpeaker,
        nextSelectedSpeaker,
        takeLabel,
        quickRecordingThumbId,
    } = options;

    const recordingTargetVoice = computed(() => {
        if (!selectedThumb.value) return null;
        return targetVoiceForThumb(selectedThumb.value);
    });

    const recordingTargetSpeaker = computed(() => {
        return speakerForVoice(recordingTargetVoice.value, selectedThumb.value) || selectedSpeaker.value || nextSelectedSpeaker.value;
    });

    const recordingSceneLabel = computed(() => {
        return selectedThumb.value?.title || `Scene ${getSelectedSceneNumber() || 1}`;
    });

    const recordingTargetLabel = computed(() => {
        return `${recordingSceneLabel.value} · ${takeLabel(recordingTargetSpeaker.value)}`;
    });

    const recordingStatusLabel = computed(() => {
        if (quickRecordingThumbId.value != null) return "Recording now";
        if (recordingTargetVoice.value?.isDraft) return "No audio yet";
        if (recordingTargetVoice.value) return "Audio ready";
        return "No audio yet";
    });

    const recordingActionLabel = computed(() => {
        if (quickRecordingThumbId.value != null) return "Stop recording";
        if (recordingTargetVoice.value && !recordingTargetVoice.value.isDraft) return "Tap to replace";
        return "Tap to record";
    });

    const recordingMicLabel = computed(() => {
        const take = takeLabel(recordingTargetSpeaker.value);
        if (quickRecordingThumbId.value != null) return `Stop recording ${take}`;
        if (recordingTargetVoice.value && !recordingTargetVoice.value.isDraft) return `Replace ${take}`;
        return `Record ${take}`;
    });

    return {
        recordingTargetVoice,
        recordingTargetSpeaker,
        recordingSceneLabel,
        recordingTargetLabel,
        recordingStatusLabel,
        recordingActionLabel,
        recordingMicLabel,
    };
}
