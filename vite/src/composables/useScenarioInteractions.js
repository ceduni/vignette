// composables/useScenarioInteractions.js
import { ref } from "vue";
import { apiFetch } from "../api/rest";

const LIKES_KEY = "vignette_scenario_likes";
const BOOKMARKS_KEY = "vignette_scenario_bookmarks";

function loadSet(key) {
  try {
    return new Set(JSON.parse(localStorage.getItem(key) ?? "[]"));
  } catch {
    return new Set();
  }
}

function saveSet(key, set) {
  localStorage.setItem(key, JSON.stringify([...set]));
}

// État global partagé
const likedIds = ref(loadSet(LIKES_KEY));
const bookmarkedIds = ref(loadSet(BOOKMARKS_KEY));

export function useScenarioInteractions() {

  // ── Helpers ──────────────────────────────────────────────────────────────

  function setLiked(scenarioId, value) {
    const id = String(scenarioId);
    const next = new Set(likedIds.value);
    value ? next.add(id) : next.delete(id);
    likedIds.value = next;
    saveSet(LIKES_KEY, next);
  }

  function setBookmarked(scenarioId, value) {
    const id = String(scenarioId);
    const next = new Set(bookmarkedIds.value);
    value ? next.add(id) : next.delete(id);
    bookmarkedIds.value = next;
    saveSet(BOOKMARKS_KEY, next);
  }

  function applyStatus(scenarioId, status) {
    setLiked(scenarioId, status.liked);
    setBookmarked(scenarioId, status.bookmarked);
  }

  // ── Lecture ───────────────────────────────────────────────────────────────

  function isLiked(scenarioId) {
    return likedIds.value.has(String(scenarioId));
  }

  function isBookmarked(scenarioId) {
    return bookmarkedIds.value.has(String(scenarioId));
  }

  async function fetchStatus(scenarioId) {
    try {
      const status = await apiFetch(`/api/scenarios/${scenarioId}/interactions`);
      applyStatus(scenarioId, status);
    } catch {
      // silencieux — localStorage reste le fallback
    }
  }

  // ── Like ──────────────────────────────────────────────────────────────────

  async function toggleLike(scenarioId) {
    const id = String(scenarioId);
    const wasLiked = isLiked(id);

    // Optimistic update
    setLiked(id, !wasLiked);

    try {
      const method = wasLiked ? "DELETE" : "POST";
      const status = await apiFetch(`/api/scenarios/${id}/like`, { method });
      applyStatus(id, status);
    } catch {
      // Rollback si erreur réseau ou non authentifié
      setLiked(id, wasLiked);
    }
  }

  // ── Bookmark ──────────────────────────────────────────────────────────────

  async function toggleBookmark(scenarioId) {
    const id = String(scenarioId);
    const wasBookmarked = isBookmarked(id);

    // Optimistic update
    setBookmarked(id, !wasBookmarked);

    try {
      const method = wasBookmarked ? "DELETE" : "POST";
      const status = await apiFetch(`/api/scenarios/${id}/bookmark`, { method });
      applyStatus(id, status);
    } catch {
      // Rollback si erreur réseau ou non authentifié
      setBookmarked(id, wasBookmarked);
    }
  }

  return {
    isLiked,
    toggleLike,
    isBookmarked,
    toggleBookmark,
    fetchStatus,
    likedIds,
    bookmarkedIds,
  };
}