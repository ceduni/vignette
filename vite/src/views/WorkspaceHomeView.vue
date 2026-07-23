<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {deleteScenario, fetchMyScenarios, fetchScenarioThumbnails, updateScenarioMetadata} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useDebouncedRef} from "../composables/useDebouncedRef";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";
import {apiFetch} from "../api/rest";
import ScenarioDiscussionModal from "../components/community/ScenarioDiscussionModal.vue";

const scenarios = ref([]);
const previewMap = ref({});
const likeCountMap = ref({});
const discussionScenario = ref(null);
function openDiscussion(s) { discussionScenario.value = s; }
function closeDiscussion() { discussionScenario.value = null; }
const error = ref("");
const loading = ref(false);
const statusFilter = ref("ALL");
const {source: search, debounced} = useDebouncedRef("", 250);
const effectiveSearch = ref("");
watch(debounced, (v) => { effectiveSearch.value = v.trim().toLowerCase(); });

const stats = computed(() => ({
  total: scenarios.value.length,
  published: scenarios.value.filter(s => s.visibilityStatus === "PUBLISHED").length,
  draft: scenarios.value.filter(s => s.visibilityStatus !== "PUBLISHED").length,
}));

const filtered = computed(() => {
  const q = effectiveSearch.value;
  return scenarios.value.filter(s => {
    const matchesSearch = !q || [
      s.title ?? "",
      String(s.languageId ?? ""),
      s.description ?? "",
      ...(s.tags ?? []).map(String),
    ].some(v => v.toLowerCase().includes(q));
    const matchesStatus = statusFilter.value === "ALL" ||
      String(s.visibilityStatus).toUpperCase() === statusFilter.value;
    return matchesSearch && matchesStatus;
  });
});

function thumbnailUrl(id) {
  const thumbId = previewMap.value[id];
  return thumbId ? buildApiUrl(`/api/thumbnails/${thumbId}/content`) : null;
}

const TILE_GRADIENTS = [
  "linear-gradient(135deg,#D4E5CA,#D4E5CA)",
  "linear-gradient(135deg,#FFF0EE,#D4E5CA)",
  "linear-gradient(135deg,#c5d9b8,#afc8a0)",
  "linear-gradient(135deg,#ddd4f5,#c8bde8)",
  "linear-gradient(135deg,#A8C498,#A8C498)",
];

function placeholderGradient(index) {
  return TILE_GRADIENTS[index % TILE_GRADIENTS.length];
}

const confirmDelete = ref(null);
const deleting = ref(false);
const editTarget = ref(null);
const editForm = ref({ title: "", description: "", tags: [] });
const editSaving = ref(false);
const editError = ref("");

function openDelete(s) { confirmDelete.value = s; }
function cancelDelete() { confirmDelete.value = null; }

async function doDelete() {
  if (!confirmDelete.value) return;
  deleting.value = true;
  try {
    await deleteScenario(confirmDelete.value.id);
    scenarios.value = scenarios.value.filter(s => s.id !== confirmDelete.value.id);
    confirmDelete.value = null;
  } catch {
    confirmDelete.value = null;
  } finally {
    deleting.value = false;
  }
}

function openEdit(s) {
  editTarget.value = s;
  editForm.value = { title: s.title || "", description: s.description || "", tags: [...(s.tags ?? [])] };
  editError.value = "";
}
function cancelEdit() { editTarget.value = null; }

async function saveEdit() {
  if (!editTarget.value) return;
  editSaving.value = true;
  editError.value = "";
  try {
    const updated = await updateScenarioMetadata(editTarget.value.id, editForm.value);
    scenarios.value = scenarios.value.map(s => s.id === editTarget.value.id ? { ...s, ...updated } : s);
    cancelEdit();
  } catch (e) {
    editError.value = e.message || "Failed to save.";
  } finally {
    editSaving.value = false;
  }
}

const DRAFT_KEY = "vignette:unclaimed-draft-audios";
const drafts = ref([]);

function loadDrafts() {
  try { drafts.value = JSON.parse(localStorage.getItem(DRAFT_KEY) || "[]"); }
  catch { drafts.value = []; }
}

