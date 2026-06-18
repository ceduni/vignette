import {computed, ref} from "vue";
import worldCountries from "world-countries";

import {fetchLanguagesByCountry} from "@/api/languageMap";

type LanguageRow = {
  id: string;
  name: string;
  level: string;
  family: string;
  parent: string;
  countryIds?: string;
};

type ScenarioAction = {
  id: string;
  title: string;
  language: string;
  level: string;
  status: string;
  type: string;
  ctaLabel: string;
};

type LanguagePin = {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  countryIds?: string;
};

type FocusMode = "idle" | "country" | "language";
type ActionSource = "map" | "list" | "system";

const SCENARIO_TEMPLATES = [
  {title: "Basic greetings", level: "Beginner", status: "Coming soon", type: "Vocabulary"},
  {title: "Survival phrases", level: "Beginner", status: "Coming soon", type: "Dialogue"},
  {title: "Cultural etiquette", level: "Intermediate", status: "Coming soon", type: "Culture"},
  {title: "Daily routines", level: "Intermediate", status: "Coming soon", type: "Listening"},
  {title: "Story prompts", level: "Advanced", status: "Coming soon", type: "Scenario"},
];

const iso2ToIso3 = new Map(
    worldCountries
        .filter((country) => country?.cca2 && country?.cca3)
        .map((country) => [String(country.cca2).toUpperCase(), String(country.cca3).toUpperCase()])
);

const validIso3 = new Set(
    worldCountries
        .filter((country) => country?.cca3)
        .map((country) => String(country.cca3).toUpperCase())
);

const mapLoading = ref(false);
const mapLoaded = ref(false);
const mapError = ref("");

const languagesByCountry = ref<Record<string, LanguageRow[]>>({});
const catalogLanguages = ref<LanguageRow[]>([]);

// Anti-boucle : états séparés + source explicite.
const activeCountryId = ref("");
const activeLanguageId = ref("");
const activeLanguagePin = ref<LanguagePin | null>(null);
const focusMode = ref<FocusMode>("idle");
const lastActionSource = ref<ActionSource>("system");

function toIsoA3Tokens(raw: string | null | undefined): string[] {
  if (!raw) return [];

  const tokens = String(raw)
      .split(/[\s,;]+/)
      .map((token) => token.trim().toUpperCase())
      .filter(Boolean);

  const mapped = tokens
      .map((token) => {
        if (token.length === 3 && validIso3.has(token)) return token;
        if (token.length === 2 && iso2ToIso3.has(token)) return iso2ToIso3.get(token) || null;
        return null;
      })
      .filter((token): token is string => !!token);

  return Array.from(new Set(mapped));
}

function buildScenariosForCountry(isoA3: string, rows: LanguageRow[]): ScenarioAction[] {
  return rows.slice(0, 8).map((language, index) => {
    const template = SCENARIO_TEMPLATES[index % SCENARIO_TEMPLATES.length];

    return {
      id: `${isoA3}-${language.id || index}`,
      title: template.title,
      language: language.name || "Unknown language",
      level: template.level,
      status: template.status,
      type: template.type,
      ctaLabel: `Lancer le scénario ${index + 1}`,
    };
  });
}

const languageCountriesIndex = computed<Record<string, string[]>>(() => {
  const index: Record<string, Set<string>> = {};

  Object.entries(languagesByCountry.value).forEach(([isoA3, rows]) => {
    rows.forEach((row) => {
      const id = String(row?.id ?? "");
      if (!id) return;

      if (!index[id]) {
        index[id] = new Set<string>();
      }
      index[id].add(isoA3);
    });
  });

  return Object.fromEntries(
      Object.entries(index).map(([id, set]) => [id, Array.from(set)])
  );
});

const clusterCountByIso = computed<Record<string, number>>(() => {
  return Object.entries(languagesByCountry.value).reduce((acc, [isoA3, rows]) => {
    const scenarios = buildScenariosForCountry(isoA3, rows);
    acc[isoA3] = scenarios.length;
    return acc;
  }, {} as Record<string, number>);
});

const highlightedCountryIds = computed<string[]>(() => {
  if (focusMode.value === "country" && activeCountryId.value) {
    return [activeCountryId.value];
  }

  if (focusMode.value === "language" && activeLanguageId.value) {
    return languageCountriesIndex.value[activeLanguageId.value] ?? [];
  }

  return [];
});

const countryFocusLanguages = computed<LanguageRow[]>(() => {
  if (!activeCountryId.value) return [];
  return languagesByCountry.value[activeCountryId.value] ?? [];
});

const languageFocusScenarios = computed<ScenarioAction[]>(() => {
  if (!activeLanguageId.value) return [];

  const language = catalogLanguages.value.find((row) => String(row.id) === String(activeLanguageId.value));
  if (!language) return [];

  return SCENARIO_TEMPLATES.map((template, index) => ({
    id: `${activeLanguageId.value}-scenario-${index + 1}`,
    title: template.title,
    language: language.name || "Unknown language",
    level: template.level,
    status: template.status,
    type: template.type,
    ctaLabel: `Lancer le scénario ${index + 1}`,
  }));
});

