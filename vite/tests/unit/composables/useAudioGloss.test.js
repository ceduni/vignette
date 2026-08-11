import {ref, nextTick} from "vue";
import {useAudioGloss} from "@/composables/useAudioGloss";

function makeToast() {
    return {success: vi.fn(), error: vi.fn()};
}

describe("useAudioGloss", () => {
    it("loads gloss fields from the selected voice", async () => {
        const selectedVoice = ref({id: 1, transcription: "hello", gloss: "1sg-go", freeTranslation: "I go"});
        const selectedThumb = ref({id: 10});

        const {glossAudioId, glossTranscription, glossGloss, glossFreeTranslation} = useAudioGloss(
            selectedVoice, selectedThumb, {toast: makeToast()}
        );

        expect(glossAudioId.value).toBe(1);
        expect(glossTranscription.value).toBe("hello");
        expect(glossGloss.value).toBe("1sg-go");
        expect(glossFreeTranslation.value).toBe("I go");

        selectedVoice.value = {id: 2};
        await nextTick();
        expect(glossAudioId.value).toBe(2);
        expect(glossTranscription.value).toBe("");
    });

    it("saveGloss does nothing when there is no selected audio", async () => {
        const selectedVoice = ref(null);
        const selectedThumb = ref(null);
        const updateAudioGloss = vi.fn();

        const {saveGloss} = useAudioGloss(selectedVoice, selectedThumb, {
            updateAudioGloss,
            toast: makeToast(),
        });

        await saveGloss();
        expect(updateAudioGloss).not.toHaveBeenCalled();
    });

    it("saveGloss updates local voice fields for a draft/local take without calling the API", async () => {
        const selectedVoice = ref({id: "local-1", isDraft: true});
        const selectedThumb = ref({id: 10});
        const updateAudioGloss = vi.fn();
        const updateVoiceFields = vi.fn();
        const toast = makeToast();

        const {glossTranscription, saveGloss} = useAudioGloss(selectedVoice, selectedThumb, {
            updateAudioGloss,
            updateVoiceFields,
            toast,
        });

        glossTranscription.value = "new text";
        await saveGloss();

        expect(updateAudioGloss).not.toHaveBeenCalled();
        expect(updateVoiceFields).toHaveBeenCalledWith(
            {id: 10},
            {id: "local-1", isDraft: true},
            {transcription: "new text", gloss: "", freeTranslation: ""}
        );
        expect(toast.success).toHaveBeenCalledWith("Gloss saved.");
    });

    it("saveGloss persists via the API and refreshes the thumbnail's audios", async () => {
        const selectedVoice = ref({id: 5, transcription: "a"});
        const selectedThumb = ref({id: 10});
        const updateAudioGloss = vi.fn(async () => {});
        const fetchThumbnailAudios = vi.fn(async () => [{id: 5, transcription: "b"}]);
        const audioMap = ref({});
        const toast = makeToast();

        const {glossTranscription, saveGloss} = useAudioGloss(selectedVoice, selectedThumb, {
            updateAudioGloss,
            fetchThumbnailAudios,
            audioMap,
            toast,
        });

        glossTranscription.value = "b";
        await saveGloss();

        expect(updateAudioGloss).toHaveBeenCalledWith(5, {
            transcription: "b",
            gloss: null,
            freeTranslation: null,
        });
        expect(fetchThumbnailAudios).toHaveBeenCalledWith(10);
        expect(audioMap.value[10]).toEqual([{id: 5, transcription: "b"}]);
        expect(toast.success).toHaveBeenCalledWith("Gloss saved.");
    });

    it("saveGloss reports an error if the API call fails", async () => {
        const selectedVoice = ref({id: 5});
        const selectedThumb = ref({id: 10});
        const updateAudioGloss = vi.fn(async () => {
            throw new Error("network down");
        });
        const toast = makeToast();

        const {saveGloss, glossSaving} = useAudioGloss(selectedVoice, selectedThumb, {
            updateAudioGloss,
            toast,
        });

        await saveGloss();

        expect(toast.error).toHaveBeenCalledWith("network down");
        expect(glossSaving.value).toBe(false);
    });

    it("focusGloss scrolls to and focuses the transcription input", async () => {
        const selectedVoice = ref(null);
        const selectedThumb = ref(null);
        const {glossTranscriptionInput, focusGloss} = useAudioGloss(selectedVoice, selectedThumb, {toast: makeToast()});

        const el = {scrollIntoView: vi.fn(), focus: vi.fn()};
        glossTranscriptionInput.value = el;

        focusGloss();
        await nextTick();

        expect(el.scrollIntoView).toHaveBeenCalledWith({behavior: "smooth", block: "center"});
        expect(el.focus).toHaveBeenCalled();
    });
});
