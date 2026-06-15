<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {fetchScenarios, fetchScenarioThumbnails} from "../api/scenarios";
import ScenarioCard from "../components/ScenarioCard.vue";
import BasePageHeader from "../components/ui/BasePageHeader.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import {useDebouncedRef} from "../composables/useDebouncedRef";
import {RouterLink} from "vue-router";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";

const scenarios = ref([]);
const previewMap = ref({});
const error = ref("");
const loading = ref(false);
const {source: search, debounced} = useDebouncedRef("", 250);
const effectiveSearch = ref("");

const { isLiked, toggleLike, isBookmarked, toggleBookmark } = useScenarioInteractions();

watch(debounced, (value) => {
  effectiveSearch.value = value.trim().toLowerCase();
});

const statusFilter = ref("ALL");
const filtered = computed(() => {
  const q = effectiveSearch.value;
  return scenarios.value.filter((s) => {
    const title = (s.title ?? "").toLowerCase();
    const language = String(s.languageId ?? "").toLowerCase();
    const author = (s.authorUsername ?? "").toLowerCase();
    const description = (s.description ?? "").toLowerCase();
    const status = String(s.visibilityStatus ?? "").toUpperCase();
    const tags = Array.isArray(s.tags)
      ? s.tags.map(tag => String(tag).toLowerCase())
      : [];
    const matchesSearch = !q || (
      title.includes(q) ||
      language.includes(q) ||
      author.includes(q) ||
      description.includes(q) ||
      tags.some(tag => tag.includes(q))
    );
    const matchesStatus = statusFilter.value === "ALL" || status === statusFilter.value;
    return matchesSearch && matchesStatus;
  });
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await fetchScenarios();
    scenarios.value = Array.isArray(data) ? data : [];
    const map = {};
    await Promise.all(
      scenarios.value.map(async (s) => {
        try {
          const thumbs = await fetchScenarioThumbnails(s.id);
          map[s.id] = thumbs?.[0]?.id ?? null;
        } catch {
          map[s.id] = null;
        }
      })
    );
    previewMap.value = map;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main class="page">
    <section class="section">
      <BasePageHeader
        title="Scenario gallery"
        subtitle="Browse public scenarios and, when authenticated, your own drafts."
      >
        <template #actions>
          <RouterLink to="/create-scenario" class="btn btn--primary">
            Create scenario
          </RouterLink>
        </template>
      </BasePageHeader>

      <div class="card search-panel">
        <div class="storyboard-settings-grid">
          <label>
            Search
            <input v-model="search" placeholder="Search scenarios by name, authors, languages or tags"/>
          </label>
          <label>
            Status
            <select v-model="statusFilter">
              <option value="ALL">All visible scenarios</option>
              <option value="PUBLISHED">Published</option>
              <option value="DRAFT">Drafts</option>
            </select>
          </label>
        </div>
      </div>

      <BaseLoader v-if="loading">Loading scenarios...</BaseLoader>
      <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

      <template v-else>
        <div class="results-meta">
          <span>{{ filtered.length }} scenario(s)</span>
        </div>

        <section v-if="filtered.length" class="scenarios-grid">
          <div v-for="s in filtered" :key="s.id" class="scenario-item">
            <ScenarioCard
              :scenario="s"
              :preview-id="previewMap[s.id]"
            />
            <div class="scenario-item__actions">
              <button
                type="button"
                class="interaction-btn"
                :class="{ 'interaction-btn--active': isLiked(s.id) }"
                :title="isLiked(s.id) ? 'Unlike' : 'Like'"
                @click.prevent="toggleLike(s.id)"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
                <span>{{ isLiked(s.id) ? "Liked" : "Like" }}</span>
              </button>

              <button
                type="button"
                class="interaction-btn"
                :class="{ 'interaction-btn--active': isBookmarked(s.id) }"
                :title="isBookmarked(s.id) ? 'Remove bookmark' : 'Bookmark'"
                @click.prevent="toggleBookmark(s.id)"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                </svg>
                <span>{{ isBookmarked(s.id) ? "Saved" : "Save" }}</span>
              </button>
            </div>
          </div>
        </section>

        <BaseEmptyState
          v-else
          title="No scenarios found"
          message="Try another search query."
        />
      </template>
    </section>
  </main>
</template>

<style scoped>
.scenarios-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.25rem;
}

.scenario-item {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.scenario-item__actions {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem 0.75rem;
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-top: none;
  border-radius: 0 0 var(--radius) var(--radius);
}

.interaction-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: #FFFCF7;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-soft);
  cursor: pointer;
  transition: all 0.15s;
  flex: 1;
  justify-content: center;
}

.interaction-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.06);
}

.interaction-btn--active {
  background: rgba(192, 74, 8, 0.10);
  border-color: var(--primary);
  color: var(--primary);
}

.interaction-btn--active svg {
  fill: var(--primary);
}
</style>