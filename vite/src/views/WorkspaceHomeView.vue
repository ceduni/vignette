<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {RouterLink, useRouter} from "vue-router";
import {deleteScenario, fetchMyScenarios, fetchScenarioThumbnails, updateScenarioMetadata} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useDebouncedRef} from "../composables/useDebouncedRef";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";

const router = useRouter();

const scenarios = ref([]);
const previewMap = ref({});
const error = ref("");
const loading = ref(false);
const {source: search, debounced} = useDebouncedRef("", 250);
const effectiveSearch = ref("");
watch(debounced, v => { effectiveSearch.value = v.trim().toLowerCase(); });

const drafts_scenarios = computed(() => scenarios.value.filter(s => s.visibilityStatus !== "PUBLISHED"));
const published_scenarios = computed(() => scenarios.value.filter(s => s.visibilityStatus === "PUBLISHED"));

function matchesSearch(s) {
  const q = effectiveSearch.value;
  if (!q) return true;
  return [s.title ?? "", String(s.languageId ?? ""), s.description ?? "", ...(s.tags ?? []).map(String)]
    .some(v => v.toLowerCase().includes(q));
}
const filtered_drafts = computed(() => drafts_scenarios.value.filter(matchesSearch));
const filtered_published = computed(() => published_scenarios.value.filter(matchesSearch));

function thumbnailUrl(id) {
  const thumbId = previewMap.value[id];
  return thumbId ? buildApiUrl(`/api/thumbnails/${thumbId}/content`) : null;
}

const TILE_GRADIENTS = [
  "linear-gradient(135deg,#D4E5CA,#c5d9b8)",
  "linear-gradient(135deg,#FFF0EE,#f5d4ce)",
  "linear-gradient(135deg,#c5d9b8,#afc8a0)",
  "linear-gradient(135deg,#e8d4f5,#d4bde8)",
  "linear-gradient(135deg,#A8C498,#8fb87f)",
];
function placeholderGradient(index) { return TILE_GRADIENTS[index % TILE_GRADIENTS.length]; }

// ── delete ──
const confirmDelete = ref(null);
const deleting = ref(false);
function openDelete(s) { confirmDelete.value = s; }
function cancelDelete() { confirmDelete.value = null; }
async function doDelete() {
  if (!confirmDelete.value) return;
  deleting.value = true;
  try {
    await deleteScenario(confirmDelete.value.id);
    scenarios.value = scenarios.value.filter(s => s.id !== confirmDelete.value.id);
    confirmDelete.value = null;
  } catch { confirmDelete.value = null; }
  finally { deleting.value = false; }
}

// ── edit ──
const editTarget = ref(null);
const editForm = ref({ title: "", description: "", tags: [] });
const editSaving = ref(false);
const editError = ref("");
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
  } catch (e) { editError.value = e.message || "Failed to save."; }
  finally { editSaving.value = false; }
}

// ── emergency audios ──
const DRAFT_KEY = "vignette:unclaimed-draft-audios";
const emergencyDrafts = ref([]);
function loadEmergencyDrafts() {
  try { emergencyDrafts.value = JSON.parse(localStorage.getItem(DRAFT_KEY) || "[]"); }
  catch { emergencyDrafts.value = []; }
}
function removeEmergencyDraft(id) {
  emergencyDrafts.value = emergencyDrafts.value.filter(d => d.id !== id);
  localStorage.setItem(DRAFT_KEY, JSON.stringify(emergencyDrafts.value));
}

// ── attach picker ──
const attachDraft = ref(null);
function openAttachPicker(draft) { attachDraft.value = draft; }
function closeAttachPicker() { attachDraft.value = null; }
function attachTo(scenarioId) {
  if (!attachDraft.value) return;
  router.push(`/scenarios/${scenarioId}?draftAudio=${attachDraft.value.id}`);
  closeAttachPicker();
}

// ── load ──
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await fetchMyScenarios();
    scenarios.value = Array.isArray(data) ? data : (data.content ?? []);
    const map = {};
    await Promise.all(scenarios.value.map(async s => {
      try { const thumbs = await fetchScenarioThumbnails(s.id); map[s.id] = thumbs?.[0]?.id ?? null; }
      catch { map[s.id] = null; }
    }));
    previewMap.value = map;
  } catch (e) { error.value = e.message; }
  finally { loading.value = false; }
}

onMounted(() => { load(); loadEmergencyDrafts(); });
const { openReader, activeScenario, closeReader } = useScenarioReader();
</script>

