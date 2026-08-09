<script setup>
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { fetchScenarios, fetchScenarioThumbnails, forkScenario } from "../api/scenarios";
import { buildApiUrl } from "../api/rest";
import { useScenarioInteractions } from "../composables/useScenarioInteractions";
import { useBookmarkCategories, SUGGESTED_CATEGORIES } from "../composables/useBookmarkCategories";
import { useScenarioReader } from "../composables/useScenarioReader";
import { useAuth } from "../composables/useAuth";
import BaseLoader from "../components/ui/BaseLoader.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import ScenarioDiscussionModal from "../components/community/ScenarioDiscussionModal.vue";
import CopyScenarioModal from "../components/scenario/CopyScenarioModal.vue";

const { isLiked, toggleLike, isBookmarked, toggleBookmark, bookmarkedIds } = useScenarioInteractions();
const { categoryMap, categoryList, getCategory, setCategory, removeCategory, addCategory, deleteCategory, renameCategory, groupByCategory } = useBookmarkCategories();
const { openReader, activeScenario, closeReader } = useScenarioReader();
const { isAuthenticated, currentUser } = useAuth();
const router = useRouter();

const copyingId = ref(null);
const copyError = ref("");
const copyTarget = ref(null);

function openCopyModal(s) {
  copyError.value = "";
  copyTarget.value = s;
}

function closeCopyModal() {
  copyTarget.value = null;
}

async function confirmCopy(title) {
  if (!copyTarget.value || copyingId.value) return;

  copyingId.value = copyTarget.value.id;
  copyError.value = "";
  try {
    const result = await forkScenario(copyTarget.value.id, title);
    copyTarget.value = null;
    router.push(`/scenarios/${result.id}`);
  } catch (e) {
    copyError.value = e.message || "Could not copy this scenario.";
  } finally {
    copyingId.value = null;
  }
}

const allScenarios = ref([]);
const previewMap = ref({});
const loading = ref(false);
const error = ref("");
const discussionScenario = ref(null);
function openDiscussion(s) { discussionScenario.value = s; }
function closeDiscussion() { discussionScenario.value = null; }

// UI state
const activeCategory = ref("__all__"); // "__all__" | "__none__" | categoryName
const categoryPickerScenarioId = ref(null); // which scenario's picker is open
const newCategoryName = ref("");
const editingCategory = ref(null); // { old, new }
const showManageCategories = ref(false);

const scenarios = computed(() =>
  allScenarios.value.filter(s => bookmarkedIds.value.has(String(s.id)))
);

const filtered = computed(() => {
  if (activeCategory.value === "__all__") return scenarios.value;
  if (activeCategory.value === "__none__") return scenarios.value.filter(s => !getCategory(s.id));
  return scenarios.value.filter(s => getCategory(s.id) === activeCategory.value);
});

// Count per category for badges
const categoryCounts = computed(() => {
  const counts = { __all__: scenarios.value.length, __none__: 0 };
  for (const s of scenarios.value) {
    const cat = getCategory(s.id);
    if (!cat) counts.__none__++;
    else counts[cat] = (counts[cat] ?? 0) + 1;
  }
  return counts;
});

// Categories that have at least one bookmark
const usedCategories = computed(() =>
  categoryList.value.filter(c => (categoryCounts.value[c] ?? 0) > 0)
);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await fetchScenarios();
    const all = Array.isArray(data) ? data : (data.content ?? []);
    allScenarios.value = all.filter(s => s.visibilityStatus === "PUBLISHED");

    const map = {};
    await Promise.all(
      allScenarios.value.map(async (s) => {
        try {
          const thumbs = await fetchScenarioThumbnails(s.id);
          map[s.id] = thumbs?.[0]?.id ?? null;
        } catch { map[s.id] = null; }
      })
    );
    previewMap.value = map;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function thumbnailUrl(id) {
  const thumbId = previewMap.value[id];
  return thumbId ? buildApiUrl(`/api/thumbnails/${thumbId}/content`) : null;
}

