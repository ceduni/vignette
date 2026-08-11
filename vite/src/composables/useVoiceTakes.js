import {useVoiceSelection} from "./useVoiceSelection";
import {useAudioFileImport} from "./useAudioFileImport";
import {useQuickRecording} from "./useQuickRecording";
import {useDraftAudioImport} from "./useDraftAudioImport";
import {useRecordingLabels} from "./useRecordingLabels";

export function useVoiceTakes(options = {}) {
    let quickRecording;

    const voiceSelection = useVoiceSelection({
        ...options,
        getQuickRecordingThumbId: () => quickRecording.quickRecordingThumbId.value,
        getQuickRecordingVoiceId: () => quickRecording.quickRecordingVoiceId.value,
        stopQuickRecording: (...args) => quickRecording.stopQuickRecording(...args),
    });

    const audioFileImport = useAudioFileImport({
        ...options,
        ...voiceSelection,
    });

    quickRecording = useQuickRecording({
        ...options,
        ...voiceSelection,
        uploadVoiceAudioFile: audioFileImport.uploadVoiceAudioFile,
    });

    const draftAudioImport = useDraftAudioImport({
        ...options,
        ...voiceSelection,
        prepareAudioUploadFile: audioFileImport.prepareAudioUploadFile,
        uploadVoiceAudioFile: audioFileImport.uploadVoiceAudioFile,
    });

    const recordingLabels = useRecordingLabels({
        ...options,
        ...voiceSelection,
        quickRecordingThumbId: quickRecording.quickRecordingThumbId,
    });

    return {
        ...voiceSelection,
        ...audioFileImport,
        ...quickRecording,
        ...draftAudioImport,
        ...recordingLabels,
    };
}
