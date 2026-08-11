import { ref, computed, watch } from "vue";
import { apiFetch } from "../api/rest";
import { useAuth } from "./useAuth";

const _likedIds     = ref(new Set());
const _bookmarkedIds = ref(new Set());
let   _loadedUid    = null;

function likesKey(uid)     { return `vignette_likes_${uid}`; }
function bookmarksKey(uid) { return `vignette_bookmarks_${uid}`; }

function readSet(key) {
  try { return new Set(JSON.parse(localStorage.getItem(key) ?? "[]")); }
  catch { return new Set(); }
}

function writeSet(key, set) {
  localStorage.setItem(key, JSON.stringify([...set]));
}

export function useScenarioInteractions() {
  const { currentUser } = useAuth();

  function uid() {
    return currentUser.value?.id
      ?? currentUser.value?.username
      ?? "guest";
  }

  function ensureLoaded() {
    const currentUid = uid();
    if (currentUid !== _loadedUid) {
      _likedIds.value      = readSet(likesKey(currentUid));
      _bookmarkedIds.value = readSet(bookmarksKey(currentUid));
      _loadedUid           = currentUid;
    }
  }

  watch(() => currentUser.value?.id ?? currentUser.value?.username, () => {
    _loadedUid = null;
    ensureLoaded();
  });

  function isLiked(scenarioId) {
    ensureLoaded();
    return _likedIds.value.has(String(scenarioId));
  }

  function isBookmarked(scenarioId) {
    ensureLoaded();
    return _bookmarkedIds.value.has(String(scenarioId));
  }

  const bookmarkedIds = computed(() => {
    ensureLoaded();
    return _bookmarkedIds.value;
  });

  const likedIds = computed(() => {
    ensureLoaded();
    return _likedIds.value;
  });

  async function toggleLike(scenarioId) {
    ensureLoaded();
    const id = String(scenarioId);
    const wasLiked = _likedIds.value.has(id);

    const next = new Set(_likedIds.value);
    wasLiked ? next.delete(id) : next.add(id);
    _likedIds.value = next;
    writeSet(likesKey(uid()), next);

    try {
      await apiFetch(`/api/scenarios/${id}/like`, {
        method: wasLiked ? "DELETE" : "POST",
      });
    } catch {}
  }

  async function toggleBookmark(scenarioId) {
    ensureLoaded();
    const id = String(scenarioId);
    const wasBookmarked = _bookmarkedIds.value.has(id);

    const next = new Set(_bookmarkedIds.value);
    wasBookmarked ? next.delete(id) : next.add(id);
    _bookmarkedIds.value = next;
    writeSet(bookmarksKey(uid()), next);

    try {
      await apiFetch(`/api/scenarios/${id}/bookmark`, {
        method: wasBookmarked ? "DELETE" : "POST",
      });
    } catch {}
  }

  async function fetchStatus(scenarioId) {
    try {
      return await apiFetch(`/api/scenarios/${scenarioId}/interactions`);
    } catch {
      return null;
    }
  }

  return {
    isLiked,
    isBookmarked,
    toggleLike,
    toggleBookmark,
    fetchStatus,
    likedIds,
    bookmarkedIds,
  };
}