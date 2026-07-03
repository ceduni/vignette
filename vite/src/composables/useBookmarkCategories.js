// composables/useBookmarkCategories.js
// Gestion des catégories de bookmarks — localStorage, backend plus tard
import { ref, computed } from "vue";

const CATEGORIES_KEY = "vignette_bookmark_categories";  // { scenarioId: categoryName }
const CATEGORY_LIST_KEY = "vignette_bookmark_category_list"; // string[]

// Suggestions de catégories par défaut
export const SUGGESTED_CATEGORIES = [
  "To study",
  "Favorites",
  "Share with class",
  "Field research",
];

function loadCategoryMap() {
  try { return JSON.parse(localStorage.getItem(CATEGORIES_KEY) ?? "{}"); }
  catch { return {}; }
}

function loadCategoryList() {
  try {
    const saved = JSON.parse(localStorage.getItem(CATEGORY_LIST_KEY) ?? "null");
    if (Array.isArray(saved)) return saved;
  } catch {}
  return [...SUGGESTED_CATEGORIES];
}

// État global partagé
const categoryMap = ref(loadCategoryMap());   // { scenarioId → categoryName }
const categoryList = ref(loadCategoryList()); // liste de toutes les catégories

function persist() {
  localStorage.setItem(CATEGORIES_KEY, JSON.stringify(categoryMap.value));
  localStorage.setItem(CATEGORY_LIST_KEY, JSON.stringify(categoryList.value));
}

export function useBookmarkCategories() {

  // Catégorie d'un scénario (null = non catégorisé)
  function getCategory(scenarioId) {
    return categoryMap.value[String(scenarioId)] ?? null;
  }

  // Assigner une catégorie à un bookmark
  function setCategory(scenarioId, category) {
    const id = String(scenarioId);
    const next = { ...categoryMap.value };
    if (category) {
      next[id] = category;
      // Ajouter la catégorie à la liste si elle n'existe pas
      if (!categoryList.value.includes(category)) {
        categoryList.value = [...categoryList.value, category];
      }
    } else {
      delete next[id];
    }
    categoryMap.value = next;
    persist();
  }

  // Retirer la catégorie quand on retire le bookmark
  function removeCategory(scenarioId) {
    const id = String(scenarioId);
    const next = { ...categoryMap.value };
    delete next[id];
    categoryMap.value = next;
    persist();
  }

  // Créer une nouvelle catégorie personnalisée
  function addCategory(name) {
    const trimmed = name?.trim();
    if (!trimmed || categoryList.value.includes(trimmed)) return false;
    categoryList.value = [...categoryList.value, trimmed];
    persist();
    return true;
  }

  // Supprimer une catégorie (et retirer les assignments)
  function deleteCategory(name) {
    categoryList.value = categoryList.value.filter(c => c !== name);
    const next = { ...categoryMap.value };
    for (const [id, cat] of Object.entries(next)) {
      if (cat === name) delete next[id];
    }
    categoryMap.value = next;
    persist();
  }

  // Renommer une catégorie
  function renameCategory(oldName, newName) {
    const trimmed = newName?.trim();
    if (!trimmed || trimmed === oldName) return false;
    categoryList.value = categoryList.value.map(c => c === oldName ? trimmed : c);
    const next = { ...categoryMap.value };
    for (const [id, cat] of Object.entries(next)) {
      if (cat === oldName) next[id] = trimmed;
    }
    categoryMap.value = next;
    persist();
    return true;
  }

  // Scénarios groupés par catégorie { categoryName: [scenarioId, ...], "": [uncategorized ids] }
  function groupByCategory(scenarioIds) {
    const groups = {};
    for (const id of scenarioIds) {
      const cat = getCategory(id) ?? "";
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(id);
    }
    return groups;
  }

  return {
    categoryMap,
    categoryList,
    getCategory,
    setCategory,
    removeCategory,
    addCategory,
    deleteCategory,
    renameCategory,
    groupByCategory,
    SUGGESTED_CATEGORIES,
  };
}