import {useVoiceSelection} from "./useVoiceSelection";
import {useAudioFileImport} from "./useAudioFileImport";
import {useQuickRecording} from "./useQuickRecording";
import {useDraftAudioImport} from "./useDraftAudioImport";
import {useRecordingLabels} from "./useRecordingLabels";

export function useVoiceTakes(options = {}) {
    // `quickRecording` is created after `voiceSelection` but a couple of voice-selection
    // functions (e.g. removeVoice) need to stop an in-progress quick recording. These
    // getters close over the binding below, which is filled in once quickRecording exists.
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
