import {ref, nextTick} from "vue";
import {useAudioTrimEditor} from "@/composables/useAudioTrimEditor";

const toastMocks = vi.hoisted(() => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
}));

const trimMocks = vi.hoisted(() => ({
    trimAudioSourceToWav: vi.fn(),
}));

vi.mock("@/composables/useToast", () => ({
    useToast: () => toastMocks,
}));

vi.mock("@/utils/audioTrim", () => trimMocks);

Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    writable: true,
    value: vi.fn(),
});

Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    writable: true,
    value: vi.fn(),
});

describe("useAudioTrimEditor", () => {
    beforeEach(() => {
        toastMocks.success.mockReset();
        toastMocks.error.mockReset();
        toastMocks.info.mockReset();
        trimMocks.trimAudioSourceToWav.mockReset();
        trimMocks.trimAudioSourceToWav.mockResolvedValue({
            blob: new Blob(["trimmed"], {type: "audio/wav"}),
            durationSeconds: 2.5,
        });
        URL.createObjectURL.mockReset();
        URL.createObjectURL.mockReturnValue("blob:trimmed-audio");
        URL.revokeObjectURL.mockReset();
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

    it("applyTrimSelection cuts a local audio file and closes the editor", async () => {
        const selectedVoice = ref({id: "local-1", previewUrl: "blob:original", trimStart: 0, trimEnd: 100});
        const selectedThumb = ref({id: 10});
        const updateVoiceFields = vi.fn();

        const {
            recordingTrimOpen,
            trimStart,
            trimEnd,
            updateTrimStart,
            updateTrimEnd,
            applyTrimSelection,
        } = useAudioTrimEditor(selectedVoice, selectedThumb, {
            updateVoiceFields,
            studioSandboxMode: ref(true),
        });

        recordingTrimOpen.value = true;
        updateTrimStart(10);
        updateTrimEnd(90);
        await applyTrimSelection();

        expect(trimMocks.trimAudioSourceToWav).toHaveBeenCalledWith("blob:original", 10, 90);
        expect(updateVoiceFields).toHaveBeenCalledWith(
            {id: 10},
            {id: "local-1", previewUrl: "blob:original", trimStart: 0, trimEnd: 100},
            {previewUrl: "blob:trimmed-audio", trimStart: 0, trimEnd: 100}
        );
        expect(trimStart.value).toBe(0);
        expect(trimEnd.value).toBe(100);
        expect(recordingTrimOpen.value).toBe(false);
        expect(toastMocks.success).toHaveBeenCalled();
    });

    it("replaces a persisted audio and reloads the thumbnail audios", async () => {
        const selectedVoice = ref({id: 7, title: "Take A"});
        const selectedThumb = ref({id: 10});
        const audioMap = ref({10: [selectedVoice.value]});
        const replaceAudioContent = vi.fn().mockResolvedValue({id: 7});
        const fetchThumbnailAudios = vi.fn().mockResolvedValue([{id: 7, title: "Take A", mime: "audio/wav"}]);

        const editor = useAudioTrimEditor(selectedVoice, selectedThumb, {
            audioMap,
            replaceAudioContent,
            fetchThumbnailAudios,
        });
        editor.updateTrimStart(25);
        editor.updateTrimEnd(75);

        await editor.applyTrimSelection();

        expect(replaceAudioContent).toHaveBeenCalledWith(7, expect.any(FormData));
        expect(fetchThumbnailAudios).toHaveBeenCalledWith(10);
        expect(audioMap.value[10]).toEqual([{id: 7, title: "Take A", mime: "audio/wav"}]);
        expect(toastMocks.success).toHaveBeenCalledWith("Audio cut saved (2.5 s).");
    });

    it("applyTrimSelection does nothing when there's no selected voice or thumb", async () => {
        const selectedVoice = ref(null);
        const selectedThumb = ref(null);
        const updateVoiceFields = vi.fn();

        const {applyTrimSelection} = useAudioTrimEditor(selectedVoice, selectedThumb, {updateVoiceFields});
        await applyTrimSelection();

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