function removeDraft(id) {
  drafts.value = drafts.value.filter(d => d.id !== id);
  localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts.value));
}

function draftPath(draft) {
  return `/scenarios/emergency-${draft.id}?draftAudio=${draft.id}`;
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await fetchMyScenarios();
    scenarios.value = Array.isArray(data) ? data : (data.content ?? []);
    const map = {};
    const likes = {};
    await Promise.all(
      scenarios.value.map(async (s) => {
        try {
          const thumbs = await fetchScenarioThumbnails(s.id);
          map[s.id] = thumbs?.[0]?.id ?? null;
        } catch {
          map[s.id] = null;
        }
        if (s.visibilityStatus === "PUBLISHED") {
          try {
            const status = await apiFetch(`/api/scenarios/${s.id}/interactions`);
            likes[s.id] = status?.likeCount ?? 0;
          } catch {
            likes[s.id] = 0;
          }
        }
      })
    );
    previewMap.value = map;
    likeCountMap.value = likes;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => { load(); loadDrafts(); });

const { openReader, activeScenario, closeReader } = useScenarioReader();
</script>

<template>
  <main class="ms-root">
    <div class="ms-hero">
      <div>
        <p class="ms-eyebrow">Your workspace</p>
        <h1 class="ms-title">My Scenarios</h1>
        <div v-if="!loading && scenarios.length" class="ms-stats">
          <span class="ms-stats__num">{{ stats.total }}</span>
          <span class="ms-stats__lbl">total</span>
          <span class="ms-stats__sep">·</span>
          <span class="ms-stats__num ms-stats__num--pub">{{ stats.published }}</span>
          <span class="ms-stats__lbl">published</span>
          <span class="ms-stats__sep">·</span>
          <span class="ms-stats__num ms-stats__num--draft">{{ stats.draft }}</span>
          <span class="ms-stats__lbl">drafts</span>
        </div>
      </div>
      <RouterLink to="/create-scenario" class="ms-hero__cta">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        New scenario
      </RouterLink>
    </div>

    <Transition name="ms-drafts">
      <div v-if="drafts.length" class="ms-drafts">
        <div class="ms-drafts__head">
          <p class="ms-drafts__eyebrow">Emergency audio</p>
          <strong class="ms-drafts__count">{{ drafts.length }} unattached draft{{ drafts.length !== 1 ? 's' : '' }}</strong>
        </div>
        <div class="ms-drafts__list">
          <article v-for="draft in drafts" :key="draft.id" class="ms-draft">
            <div class="ms-draft__wave" aria-hidden="true">
              <span v-for="i in 10" :key="i"></span>
            </div>
            <div class="ms-draft__info">
              <strong class="ms-draft__title">{{ draft.title }}</strong>
              <small class="ms-draft__date">{{ new Date(draft.createdAt).toLocaleDateString() }}</small>
            </div>
            <audio :src="draft.dataUrl" controls class="ms-draft__audio"></audio>
            <div class="ms-draft__actions">
              <RouterLink :to="draftPath(draft)" class="ms-draft__open">Open in studio →</RouterLink>
              <button type="button" class="ms-draft__del" title="Delete draft" @click="removeDraft(draft.id)">×</button>
            </div>
          </article>
        </div>
      </div>
    </Transition>

    <div class="ms-bar">
      <div class="ms-search">
        <svg class="ms-search__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input v-model="search" class="ms-search__input" placeholder="Search by title, language, tag…"/>
        <button v-if="search" type="button" class="ms-search__clear" @click="search = ''">×</button>
      </div>
      <div class="ms-tabs" role="tablist">
        <button type="button" class="ms-tab" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">
          All <span class="ms-tab__count">{{ stats.total }}</span>
        </button>
        <button type="button" class="ms-tab" :class="{ active: statusFilter === 'PUBLISHED' }" @click="statusFilter = 'PUBLISHED'">
          Published <span class="ms-tab__count ms-tab__count--pub">{{ stats.published }}</span>
        </button>
        <button type="button" class="ms-tab" :class="{ active: statusFilter === 'DRAFT' }" @click="statusFilter = 'DRAFT'">
          Drafts <span class="ms-tab__count ms-tab__count--draft">{{ stats.draft }}</span>
        </button>
      </div>
    </div>

    <BaseLoader v-if="loading">Loading your scenarios…</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else>
      <div v-if="filtered.length" class="ms-grid">
        <div v-for="(s, index) in filtered" :key="s.id" class="ms-card"
             :class="s.visibilityStatus === 'PUBLISHED' ? 'ms-card--pub' : 'ms-card--draft'">
          <RouterLink :to="`/scenarios/${s.id}`" class="ms-card__thumb" tabindex="-1">
            <img v-if="thumbnailUrl(s.id)" :src="thumbnailUrl(s.id)" :alt="s.title || 'Scene preview'" class="ms-card__img"/>
            <div v-else class="ms-card__placeholder" :style="{ background: placeholderGradient(index) }">
              <span class="ms-card__placeholder-num">{{ String(index + 1).padStart(2, '0') }}</span>
            </div>
            <span class="ms-card__badge" :class="s.visibilityStatus === 'PUBLISHED' ? 'ms-card__badge--pub' : 'ms-card__badge--draft'">
              <span class="ms-card__badge-dot"></span>
              {{ s.visibilityStatus === "PUBLISHED" ? "Published" : "Draft" }}
            </span>
            <div class="ms-card__overlay" aria-hidden="true">
              <span class="ms-card__overlay-label">Open studio →</span>
            </div>
          </RouterLink>

          <div class="ms-card__body">
            <RouterLink :to="`/scenarios/${s.id}`" class="ms-card__title-link">
              <h3 class="ms-card__title">{{ s.title || "Untitled scenario" }}</h3>
            </RouterLink>
            <div class="ms-card__meta">
              <span v-if="s.languageId" class="ms-card__lang">{{ s.languageId }}</span>
              <template v-if="s.tags?.length">
                <span class="ms-card__meta-sep">·</span>
                <span v-for="tag in s.tags.slice(0, 2)" :key="tag" class="ms-card__tag">#{{ tag }}</span>
              </template>
            </div>
            <p v-if="s.description?.trim()" class="ms-card__desc">
              {{ s.description.trim().length > 80 ? s.description.trim().slice(0, 77) + "…" : s.description.trim() }}
            </p>
            <div class="ms-card__actions">
              <RouterLink :to="`/scenarios/${s.id}`" class="ms-card__action ms-card__action--open">
                Open studio
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </RouterLink>
              <button v-if="s.visibilityStatus === 'PUBLISHED'" type="button"
                      class="ms-card__action ms-card__action--read" title="Read scenario" @click.stop="openReader(s)">
                <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="13" height="13">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
                Read
              </button>
              <span v-if="s.visibilityStatus === 'PUBLISHED'" class="ms-card__like-count" title="Likes">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
                {{ likeCountMap[s.id] ?? 0 }}
              </span>
              <button v-if="s.visibilityStatus === 'PUBLISHED'" type="button"
                      class="ms-card__action ms-card__action--discussion" title="Discussion" @click.stop="openDiscussion(s)">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
              <button type="button" class="ms-card__action ms-card__action--edit" title="Edit" @click.stop="openEdit(s)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z"/>
                </svg>
              </button>
              <button type="button" class="ms-card__action ms-card__action--delete" title="Delete" @click.stop="openDelete(s)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                  <path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="scenarios.length" class="ms-noresults">
        <div class="ms-noresults__icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
        </div>
        <p class="ms-noresults__text">No scenarios match <strong>"{{ search || statusFilter }}"</strong></p>
        <button type="button" class="ms-noresults__reset" @click="search = ''; statusFilter = 'ALL'">Clear filters</button>
      </div>

      <div v-else class="ms-empty">
        <div class="ms-empty__tiles" aria-hidden="true">
          <div class="ms-empty__tile" style="background: linear-gradient(135deg,#D4E5CA,#D4E5CA); height:110px; width: 130px;"></div>
          <div class="ms-empty__tile" style="background: linear-gradient(135deg,#FFF0EE,#D4E5CA); height:140px; width: 100px;"></div>
          <div class="ms-empty__tile" style="background: linear-gradient(135deg,#c5d9b8,#afc8a0); height:95px; width: 120px;"></div>
        </div>
        <p class="ms-empty__eyebrow">Nothing here yet</p>
        <h2 class="ms-empty__title">Create your first scenario</h2>
        <p class="ms-empty__sub">A scenario is a storyboard of scenes — add images, record voices,<br>annotate with linguistic glosses and publish to the community.</p>
        <RouterLink to="/create-scenario" class="ms-hero__cta">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          Create my first scenario
        </RouterLink>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="confirmDelete" class="ms-backdrop" @click.self="cancelDelete">
        <div class="ms-confirm">
          <p class="ms-confirm__eyebrow">Permanent action</p>
          <h2 class="ms-confirm__title">Delete "{{ confirmDelete.title || 'Untitled' }}"?</h2>
          <p class="ms-confirm__body">This will permanently remove all scenes, audio recordings, and annotations. This cannot be undone.</p>
          <div class="ms-confirm__actions">
            <button type="button" class="ms-confirm__cancel" @click="cancelDelete">Keep it</button>
            <button type="button" class="ms-confirm__delete" :disabled="deleting" @click="doDelete">
              <template v-if="deleting"><span class="ms-spin"></span> Deleting…</template>
              <template v-else>Yes, delete</template>
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="editTarget" class="ms-backdrop" @click.self="cancelEdit">
        <div class="ms-edit">
          <div class="ms-edit__head">
            <div>
              <p class="ms-edit__eyebrow">Edit scenario</p>
              <h2 class="ms-edit__title">{{ editTarget.title || "Untitled" }}</h2>
            </div>
            <button type="button" class="ms-edit__close" @click="cancelEdit">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 6 6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div class="ms-edit__field">
            <label class="ms-edit__label">Title</label>
            <input v-model="editForm.title" class="ms-edit__input" placeholder="Scenario title"/>
          </div>
          <div class="ms-edit__field">
            <label class="ms-edit__label">Description <span class="ms-edit__opt">optional</span></label>
            <textarea v-model="editForm.description" class="ms-edit__textarea" rows="3" placeholder="Describe the scene context…"/>
          </div>
          <p v-if="editError" class="ms-edit__error">{{ editError }}</p>
          <div class="ms-edit__footer">
            <button type="button" class="ms-edit__cancel" @click="cancelEdit">Cancel</button>
            <button type="button" class="ms-edit__save" :disabled="editSaving || !editForm.title.trim()" @click="saveEdit">
              <template v-if="editSaving"><span class="ms-spin"></span> Saving…</template>
              <template v-else>Save changes</template>
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
    <ScenarioDiscussionModal :scenario="discussionScenario" @close="closeDiscussion" />
  </main>
