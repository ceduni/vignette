// composables/useScenarioInteractions.js
// État réactif par userId — se recalcule au changement de compte
import { ref, computed, watch } from "vue";
import { apiFetch } from "../api/rest";
import { useAuth } from "./useAuth";

// Refs réactifs au niveau du module — mis à jour à chaque toggle
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

  // Recharge depuis localStorage si l'utilisateur a changé
  function ensureLoaded() {
    const currentUid = uid();
    if (currentUid !== _loadedUid) {
      _likedIds.value      = readSet(likesKey(currentUid));
      _bookmarkedIds.value = readSet(bookmarksKey(currentUid));
      _loadedUid           = currentUid;
    }
  }

  // Se réinitialise automatiquement au changement de compte
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

  // bookmarkedIds réactif pour BookmarkedScenariosView
  const bookmarkedIds = computed(() => {
    ensureLoaded();
    return _bookmarkedIds.value;
  });

  const likedIds = computed(() => {
    ensureLoaded();
    return _likedIds.value;
  });

  // Retourne le statut renvoyé par le serveur (dont likeCount à jour) pour que
  // les vues qui affichent un compteur se synchronisent sur la vérité serveur
  // plutôt que de recalculer le delta elles-mêmes. Retourne null en cas d'échec
  // réseau, après avoir annulé la mise à jour optimiste.
  async function toggleLike(scenarioId) {
    ensureLoaded();
    const id = String(scenarioId);
    const wasLiked = _likedIds.value.has(id);

    // Mise à jour réactive immédiate — crée un nouveau Set pour déclencher la réactivité
    const next = new Set(_likedIds.value);
    wasLiked ? next.delete(id) : next.add(id);
    _likedIds.value = next;
    writeSet(likesKey(uid()), next);

    try {
      return await apiFetch(`/api/scenarios/${id}/like`, {
        method: wasLiked ? "DELETE" : "POST",
      });
    } catch {
      // La requête a échoué — on annule la mise à jour optimiste pour ne pas
      // dériver de l'état serveur
      const reverted = new Set(_likedIds.value);
      wasLiked ? reverted.add(id) : reverted.delete(id);
      _likedIds.value = reverted;
      writeSet(likesKey(uid()), reverted);
      return null;
    }
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
      return await apiFetch(`/api/scenarios/${id}/bookmark`, {
        method: wasBookmarked ? "DELETE" : "POST",
      });
    } catch {
      const reverted = new Set(_bookmarkedIds.value);
      wasBookmarked ? reverted.add(id) : reverted.delete(id);
      _bookmarkedIds.value = reverted;
      writeSet(bookmarksKey(uid()), reverted);
      return null;
    }
  }

  async function fetchStatus(scenarioId) {
    try {
      return await apiFetch(`/api/scenarios/${scenarioId}/interactions`);
    } catch {
      return null;
    }
  }

  // Hydrates the like/bookmark sets from the server — the source of truth.
  // localStorage is only an optimistic-UI cache: it can drift (different
  // browser/device, cleared storage, a toggle request that failed silently),
  // so this should be called once per session (on login) to reconcile it.
  async function loadMyInteractionsFromServer() {
    if (!currentUser.value) return;
    try {
      const data = await apiFetch("/api/scenarios/interactions/mine");
      const liked = new Set((data.likedScenarioIds ?? []).map(String));
      const bookmarked = new Set((data.bookmarkedScenarioIds ?? []).map(String));
      const currentUid = uid();

      _likedIds.value = liked;
      _bookmarkedIds.value = bookmarked;
      _loadedUid = currentUid;

      writeSet(likesKey(currentUid), liked);
      writeSet(bookmarksKey(currentUid), bookmarked);
    } catch {
      // Server unreachable — keep whatever's cached in localStorage.
    }
  }

  return {
    isLiked,
    isBookmarked,
    toggleLike,
    toggleBookmark,
    fetchStatus,
    loadMyInteractionsFromServer,
    likedIds,
    bookmarkedIds,
  };
}