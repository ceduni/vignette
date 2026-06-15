// composables/useLanguageFollows.js
import { ref, computed } from "vue";
import { apiFetch } from "../api/rest";

const FOLLOWS_KEY = "vignette_language_follows";
const FOLLOWS_META_KEY = "vignette_language_follows_meta";

function loadArray() {
  try { return JSON.parse(localStorage.getItem(FOLLOWS_KEY) ?? "[]"); }
  catch { return []; }
}

function loadMeta() {
  try { return JSON.parse(localStorage.getItem(FOLLOWS_META_KEY) ?? "{}"); }
  catch { return {}; }
}

// État global partagé entre tous les composants
const followedIdsArray = ref(loadArray());
const followedMeta = ref(loadMeta());

function persist() {
  localStorage.setItem(FOLLOWS_KEY, JSON.stringify(followedIdsArray.value));
  localStorage.setItem(FOLLOWS_META_KEY, JSON.stringify(followedMeta.value));
}

export function useLanguageFollows() {

  function isFollowing(languageId) {
    return followedIdsArray.value.includes(String(languageId));
  }

  function setFollowing(languageId, value, meta = null) {
    const id = String(languageId);
    const next = followedIdsArray.value.filter((x) => x !== id);
    if (value) next.push(id);
    followedIdsArray.value = next;

    const nextMeta = { ...followedMeta.value };
    if (value && meta) nextMeta[id] = meta;
    else if (!value) delete nextMeta[id];
    followedMeta.value = nextMeta;

    persist();
  }

  async function toggleFollow(languageId, languageName = null) {
    const id = String(languageId);
    const wasFollowing = isFollowing(id);
    const meta = { id, name: languageName ?? id };

    // Toujours persister localement — pas de rollback
    setFollowing(id, !wasFollowing, meta);

    // Appel API best-effort — l'échec ne défait pas le localStorage
    try {
      const method = wasFollowing ? "DELETE" : "POST";
      await apiFetch(`/api/languages/${id}/follow`, { method });
    } catch {
      // Silencieux — le follow est quand même sauvegardé localement
    }
  }

  const followedLanguagesList = computed(() =>
    followedIdsArray.value.map((id) => ({
      id,
      name: followedMeta.value[id]?.name ?? id,
    }))
  );

  return {
    followedIds: computed(() => new Set(followedIdsArray.value)),
    followedIdsArray,
    followedMeta,
    isFollowing,
    toggleFollow,
    followedLanguagesList,
    getFollowedLanguages: () => followedLanguagesList.value,
  };
}