</template>

<style scoped>
.ms-root { max-width: 1200px; margin: 0 auto; padding: 32px 24px 80px; display: flex; flex-direction: column; gap: 28px; }
.ms-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; flex-wrap: wrap; }
.ms-eyebrow { margin: 0 0 6px; font-size: 0.72rem; font-weight: 900; letter-spacing: 0.14em; text-transform: uppercase; color: var(--primary); }
.ms-title { margin: 0 0 10px; font-size: clamp(1.8rem, 4vw, 2.6rem); font-weight: 950; letter-spacing: -0.025em; color: var(--text); line-height: 1.05; }
.ms-stats { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.ms-stats__num { font-size: 1rem; font-weight: 800; color: var(--text); }
.ms-stats__num--pub { color: #4A6741; }
.ms-stats__num--draft { color: #485B38; }
.ms-stats__lbl { font-size: 0.82rem; color: var(--text-soft); }
.ms-stats__sep { color: var(--border); font-weight: 700; }
.ms-hero__cta { display: inline-flex; align-items: center; gap: 8px; min-height: 46px; padding: 0 22px; border-radius: 14px; background: var(--text); color: #fff; font-size: 0.9rem; font-weight: 800; text-decoration: none; white-space: nowrap; transition: background 160ms ease, transform 120ms ease; }
.ms-hero__cta:hover { background: var(--primary); transform: translateY(-1px); }
.ms-hero__cta svg { width: 15px; height: 15px; }
.ms-bar { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.ms-search { flex: 1; min-width: 220px; display: flex; align-items: center; gap: 10px; border: 1.5px solid var(--border); border-radius: 12px; padding: 0 14px; background: #fff; transition: border-color 160ms ease, box-shadow 160ms ease; }
.ms-search:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(192, 74, 8, 0.08); }
.ms-search__icon { width: 16px; height: 16px; flex-shrink: 0; color: var(--text-soft); }
.ms-search__input { flex: 1; border: 0; outline: none; padding: 12px 0; font: inherit; font-size: 0.9rem; color: var(--text); background: transparent; }
.ms-search__input::placeholder { color: var(--text-soft); }
.ms-search__clear { border: 0; background: transparent; color: var(--text-soft); cursor: pointer; font-size: 18px; line-height: 1; padding: 0; }
.ms-search__clear:hover { color: var(--text); }
.ms-tabs { display: flex; gap: 2px; padding: 3px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface-alt); flex-shrink: 0; }
.ms-tab { display: flex; align-items: center; gap: 6px; border: 0; border-radius: 9px; padding: 7px 14px; background: transparent; color: var(--text-soft); cursor: pointer; font: inherit; font-size: 0.82rem; font-weight: 700; transition: background 140ms ease, color 140ms ease, box-shadow 140ms ease; white-space: nowrap; }
.ms-tab.active { background: #fff; color: var(--text); box-shadow: 0 1px 4px rgba(30, 8, 18, 0.1); }
.ms-tab__count { display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 18px; border-radius: 999px; padding: 0 5px; background: var(--border); color: var(--text-soft); font-size: 0.68rem; font-weight: 800; }
.ms-tab.active .ms-tab__count { background: var(--bg); }
.ms-tab__count--pub { background: rgba(74,103,65,0.12); color: #4A6741; }
.ms-tab__count--draft { background: rgba(72,91,56,0.12); color: #485B38; }
.ms-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; align-items: start; }
.ms-card { display: flex; flex-direction: column; border-radius: 18px; overflow: hidden; background: #fff; border: 1.5px solid var(--border); transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease; box-shadow: 0 2px 8px rgba(30, 8, 18, 0.05); }
.ms-card:hover { transform: translateY(-5px); box-shadow: 0 16px 40px rgba(30, 8, 18, 0.12); border-color: transparent; }
.ms-card--pub { border-left: 3px solid #4A6741; }
.ms-card--draft { border-left: 3px solid #485B38; }
.ms-card__thumb { position: relative; aspect-ratio: 4 / 3; overflow: hidden; background: var(--surface-alt); display: block; text-decoration: none; }
.ms-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 300ms ease; }
.ms-card:hover .ms-card__img { transform: scale(1.04); }
.ms-card__placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.ms-card__placeholder-num { font-size: 2.5rem; font-weight: 950; color: rgba(30,8,18,0.18); letter-spacing: -0.04em; }
.ms-card__badge { position: absolute; top: 10px; left: 10px; display: inline-flex; align-items: center; gap: 5px; border-radius: 999px; padding: 4px 10px; font-size: 0.68rem; font-weight: 800; letter-spacing: 0.04em; backdrop-filter: blur(8px); }
.ms-card__badge--pub { background: rgba(74,103,65,0.9); color: #fff; }
.ms-card__badge--draft { background: rgba(255,255,255,0.92); color: #8B3010; border: 1px solid rgba(72,91,56,0.3); }
.ms-card__badge-dot { width: 5px; height: 5px; border-radius: 999px; background: currentColor; }
.ms-card__overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(30,8,18,0.45); opacity: 0; transition: opacity 200ms ease; backdrop-filter: blur(2px); }
.ms-card:hover .ms-card__overlay { opacity: 1; }
.ms-card__overlay-label { color: #fff; font-size: 0.9rem; font-weight: 800; letter-spacing: 0.02em; transform: translateY(4px); transition: transform 200ms ease; }
.ms-card:hover .ms-card__overlay-label { transform: translateY(0); }
.ms-card__body { padding: 14px 16px 18px; display: flex; flex-direction: column; gap: 6px; }
.ms-card__title-link { text-decoration: none; color: inherit; }
.ms-card__title-link:hover .ms-card__title { color: var(--primary); }
.ms-card__title { margin: 0; font-size: 1rem; font-weight: 800; color: var(--text); line-height: 1.25; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ms-card__meta { display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.ms-card__lang { font-size: 0.75rem; font-weight: 700; color: var(--primary); background: rgba(192,74,8,0.08); border-radius: 6px; padding: 2px 7px; }
.ms-card__tag { font-size: 0.72rem; font-weight: 600; color: var(--text-soft); }
.ms-card__meta-sep { color: var(--border); }
.ms-card__desc { margin: 0; font-size: 0.8rem; color: var(--text-soft); line-height: 1.5; }
.ms-card__actions { display: flex; align-items: center; gap: 6px; margin-top: 8px; padding-top: 10px; border-top: 1px solid var(--border); }
.ms-card__action { display: inline-flex; align-items: center; gap: 6px; border: 1.5px solid var(--border); border-radius: 10px; padding: 6px 10px; background: transparent; color: var(--text-soft); font: inherit; font-size: 0.78rem; font-weight: 700; text-decoration: none; cursor: pointer; transition: background 140ms ease, color 140ms ease, border-color 140ms ease; }
.ms-card__action--open { flex: 1; justify-content: center; background: var(--text); color: #fff; border-color: var(--text); }
.ms-card__action--open:hover { background: var(--primary); border-color: var(--primary); }
.ms-card__action--open svg { width: 13px; height: 13px; }
.ms-card__action--read { color: var(--primary); border-color: rgba(192,74,8,0.28); background: rgba(192,74,8,0.05); flex-shrink: 0; }
.ms-card__action--read:hover { background: var(--primary); border-color: var(--primary); color: #fff; }
.ms-card__action--read svg { flex-shrink: 0; }
.ms-card__action--edit { flex-shrink: 0; }
.ms-card__action--edit svg { width: 14px; height: 14px; }
.ms-card__action--edit:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); }
.ms-card__action--delete { flex-shrink: 0; }
.ms-card__action--delete svg { width: 14px; height: 14px; }
.ms-card__action--delete:hover { border-color: #A8334C; color: #A8334C; background: rgba(168,51,76,0.06); }
.ms-noresults { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 20px; text-align: center; }
.ms-noresults__icon { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 14px; background: var(--surface-alt); border: 1px solid var(--border); color: var(--text-soft); }
.ms-noresults__icon svg { width: 22px; height: 22px; }
.ms-noresults__text { margin: 0; font-size: 0.95rem; color: var(--text-soft); }
.ms-noresults__reset { border: 1.5px solid var(--border); border-radius: 10px; padding: 8px 18px; background: #fff; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; transition: border-color 140ms ease; }
.ms-noresults__reset:hover { border-color: var(--primary); color: var(--primary); }
.ms-empty { display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 60px 20px 80px; text-align: center; }
.ms-empty__tiles { display: flex; gap: 10px; align-items: flex-end; margin-bottom: 8px; }
.ms-empty__tile { border: 2.5px solid #1E0812; border-radius: 10px; box-shadow: 3px 3px 0 #1E0812; opacity: 0.45; }
.ms-empty__eyebrow { margin: 0; font-size: 0.7rem; font-weight: 900; color: var(--text-soft); letter-spacing: 0.14em; text-transform: uppercase; }
.ms-empty__title { margin: 0; font-size: clamp(1.3rem, 3vw, 1.8rem); font-weight: 950; color: var(--text); letter-spacing: -0.02em; }
.ms-empty__sub { margin: 0; font-size: 0.9rem; color: var(--text-soft); line-height: 1.6; max-width: 460px; }
.ms-backdrop { position: fixed; inset: 0; z-index: 200; display: flex; align-items: center; justify-content: center; padding: 16px; background: rgba(30,8,18,0.55); backdrop-filter: blur(4px); }
.ms-confirm { display: flex; flex-direction: column; gap: 14px; width: min(420px, 100%); border: 3px solid #1E0812; border-radius: 18px; padding: 26px; background: #FFF0EE; box-shadow: 6px 6px 0 #1E0812; }
.ms-confirm__eyebrow { margin: 0; font-size: 0.65rem; font-weight: 900; color: #A8334C; letter-spacing: 0.14em; text-transform: uppercase; }
.ms-confirm__title { margin: 0; font-size: 1.25rem; font-weight: 950; color: #1E0812; letter-spacing: -0.02em; }
.ms-confirm__body { margin: 0; font-size: 0.88rem; color: #785068; line-height: 1.55; }
.ms-confirm__actions { display: flex; gap: 10px; }
.ms-confirm__cancel { flex: 1; border: 1.5px solid #D4E5CA; border-radius: 12px; padding: 12px; background: transparent; color: #785068; font: inherit; font-weight: 700; cursor: pointer; transition: background 140ms ease; }
.ms-confirm__cancel:hover { background: #D4E5CA; }
.ms-confirm__delete { flex: 1; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 12px; padding: 12px; background: #A8334C; color: #fff; font: inherit; font-size: 0.92rem; font-weight: 800; cursor: pointer; transition: background 160ms ease; }
.ms-confirm__delete:hover:not(:disabled) { background: #8b2940; }
.ms-confirm__delete:disabled { opacity: 0.6; cursor: not-allowed; }
.ms-edit { display: flex; flex-direction: column; gap: 16px; width: min(460px, 100%); border: 3px solid #1E0812; border-radius: 20px; padding: 24px; background: #FFF0EE; box-shadow: 6px 6px 0 #1E0812; }
.ms-edit__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ms-edit__eyebrow { margin: 0 0 3px; font-size: 0.62rem; font-weight: 900; color: #485B38; letter-spacing: 0.14em; text-transform: uppercase; }
.ms-edit__title { margin: 0; font-size: 1.1rem; font-weight: 950; color: #1E0812; letter-spacing: -0.02em; }
.ms-edit__close { display: grid; place-items: center; width: 30px; height: 30px; border: 1.5px solid #D4E5CA; border-radius: 999px; background: transparent; color: #785068; cursor: pointer; flex-shrink: 0; transition: background 140ms ease; }
.ms-edit__close:hover { background: #1E0812; color: #FFF0EE; }
.ms-edit__close svg { width: 13px; height: 13px; }
.ms-edit__field { display: flex; flex-direction: column; gap: 6px; }
.ms-edit__label { font-size: 0.65rem; font-weight: 900; color: #785068; letter-spacing: 0.1em; text-transform: uppercase; }
.ms-edit__opt { font-size: 0.62rem; font-weight: 600; color: #785068; }
.ms-edit__input, .ms-edit__textarea { border: 1.5px solid #D4E5CA; border-radius: 10px; padding: 11px 13px; background: #fff; color: #1E0812; font: inherit; font-size: 0.92rem; font-weight: 600; outline: none; transition: border-color 160ms ease; }
.ms-edit__input:focus, .ms-edit__textarea:focus { border-color: #485B38; }
.ms-edit__textarea { resize: vertical; min-height: 80px; }
.ms-edit__error { margin: 0; font-size: 0.82rem; color: #A8334C; font-weight: 600; }
.ms-edit__footer { display: flex; gap: 10px; }
.ms-edit__cancel { border: 1.5px solid #D4E5CA; border-radius: 12px; padding: 11px 20px; background: transparent; color: #785068; font: inherit; font-weight: 700; cursor: pointer; transition: background 140ms ease; }
.ms-edit__cancel:hover { background: #D4E5CA; }
.ms-edit__save { flex: 1; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 12px; padding: 11px; background: #1E0812; color: #FFF0EE; font: inherit; font-size: 0.92rem; font-weight: 800; cursor: pointer; transition: background 160ms ease; }
.ms-edit__save:hover:not(:disabled) { background: #485B38; }
.ms-edit__save:disabled { opacity: 0.55; cursor: not-allowed; }
.ms-drafts { border: 2px solid #D4E5CA; border-radius: 16px; padding: 16px; background: #FFF0EE; display: flex; flex-direction: column; gap: 12px; }
.ms-drafts-enter-active, .ms-drafts-leave-active { transition: opacity 200ms ease, transform 200ms ease; }
.ms-drafts-enter-from, .ms-drafts-leave-to { opacity: 0; transform: translateY(-8px); }
.ms-drafts__head { display: flex; align-items: center; gap: 10px; }
.ms-drafts__eyebrow { margin: 0; font-size: 0.65rem; font-weight: 900; color: #485B38; letter-spacing: 0.14em; text-transform: uppercase; }
.ms-drafts__count { font-size: 0.82rem; font-weight: 800; color: #1E0812; margin-left: auto; }
.ms-drafts__list { display: flex; flex-direction: column; gap: 8px; }
.ms-draft { display: grid; grid-template-columns: auto 1fr auto auto; align-items: center; gap: 12px; border: 1.5px solid #D4E5CA; border-radius: 12px; padding: 10px 14px; background: #fff; }
.ms-draft__wave { display: flex; align-items: center; gap: 2px; height: 24px; width: 36px; flex-shrink: 0; }
.ms-draft__wave span { flex: 1; border-radius: 2px; background: #485B38; opacity: 0.6; animation: draft-wave 1.2s ease-in-out infinite; }
.ms-draft__wave span:nth-child(odd) { animation-delay: 0s; }
.ms-draft__wave span:nth-child(even) { animation-delay: 0.3s; }
@keyframes draft-wave { 0%, 100% { height: 20%; opacity: 0.4; } 50% { height: 90%; opacity: 0.7; } }
.ms-draft__info { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.ms-draft__title { font-size: 0.85rem; font-weight: 800; color: #1E0812; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ms-draft__date { font-size: 0.7rem; color: #785068; }
.ms-draft__audio { height: 28px; width: 140px; flex-shrink: 0; border-radius: 8px; }
.ms-draft__actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.ms-draft__open { font-size: 0.78rem; font-weight: 800; color: #485B38; text-decoration: none; white-space: nowrap; transition: color 140ms ease; }
.ms-draft__open:hover { color: #8B3010; }
.ms-draft__del { display: grid; place-items: center; width: 24px; height: 24px; border: 1.5px solid #D4E5CA; border-radius: 8px; background: transparent; color: #785068; font-size: 16px; cursor: pointer; transition: background 140ms ease, color 140ms ease; }
.ms-draft__del:hover { background: rgba(168,51,76,0.1); color: #A8334C; border-color: #A8334C; }
.ms-spin { width: 13px; height: 13px; border: 2px solid rgba(255,244,236,0.3); border-top-color: #FFF0EE; border-radius: 999px; animation: ms-spin 0.7s linear infinite; display: inline-block; }
@keyframes ms-spin { to { transform: rotate(360deg); } }
@media (max-width: 640px) {
  .ms-root { padding: 20px 14px 60px; gap: 20px; }
  .ms-hero { flex-direction: column; align-items: flex-start; gap: 14px; }
  .ms-bar { flex-direction: column; align-items: stretch; }
  .ms-tabs { justify-content: stretch; }
  .ms-tab { flex: 1; justify-content: center; }
  .ms-grid { grid-template-columns: 1fr; }
  .ms-draft { grid-template-columns: auto 1fr; grid-template-rows: auto auto; gap: 8px; }
  .ms-draft__audio { width: 100%; grid-column: 1 / -1; }
  .ms-draft__actions { grid-column: 1 / -1; justify-content: space-between; }
}
.ms-card__like-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--primary);
  flex-shrink: 0;
}

.ms-card__action--discussion {
  padding: 6px 9px;
  flex-shrink: 0;
}
.ms-card__action--discussion:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(192, 74, 8, 0.05);
}
</style>