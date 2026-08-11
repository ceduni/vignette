import {ref, nextTick} from "vue";
import {useAudioTrimEditor} from "@/composables/useAudioTrimEditor";

const toastMocks = vi.hoisted(() => ({
    success: vi.fn(),
    error: vi.fn(),
}));

vi.mock("@/composables/useToast", () => ({
    useToast: () => toastMocks,
}));

describe("useAudioTrimEditor", () => {
    beforeEach(() => {
        toastMocks.success.mockReset();
        toastMocks.error.mockReset();
    });

    it("loads trimStart/trimEnd from the selected voice, defaulting to 0/100", async () => {
        const selectedVoice = ref({id: 1, trimStart: 20, trimEnd: 80});
        const selectedThumb = ref({id: 10});

        const {trimStart, trimEnd} = useAudioTrimEditor(selectedVoice, selectedThumb);
        expect(trimStart.value).toBe(20);
        expect(trimEnd.value).toBe(80);

        selectedVoice.value = {id: 2};
        await nextTick();
        expect(trimStart.value).toBe(0);
        expect(trimEnd.value).toBe(100);
    });

    it("keeps trimStart below trimEnd and vice versa when updated", () => {
        const selectedVoice = ref({id: 1, trimStart: 0, trimEnd: 100});
        const selectedThumb = ref({id: 10});

        const {trimStart, trimEnd, updateTrimStart, updateTrimEnd} = useAudioTrimEditor(selectedVoice, selectedThumb);

        updateTrimEnd(30);
        expect(trimEnd.value).toBe(30);

        updateTrimStart(90);
        expect(trimStart.value).toBe(29);
    });

    it("applyTrimSelection persists trimStart/trimEnd via updateVoiceFields and closes the editor", () => {
        const selectedVoice = ref({id: 1, trimStart: 0, trimEnd: 100});
        const selectedThumb = ref({id: 10});
        const updateVoiceFields = vi.fn();

        const {
            recordingTrimOpen,
            trimStart,
            trimEnd,
            updateTrimStart,
            updateTrimEnd,
            applyTrimSelection,
        } = useAudioTrimEditor(selectedVoice, selectedThumb, {updateVoiceFields});

        recordingTrimOpen.value = true;
        updateTrimStart(10);
        updateTrimEnd(90);
        applyTrimSelection();

        expect(updateVoiceFields).toHaveBeenCalledWith(
            {id: 10},
            {id: 1, trimStart: 0, trimEnd: 100},
            {trimStart: trimStart.value, trimEnd: trimEnd.value}
        );
        expect(recordingTrimOpen.value).toBe(false);
        expect(toastMocks.success).toHaveBeenCalled();
    });

    it("applyTrimSelection does nothing when there's no selected voice or thumb", () => {
        const selectedVoice = ref(null);
        const selectedThumb = ref(null);
        const updateVoiceFields = vi.fn();

        const {applyTrimSelection} = useAudioTrimEditor(selectedVoice, selectedThumb, {updateVoiceFields});
        applyTrimSelection();

        expect(updateVoiceFields).not.toHaveBeenCalled();
    });

    it("resetTrimSelection resets to the full range", () => {
        const selectedVoice = ref({id: 1, trimStart: 20, trimEnd: 80});
        const selectedThumb = ref({id: 10});

        const {trimStart, trimEnd, resetTrimSelection} = useAudioTrimEditor(selectedVoice, selectedThumb);
        expect(trimStart.value).toBe(20);

        resetTrimSelection();
        expect(trimStart.value).toBe(0);
        expect(trimEnd.value).toBe(100);
    });
});