const TILE_GRADIENTS = [
  "linear-gradient(135deg,#D4E5CA,#c5d9b8)",
  "linear-gradient(135deg,#FFE0C0,#FFF0EE)",
  "linear-gradient(135deg,#c5d9b8,#afc8a0)",
  "linear-gradient(135deg,#ddd4f5,#c8bde8)",
  "linear-gradient(135deg,#A8C498,#8fb87f)",
];
function placeholderGradient(i) { return TILE_GRADIENTS[i % TILE_GRADIENTS.length]; }

function handleRemoveBookmark(scenarioId) {
  removeCategory(scenarioId);
  toggleBookmark(scenarioId);
}

function openCategoryPicker(scenarioId) {
  categoryPickerScenarioId.value = categoryPickerScenarioId.value === scenarioId ? null : scenarioId;
}

function assignCategory(scenarioId, category) {
  setCategory(scenarioId, category);
  categoryPickerScenarioId.value = null;
}

function handleAddCategory() {
  const name = newCategoryName.value.trim();
  if (!name) return;
  addCategory(name);
  newCategoryName.value = "";
}

function startRenameCategory(name) {
  editingCategory.value = { old: name, new: name };
}

function confirmRenameCategory() {
  if (!editingCategory.value) return;
  renameCategory(editingCategory.value.old, editingCategory.value.new);
  editingCategory.value = null;
}

function handleDeleteCategory(name) {
  if (activeCategory.value === name) activeCategory.value = "__all__";
  deleteCategory(name);
}

onMounted(load);
</script>

