import {computed, ref, watch} from "vue";
import {buildApiUrl} from "../api/rest";
import {useToast} from "./useToast";
import {renderAmbiencePresetBlob} from "../utils/ambienceSynth";

export const ambiencePresets = [
    {
        id: "forest-morning",
        title: "Forest morning",
        badge: "Nature",
        hint: "Birds, leaves, light air",
        query: "forest morning birds ambience",
        profile: "forest",
        volume: 24,
        mixkitUrl: "https://mixkit.co/free-sound-effects/nature/",
    },
    {
        id: "rain-window",
        title: "Rain on window",
        badge: "Weather",
        hint: "Soft rain, indoor distance",
        query: "rain window room ambience",
        profile: "rain",
        volume: 22,
        mixkitUrl: "https://mixkit.co/free-sound-effects/nature/",
    },
    {
        id: "busy-street",
        title: "Busy street",
        badge: "Urban",
        hint: "Traffic, footsteps, voices",
        query: "busy city street traffic ambience",
        profile: "street",
        volume: 18,
        mixkitUrl: "https://mixkit.co/free-sound-effects/lifestyle/",
    },
    {
        id: "market-crowd",
        title: "Market crowd",
        badge: "Crowd",
        hint: "Vendors, chatter, movement",
        query: "market crowd vendors ambience",
        profile: "market",
        volume: 20,
        mixkitUrl: "https://mixkit.co/free-sound-effects/lifestyle/",
    },
    {
        id: "cafe-murmur",
        title: "Cafe murmur",
        badge: "Indoor",
        hint: "Low voices, cups, room tone",
        query: "cafe ambience coffee shop murmur",
        profile: "cafe",
        volume: 16,
        mixkitUrl: "https://mixkit.co/free-sound-effects/lifestyle/",
    },
    {
        id: "quiet-room",
        title: "Quiet room tone",
        badge: "Room",
        hint: "Subtle air, interior stillness",
        query: "quiet room tone interior ambience",
        profile: "room",
        volume: 10,
        mixkitUrl: "https://mixkit.co/free-sound-effects/lifestyle/",
    },
    {
        id: "night-insects",
        title: "Night insects",
        badge: "Night",
        hint: "Crickets, soft outdoor bed",
        query: "night crickets insects ambience",
        profile: "night",
        volume: 20,
        mixkitUrl: "https://mixkit.co/free-sound-effects/nature/",
    },
    {
        id: "ocean-shore",
        title: "Ocean shore",
        badge: "Water",
        hint: "Waves, shore, open air",
        query: "ocean waves shore ambience",
        profile: "ocean",
        volume: 24,
        mixkitUrl: "https://mixkit.co/free-sound-effects/nature/",
    },
];

export const ambienceLibraries = [
    {id: "pixabay", label: "Pixabay", tag: "quick", note: "royalty-free media"},
    {id: "freesound", label: "Freesound", tag: "field", note: "Creative Commons sounds"},
    {id: "mixkit", label: "Mixkit", tag: "curated", note: "free sound effects"},
];

