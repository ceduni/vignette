import {computed, nextTick, ref, watch} from "vue";
import {buildSelectedAudios} from "@/utils/scenarioStoryboard.js";

export function useVoiceSelection(options = {}) {
    const {
        selectedThumb,
        audioMap,
        activeAudioId,
        selectedVoice,
        selectedAudios,
        toast,
        glossTranscription,
        glossGloss,
        glossFreeTranslation,
        selectThumb,
        openRecorderForSelection,
        setActiveAudio,
        autoplayStop,
        autoplayPause,
        isAutoplayPlaying,
        autoplayCurrentItem,
        deleteAudio,
        getQuickRecordingThumbId,
        getQuickRecordingVoiceId,
        stopQuickRecording,
    } = options;

    const selectedSpeaker = ref("A");
    const selectedVoiceId = ref(null);
    const studioRecorderEl = ref(null);

    const nextSelectedSpeaker = computed(() => {
        return selectedThumb.value ? nextSpeakerForThumb(selectedThumb.value) : "A";
    });

    function scrollToRecorder() {
        nextTick(() => {
            studioRecorderEl.value?.scrollIntoView?.({behavior: "smooth", block: "center"});
        });
    }

    function selectVoice(audio, thumb = selectedThumb.value, {scrollRecorder = true} = {}) {
        if (!audio) return;

        const targetThumb = thumb ?? selectedThumb.value;
        if (targetThumb && String(selectedThumb.value?.id ?? "") !== String(targetThumb.id)) {
            selectedThumb.value = targetThumb;
        }

        selectedVoiceId.value = audio.id;
        const takeIndex = buildSelectedAudios(audioMap.value, targetThumb).findIndex((item) => String(item.id) === String(audio.id));
        selectedSpeaker.value = speakerForAudio(audio, Math.max(0, takeIndex));
        glossTranscription.value = audio.transcription ?? "";
        glossGloss.value = audio.gloss ?? "";
        glossFreeTranslation.value = audio.freeTranslation ?? "";
        openRecorderForSelection({scroll: scrollRecorder});
    }

    function selectSpeakerSlot(speaker, thumb = selectedThumb.value) {
        if (!thumb?.id) return;
        const existing = findVoiceBySpeaker(thumb, speaker);

        if (existing) {
            selectVoice(existing, thumb);
            return;
        }

        if ((audioMap.value[thumb.id] || []).length >= 4) {
            toast.error("This scene already has four takes.");
            return;
        }

        const draft = addDraftVoice(thumb, speaker);
        if (draft) {
            selectVoice(draft, thumb);
        }
    }

    function findVoiceBySpeaker(thumb, speaker) {
        if (!thumb?.id || !speaker) return null;
        return buildSelectedAudios(audioMap.value, thumb).find((audio, index) =>
            speakerForAudio(audio, index) === speaker
        ) ?? null;
    }

    function indexForSpeaker(speaker) {
        const index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(String(speaker || "").toUpperCase());
        return index >= 0 ? index : 0;
    }

    function nextVoiceIdxForSpeaker(thumb, speaker) {
        const voices = audioMap.value[thumb?.id] || [];
        const used = new Set(voices.map((audio) => Number(audio.idx)).filter(Number.isFinite));
        const preferred = indexForSpeaker(speaker) + 1;
        if (!used.has(preferred)) return preferred;
        return Math.max(0, ...used) + 1;
    }

    function addDraftVoice(targetThumb, speaker = nextSpeakerForThumb(targetThumb)) {
        if (!targetThumb?.id) return null;
        const voices = audioMap.value[targetThumb.id] || [];
        const assignedSpeaker = speaker || nextSpeakerForThumb(targetThumb);
        const existing = findVoiceBySpeaker(targetThumb, assignedSpeaker);
        if (existing) return existing;

        if (voices.length >= 4) {
            toast.error("This scene already has four takes.");
            return null;
        }

        const draft = {
            id: `draft-${targetThumb.id}-${assignedSpeaker}-${Date.now()}`,
            idx: nextVoiceIdxForSpeaker(targetThumb, assignedSpeaker),
            title: takeLabel(assignedSpeaker),
            speaker: assignedSpeaker,
            isDraft: true,
            transcription: "",
            gloss: "",
            freeTranslation: "",
        };

        audioMap.value = {
            ...audioMap.value,
            [targetThumb.id]: [...voices, draft],
        };

        return draft;
    }

    function ensureVoiceForRecording(thumb, requestedVoice = null, speaker = null, {preferSelected = true} = {}) {
        if (!thumb?.id) return {voice: null, created: false};

        if (String(selectedThumb.value?.id ?? "") !== String(thumb.id)) {
            selectThumb(thumb);
        }

        const voices = voicesForThumb(thumb);
        const requested = requestedVoice
            ? voices.find((audio) => String(audio.id) === String(requestedVoice.id))
            : null;
        const selected = preferSelected ? targetVoiceForThumb(thumb) : null;
        const target = requested ?? selected;

        if (target) {
            selectVoice(target, thumb);
            return {voice: target, created: false};
        }

        const targetSpeaker = speaker || selectedSpeaker.value || nextSpeakerForThumb(thumb);
        const existing = findVoiceBySpeaker(thumb, targetSpeaker);
        if (existing) {
            selectVoice(existing, thumb);
            return {voice: existing, created: false};
        }

        const draft = addDraftVoice(thumb, targetSpeaker);
        if (draft) selectVoice(draft, thumb);

        return {voice: draft, created: !!draft?.isDraft};
    }

    async function removeVoice(audio, thumb = selectedThumb.value) {
        if (!audio || !thumb?.id) return;

        if (isAudioPlaying(audio, thumb)) {
            autoplayStop();
        }

        if (
            getQuickRecordingThumbId() != null &&
            String(getQuickRecordingThumbId()) === String(thumb.id) &&
            String(getQuickRecordingVoiceId() ?? "") === String(audio.id)
        ) {
            stopQuickRecording();
        }

        try {
            const isLocalVoice = audio.isDraft || String(audio.id).startsWith("local-") || String(audio.id).startsWith("draft-");
            if (!isLocalVoice) {
                await deleteAudio(audio.id);
            }

            const currentVoices = audioMap.value[thumb.id] || [];
            const removedIndex = currentVoices.findIndex((item) => String(item.id) === String(audio.id));
            const remaining = currentVoices.filter((item) => String(item.id) !== String(audio.id));
            audioMap.value = {
                ...audioMap.value,
                [thumb.id]: remaining,
            };

            const sortedRemaining = buildSelectedAudios({[thumb.id]: remaining}, thumb);
            const nextVoice = sortedRemaining[Math.min(Math.max(removedIndex, 0), sortedRemaining.length - 1)] ?? sortedRemaining[0] ?? null;
            selectedVoiceId.value = nextVoice?.id ?? null;
            selectedSpeaker.value = nextVoice ? speakerForAudio(nextVoice, Math.max(0, sortedRemaining.indexOf(nextVoice))) : nextSpeakerForThumb(thumb);
            activeAudioId.value = String(activeAudioId.value ?? "") === String(audio.id) ? null : activeAudioId.value;
            toast.success("Take removed.");
        } catch (e) {
            toast.error(e.message || "Could not remove this take.");
        }
    }

    function speakerForAudio(audio, index) {
        return audio?.speaker || speakerForIndex(index);
    }

    function takeLabel(speaker = "A") {
        return `Take ${String(speaker || "A").toUpperCase()}`;
    }

    function normalizedAudioTitle(title, speaker = "A") {
        const cleanTitle = String(title || "").trim();
        if (/^Voice\s+[A-Z](\b|$)/i.test(cleanTitle)) {
            return cleanTitle.replace(/^Voice\s+[A-Z]/i, takeLabel(speaker));
        }
        return cleanTitle;
    }

    function speakerClass(audio, index) {
        return String(speakerForAudio(audio, index)).toLowerCase();
    }

    function voicesForThumb(thumb) {
        return buildSelectedAudios(audioMap.value, thumb);
    }

    function firstVoiceForThumb(thumb) {
        return voicesForThumb(thumb)[0] ?? null;
    }

    function voiceIndexInThumb(audio, thumb) {
        if (!audio || !thumb?.id) return -1;
        return voicesForThumb(thumb).findIndex((item) => String(item.id) === String(audio.id));
    }

    function speakerForVoice(audio, thumb) {
        if (!audio) return "";
        const index = voiceIndexInThumb(audio, thumb);
        return speakerForAudio(audio, Math.max(0, index));
    }

    function voiceDisplayTitle(audio, thumb = selectedThumb.value) {
        const speaker = speakerForVoice(audio, thumb) || selectedSpeaker.value || "A";
        return normalizedAudioTitle(audio?.title, speaker) || takeLabel(speaker);
    }

    function audioPreviewDetail(audio, thumb = selectedThumb.value) {
        if (!audio) return "";
        const speaker = speakerForVoice(audio, thumb) || selectedSpeaker.value || "A";
        const title = voiceDisplayTitle(audio, thumb);
        return title === takeLabel(speaker) ? "Recorded audio" : title;
    }

    function selectedVoiceForThumb(thumb) {
        if (!thumb?.id || String(selectedThumb.value?.id ?? "") !== String(thumb.id)) return null;
        return voicesForThumb(thumb).find((audio) => String(audio.id) === String(selectedVoiceId.value ?? "")) ?? null;
    }

    function targetVoiceForThumb(thumb) {
        if (!thumb?.id || String(selectedThumb.value?.id ?? "") !== String(thumb.id)) return null;
        return selectedVoiceForThumb(thumb) ?? selectedVoice.value ?? null;
    }

    function isLocalAudioId(id) {
        return String(id ?? "").startsWith("local-") || String(id ?? "").startsWith("draft-");
    }

    function speakerForIndex(index) {
        const normalized = Math.max(0, Number(index) || 0);
        const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        if (normalized < alphabet.length) {
            return alphabet[normalized];
        }

        return `A${normalized - alphabet.length + 1}`;
    }

    function nextSpeakerForThumb(thumb) {
        if (!thumb?.id) return "A";
        const used = new Set(voicesForThumb(thumb).map((audio, index) => speakerForAudio(audio, index)));
        const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return alphabet.split("").find((speaker) => !used.has(speaker)) || speakerForIndex(used.size);
    }

    function isAudioPlaying(audio, thumb = selectedThumb.value) {
        const item = autoplayCurrentItem();
        return (
            isAutoplayPlaying() &&
            String(activeAudioId.value ?? "") === String(audio?.id ?? "") &&
            String(item?.audioId ?? "") === String(audio?.id ?? "") &&
            String(item?.thumbnailId ?? "") === String(thumb?.id ?? "")
        );
    }

    async function toggleAudioPlayback(audio, thumb = selectedThumb.value) {
        if (!audio || !thumb) {
            autoplayStop();
            activeAudioId.value = null;
            return;
        }

        selectVoice(audio, thumb);

        if (audio.isDraft) {
            return;
        }

        if (isAudioPlaying(audio, thumb)) {
            autoplayPause();
            return;
        }

        if (String(selectedThumb.value?.id ?? "") !== String(thumb.id)) {
            selectedThumb.value = thumb;
            selectedSpeaker.value = nextSpeakerForThumb(thumb);
        }

        await setActiveAudio(audio);
    }

    function addLocalAudioClip(targetThumb, {title, previewUrl, speaker, sourceDraftId = null}) {
        if (!targetThumb?.id || !previewUrl) return;
        const currentVoice = selectedVoice.value && String(selectedThumb.value?.id ?? "") === String(targetThumb.id)
            ? selectedVoice.value
            : null;
        const assignedSpeaker = speaker || currentVoice?.speaker || nextSpeakerForThumb(targetThumb);
        const nextAudio = {
            id: currentVoice?.isDraft ? currentVoice.id : `local-audio-${Date.now()}-${Math.round(Math.random() * 1000)}`,
            idx: currentVoice?.idx ?? ((audioMap.value[targetThumb.id]?.length ?? 0) + 1),
            title: title || normalizedAudioTitle(currentVoice?.title, assignedSpeaker) || takeLabel(assignedSpeaker),
            speaker: assignedSpeaker,
            previewUrl,
            sourceDraftId,
            isDraft: false,
            transcription: currentVoice?.transcription ?? "",
            gloss: currentVoice?.gloss ?? "",
            freeTranslation: currentVoice?.freeTranslation ?? "",
        };

        const existing = audioMap.value[targetThumb.id] || [];
        const replaceExisting = currentVoice && existing.some((audio) => String(audio.id) === String(currentVoice.id));

        audioMap.value = {
            ...audioMap.value,
            [targetThumb.id]: replaceExisting
                ? existing.map((audio) => String(audio.id) === String(currentVoice.id) ? nextAudio : audio)
                : [...existing, nextAudio],
        };

        selectedVoiceId.value = nextAudio.id;
        selectedSpeaker.value = assignedSpeaker;
    }

    function recordButtonVoiceForThumb(thumb) {
        if (!thumb?.id || String(selectedThumb.value?.id ?? "") !== String(thumb.id)) return null;
        const selected = selectedVoiceForThumb(thumb);
        return selected?.isDraft ? selected : null;
    }

    function recordButtonLabelForThumb(thumb) {
        if (!thumb?.id) return "Record take";
        const targetVoice = recordButtonVoiceForThumb(thumb);
        if (targetVoice) {
            const speaker = speakerForVoice(targetVoice, thumb);
            return `Record ${takeLabel(speaker)}`;
        }
        if (voicesForThumb(thumb).length >= 4) return "Max takes reached";
        return `Record ${takeLabel(nextSpeakerForThumb(thumb))}`;
    }

    watch(
        selectedVoice,
        (audio) => {
            selectedSpeaker.value = audio ? speakerForAudio(audio, Math.max(0, selectedAudios.value.indexOf(audio))) : nextSelectedSpeaker.value;
        },
        {immediate: true}
    );

    return {
        selectedSpeaker,
        selectedVoiceId,
        studioRecorderEl,
        nextSelectedSpeaker,
        scrollToRecorder,
        selectVoice,
        selectSpeakerSlot,
        findVoiceBySpeaker,
        addDraftVoice,
        ensureVoiceForRecording,
        removeVoice,
        speakerForAudio,
        takeLabel,
        normalizedAudioTitle,
        speakerClass,
        voicesForThumb,
        firstVoiceForThumb,
        voiceIndexInThumb,
        speakerForVoice,
        voiceDisplayTitle,
        audioPreviewDetail,
        selectedVoiceForThumb,
        targetVoiceForThumb,
        isLocalAudioId,
        speakerForIndex,
        nextSpeakerForThumb,
        isAudioPlaying,
        toggleAudioPlayback,
        addLocalAudioClip,
        recordButtonVoiceForThumb,
        recordButtonLabelForThumb,
    };
}
