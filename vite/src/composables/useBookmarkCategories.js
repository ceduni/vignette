import { ref, computed } from "vue";

const CATEGORIES_KEY = "vignette_bookmark_categories";  // { scenarioId: categoryName }
const CATEGORY_LIST_KEY = "vignette_bookmark_category_list"; // string[]

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

const categoryMap = ref(loadCategoryMap());   // { scenarioId → categoryName }
const categoryList = ref(loadCategoryList()); // liste de toutes les catégories

function persist() {
  localStorage.setItem(CATEGORIES_KEY, JSON.stringify(categoryMap.value));
  localStorage.setItem(CATEGORY_LIST_KEY, JSON.stringify(categoryList.value));
}

export function useBookmarkCategories() {

  function getCategory(scenarioId) {
    return categoryMap.value[String(scenarioId)] ?? null;
  }

  function setCategory(scenarioId, category) {
    const id = String(scenarioId);
    const next = { ...categoryMap.value };
    if (category) {
      next[id] = category;
      if (!categoryList.value.includes(category)) {
        categoryList.value = [...categoryList.value, category];
      }
    } else {
      delete next[id];
    }
    categoryMap.value = next;
    persist();
  }

  function removeCategory(scenarioId) {
    const id = String(scenarioId);
    const next = { ...categoryMap.value };
    delete next[id];
    categoryMap.value = next;
    persist();
  }

  function addCategory(name) {
    const trimmed = name?.trim();
    if (!trimmed || categoryList.value.includes(trimmed)) return false;
    categoryList.value = [...categoryList.value, trimmed];
    persist();
    return true;
  }

  function deleteCategory(name) {
    categoryList.value = categoryList.value.filter(c => c !== name);
    const next = { ...categoryMap.value };
    for (const [id, cat] of Object.entries(next)) {
      if (cat === name) delete next[id];
    }
    categoryMap.value = next;
    persist();
  }

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