import {ref} from "vue";
import {fetchScenarioThumbnails} from "../api/scenarios";
import {downloadAllScenesSvg} from "../utils/sceneDownload";
import {useToast} from "./useToast";

/** Downloads every scene of a scenario as SVG files, from a scenario list card. */
export function useScenarioSvgDownload() {
    const toast = useToast();
    const downloadingScenarioId = ref(null);

    async function downloadScenarioSvg(scenario) {
        if (!scenario?.id || downloadingScenarioId.value != null) return;
        downloadingScenarioId.value = scenario.id;
        try {
            const thumbs = await fetchScenarioThumbnails(scenario.id);
            if (!thumbs?.length) {
                toast.error("This scenario has no scenes to download.");
                return;
            }
            const sorted = [...thumbs].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
            await downloadAllScenesSvg(scenario.title ?? "scenario", sorted);
        } catch (e) {
            toast.error(`Download failed: ${e?.message || "unknown error"}`);
        } finally {
            downloadingScenarioId.value = null;
        }
    }

    return {downloadingScenarioId, downloadScenarioSvg};
}
