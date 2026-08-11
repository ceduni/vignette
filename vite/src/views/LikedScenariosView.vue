<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {fetchScenarios, fetchScenarioThumbnails} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useScenarioReader} from "../composables/useScenarioReader";
import BaseLoader from "../components/ui/BaseLoader.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";

const { isLiked, toggleLike, isBookmarked, toggleBookmark, likedIds } = useScenarioInteractions();
const { openReader, activeScenario, closeReader } = useScenarioReader();

const allScenarios = ref([]);
const previewMap = ref({});
const loading = ref(false);
const error = ref("");

const scenarios = computed(() =>
  allScenarios.value.filter(s => likedIds.value.has(String(s.id)))
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

onMounted(load);
</script>

<template>
  <main class="iv-root">
    <div class="iv-hero">
      <div>
        <p class="iv-eyebrow">Your likes</p>
        <h1 class="iv-title">Liked Scenarios</h1>
        <p class="iv-subtitle">Scenarios you've saved from the community catalogue.</p>
      </div>
      <RouterLink to="/scenarios" class="iv-hero__cta">
        Browse catalogue
      </RouterLink>
    </div>

    <div v-if="loading" class="iv-loader">
      <div class="iv-spinner"></div>
      <p>Loading…</p>
    </div>

    <div v-else-if="error" class="iv-error">{{ error }}</div>

    <template v-else>
      <p class="iv-count">{{ scenarios.length }} scenario{{ scenarios.length !== 1 ? 's' : '' }}</p>

      <div v-if="scenarios.length" class="iv-grid">
        <div v-for="(s, index) in scenarios" :key="s.id" class="iv-card">
          <RouterLink :to="`/scenarios/${s.id}`" class="iv-card__thumb" tabindex="-1">
            <img v-if="thumbnailUrl(s.id)" :src="thumbnailUrl(s.id)" :alt="s.title" class="iv-card__img"/>
            <div v-else class="iv-card__placeholder" :style="{ background: placeholderGradient(index) }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"
                   stroke-linecap="round" stroke-linejoin="round" class="iv-card__placeholder-icon">
                <rect x="3" y="3" width="7" height="7" rx="1"/>
                <rect x="14" y="3" width="7" height="7" rx="1"/>
                <rect x="3" y="14" width="7" height="7" rx="1"/>
                <rect x="14" y="14" width="7" height="7" rx="1"/>
              </svg>
            </div>
            <div class="iv-card__overlay"><span>Open →</span></div>
          </RouterLink>

          <div class="iv-card__body">
            <RouterLink :to="`/scenarios/${s.id}`" class="iv-card__title-link">
              <h3 class="iv-card__title">{{ s.title || "Untitled" }}</h3>
            </RouterLink>
            <div class="iv-card__meta">
              <span class="iv-card__author">{{ s.authorUsername ?? "Unknown" }}</span>
              <span v-if="s.languageId" class="iv-card__lang">{{ s.languageId }}</span>
            </div>
            <p v-if="s.description?.trim()" class="iv-card__desc">
              {{ s.description.trim().length > 80 ? s.description.trim().slice(0, 77) + "…" : s.description.trim() }}
            </p>
            <div class="iv-card__actions">
              <button type="button" class="iv-card__action iv-card__action--read" @click="openReader(s)">
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="13" height="13">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <button
                type="button"
                class="iv-card__icon-btn iv-card__icon-btn--active"
                title="Remove"
                @click="toggleLike(s.id)"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
              </button>
              <RouterLink :to="`/scenarios/${s.id}`" class="iv-card__action iv-card__action--open">
                Open <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                  stroke-linecap="round" stroke-linejoin="round" width="12" height="12">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </RouterLink>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="iv-empty">
        <div class="iv-empty__icon">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
        </div>
        <h2 class="iv-empty__title">No liked scenarios yet</h2>
        <p class="iv-empty__sub">Like scenarios from the catalogue to find them here.</p>
        <RouterLink to="/scenarios" class="iv-hero__cta">Browse catalogue</RouterLink>
      </div>
    </template>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
  </main>
</template>

<style scoped>
.iv-root { max-width: 1200px; margin: 0 auto; padding: 32px 24px 80px; display: flex; flex-direction: column; gap: 24px; }
.iv-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; flex-wrap: wrap; }
.iv-eyebrow { margin: 0 0 6px; font-size: 0.72rem; font-weight: 900; letter-spacing: 0.14em; text-transform: uppercase; color: var(--primary); }
.iv-title { margin: 0 0 6px; font-size: clamp(1.8rem, 4vw, 2.6rem); font-weight: 950; letter-spacing: -0.025em; color: var(--text); line-height: 1.05; }
.iv-subtitle { margin: 0; font-size: 0.92rem; color: var(--text-soft); }
.iv-hero__cta { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; padding: 0 20px; border-radius: 14px; background: var(--text); color: #fff; font-size: 0.88rem; font-weight: 800; text-decoration: none; white-space: nowrap; transition: background 160ms ease, transform 120ms ease; }
.iv-hero__cta:hover { background: var(--primary); transform: translateY(-1px); }
.iv-count { font-size: 0.82rem; color: var(--text-soft); margin: 0; }
.iv-loader { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 20px; color: var(--text-soft); }
.iv-spinner { width: 28px; height: 28px; border: 3px solid var(--border); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.iv-error { color: var(--danger); font-size: 0.9rem; }
.iv-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; align-items: start; }
.iv-card { display: flex; flex-direction: column; border-radius: 18px; overflow: hidden; background: #fff; border: 1.5px solid var(--border); box-shadow: 0 2px 8px rgba(42,21,0,0.05); transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease; }
.iv-card:hover { transform: translateY(-4px); box-shadow: 0 14px 36px rgba(42,21,0,0.11); border-color: rgba(192,74,8,0.2); }
.iv-card__thumb { position: relative; aspect-ratio: 4/3; overflow: hidden; background: var(--surface-alt); display: block; text-decoration: none; }
.iv-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 300ms ease; }
.iv-card:hover .iv-card__img { transform: scale(1.04); }
.iv-card__placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.iv-card__placeholder-icon { width: 36px; height: 36px; color: rgba(42,21,0,0.2); }
.iv-card__overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(42,21,0,0.42); opacity: 0; transition: opacity 200ms ease; backdrop-filter: blur(2px); color: #fff; font-size: 0.9rem; font-weight: 800; }
.iv-card:hover .iv-card__overlay { opacity: 1; }
.iv-card__body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 6px; }
.iv-card__title-link { text-decoration: none; color: inherit; }
.iv-card__title-link:hover .iv-card__title { color: var(--primary); }
.iv-card__title { margin: 0; font-size: 1rem; font-weight: 800; color: var(--text); line-height: 1.25; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.iv-card__meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.iv-card__author { font-size: 0.76rem; font-weight: 600; color: var(--text-soft); }
.iv-card__lang { font-size: 0.72rem; font-weight: 700; color: var(--primary); background: rgba(192,74,8,0.08); border-radius: 6px; padding: 2px 7px; }
.iv-card__desc { margin: 0; font-size: 0.8rem; color: var(--text-soft); line-height: 1.5; }
.iv-card__actions { display: flex; gap: 6px; margin-top: 8px; padding-top: 10px; border-top: 1px solid var(--border); }
.iv-card__action { display: inline-flex; align-items: center; gap: 5px; border: 1.5px solid var(--border); border-radius: 10px; padding: 6px 12px; background: transparent; color: var(--text-soft); font: inherit; font-size: 0.78rem; font-weight: 700; text-decoration: none; cursor: pointer; transition: background 140ms ease, color 140ms ease, border-color 140ms ease; }
.iv-card__action--read { background: var(--primary); border-color: var(--primary); color: #fff; flex: 1; justify-content: center; }
.iv-card__action--read:hover { background: var(--primary-strong); border-color: var(--primary-strong); }
.iv-card__action--open:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); }
.iv-card__icon-btn { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex-shrink: 0; border: 1.5px solid var(--border); border-radius: 10px; background: transparent; color: var(--text-soft); cursor: pointer; transition: background 140ms ease, color 140ms ease, border-color 140ms ease; }
.iv-card__icon-btn--active { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.08); }
.iv-card__icon-btn:hover { border-color: var(--danger); color: var(--danger); background: rgba(180,35,24,0.06); }
.iv-empty { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 60px 20px 80px; text-align: center; }
.iv-empty__icon { width: 48px; height: 48px; color: var(--text-soft); opacity: 0.3; display: flex; align-items: center; justify-content: center; }
.iv-empty__title { margin: 0; font-size: 1.4rem; font-weight: 800; color: var(--text); }
.iv-empty__sub { margin: 0; font-size: 0.9rem; color: var(--text-soft); max-width: 400px; }
@media (max-width: 640px) { .iv-root { padding: 20px 14px 60px; } .iv-grid { grid-template-columns: 1fr; } }
</style>