<template>
  <main class="ws">

    <!-- ── HERO ── -->
    <div class="ws-hero">
      <div class="ws-hero__left">
        <p class="ws-eyebrow">Your creative space</p>
        <h1 class="ws-title">My Scenarios</h1>
        <div class="ws-stats">
          <div class="ws-stat">
            <span class="ws-stat__num">{{ scenarios.length }}</span>
            <span class="ws-stat__lbl">total</span>
          </div>
          <div class="ws-stat-sep"/>
          <div class="ws-stat">
            <span class="ws-stat__num ws-stat__num--draft">{{ drafts_scenarios.length }}</span>
            <span class="ws-stat__lbl">brouillons</span>
          </div>
          <div class="ws-stat-sep"/>
          <div class="ws-stat">
            <span class="ws-stat__num ws-stat__num--pub">{{ published_scenarios.length }}</span>
            <span class="ws-stat__lbl">published</span>
          </div>
          <template v-if="emergencyDrafts.length">
            <div class="ws-stat-sep"/>
            <div class="ws-stat">
              <span class="ws-stat__num ws-stat__num--em">{{ emergencyDrafts.length }}</span>
              <span class="ws-stat__lbl">emergency</span>
            </div>
          </template>
        </div>
      </div>
      <RouterLink to="/create-scenario" class="ws-cta">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
        New scenario
      </RouterLink>
    </div>

    <!-- ── SEARCH ── -->
    <div class="ws-search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>
      <input v-model="search" placeholder="Search your scenarios…" class="ws-search__input"/>
      <button v-if="search" type="button" class="ws-search__clear" @click="search = ''">×</button>
    </div>

    <BaseLoader v-if="loading">Loading your scenarios…</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else>

      <!-- ── EMERGENCY AUDIOS ── -->
      <Transition name="ws-slide">
        <section v-if="emergencyDrafts.length" class="ws-block ws-block--em">
          <div class="ws-block__head">
            <div class="ws-block__icon ws-block__icon--em">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            </div>
            <div>
              <h2 class="ws-block__title">Emergency audios</h2>
              <p class="ws-block__sub">Recordings saved on the fly — attach them to a scenario</p>
            </div>
            <span class="ws-pill ws-pill--em">{{ emergencyDrafts.length }}</span>
          </div>

          <div class="ws-em-list">
            <div v-for="draft in emergencyDrafts" :key="draft.id" class="ws-em-row">
              <div class="ws-em-wave" aria-hidden="true">
                <span v-for="i in 10" :key="i"></span>
              </div>
              <div class="ws-em-info">
                <strong class="ws-em-name">{{ draft.title || "Untitled recording" }}</strong>
                <small class="ws-em-date">{{ new Date(draft.createdAt).toLocaleDateString('fr-CA', {year:'numeric',month:'short',day:'numeric'}) }}</small>
              </div>
              <audio :src="draft.dataUrl" controls class="ws-em-audio"></audio>
              <div class="ws-em-actions">
                <button type="button" class="ws-em-attach" @click="openAttachPicker(draft)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>
                  Attach to scenario
                </button>
                <button type="button" class="ws-em-del" title="Delete recording" @click="removeEmergencyDraft(draft.id)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                </button>
              </div>
            </div>
          </div>
        </section>
      </Transition>

      <!-- ── BROUILLONS ── -->
      <section class="ws-block">
        <div class="ws-block__head">
          <div class="ws-block__icon ws-block__icon--draft">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          </div>
          <div>
            <h2 class="ws-block__title">Brouillons</h2>
            <p class="ws-block__sub">En cours de création — pas encore publiés</p>
          </div>
          <span class="ws-pill ws-pill--draft">{{ drafts_scenarios.length }}</span>
        </div>

        <div v-if="filtered_drafts.length" class="ws-grid">
          <article v-for="(s, i) in filtered_drafts" :key="s.id" class="ws-card ws-card--draft">
            <RouterLink :to="`/scenarios/${s.id}`" class="ws-card__cover" tabindex="-1">
              <img v-if="thumbnailUrl(s.id)" :src="thumbnailUrl(s.id)" :alt="s.title" class="ws-card__img"/>
              <div v-else class="ws-card__placeholder" :style="{background: placeholderGradient(i)}">
                <span class="ws-card__num">{{ String(i+1).padStart(2,'0') }}</span>
              </div>
              <span class="ws-card__status ws-card__status--draft">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" width="9" height="9"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z"/></svg>
                Brouillon
              </span>
              <div class="ws-card__hover">
                <span class="ws-card__hover-label">Ouvrir le studio →</span>
              </div>
            </RouterLink>
            <div class="ws-card__body">
              <RouterLink :to="`/scenarios/${s.id}`" class="ws-card__name-link">
                <h3 class="ws-card__name">{{ s.title || "Scénario sans titre" }}</h3>
              </RouterLink>
              <div class="ws-card__meta">
                <span v-if="s.languageId" class="ws-card__lang">{{ s.languageId }}</span>
                <span v-for="tag in (s.tags ?? []).slice(0,2)" :key="tag" class="ws-card__tag">#{{ tag }}</span>
              </div>
              <p v-if="s.description?.trim()" class="ws-card__desc">{{ s.description.trim().slice(0,80) }}{{ s.description.trim().length > 80 ? '…' : '' }}</p>
              <div class="ws-card__actions">
                <RouterLink :to="`/scenarios/${s.id}`" class="ws-card__btn ws-card__btn--primary">
                  Studio
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" width="12" height="12"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </RouterLink>
                <button type="button" class="ws-card__btn ws-card__btn--icon" title="Modifier" @click.stop="openEdit(s)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z"/></svg>
                </button>
                <button type="button" class="ws-card__btn ws-card__btn--icon ws-card__btn--del" title="Supprimer" @click.stop="openDelete(s)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else-if="search" class="ws-empty-filter">Aucun brouillon ne correspond à "<strong>{{ search }}</strong>"</div>
        <div v-else class="ws-empty-block">
          <div class="ws-empty-block__tiles">
            <div class="ws-empty-block__tile" style="background:#D4E5CA;height:70px;width:80px;"></div>
            <div class="ws-empty-block__tile" style="background:#FFF0EE;height:90px;width:65px;"></div>
            <div class="ws-empty-block__tile" style="background:#c5d9b8;height:60px;width:75px;"></div>
          </div>
          <p class="ws-empty-block__msg">Aucun brouillon pour l'instant.</p>
          <RouterLink to="/create-scenario" class="ws-empty-block__cta">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M12 5v14M5 12h14"/></svg>
            Créer un scénario
          </RouterLink>
        </div>
      </section>

      <!-- ── PUBLISHED ── -->
      <section class="ws-block">
        <div class="ws-block__head">
          <div class="ws-block__icon ws-block__icon--pub">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          </div>
          <div>
            <h2 class="ws-block__title">Published</h2>
            <p class="ws-block__sub">Visible to the community</p>
          </div>
          <span class="ws-pill ws-pill--pub">{{ published_scenarios.length }}</span>
        </div>

        <div v-if="filtered_published.length" class="ws-grid">
          <article v-for="(s, i) in filtered_published" :key="s.id" class="ws-card ws-card--pub">
            <RouterLink :to="`/scenarios/${s.id}`" class="ws-card__cover" tabindex="-1">
              <img v-if="thumbnailUrl(s.id)" :src="thumbnailUrl(s.id)" :alt="s.title" class="ws-card__img"/>
              <div v-else class="ws-card__placeholder" :style="{background: placeholderGradient(i)}">
                <span class="ws-card__num">{{ String(i+1).padStart(2,'0') }}</span>
              </div>
              <span class="ws-card__status ws-card__status--pub">
                <span class="ws-card__status-dot"></span>
                Published
              </span>
              <div class="ws-card__hover">
                <span class="ws-card__hover-label">Open studio →</span>
              </div>
            </RouterLink>
            <div class="ws-card__body">
              <RouterLink :to="`/scenarios/${s.id}`" class="ws-card__name-link">
                <h3 class="ws-card__name">{{ s.title || "Untitled scenario" }}</h3>
              </RouterLink>
              <div class="ws-card__meta">
                <span v-if="s.languageId" class="ws-card__lang">{{ s.languageId }}</span>
                <span v-for="tag in (s.tags ?? []).slice(0,2)" :key="tag" class="ws-card__tag">#{{ tag }}</span>
              </div>
              <p v-if="s.description?.trim()" class="ws-card__desc">{{ s.description.trim().slice(0,80) }}{{ s.description.trim().length > 80 ? '…' : '' }}</p>
              <div class="ws-card__actions">
                <RouterLink :to="`/scenarios/${s.id}`" class="ws-card__btn ws-card__btn--primary">
                  Studio
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" width="12" height="12"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </RouterLink>
                <button type="button" class="ws-card__btn ws-card__btn--read" title="Read" @click.stop="openReader(s)">
                  <svg viewBox="0 0 24 24" fill="currentColor" stroke="none" width="12" height="12"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                  Read
                </button>
                <button type="button" class="ws-card__btn ws-card__btn--icon" title="Edit" @click.stop="openEdit(s)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z"/></svg>
                </button>
                <button type="button" class="ws-card__btn ws-card__btn--icon ws-card__btn--del" title="Delete" @click.stop="openDelete(s)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                </button>
              </div>
            </div>
          </article>
        </div>

        <div v-else-if="search" class="ws-empty-filter">No published scenario matches "<strong>{{ search }}</strong>"</div>
        <div v-else class="ws-empty-block ws-empty-block--inline">
          <p class="ws-empty-block__msg">No published scenarios yet.</p>
          <p class="ws-empty-block__hint">Open a brouillon in the studio and hit Publish when it's ready.</p>
        </div>
      </section>

    </template>

    <!-- ── ATTACH PICKER MODAL ── -->
    <Teleport to="body">
      <div v-if="attachDraft" class="ws-modal-bg" @click.self="closeAttachPicker">
        <div class="ws-modal ws-modal--attach">
          <div class="ws-modal__head">
            <div>
              <p class="ws-modal__eyebrow">Emergency audio</p>
              <h2 class="ws-modal__title">Attach "{{ attachDraft.title || 'Untitled recording' }}"</h2>
              <p class="ws-modal__sub">Choose the scenario to attach this recording to</p>
            </div>
            <button type="button" class="ws-modal__close" @click="closeAttachPicker">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M18 6 6 18M6 6l12 12"/></svg>
            </button>
          </div>
          <div v-if="scenarios.length" class="ws-attach-list">
            <button v-for="s in scenarios" :key="s.id" type="button" class="ws-attach-row" @click="attachTo(s.id)">
              <div class="ws-attach-thumb">
                <img v-if="thumbnailUrl(s.id)" :src="thumbnailUrl(s.id)" :alt="s.title" class="ws-attach-thumb__img"/>
                <div v-else class="ws-attach-thumb__fallback">{{ (s.title || '?')[0] }}</div>
              </div>
              <div class="ws-attach-info">
                <strong class="ws-attach-name">{{ s.title || "Untitled" }}</strong>
                <span class="ws-attach-status" :class="s.visibilityStatus === 'PUBLISHED' ? 'ws-attach-status--pub' : 'ws-attach-status--draft'">
                  {{ s.visibilityStatus === 'PUBLISHED' ? 'Published' : 'Brouillon' }}
                </span>
              </div>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16" class="ws-attach-arrow"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </button>
          </div>
          <div v-else class="ws-attach-empty">
            You have no scenarios yet. <RouterLink to="/create-scenario" @click="closeAttachPicker">Create one first →</RouterLink>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── DELETE CONFIRM ── -->
    <Teleport to="body">
      <div v-if="confirmDelete" class="ws-modal-bg" @click.self="cancelDelete">
        <div class="ws-modal ws-modal--confirm">
          <p class="ws-modal__eyebrow ws-modal__eyebrow--danger">Action permanente</p>
          <h2 class="ws-modal__title">Supprimer "{{ confirmDelete.title || 'Sans titre' }}" ?</h2>
          <p class="ws-modal__body">Toutes les scènes, enregistrements audio et annotations seront supprimés définitivement.</p>
          <div class="ws-modal__footer">
            <button type="button" class="ws-btn-ghost" @click="cancelDelete">Conserver</button>
            <button type="button" class="ws-btn-danger" :disabled="deleting" @click="doDelete">
              <span v-if="deleting" class="ws-spin"></span>
              {{ deleting ? 'Suppression…' : 'Oui, supprimer' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── EDIT METADATA ── -->
    <Teleport to="body">
      <div v-if="editTarget" class="ws-modal-bg" @click.self="cancelEdit">
        <div class="ws-modal ws-modal--edit">
          <div class="ws-modal__head">
            <div>
              <p class="ws-modal__eyebrow">Modifier le scénario</p>
              <h2 class="ws-modal__title">{{ editTarget.title || "Sans titre" }}</h2>
            </div>
            <button type="button" class="ws-modal__close" @click="cancelEdit">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M18 6 6 18M6 6l12 12"/></svg>
            </button>
          </div>
          <div class="ws-field">
            <label class="ws-field__label">Titre</label>
            <input v-model="editForm.title" class="ws-field__input" placeholder="Titre du scénario"/>
          </div>
          <div class="ws-field">
            <label class="ws-field__label">Description <span class="ws-field__opt">optionnel</span></label>
            <textarea v-model="editForm.description" class="ws-field__textarea" rows="3" placeholder="Contexte de la scène…"/>
          </div>
          <p v-if="editError" class="ws-field__error">{{ editError }}</p>
          <div class="ws-modal__footer">
            <button type="button" class="ws-btn-ghost" @click="cancelEdit">Annuler</button>
            <button type="button" class="ws-btn-primary" :disabled="editSaving || !editForm.title.trim()" @click="saveEdit">
              <span v-if="editSaving" class="ws-spin"></span>
              {{ editSaving ? 'Sauvegarde…' : 'Sauvegarder' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
  </main>
</template>

<style scoped>
/* ── root ── */
.ws {
  max-width: 1180px;
  margin: 0 auto;
  padding: 40px 24px 100px;
  display: flex;
  flex-direction: column;
  gap: 48px;
}

/* ── hero ── */
.ws-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.ws-eyebrow {
  margin: 0 0 6px;
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--primary);
}
.ws-title {
  margin: 0 0 16px;
  font-size: clamp(2rem, 5vw, 3rem);
  font-weight: 950;
  letter-spacing: -0.03em;
  color: var(--text);
  line-height: 1.0;
}
.ws-stats {
  display: flex;
  align-items: center;
  gap: 14px;
}
.ws-stat { display: flex; flex-direction: column; gap: 1px; }
.ws-stat__num { font-size: 1.4rem; font-weight: 900; color: var(--text); line-height: 1; }
.ws-stat__num--draft { color: #785068; }
.ws-stat__num--pub { color: #4A6741; }
.ws-stat__num--em { color: #A8334C; }
.ws-stat__lbl { font-size: 0.7rem; font-weight: 700; color: var(--text-soft); text-transform: uppercase; letter-spacing: 0.06em; }
.ws-stat-sep { width: 1px; height: 32px; background: var(--border); }
.ws-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 0 24px;
  border-radius: 16px;
  background: var(--text);
  color: #fff;
  font-size: 0.92rem;
  font-weight: 800;
  text-decoration: none;
  white-space: nowrap;
  box-shadow: 0 4px 16px rgba(30,8,18,0.18);
  transition: background 160ms ease, transform 130ms ease, box-shadow 160ms ease;
}
.ws-cta:hover { background: var(--primary); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(192,74,8,0.28); }
.ws-cta svg { width: 15px; height: 15px; }

/* ── search ── */
.ws-search {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1.5px solid var(--border);
  border-radius: 14px;
  padding: 0 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(30,8,18,0.04);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}
.ws-search:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(192,74,8,0.08); }
.ws-search svg { width: 16px; height: 16px; flex-shrink: 0; color: var(--text-soft); }
.ws-search__input { flex: 1; border: 0; outline: none; padding: 13px 0; font: inherit; font-size: 0.92rem; color: var(--text); background: transparent; }
.ws-search__input::placeholder { color: var(--text-soft); }
.ws-search__clear { border: 0; background: transparent; color: var(--text-soft); cursor: pointer; font-size: 20px; line-height: 1; padding: 0; }

/* ── block ── */
.ws-block {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.ws-block--em {
  border: 2px solid rgba(168,51,76,0.2);
  border-radius: 20px;
  padding: 22px;
  background: linear-gradient(135deg, rgba(255,240,238,0.7), rgba(255,248,246,0.4));
}
.ws-block__head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.ws-block__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  flex-shrink: 0;
}
.ws-block__icon svg { width: 20px; height: 20px; }
.ws-block__icon--draft { background: rgba(120,80,104,0.1); color: #785068; }
.ws-block__icon--pub { background: rgba(74,103,65,0.1); color: #4A6741; }
.ws-block__icon--em { background: rgba(168,51,76,0.1); color: #A8334C; }
.ws-block__title { margin: 0 0 2px; font-size: 1.2rem; font-weight: 900; color: var(--text); letter-spacing: -0.015em; }
.ws-block__sub { margin: 0; font-size: 0.8rem; color: var(--text-soft); }
.ws-pill {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 26px;
  border-radius: 999px;
  padding: 0 10px;
  font-size: 0.78rem;
  font-weight: 900;
  flex-shrink: 0;
}
.ws-pill--draft { background: rgba(120,80,104,0.12); color: #785068; }
.ws-pill--pub { background: rgba(74,103,65,0.12); color: #4A6741; }
.ws-pill--em { background: rgba(168,51,76,0.12); color: #A8334C; }

/* ── emergency list ── */
.ws-em-list { display: flex; flex-direction: column; gap: 10px; }
.ws-em-row {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1.5px solid rgba(168,51,76,0.15);
  border-radius: 14px;
  padding: 14px 18px;
  transition: box-shadow 160ms ease;
}
.ws-em-row:hover { box-shadow: 0 4px 16px rgba(168,51,76,0.1); }
.ws-em-wave { display: flex; align-items: center; gap: 2px; height: 30px; width: 40px; flex-shrink: 0; }
.ws-em-wave span { flex: 1; border-radius: 3px; background: #A8334C; opacity: 0.45; animation: em-wave 1.4s ease-in-out infinite; }
.ws-em-wave span:nth-child(odd) { animation-delay: 0s; }
.ws-em-wave span:nth-child(even) { animation-delay: 0.4s; }
@keyframes em-wave { 0%,100% { height: 20%; opacity: 0.3; } 50% { height: 90%; opacity: 0.65; } }
.ws-em-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.ws-em-name { font-size: 0.9rem; font-weight: 800; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ws-em-date { font-size: 0.72rem; color: var(--text-soft); }
.ws-em-audio { height: 28px; width: 180px; flex-shrink: 0; border-radius: 8px; }
.ws-em-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.ws-em-attach {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1.5px solid rgba(168,51,76,0.35);
  border-radius: 10px;
  padding: 7px 14px;
  background: rgba(168,51,76,0.06);
  color: #A8334C;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
  transition: background 140ms ease, border-color 140ms ease;
}
.ws-em-attach:hover { background: #A8334C; color: #fff; border-color: #A8334C; }
.ws-em-del {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1.5px solid var(--border);
  border-radius: 9px;
  background: transparent;
  color: var(--text-soft);
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
  flex-shrink: 0;
}
.ws-em-del:hover { background: rgba(168,51,76,0.08); color: #A8334C; border-color: rgba(168,51,76,0.4); }

/* ── scenario grid ── */
.ws-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 20px;
  align-items: start;
}
.ws-card {
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  border: 1.5px solid var(--border);
  box-shadow: 0 2px 10px rgba(30,8,18,0.06);
  transition: transform 200ms ease, box-shadow 220ms ease, border-color 200ms ease;
}
.ws-card:hover { transform: translateY(-5px); box-shadow: 0 18px 44px rgba(30,8,18,0.12); border-color: transparent; }
.ws-card--draft { border-top: 3px solid #785068; }
.ws-card--pub { border-top: 3px solid #4A6741; }
.ws-card__cover { position: relative; aspect-ratio: 4/3; overflow: hidden; background: var(--surface-alt); display: block; text-decoration: none; }
.ws-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 320ms ease; }
.ws-card:hover .ws-card__img { transform: scale(1.05); }
.ws-card__placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.ws-card__num { font-size: 2.8rem; font-weight: 950; color: rgba(30,8,18,0.13); letter-spacing: -0.05em; }
.ws-card__status {
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 4px 11px;
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  backdrop-filter: blur(10px);
}
.ws-card__status--draft { background: rgba(255,255,255,0.94); color: #785068; border: 1px solid rgba(120,80,104,0.2); }
.ws-card__status--pub { background: rgba(74,103,65,0.92); color: #fff; }
.ws-card__status-dot { width: 5px; height: 5px; border-radius: 999px; background: currentColor; }
.ws-card__hover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30,8,18,0.44);
  opacity: 0;
  transition: opacity 200ms ease;
  backdrop-filter: blur(3px);
}
.ws-card:hover .ws-card__hover { opacity: 1; }
.ws-card__hover-label { color: #fff; font-size: 0.9rem; font-weight: 800; transform: translateY(5px); transition: transform 200ms ease; }
.ws-card:hover .ws-card__hover-label { transform: translateY(0); }
.ws-card__body { padding: 14px 16px 18px; display: flex; flex-direction: column; gap: 6px; }
.ws-card__name-link { text-decoration: none; color: inherit; }
.ws-card__name-link:hover .ws-card__name { color: var(--primary); }
.ws-card__name { margin: 0; font-size: 1rem; font-weight: 800; color: var(--text); line-height: 1.2; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ws-card__meta { display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.ws-card__lang { font-size: 0.72rem; font-weight: 700; color: var(--primary); background: rgba(192,74,8,0.08); border-radius: 6px; padding: 2px 7px; }
.ws-card__tag { font-size: 0.7rem; font-weight: 600; color: var(--text-soft); }
.ws-card__desc { margin: 0; font-size: 0.8rem; color: var(--text-soft); line-height: 1.5; }
.ws-card__actions { display: flex; gap: 6px; margin-top: 8px; padding-top: 10px; border-top: 1px solid var(--border); }
.ws-card__btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1.5px solid var(--border);
  border-radius: 10px;
  padding: 7px 11px;
  background: transparent;
  color: var(--text-soft);
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
}
.ws-card__btn--primary { flex: 1; justify-content: center; background: var(--text); color: #fff; border-color: var(--text); }
.ws-card__btn--primary:hover { background: var(--primary); border-color: var(--primary); }
.ws-card__btn--read { color: var(--primary); border-color: rgba(192,74,8,0.25); background: rgba(192,74,8,0.05); }
.ws-card__btn--read:hover { background: var(--primary); border-color: var(--primary); color: #fff; }
.ws-card__btn--icon { padding: 7px; }
.ws-card__btn--icon:hover { border-color: var(--primary); color: var(--primary); background: rgba(192,74,8,0.05); }
.ws-card__btn--del:hover { border-color: #A8334C; color: #A8334C; background: rgba(168,51,76,0.06); }

/* ── empty states ── */
.ws-empty-filter { font-size: 0.88rem; color: var(--text-soft); padding: 8px 0; }
.ws-empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 48px 24px;
  border: 2px dashed var(--border);
  border-radius: 18px;
  text-align: center;
}
.ws-empty-block__tiles { display: flex; gap: 8px; align-items: flex-end; }
.ws-empty-block__tile { border: 2px solid rgba(30,8,18,0.15); border-radius: 8px; box-shadow: 2px 2px 0 rgba(30,8,18,0.1); opacity: 0.5; }
.ws-empty-block__msg { margin: 0; font-size: 0.95rem; font-weight: 700; color: var(--text-soft); }
.ws-empty-block__hint { margin: -6px 0 0; font-size: 0.82rem; color: var(--text-soft); opacity: 0.7; }
.ws-empty-block__cta {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1.5px solid var(--border);
  border-radius: 11px;
  padding: 9px 18px;
  background: #fff;
  color: var(--text);
  font: inherit;
  font-size: 0.85rem;
  font-weight: 800;
  text-decoration: none;
  transition: border-color 140ms ease, color 140ms ease;
}
.ws-empty-block__cta:hover { border-color: var(--primary); color: var(--primary); }
.ws-empty-block--inline { flex-direction: column; align-items: flex-start; text-align: left; padding: 24px 28px; }

/* ── modals ── */
.ws-modal-bg {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(30,8,18,0.55);
  backdrop-filter: blur(5px);
}
.ws-modal {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: min(480px, 100%);
  border: 2.5px solid #1E0812;
  border-radius: 22px;
  padding: 26px;
  background: #FFF8F6;
  box-shadow: 8px 8px 0 #1E0812;
  max-height: 85vh;
  overflow-y: auto;
}
.ws-modal--attach { gap: 14px; }
.ws-modal__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ws-modal__eyebrow { margin: 0 0 3px; font-size: 0.62rem; font-weight: 900; letter-spacing: 0.14em; text-transform: uppercase; color: #485B38; }
.ws-modal__eyebrow--danger { color: #A8334C; }
.ws-modal__title { margin: 0 0 2px; font-size: 1.15rem; font-weight: 950; color: #1E0812; letter-spacing: -0.02em; }
.ws-modal__sub { margin: 0; font-size: 0.8rem; color: #785068; }
.ws-modal__body { margin: 0; font-size: 0.9rem; color: #785068; line-height: 1.6; }
.ws-modal__close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 140ms ease;
}
.ws-modal__close:hover { background: #1E0812; color: #FFF0EE; border-color: #1E0812; }
.ws-modal__footer { display: flex; gap: 10px; }

/* attach list */
.ws-attach-list { display: flex; flex-direction: column; gap: 6px; }
.ws-attach-row {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1.5px solid #D4E5CA;
  border-radius: 13px;
  padding: 10px 14px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: border-color 140ms ease, background 140ms ease, transform 120ms ease;
}
.ws-attach-row:hover { border-color: var(--primary); background: rgba(192,74,8,0.03); transform: translateX(3px); }
.ws-attach-thumb { width: 44px; height: 34px; border-radius: 7px; overflow: hidden; background: var(--surface-alt); flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.ws-attach-thumb__img { width: 100%; height: 100%; object-fit: cover; }
.ws-attach-thumb__fallback { font-size: 1.1rem; font-weight: 900; color: rgba(30,8,18,0.2); }
.ws-attach-info { flex: 1; display: flex; align-items: center; gap: 10px; min-width: 0; }
.ws-attach-name { font-size: 0.9rem; font-weight: 800; color: #1E0812; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ws-attach-status { font-size: 0.68rem; font-weight: 800; border-radius: 999px; padding: 2px 8px; flex-shrink: 0; }
.ws-attach-status--draft { background: rgba(120,80,104,0.1); color: #785068; }
.ws-attach-status--pub { background: rgba(74,103,65,0.1); color: #4A6741; }
.ws-attach-arrow { color: var(--text-soft); flex-shrink: 0; transition: color 140ms ease; }
.ws-attach-row:hover .ws-attach-arrow { color: var(--primary); }
.ws-attach-empty { font-size: 0.88rem; color: #785068; }
.ws-attach-empty a { color: var(--primary); font-weight: 700; }

/* shared buttons */
.ws-btn-ghost { border: 1.5px solid #D4E5CA; border-radius: 12px; padding: 11px 20px; background: transparent; color: #785068; font: inherit; font-weight: 700; cursor: pointer; transition: background 140ms ease; }
.ws-btn-ghost:hover { background: #D4E5CA; }
.ws-btn-primary { flex: 1; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 12px; padding: 11px; background: #1E0812; color: #FFF0EE; font: inherit; font-size: 0.92rem; font-weight: 800; cursor: pointer; transition: background 160ms ease; }
.ws-btn-primary:hover:not(:disabled) { background: #485B38; }
.ws-btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
.ws-btn-danger { flex: 1; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 12px; padding: 11px; background: #A8334C; color: #fff; font: inherit; font-size: 0.92rem; font-weight: 800; cursor: pointer; transition: background 160ms ease; }
.ws-btn-danger:hover:not(:disabled) { background: #8b2940; }
.ws-btn-danger:disabled { opacity: 0.6; cursor: not-allowed; }

/* field */
.ws-field { display: flex; flex-direction: column; gap: 6px; }
.ws-field__label { font-size: 0.65rem; font-weight: 900; color: #785068; letter-spacing: 0.1em; text-transform: uppercase; }
.ws-field__opt { font-size: 0.62rem; font-weight: 600; }
.ws-field__input, .ws-field__textarea { border: 1.5px solid #D4E5CA; border-radius: 10px; padding: 11px 13px; background: #fff; color: #1E0812; font: inherit; font-size: 0.92rem; font-weight: 600; outline: none; transition: border-color 160ms ease; }
.ws-field__input:focus, .ws-field__textarea:focus { border-color: #485B38; }
.ws-field__textarea { resize: vertical; min-height: 80px; }
.ws-field__error { margin: 0; font-size: 0.82rem; color: #A8334C; font-weight: 600; }

/* transitions */
.ws-slide-enter-active, .ws-slide-leave-active { transition: opacity 220ms ease, transform 220ms ease; }
.ws-slide-enter-from, .ws-slide-leave-to { opacity: 0; transform: translateY(-10px); }

/* spinner */
.ws-spin { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,0.25); border-top-color: #fff; border-radius: 999px; animation: ws-spin 0.65s linear infinite; display: inline-block; flex-shrink: 0; }
@keyframes ws-spin { to { transform: rotate(360deg); } }

/* responsive */
@media (max-width: 640px) {
  .ws { padding: 24px 14px 80px; gap: 36px; }
  .ws-hero { flex-direction: column; align-items: flex-start; gap: 16px; }
  .ws-grid { grid-template-columns: 1fr; }
  .ws-em-row { grid-template-columns: auto 1fr; grid-template-rows: auto auto; gap: 10px; }
  .ws-em-audio { width: 100%; grid-column: 1/-1; }
  .ws-em-actions { grid-column: 1/-1; justify-content: space-between; }
}
</style>
