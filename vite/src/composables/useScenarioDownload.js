import {ref} from "vue";
import {fetchScenarioThumbnails} from "../api/scenarios";
import {downloadAllScenesSvg} from "../utils/sceneDownload";
import {downloadScenarioZip} from "../utils/scenarioExport";
import {useToast} from "./useToast";

export function useScenarioDownload() {
    const toast = useToast();
    const downloadingScenarioId = ref(null);

    async function downloadScenario(scenario, format) {
        if (!scenario?.id || downloadingScenarioId.value != null) return;
        downloadingScenarioId.value = scenario.id;
        try {
            const thumbs = await fetchScenarioThumbnails(scenario.id);
            if (!thumbs?.length) {
                toast.error("This scenario has no scenes to download.");
                return;
            }
            const sorted = [...thumbs].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
            if (format === "zip") await downloadScenarioZip(scenario, sorted);
            else await downloadAllScenesSvg(scenario.title ?? "scenario", sorted);
        } catch (e) {
            toast.error(`Download failed: ${e?.message || "unknown error"}`);
        } finally {
            downloadingScenarioId.value = null;
        }
    }

    return {downloadingScenarioId, downloadScenario};
}
