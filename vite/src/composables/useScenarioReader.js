import { ref } from "vue";

export function useScenarioReader() {
  const activeScenario = ref(null);
  function openReader(scenario) {
    activeScenario.value = {
      id: String(scenario.id),
      title: scenario.title ?? "Untitled",
      authorUsername: scenario.authorUsername ?? "Unknown",
      storyboardLayoutMode: scenario.storyboardLayoutMode ?? "PRESET",
      storyboardPreset: scenario.storyboardPreset ?? "GRID_3",
      storyboardColumns: scenario.storyboardColumns ?? 3,
    };
  }

  function closeReader() {
    activeScenario.value = null;
  }

  return { activeScenario, openReader, closeReader };
}