<template>
  <main class="bk-root">

    <!-- Hero -->
    <div class="bk-hero">
      <div>
        <p class="bk-eyebrow">Your bookmarks</p>
        <h1 class="bk-title">Bookmarked Scenarios</h1>
        <p class="bk-subtitle">Scenarios you've saved, organised by category.</p>
      </div>
      <div class="bk-hero__actions">
        <button type="button" class="bk-manage-btn" @click="showManageCategories = !showManageCategories">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>
          </svg>
          Manage categories
        </button>
        <RouterLink to="/scenarios" class="bk-hero__cta">Browse catalogue</RouterLink>
      </div>
    </div>

    <!-- Manage categories panel -->
    <Transition name="bk-fade">
      <div v-if="showManageCategories" class="bk-manage">
        <div class="bk-manage__head">
          <h2 class="bk-manage__title">Categories</h2>
          <button type="button" class="bk-manage__close" @click="showManageCategories = false">×</button>
        </div>

        <!-- Suggestions -->
        <div class="bk-manage__suggestions">
          <p class="bk-manage__label">Suggestions</p>
          <div class="bk-suggestion-row">
            <button
              v-for="s in SUGGESTED_CATEGORIES"
              :key="s"
              type="button"
              class="bk-suggestion"
              :class="{ 'bk-suggestion--exists': categoryList.includes(s) }"
              :disabled="categoryList.includes(s)"
              @click="addCategory(s)"
            >
              {{ s }}
              <span v-if="categoryList.includes(s)">✓</span>
              <span v-else>+</span>
            </button>
          </div>
        </div>

        <!-- Existing categories -->
        <div class="bk-manage__list">
          <p class="bk-manage__label">Your categories</p>
          <div v-for="cat in categoryList" :key="cat" class="bk-manage__item">
            <template v-if="editingCategory?.old === cat">
              <input
                v-model="editingCategory.new"
                class="bk-manage__rename-input"
                @keydown.enter="confirmRenameCategory"
                @keydown.escape="editingCategory = null"
                autofocus
              />
              <button type="button" class="bk-manage__action bk-manage__action--save" @click="confirmRenameCategory">Save</button>
              <button type="button" class="bk-manage__action" @click="editingCategory = null">Cancel</button>
            </template>
            <template v-else>
              <span class="bk-manage__cat-name">{{ cat }}</span>
              <span class="bk-manage__cat-count">{{ categoryCounts[cat] ?? 0 }}</span>
              <button type="button" class="bk-manage__action" @click="startRenameCategory(cat)">Rename</button>
              <button type="button" class="bk-manage__action bk-manage__action--danger" @click="handleDeleteCategory(cat)">Delete</button>
            </template>
          </div>

          <div v-if="!categoryList.length" class="bk-manage__empty">No categories yet.</div>
        </div>

        <!-- Add new -->
        <div class="bk-manage__add">
          <input
            v-model="newCategoryName"
            class="bk-manage__add-input"
            placeholder="New category name…"
            @keydown.enter="handleAddCategory"
          />
          <button type="button" class="bk-manage__add-btn" :disabled="!newCategoryName.trim()" @click="handleAddCategory">
            Add
          </button>
        </div>
      </div>
    </Transition>

    <!-- Category filter tabs -->
    <div class="bk-tabs">
      <button
        type="button"
        class="bk-tab"
        :class="{ 'bk-tab--active': activeCategory === '__all__' }"
        @click="activeCategory = '__all__'"
      >
        All
        <span class="bk-tab__count">{{ categoryCounts.__all__ ?? 0 }}</span>
      </button>
      <button
        v-for="cat in usedCategories"
        :key="cat"
        type="button"
        class="bk-tab"
        :class="{ 'bk-tab--active': activeCategory === cat }"
        @click="activeCategory = cat"
      >
        {{ cat }}
        <span class="bk-tab__count">{{ categoryCounts[cat] ?? 0 }}</span>
      </button>
      <button
        v-if="(categoryCounts.__none__ ?? 0) > 0"
        type="button"
        class="bk-tab bk-tab--uncategorized"
        :class="{ 'bk-tab--active': activeCategory === '__none__' }"
        @click="activeCategory = '__none__'"
      >
        Uncategorized
        <span class="bk-tab__count">{{ categoryCounts.__none__ }}</span>
      </button>
    </div>

    <div v-if="loading" class="bk-loader">
      <div class="bk-spinner"></div>
      <p>Loading…</p>
    </div>
    <div v-else-if="error" class="bk-error">{{ error }}</div>

    <template v-else>
      <p class="bk-count">{{ filtered.length }} scenario{{ filtered.length !== 1 ? 's' : '' }}</p>

      <div v-if="filtered.length" class="bk-grid">
        <div
          v-for="(s, index) in filtered"
          :key="s.id"
          class="bk-card"
          :class="{ 'bk-card--picker-open': categoryPickerScenarioId === s.id }"
        >
          <!-- Category badge -->
          <div class="bk-card__cat-row">
            <button
              type="button"
              class="bk-card__cat-btn"
              :class="{ 'bk-card__cat-btn--set': !!getCategory(s.id) }"
              @click="openCategoryPicker(s.id)"
            >
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
                <line x1="7" y1="7" x2="7.01" y2="7"/>
              </svg>
              {{ getCategory(s.id) ?? "Add category" }}
            </button>

            <!-- Category picker dropdown -->
            <div v-if="categoryPickerScenarioId === s.id" class="bk-cat-picker">
              <button
                type="button"
                class="bk-cat-picker__item bk-cat-picker__item--none"
                @click="assignCategory(s.id, null)"
              >
                No category
              </button>
              <div class="bk-cat-picker__divider"></div>
              <button
                v-for="cat in categoryList"
                :key="cat"
                type="button"
                class="bk-cat-picker__item"
                :class="{ 'bk-cat-picker__item--active': getCategory(s.id) === cat }"
                @click="assignCategory(s.id, cat)"
              >
                {{ cat }}
                <span v-if="getCategory(s.id) === cat">✓</span>
              </button>
              <div v-if="!categoryList.length" class="bk-cat-picker__empty">
                No categories yet —
                <button type="button" @click="showManageCategories = true; categoryPickerScenarioId = null">create one</button>
              </div>
              <div class="bk-cat-picker__divider"></div>
              <button type="button" class="bk-cat-picker__item bk-cat-picker__item--new" @click="showManageCategories = true; categoryPickerScenarioId = null">
                + Manage categories
              </button>
            </div>
          </div>

          <!-- Thumbnail -->
          <RouterLink :to="`/scenarios/${s.id}`" class="bk-card__thumb" tabindex="-1">
            <img v-if="thumbnailUrl(s.id)" :src="thumbnailUrl(s.id)" :alt="s.title" class="bk-card__img"/>
            <div v-else class="bk-card__placeholder" :style="{ background: placeholderGradient(index) }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"
                   stroke-linecap="round" stroke-linejoin="round" class="bk-card__placeholder-icon">
                <rect x="3" y="3" width="7" height="7" rx="1"/>
                <rect x="14" y="3" width="7" height="7" rx="1"/>
                <rect x="3" y="14" width="7" height="7" rx="1"/>
                <rect x="14" y="14" width="7" height="7" rx="1"/>
              </svg>
            </div>
            <div class="bk-card__overlay"><span>Read →</span></div>
          </RouterLink>

          <!-- Body -->
          <div class="bk-card__body">
            <RouterLink :to="`/scenarios/${s.id}`" class="bk-card__title-link">
              <h3 class="bk-card__title">{{ s.title || "Untitled" }}</h3>
            </RouterLink>
            <div class="bk-card__meta">
              <span class="bk-card__author">{{ s.authorUsername ?? "Unknown" }}</span>
              <span v-if="s.languageId" class="bk-card__lang">{{ s.languageId }}</span>
            </div>
            <p v-if="s.description?.trim()" class="bk-card__desc">
              {{ s.description.trim().length > 80 ? s.description.trim().slice(0, 77) + "…" : s.description.trim() }}
            </p>
            <div class="bk-card__actions">
              <button type="button" class="bk-card__action bk-card__action--read" @click="openReader(s)">
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="13" height="13">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <button
                v-if="isAuthenticated"
                type="button"
                class="bk-card__icon-btn"
                :class="{ 'bk-card__icon-btn--active': isLiked(s.id) }"
                :title="isLiked(s.id) ? 'Unlike' : 'Like'"
                @click="toggleLike(s.id)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" :fill="isLiked(s.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
              </button>
              <button
                type="button"
                class="bk-card__icon-btn bk-card__icon-btn--active"
                title="Remove bookmark"
                @click="handleRemoveBookmark(s.id)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
              <button
                v-if="isAuthenticated && currentUser && s.authorUsername !== currentUser.username"
                type="button"
                class="bk-card__icon-btn"
                :disabled="copyingId === s.id"
                :title="copyingId === s.id ? 'Copying…' : 'Copy to my scenarios'"
                @click="openCopyModal(s)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
              <button
                type="button"
                class="bk-card__icon-btn"
                title="Discussion"
                @click="openDiscussion(s)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="!scenarios.length" class="bk-empty">
        <div class="bk-empty__icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor" stroke="none">
            <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" opacity="0.25"/>
          </svg>
        </div>
        <h2 class="bk-empty__title">No bookmarked scenarios yet</h2>
        <p class="bk-empty__sub">Bookmark scenarios from the catalogue to find them here.</p>
        <RouterLink to="/scenarios" class="bk-hero__cta">Browse catalogue</RouterLink>
      </div>

      <div v-else class="bk-empty">
        <h2 class="bk-empty__title">No scenarios in "{{ activeCategory === '__none__' ? 'Uncategorized' : activeCategory }}"</h2>
        <button type="button" class="bk-hero__cta" @click="activeCategory = '__all__'">Show all bookmarks</button>
      </div>
    </template>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
    <ScenarioDiscussionModal :scenario="discussionScenario" @close="closeDiscussion" />
    <CopyScenarioModal
        :scenario="copyTarget"
        :saving="copyingId === copyTarget?.id"
        :error="copyError"
        @close="closeCopyModal"
        @confirm="confirmCopy"
    />
  </main>
