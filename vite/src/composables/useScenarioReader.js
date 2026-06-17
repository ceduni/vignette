// composables/useScenarioReader.js
// Composable partagé pour ouvrir le reader depuis n'importe quelle vue.
import { ref } from "vue";

const activeScenario = ref(null); // { id, title, authorUsername }

export function useScenarioReader() {
  function openReader(scenario) {
    activeScenario.value = {
      id: String(scenario.id),
      title: scenario.title ?? "Untitled",
      authorUsername: scenario.authorUsername ?? "Unknown",
    };
  }

  function closeReader() {
    activeScenario.value = null;
  }

  return { activeScenario, openReader, closeReader };
}