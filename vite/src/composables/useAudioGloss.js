import {nextTick, ref, watch} from "vue";

export function useAudioGloss(selectedVoiceRef, selectedThumbRef, options = {}) {
    const {
        updateAudioGloss,
        fetchThumbnailAudios,
        updateVoiceFields,
        audioMap,
        toast,
    } = options;

    const glossTranscription = ref("");
    const glossGloss = ref("");
    const glossFreeTranslation = ref("");
    const glossSaving = ref(false);
    const glossAudioId = ref(null);
    const glossTranscriptionInput = ref(null);

    watch(
        selectedVoiceRef,
        (audio) => {
            glossAudioId.value = audio?.id ?? null;
            glossTranscription.value = audio?.transcription ?? "";
            glossGloss.value = audio?.gloss ?? "";
            glossFreeTranslation.value = audio?.freeTranslation ?? "";
        },
        {immediate: true}
    );

    async function saveGloss() {
        if (!glossAudioId.value) return;
        glossSaving.value = true;
        try {
            const localVoice = selectedVoiceRef.value;
            if (localVoice?.isDraft || String(localVoice?.id ?? "").startsWith("local-")) {
                updateVoiceFields(selectedThumbRef.value, localVoice, {
                    transcription: glossTranscription.value || "",
                    gloss: glossGloss.value || "",
                    freeTranslation: glossFreeTranslation.value || "",
                });
                toast.success("Gloss saved.");
                return;
            }

            await updateAudioGloss(glossAudioId.value, {
                transcription: glossTranscription.value || null,
                gloss: glossGloss.value || null,
                freeTranslation: glossFreeTranslation.value || null,
            });
            if (selectedThumbRef.value) {
                audioMap.value[selectedThumbRef.value.id] = await fetchThumbnailAudios(selectedThumbRef.value.id);
            }
            toast.success("Gloss saved.");
        } catch (e) {
            toast.error(e.message || "Failed to save.");
        } finally {
            glossSaving.value = false;
        }
    }

    function focusGloss() {
        nextTick(() => {
            const el = glossTranscriptionInput.value;
            if (!el) return;
            el.scrollIntoView({behavior: "smooth", block: "center"});
            el.focus();
        });
    }

    return {
        glossTranscription,
        glossGloss,
        glossFreeTranslation,
        glossSaving,
        glossAudioId,
        glossTranscriptionInput,
        saveGloss,
        focusGloss,
    };
}