</template>

<style scoped>
.bk-root { max-width: 1200px; margin: 0 auto; padding: 32px 24px 80px; display: flex; flex-direction: column; gap: 20px; }

/* Hero */
.bk-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; flex-wrap: wrap; }
.bk-eyebrow { margin: 0 0 6px; font-size: 0.72rem; font-weight: 900; letter-spacing: 0.14em; text-transform: uppercase; color: var(--primary); }
.bk-title { margin: 0 0 6px; font-size: clamp(1.8rem, 4vw, 2.6rem); font-weight: 950; letter-spacing: -0.025em; color: var(--text); line-height: 1.05; }
.bk-subtitle { margin: 0; font-size: 0.92rem; color: var(--text-soft); }
.bk-hero__actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.bk-hero__cta { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; padding: 0 20px; border-radius: 14px; background: var(--text); color: #fff; font-size: 0.88rem; font-weight: 800; text-decoration: none; white-space: nowrap; transition: background 160ms ease, transform 120ms ease; }
.bk-hero__cta:hover { background: var(--primary); transform: translateY(-1px); }
.bk-manage-btn { display: inline-flex; align-items: center; gap: 7px; min-height: 44px; padding: 0 18px; border-radius: 14px; border: 1.5px solid var(--border); background: #fff; color: var(--text); font: inherit; font-size: 0.88rem; font-weight: 700; cursor: pointer; transition: border-color 160ms ease, color 160ms ease; }
.bk-manage-btn:hover { border-color: var(--primary); color: var(--primary); }

/* Manage panel */
.bk-manage { background: #fff; border: 1.5px solid var(--border); border-radius: 18px; padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.bk-manage__head { display: flex; justify-content: space-between; align-items: center; }
.bk-manage__title { margin: 0; font-size: 1rem; font-weight: 800; color: var(--text); }
.bk-manage__close { border: 0; background: transparent; font-size: 20px; color: var(--text-soft); cursor: pointer; line-height: 1; }
.bk-manage__label { font-size: 0.68rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.1em; color: var(--text-soft); margin: 0 0 8px; }
.bk-suggestion-row { display: flex; flex-wrap: wrap; gap: 6px; }
.bk-suggestion { display: inline-flex; align-items: center; gap: 5px; padding: 5px 12px; border-radius: 999px; border: 1.5px solid var(--border); background: transparent; color: var(--text); font: inherit; font-size: 0.78rem; font-weight: 700; cursor: pointer; transition: all 0.15s; }
.bk-suggestion:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.bk-suggestion--exists { background: rgba(192,74,8,0.08); border-color: rgba(192,74,8,0.25); color: var(--primary); cursor: default; }
.bk-manage__list { display: flex; flex-direction: column; gap: 6px; }
.bk-manage__item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 10px; background: var(--surface-alt); }
.bk-manage__cat-name { flex: 1; font-size: 0.88rem; font-weight: 700; color: var(--text); }
.bk-manage__cat-count { font-size: 0.72rem; font-weight: 800; color: var(--text-soft); background: var(--border); border-radius: 999px; padding: 2px 7px; }
.bk-manage__action { border: 1.5px solid var(--border); border-radius: 8px; padding: 4px 10px; background: transparent; color: var(--text-soft); font: inherit; font-size: 0.75rem; font-weight: 700; cursor: pointer; transition: all 0.15s; }
.bk-manage__action:hover { border-color: var(--primary); color: var(--primary); }
.bk-manage__action--save { border-color: var(--primary); color: var(--primary); }
.bk-manage__action--danger:hover { border-color: var(--danger); color: var(--danger); }
.bk-manage__rename-input { flex: 1; border: 1.5px solid var(--primary); border-radius: 8px; padding: 4px 10px; font: inherit; font-size: 0.88rem; outline: none; }
.bk-manage__empty { font-size: 0.85rem; color: var(--text-soft); }
.bk-manage__add { display: flex; gap: 8px; }
.bk-manage__add-input { flex: 1; border: 1.5px solid var(--border); border-radius: 10px; padding: 9px 14px; font: inherit; font-size: 0.88rem; outline: none; transition: border-color 160ms ease; }
.bk-manage__add-input:focus { border-color: var(--primary); }
.bk-manage__add-btn { border: 0; border-radius: 10px; padding: 0 18px; background: var(--primary); color: #fff; font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; transition: background 140ms ease; }
.bk-manage__add-btn:hover:not(:disabled) { background: var(--primary-strong); }
.bk-manage__add-btn:disabled { opacity: 0.45; cursor: not-allowed; }

/* Category tabs */
.bk-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.bk-tab { display: inline-flex; align-items: center; gap: 6px; padding: 7px 14px; border-radius: 999px; border: 1.5px solid var(--border); background: #fff; color: var(--text-soft); font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer; transition: all 0.15s; white-space: nowrap; }
.bk-tab:hover { border-color: var(--primary); color: var(--primary); }
.bk-tab--active { background: var(--primary); border-color: var(--primary); color: #fff; }
.bk-tab--active .bk-tab__count { background: rgba(255,255,255,0.25); color: #fff; }
.bk-tab--uncategorized { border-style: dashed; }
.bk-tab__count { display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 18px; padding: 0 5px; border-radius: 999px; background: var(--surface-alt); color: var(--text-soft); font-size: 0.68rem; font-weight: 800; }

.bk-count { font-size: 0.82rem; color: var(--text-soft); margin: 0; }
.bk-loader { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 20px; color: var(--text-soft); }
.bk-spinner { width: 28px; height: 28px; border: 3px solid var(--border); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.bk-error { color: var(--danger); font-size: 0.9rem; }

/* Grid */
.bk-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; align-items: start; }

/* Card */
.bk-card { display: flex; flex-direction: column; border-radius: 18px; overflow: visible; background: #fff; border: 1.5px solid var(--border); box-shadow: 0 2px 8px rgba(42,21,0,0.05); transition: transform 200ms ease, box-shadow 200ms ease; position: relative; }
.bk-card:hover { transform: translateY(-4px); box-shadow: 0 14px 36px rgba(42,21,0,0.11); }

/* :hover applies a transform, which creates a new stacking context — without
   this, the category picker's z-index only wins locally within its own card
   and still ends up underneath a later sibling card in the grid. */
.bk-card--picker-open { z-index: 20; }

/* Category row */
.bk-card__cat-row { position: relative; padding: 8px 12px; }
.bk-card__cat-btn { display: inline-flex; align-items: center; gap: 5px; padding: 3px 10px; border-radius: 999px; border: 1.5px dashed var(--border); background: transparent; color: var(--text-soft); font: inherit; font-size: 0.72rem; font-weight: 700; cursor: pointer; transition: all 0.15s; }
.bk-card__cat-btn:hover { border-color: var(--primary); color: var(--primary); border-style: solid; }
.bk-card__cat-btn--set { border-style: solid; border-color: rgba(192,74,8,0.3); background: rgba(192,74,8,0.06); color: var(--primary); }

/* Category picker */
.bk-cat-picker { position: absolute; top: calc(100% + 4px); left: 12px; z-index: 100; background: #fff; border: 1.5px solid var(--border); border-radius: 14px; box-shadow: 0 8px 24px rgba(42,21,0,0.12); padding: 6px; min-width: 200px; display: flex; flex-direction: column; gap: 2px; }
.bk-cat-picker__item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 10px; border-radius: 8px; border: 0; background: transparent; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer; text-align: left; transition: background 0.12s; }
.bk-cat-picker__item:hover { background: var(--surface-alt); }
.bk-cat-picker__item--active { color: var(--primary); background: rgba(192,74,8,0.06); }
.bk-cat-picker__item--none { color: var(--text-soft); }
.bk-cat-picker__item--new { color: var(--primary); font-weight: 700; }
.bk-cat-picker__divider { height: 1px; background: var(--border); margin: 2px 0; }
.bk-cat-picker__empty { font-size: 0.78rem; color: var(--text-soft); padding: 6px 10px; }
.bk-cat-picker__empty button { border: 0; background: transparent; color: var(--primary); font: inherit; font-weight: 700; cursor: pointer; text-decoration: underline; }

.bk-card__thumb { position: relative; aspect-ratio: 4/3; overflow: hidden; background: var(--surface-alt); display: block; text-decoration: none; border-radius: 0; }
.bk-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 300ms ease; }
.bk-card:hover .bk-card__img { transform: scale(1.04); }
.bk-card__placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.bk-card__placeholder-icon { width: 36px; height: 36px; color: rgba(42,21,0,0.2); }
.bk-card__overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(42,21,0,0.42); opacity: 0; transition: opacity 200ms ease; backdrop-filter: blur(2px); color: #fff; font-size: 0.9rem; font-weight: 800; }
.bk-card:hover .bk-card__overlay { opacity: 1; }
.bk-card__body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 6px; }
.bk-card__title-link { text-decoration: none; color: inherit; }
.bk-card__title-link:hover .bk-card__title { color: var(--primary); }
.bk-card__title { margin: 0; font-size: 1rem; font-weight: 800; color: var(--text); line-height: 1.25; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bk-card__meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.bk-card__author { font-size: 0.76rem; font-weight: 600; color: var(--text-soft); }
.bk-card__lang { font-size: 0.72rem; font-weight: 700; color: var(--primary); background: rgba(192,74,8,0.08); border-radius: 6px; padding: 2px 7px; }
.bk-card__desc { margin: 0; font-size: 0.8rem; color: var(--text-soft); line-height: 1.5; }
.bk-card__actions { display: flex; gap: 6px; margin-top: 8px; padding-top: 10px; border-top: 1px solid var(--border); }
.bk-card__action { display: inline-flex; align-items: center; gap: 5px; border: 1.5px solid var(--border); border-radius: 10px; padding: 6px 12px; background: transparent; color: var(--text-soft); font: inherit; font-size: 0.78rem; font-weight: 700; text-decoration: none; cursor: pointer; transition: all 0.15s; }
.bk-card__action--read { background: var(--primary); border-color: var(--primary); color: #fff; flex: 1; justify-content: center; }
.bk-card__action--read:hover { background: var(--primary-strong); border-color: var(--primary-strong); }
.bk-card__action--open:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); }
.bk-card__icon-btn { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex-shrink: 0; border: 1.5px solid var(--border); border-radius: 10px; background: transparent; color: var(--text-soft); cursor: pointer; transition: all 0.15s; }
.bk-card__icon-btn--active { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.08); }
.bk-card__icon-btn:hover { border-color: var(--danger); color: var(--danger); background: rgba(180,35,24,0.06); }

/* Empty */
.bk-empty { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 60px 20px 80px; text-align: center; }
.bk-empty__icon { color: var(--text-soft); opacity: 0.3; }
.bk-empty__title { margin: 0; font-size: 1.4rem; font-weight: 800; color: var(--text); }
.bk-empty__sub { margin: 0; font-size: 0.9rem; color: var(--text-soft); max-width: 400px; }

/* Transitions */
.bk-fade-enter-active, .bk-fade-leave-active { transition: opacity 180ms ease, transform 180ms ease; }
.bk-fade-enter-from, .bk-fade-leave-to { opacity: 0; transform: translateY(-8px); }

@media (max-width: 640px) {
  .bk-root { padding: 20px 14px 60px; }
  .bk-grid { grid-template-columns: 1fr; }
  .bk-hero { flex-direction: column; align-items: flex-start; }
}
</style>