export function useBackgroundAmbience({
    scenario,
    isOwner,
    studioSandboxMode,
    getScenarioId,
    fetchScenarioBackgroundAudios,
    uploadScenarioBackgroundAudio,
    deleteAudio,
    prepareAudioUploadFile,
    fileBaseName,
    audioImportErrorMessage,
}) {
    const toast = useToast();

    const ambiencePanelOpen = ref(false);
    const ambienceAdvancedOpen = ref(false);
    const selectedAmbiencePresetId = ref("forest-morning");
    const ambiencePresetEnabled = ref(false);
    const backgroundAudios = ref([]);
    const selectedBackgroundAudioId = ref(null);
    const backgroundAudioFileInput = ref(null);
    const ambDragOver = ref(false);
    const backgroundTitle = ref("");
    const backgroundSourceLabel = ref("");
    const backgroundSourceUrl = ref("");
    const backgroundVolume = ref(28);
    const backgroundLoop = ref(true);
    const backgroundUploading = ref(false);
    const presetAudioGenerating = ref(false);
    const backgroundPlaying = ref(false);

    let backgroundAudioPlayer = null;
    const presetAudioCache = new Map();

    const selectedBackgroundAudio = computed(() => {
        if (!backgroundAudios.value.length) return null;
        if (selectedBackgroundAudioId.value == null) return null;
        return backgroundAudios.value.find((audio) => String(audio.id) === String(selectedBackgroundAudioId.value)) ?? null;
    });

    const selectedAmbiencePreset = computed(() => {
        return ambiencePresets.find((preset) => preset.id === selectedAmbiencePresetId.value) ?? ambiencePresets[0];
    });

    function ambienceLibraryUrl(library, preset = selectedAmbiencePreset.value) {
        const query = encodeURIComponent(preset?.query || "ambience");
        if (library.id === "freesound") {
            return `https://freesound.org/search/?q=${query}`;
        }
        if (library.id === "pixabay") {
            return `https://pixabay.com/sound-effects/search/${query}/`;
        }
        return preset?.mixkitUrl || "https://mixkit.co/free-sound-effects/nature/";
    }

    const ambienceSearchLinks = computed(() => {
        return ambienceLibraries.map((library) => ({
            ...library,
            url: ambienceLibraryUrl(library, selectedAmbiencePreset.value),
        }));
    });

    const backgroundSummaryTitle = computed(() => {
        return selectedBackgroundAudio.value?.title || "Add ambience";
    });

    const backgroundSummaryNote = computed(() => {
        if (selectedBackgroundAudio.value) {
            return selectedBackgroundAudio.value.sourceLabel || selectedBackgroundAudio.value.sourceUrl || "Ready for vignette playback";
        }
        return "";
    });

    const hasActiveBackgroundAmbience = computed(() => {
        return !!selectedBackgroundAudio.value;
    });

    function backgroundAudioUrl(audio = selectedBackgroundAudio.value) {
        if (!audio) return "";
        return audio.previewUrl || audio.contentUrl || buildApiUrl(`/api/audios/${audio.id}/content`);
    }

    function getCachedPresetAudio(preset = selectedAmbiencePreset.value) {
        const existing = presetAudioCache.get(preset.id);
        if (existing) return existing;
        const blob = renderAmbiencePresetBlob(preset);
        const url = URL.createObjectURL(blob);
        const cached = {blob, url};
        presetAudioCache.set(preset.id, cached);
        return cached;
    }

    function createPresetAudioFile(preset = selectedAmbiencePreset.value) {
        const seed = `${preset.id}-${scenario.value?.id ?? "scenario"}-${Date.now()}`;
        const blob = renderAmbiencePresetBlob(preset, seed);
        return new File([blob], `${preset.id}.wav`, {type: "audio/wav"});
    }

    function openAmbiencePanel() {
        ambiencePanelOpen.value = true;
    }

    function closeAmbiencePanel() {
        ambiencePanelOpen.value = false;
    }

    function selectAmbiencePreset(preset) {
        if (!preset) return;
        stopBackgroundPlayback();
        selectedAmbiencePresetId.value = preset.id;
        ambiencePresetEnabled.value = false;
        selectedBackgroundAudioId.value = null;
        backgroundTitle.value = preset.title;
        backgroundSourceLabel.value = "Vignette preset";
        backgroundSourceUrl.value = "";
        backgroundVolume.value = preset.volume;
        backgroundLoop.value = true;
    }

    function selectBackgroundAudio(audio) {
        if (!audio) return;
        ambiencePresetEnabled.value = false;
        selectedBackgroundAudioId.value = audio.id;
    }

    function openBackgroundAudioFile() {
        backgroundAudioFileInput.value?.click?.();
    }

    function onBackgroundAudioFileChange(event) {
        const file = event.target.files?.[0] ?? null;
        importBackgroundAudioFile(file);
        event.target.value = "";
    }

    function onAmbDragOver() {
        if (isOwner.value) ambDragOver.value = true;
    }

    function onAmbDropZoneClick() {
        if (isOwner.value) openBackgroundAudioFile();
    }

    function onBackgroundAudioDrop(event) {
        ambDragOver.value = false;
        if (!isOwner.value) return;
        const file = event.dataTransfer?.files?.[0] ?? null;
        if (file) importBackgroundAudioFile(file);
    }

    async function importBackgroundAudioFile(file) {
        if (!file || !scenario.value) return;

        backgroundUploading.value = true;
        let uploadFile = file;

        try {
            uploadFile = await prepareAudioUploadFile(file);
        } catch (e) {
            toast.error(audioImportErrorMessage(e, "read this background audio"));
            backgroundUploading.value = false;
            return;
        }

        const title = backgroundTitle.value.trim() || fileBaseName(uploadFile, "") || "Vignette ambience";

        try {
            if (studioSandboxMode.value) {
                const audio = {
                    id: `background-local-${Date.now()}`,
                    title,
                    idx: backgroundAudios.value.length + 1,
                    mime: uploadFile.type || "audio/wav",
                    previewUrl: URL.createObjectURL(uploadFile),
                    sourceLabel: backgroundSourceLabel.value.trim(),
                    sourceUrl: backgroundSourceUrl.value.trim(),
                };
                backgroundAudios.value = [...backgroundAudios.value, audio];
                selectedBackgroundAudioId.value = audio.id;
                backgroundTitle.value = "";
                backgroundSourceLabel.value = "";
                backgroundSourceUrl.value = "";
                toast.success("Background audio added.");
                return;
            }

            const fd = new FormData();
            fd.append("title", title);
            fd.append("sourceLabel", backgroundSourceLabel.value.trim());
            fd.append("sourceUrl", backgroundSourceUrl.value.trim());
            fd.append("audio", uploadFile, uploadFile.name || "background-audio");

            const response = await uploadScenarioBackgroundAudio(getScenarioId(), fd);
            await loadBackgroundAudios(response?.id);
            backgroundTitle.value = "";
            backgroundSourceLabel.value = "";
            backgroundSourceUrl.value = "";
            toast.success("Background audio added.");
        } catch (e) {
            toast.error(audioImportErrorMessage(e, "add background audio"));
        } finally {
            backgroundUploading.value = false;
        }
    }

    async function useSelectedAmbiencePreset() {
        if (!scenario.value || !selectedAmbiencePreset.value) return;

        const preset = selectedAmbiencePreset.value;
        const existing = backgroundAudios.value.find((audio) =>
            audio.title === preset.title && audio.sourceLabel === "Vignette preset"
        );
        if (existing) {
            selectBackgroundAudio(existing);
            toast.info(`${preset.title} is already in this vignette.`);
            return;
        }

        presetAudioGenerating.value = true;
        try {
            const file = createPresetAudioFile(preset);
            const previousTitle = backgroundTitle.value;
            const previousSourceLabel = backgroundSourceLabel.value;
            const previousSourceUrl = backgroundSourceUrl.value;
            backgroundTitle.value = preset.title;
            backgroundSourceLabel.value = "Vignette preset";
            backgroundSourceUrl.value = "";
            await importBackgroundAudioFile(file);
            backgroundTitle.value = previousTitle || "";
            backgroundSourceLabel.value = previousSourceLabel || "";
            backgroundSourceUrl.value = previousSourceUrl || "";
        } catch (e) {
            toast.error(e.message || "Could not create preset audio.");
        } finally {
            presetAudioGenerating.value = false;
        }
    }

    async function removeBackgroundAudio(audio = selectedBackgroundAudio.value) {
        if (!audio) return;
        stopBackgroundPlayback();

        const isLocal = String(audio.id).startsWith("background-local-");
        try {
            if (!isLocal && !studioSandboxMode.value) {
                await deleteAudio(audio.id);
                await loadBackgroundAudios();
            } else {
                if (audio.previewUrl) URL.revokeObjectURL(audio.previewUrl);
                backgroundAudios.value = backgroundAudios.value.filter((item) => String(item.id) !== String(audio.id));
                selectedBackgroundAudioId.value = backgroundAudios.value[0]?.id ?? null;
            }
            toast.success("Background audio removed.");
        } catch (e) {
            toast.error(e.message || "Could not remove background audio.");
        }
    }

    function ensureBackgroundPlayer() {
        if (backgroundAudioPlayer) return backgroundAudioPlayer;
        backgroundAudioPlayer = new Audio();
        backgroundAudioPlayer.preload = "auto";
        backgroundAudioPlayer.addEventListener("ended", () => {
            backgroundPlaying.value = false;
        });
        return backgroundAudioPlayer;
    }

    function applyBackgroundPlayerSettings() {
        const player = ensureBackgroundPlayer();
        player.volume = Math.max(0, Math.min(1, Number(backgroundVolume.value) / 100));
        player.loop = !!backgroundLoop.value;
    }

    function stopBackgroundPlayback() {
        if (!backgroundAudioPlayer) return;
        backgroundAudioPlayer.pause();
        backgroundAudioPlayer.currentTime = 0;
        backgroundPlaying.value = false;
    }

    function disposeBackgroundPlayback() {
        stopBackgroundPlayback();
        if (!backgroundAudioPlayer) return;
        backgroundAudioPlayer.removeAttribute("src");
        backgroundAudioPlayer.load();
        backgroundAudioPlayer = null;
    }

    function pauseBackgroundPlayback() {
        if (!backgroundAudioPlayer) return;
        backgroundAudioPlayer.pause();
        backgroundPlaying.value = false;
    }

    async function playBackgroundAudio(audio = selectedBackgroundAudio.value) {
        let src = backgroundAudioUrl(audio);
        if (!src && selectedAmbiencePreset.value) {
            presetAudioGenerating.value = true;
            try {
                src = getCachedPresetAudio(selectedAmbiencePreset.value).url;
            } finally {
                presetAudioGenerating.value = false;
            }
        }
        if (!src) return;

        const player = ensureBackgroundPlayer();
        applyBackgroundPlayerSettings();

        if (player.src !== src) {
            player.src = src;
            player.currentTime = 0;
        }

        await player.play();
        backgroundPlaying.value = true;
    }

    async function toggleBackgroundAudio() {
        if (backgroundPlaying.value) {
            pauseBackgroundPlayback();
            return;
        }

        try {
            await playBackgroundAudio();
        } catch {
            toast.error("Could not play background audio.");
        }
    }

    async function loadBackgroundAudios(preferredId = selectedBackgroundAudioId.value) {
        if (studioSandboxMode.value || String(getScenarioId()).startsWith("emergency-")) {
            return;
        }

        try {
            const rows = await fetchScenarioBackgroundAudios(getScenarioId());
            backgroundAudios.value = Array.isArray(rows) ? rows : [];

            const preferred = backgroundAudios.value.find((audio) => String(audio.id) === String(preferredId ?? ""));
            selectedBackgroundAudioId.value = preferred?.id ?? backgroundAudios.value[0]?.id ?? null;
            ambiencePresetEnabled.value = !selectedBackgroundAudioId.value && ambiencePresetEnabled.value;
        } catch (e) {
            console.error(`Failed to load background audios for scenario ${getScenarioId()}`, e);
            backgroundAudios.value = [];
            selectedBackgroundAudioId.value = null;
        }
    }

    watch([backgroundVolume, backgroundLoop], applyBackgroundPlayerSettings);

    return {
        ambiencePanelOpen,
        ambienceAdvancedOpen,
        selectedAmbiencePresetId,
        ambiencePresetEnabled,
        backgroundAudios,
        selectedBackgroundAudioId,
        backgroundAudioFileInput,
        ambDragOver,
        backgroundTitle,
        backgroundSourceLabel,
        backgroundSourceUrl,
        backgroundVolume,
        backgroundLoop,
        backgroundUploading,
        presetAudioGenerating,
        backgroundPlaying,
        ambiencePresets,
        ambienceLibraries,
        selectedBackgroundAudio,
        selectedAmbiencePreset,
        ambienceSearchLinks,
        backgroundSummaryTitle,
        backgroundSummaryNote,
        hasActiveBackgroundAmbience,
        openAmbiencePanel,
        closeAmbiencePanel,
        selectAmbiencePreset,
        selectBackgroundAudio,
        openBackgroundAudioFile,
        onBackgroundAudioFileChange,
        onAmbDragOver,
        onAmbDropZoneClick,
        onBackgroundAudioDrop,
        importBackgroundAudioFile,
        useSelectedAmbiencePreset,
        removeBackgroundAudio,
        stopBackgroundPlayback,
        disposeBackgroundPlayback,
        pauseBackgroundPlayback,
        playBackgroundAudio,
        toggleBackgroundAudio,
        loadBackgroundAudios,
    };
}
