import { ref, computed } from "vue";
import { apiFetch } from "../api/rest";
import { useAuth } from "./useAuth";

const followedIdsArray = ref([]);   // string[]
const followedMeta    = ref({});    // { languageId: { id, name } }
const initialized     = ref(false);

export function useLanguageFollows() {
  const { currentUser } = useAuth();

  async function loadFollows() {
    try {
      const ids = await apiFetch("/api/languages/followed");
      if (Array.isArray(ids)) {
        followedIdsArray.value = ids.map(String);
      }
      initialized.value = true;
    } catch {
      followedIdsArray.value = [];
      initialized.value = true;
    }
  }

  function reset() {
    followedIdsArray.value = [];
    followedMeta.value     = {};
    initialized.value      = false;
  }

  function isFollowing(languageId) {
    return followedIdsArray.value.includes(String(languageId));
  }

  async function toggleFollow(languageId, languageName = null) {
    const id = String(languageId);
    const wasFollowing = isFollowing(id);

    if (wasFollowing) {
      followedIdsArray.value = followedIdsArray.value.filter(x => x !== id);
      const next = { ...followedMeta.value };
      delete next[id];
      followedMeta.value = next;
    } else {
      followedIdsArray.value = [...followedIdsArray.value, id];
      followedMeta.value = {
        ...followedMeta.value,
        [id]: { id, name: languageName ?? id },
      };
    }

    try {
      await apiFetch(`/api/languages/${id}/follow`, { method: "POST" });
    } catch {
      if (wasFollowing) {
        followedIdsArray.value = [...followedIdsArray.value, id];
      } else {
        followedIdsArray.value = followedIdsArray.value.filter(x => x !== id);
      }
    }
  }

  const followedLanguagesList = computed(() =>
    followedIdsArray.value.map(id => ({
      id,
      name: followedMeta.value[id]?.name ?? id,
    }))
  );

  return {
    followedIds:          computed(() => new Set(followedIdsArray.value)),
    followedIdsArray,
    followedMeta,
    initialized,
    isFollowing,
    toggleFollow,
    followedLanguagesList,
    getFollowedLanguages: () => followedLanguagesList.value,
    loadFollows,
    reset,
  };
}