const countryFocusScenarios = computed<ScenarioAction[]>(() => {
  if (!activeCountryId.value) return [];
  return buildScenariosForCountry(activeCountryId.value, countryFocusLanguages.value);
});

const mapFocus = computed(() => {
  if (focusMode.value === "country" && activeCountryId.value) {
    return {
      mode: "country" as const,
      title: `Zone ${activeCountryId.value}`,
      subtitle: `${countryFocusLanguages.value.length} langues trouvées dans ce pays`,
      scenarios: countryFocusScenarios.value,
    };
  }

  if (focusMode.value === "language" && activeLanguageId.value) {
    const lang = catalogLanguages.value.find((row) => String(row.id) === String(activeLanguageId.value));
    return {
      mode: "language" as const,
      title: lang ? `Scénarios de ${lang.name}` : "Scénarios de la langue",
      subtitle: `${highlightedCountryIds.value.length} pays accentués`,
      scenarios: languageFocusScenarios.value,
    };
  }

  return {
    mode: "idle" as const,
    title: "Clique sur un pays ou une langue",
    subtitle: "La carte et la liste se synchroniseront ici.",
    scenarios: [] as ScenarioAction[],
  };
});

function languageBelongsToIso(countryIds: string | null | undefined, isoA3: string): boolean {
  return toIsoA3Tokens(countryIds).includes(String(isoA3 || "").toUpperCase());
}

function applyCountryFilter(rows: LanguageRow[]): LanguageRow[] {
  if (!(focusMode.value === "country" && activeCountryId.value)) {
    return rows;
  }

  return rows.filter((row) => languageBelongsToIso(String(row?.countryIds ?? ""), activeCountryId.value));
}

function setCatalogLanguages(rows: LanguageRow[]) {
  catalogLanguages.value = Array.isArray(rows) ? rows : [];
}

function clearFocus(source: ActionSource = "system") {
  activeCountryId.value = "";
  activeLanguageId.value = "";
  activeLanguagePin.value = null;
  focusMode.value = "idle";
  lastActionSource.value = source;
}

function activateCountryFromMap(isoA3: string) {
  const next = String(isoA3 || "").toUpperCase();
  if (!next) return;

  // garde d'arrêt anti-boucle
  if (
      focusMode.value === "country"
      && activeCountryId.value === next
      && lastActionSource.value === "map"
  ) {
    return;
  }

  activeCountryId.value = next;
  activeLanguageId.value = "";
  activeLanguagePin.value = null;
  focusMode.value = "country";
  lastActionSource.value = "map";
}

function activateLanguageFromList(language: {id?: string | number; countryIds?: string | null | undefined}) {
  const nextId = String(language?.id ?? "");
  if (!nextId) return;

  // toggle simple
  if (focusMode.value === "language" && activeLanguageId.value === nextId) {
    clearFocus("list");
    return;
  }

  // garde d'arrêt anti-boucle
  if (
      focusMode.value === "language"
      && activeLanguageId.value === nextId
      && lastActionSource.value === "list"
  ) {
    return;
  }

  activeLanguageId.value = nextId;
  activeLanguagePin.value = null;

  const inferredCountries = languageCountriesIndex.value[nextId] ?? toIsoA3Tokens(language?.countryIds);
  activeCountryId.value = inferredCountries[0] ?? "";

  focusMode.value = "language";
  lastActionSource.value = "list";
}

function setActiveLanguagePin(pin: LanguagePin | null) {
  activeLanguagePin.value = pin;
}

async function loadMapData() {
  if (mapLoaded.value || mapLoading.value) return;

  mapLoading.value = true;
  mapError.value = "";

  try {
    languagesByCountry.value = await fetchLanguagesByCountry();
    mapLoaded.value = true;
  } catch (err: unknown) {
    mapError.value = err instanceof Error ? err.message : "Failed to load map language data.";
  } finally {
    mapLoading.value = false;
  }
}

export function useLanguageStore() {
  return {
    mapLoading,
    mapLoaded,
    mapError,
    languagesByCountry,
    catalogLanguages,
    activeCountryId,
    activeLanguageId,
    activeLanguagePin,
    focusMode,
    lastActionSource,
    clusterCountByIso,
    highlightedCountryIds,
    mapFocus,
    countryFocusLanguages,
    countryFocusScenarios,
    languageFocusScenarios,
    toIsoA3Tokens,
    languageBelongsToIso,
    applyCountryFilter,
    setCatalogLanguages,
    clearFocus,
    activateCountryFromMap,
    activateLanguageFromList,
    setActiveLanguagePin,
    loadMapData,
  };
}
