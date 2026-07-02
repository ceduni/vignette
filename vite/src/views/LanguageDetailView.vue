<script setup>
import {computed, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {fetchLanguage, fetchLanguageScenarios, fetchMyLanguagePermissions, updateLanguage} from "../api/languages";
import {fetchDiscussionMessages} from "../api/community";
import {fetchScenarioThumbnails} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useAuth} from "../composables/useAuth";
import {useToast} from "../composables/useToast";
import {useLanguageFollows} from "../composables/useLanguageFollows";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import {useScenarioReader} from "../composables/useScenarioReader";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";
import BaseBadge from "../components/ui/BaseBadge.vue";
import DiscussionThread from "../components/community/DiscussionThread.vue";

const props = defineProps({
  id: {type: String, required: true},
});

const {loadMe, isAuthenticated} = useAuth();
const toast = useToast();
const { openReader, activeScenario, closeReader } = useScenarioReader();

const language = ref(null);
const scenarios = ref([]);
const permissions = ref({canEdit: false});
const messages = ref([]);
const error = ref("");
const loading = ref(false);
const saving = ref(false);
const saveError = ref("");
const saveSuccess = ref("");
const activeTab = ref("overview");

// --- Suivre ---
const { isFollowing: isFollowingFn, toggleFollow: toggleFollowFn, followedIdsArray } = useLanguageFollows();

// ref local synchronisé — garantit la réactivité dans ce composant
const isFollowing = ref(isFollowingFn(props.id));

// Re-sync quand followedIdsArray change (ex: toggle depuis un autre composant)
watch(followedIdsArray, () => {
  isFollowing.value = isFollowingFn(props.id);
}, { deep: false });

function loadFollowState(id) {
  isFollowing.value = isFollowingFn(id);
}

async function toggleFollow() {
  if (!isAuthenticated.value) return;
  const wasFollowing = isFollowing.value;
  await toggleFollowFn(props.id, language.value?.name ?? null);
  // Sync local après le toggle
  isFollowing.value = isFollowingFn(props.id);
  toast.success(!wasFollowing
    ? `Following ${language.value?.name ?? "this language"}.`
    : `Unfollowed ${language.value?.name ?? "this language"}.`
  );
}

// --- Placeholders démo ---
const DEMO_DISCUSSIONS = [
  { id: "d1", authorUsername: "linguist_sara", content: "Does this language have a tonal system? I noticed some patterns in the recordings that suggest pitch distinctions.", parentMessageId: null, createdAt: new Date(Date.now() - 3600000 * 2).toISOString() },
  { id: "d2", authorUsername: "prof_martinez", content: "The phonological inventory is fascinating — especially the click consonants documented in scenario 3.", parentMessageId: null, createdAt: new Date(Date.now() - 3600000 * 5).toISOString() },
  { id: "d3", authorUsername: "community_nana", content: "My grandmother speaks this language natively. Happy to contribute recordings if needed!", parentMessageId: null, createdAt: new Date(Date.now() - 3600000 * 24).toISOString() },
  { id: "d4", authorUsername: "linguist_sara", content: "Reply to the tonal question", parentMessageId: "d1", createdAt: new Date(Date.now() - 3600000).toISOString() },
  { id: "d5", authorUsername: "prof_martinez", content: "Another reply", parentMessageId: "d1", createdAt: new Date(Date.now() - 1800000).toISOString() },
];

const DEMO_SCENARIOS = [
  { id: "s1", title: "Market conversation", authorUsername: "prof_martinez", visibilityStatus: "PUBLISHED", createdAt: new Date(Date.now() - 3600000 * 48).toISOString(), description: "A typical exchange at a local market, covering greetings, numbers and basic transactions." },
  { id: "s2", title: "Family gathering", authorUsername: "community_nana", visibilityStatus: "PUBLISHED", createdAt: new Date(Date.now() - 3600000 * 72).toISOString(), description: "Vocabulary and phrases used during a traditional family gathering." },
  { id: "s3", title: "Nature and seasons", authorUsername: "linguist_sara", visibilityStatus: "PUBLISHED", createdAt: new Date(Date.now() - 3600000 * 96).toISOString(), description: "Environmental vocabulary and seasonal expressions." },
];

const DEMO_CONTRIBUTORS = ["linguist_sara", "prof_martinez", "community_nana", "ariane_l", "researcher_ko"];

// --- Données dérivées ---
const effectiveMessages = computed(() =>
  messages.value.length > 0 ? messages.value : DEMO_DISCUSSIONS
);

const effectiveScenarios = computed(() =>
  scenarios.value.length > 0 ? scenarios.value : DEMO_SCENARIOS
);

const isDemoMode = computed(() =>
  messages.value.length === 0 && scenarios.value.length === 0
);

const rootMessages = computed(() =>
  effectiveMessages.value.filter((m) => m.parentMessageId == null)
);

const replyCountById = computed(() => {
  const counts = {};
  for (const m of effectiveMessages.value) {
    if (m.parentMessageId != null) {
      const pid = String(m.parentMessageId);
      counts[pid] = (counts[pid] ?? 0) + 1;
    }
  }
  return counts;
});

const popularDiscussions = computed(() =>
  [...rootMessages.value]
    .sort((a, b) => (replyCountById.value[String(b.id)] ?? 0) - (replyCountById.value[String(a.id)] ?? 0))
    .slice(0, 3)
);

const recentScenarios = computed(() =>
  [...effectiveScenarios.value]
    .filter((s) => s.visibilityStatus === "PUBLISHED")
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 5)
);

const activeContributors = computed(() => {
  if (isDemoMode.value) return DEMO_CONTRIBUTORS;
  const seen = new Set();
  const contributors = [];
  for (const m of [...messages.value].reverse()) {
    if (m.authorUsername && !seen.has(m.authorUsername)) {
      seen.add(m.authorUsername);
      contributors.push(m.authorUsername);
    }
    if (contributors.length >= 6) break;
  }
  return contributors;
});

// --- Carousel ---
const carouselIndex = ref(0);
const CARD_WIDTH = 220;
const CARD_GAP = 14;
const PEEK = 28;

const carouselOffset = computed(() =>
  -(carouselIndex.value * (CARD_WIDTH + CARD_GAP)) + PEEK
);

function carouselScroll(dir) {
  const next = carouselIndex.value + dir;
  if (next < 0 || next >= recentScenarios.value.length) return;
  carouselIndex.value = next;
}

function carouselGoTo(i) {
  carouselIndex.value = i;
}

const scenarioThumbnailUrls = ref({});

async function loadCarouselThumbnails(scenarioList) {
  const urls = {};
  await Promise.all(
    scenarioList.map(async (s) => {
      try {
        const thumbs = await fetchScenarioThumbnails(s.id);
        if (thumbs?.length) {
          const sorted = [...thumbs].sort((a, b) => (a.idx ?? a.id) - (b.idx ?? b.id));
          urls[s.id] = buildApiUrl(`/api/thumbnails/${sorted[0].id}/content`);
        }
      } catch { /* silencieux */ }
    })
  );
  scenarioThumbnailUrls.value = urls;
}

function avatarColor(username) {
  const colors = ["#C04A08", "#982800", "#7A3812", "#D4580A", "#b45309", "#065f46", "#6d28d9", "#1e40af"];
  if (!username) return colors[0];
  return colors[username.charCodeAt(0) % colors.length];
}

function authorInitials(username) {
  if (!username) return "?";
  return username.slice(0, 2).toUpperCase();
}

function formatDate(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

// --- Formulaire d'édition ---
const editForm = ref({
  name: "", level: "", bookkeeping: false, iso639P3code: "",
  latitude: "", longitude: "", countryIds: "", familyId: "",
  parentId: "", description: "", markupDescription: "",
});
const isEditing = ref(false);

function levelVariant(level) {
  if (!level) return "neutral";
  const l = String(level).toLowerCase();
  if (l.includes("family")) return "info";
  if (l.includes("language")) return "success";
  if (l.includes("dialect")) return "warning";
  return "neutral";
}

function hydrateForm(lang) {
  editForm.value = {
    name: lang?.name ?? "", level: lang?.level ?? "",
    bookkeeping: !!lang?.bookkeeping, iso639P3code: lang?.iso639P3code ?? "",
    latitude: lang?.latitude ?? "", longitude: lang?.longitude ?? "",
    countryIds: lang?.countryIds ?? "", familyId: lang?.familyId ?? "",
    parentId: lang?.parentId ?? "", description: lang?.description ?? "",
    markupDescription: lang?.markupDescription ?? "",
  };
}

const canEditLanguage = computed(() => !!permissions.value?.canEdit);

async function load(id) {
  loading.value = true;
  error.value = "";
  language.value = null;
  scenarios.value = [];
  messages.value = [];
  permissions.value = {canEdit: false};

  try {
    await loadMe();
    const baseCalls = [fetchLanguage(id), fetchLanguageScenarios(id)];
    if (isAuthenticated.value) baseCalls.push(fetchMyLanguagePermissions(id));
    const results = await Promise.all(baseCalls);
    language.value = results[0];
    scenarios.value = results[1];
    permissions.value = isAuthenticated.value ? results[2] : {canEdit: false};
    hydrateForm(language.value);
    messages.value = await fetchDiscussionMessages("LANGUAGE", id);
    await loadCarouselThumbnails(
      [...scenarios.value]
        .filter((s) => s.visibilityStatus === "PUBLISHED")
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
        .slice(0, 5)
    );
  } catch (e) {
    error.value = e.message || "Failed to load language details.";
  } finally {
    loading.value = false;
  }
}

async function saveLanguage() {
  if (!language.value) return;
  saving.value = true;
  saveError.value = "";
  saveSuccess.value = "";
  try {
    const payload = {
      name: editForm.value.name, level: editForm.value.level,
      bookkeeping: editForm.value.bookkeeping, iso639P3code: editForm.value.iso639P3code,
      latitude: editForm.value.latitude === "" ? null : Number(editForm.value.latitude),
      longitude: editForm.value.longitude === "" ? null : Number(editForm.value.longitude),
      countryIds: editForm.value.countryIds, familyId: editForm.value.familyId,
      parentId: editForm.value.parentId, description: editForm.value.description,
      markupDescription: editForm.value.markupDescription,
    };
    language.value = await updateLanguage(language.value.id, payload);
    hydrateForm(language.value);
    isEditing.value = false;
    saveSuccess.value = "Language updated successfully.";
    toast.success(saveSuccess.value);
  } catch (e) {
    saveError.value = e.message || "Failed to update language.";
    toast.error(saveError.value);
  } finally {
    saving.value = false;
  }
}

function cancelEdit() {
  hydrateForm(language.value);
  isEditing.value = false;
  saveError.value = "";
  saveSuccess.value = "";
}

watch(
  () => props.id,
  (id) => {
    load(id);
    loadFollowState(id);
    activeTab.value = "overview";
    carouselIndex.value = 0;
  },
  { immediate: true }
);
</script>

<template>
  <main class="page lang-page">
    <BaseLoader v-if="loading">Loading language details...</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else-if="language">
      <div class="lang-content">

      <!-- Badge démo -->
      <div v-if="isDemoMode" class="demo-banner">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
        Preview mode — showing sample data to illustrate community features
      </div>

      <!-- Hero -->
      <div class="lang-hero">
        <div class="lang-hero__left">
          <div class="lang-hero__icon">
            {{ (language.name ?? "?").slice(0, 2).toUpperCase() }}
          </div>
          <div class="lang-hero__info">
            <div class="lang-hero__badges">
              <BaseBadge :variant="levelVariant(language.level)">{{ language.level ?? "Language" }}</BaseBadge>
              <BaseBadge v-if="canEditLanguage" variant="success">Can edit</BaseBadge>
            </div>
            <h1 class="lang-hero__title">{{ language.name ?? "Language" }}</h1>
            <p class="lang-hero__meta">
              <span v-if="language.familyName">{{ language.familyName }}</span>
              <span v-if="language.familyName && language.iso639P3code"> · </span>
              <span v-if="language.iso639P3code">ISO: <code>{{ language.iso639P3code }}</code></span>
              <span v-if="language.countryIds"> · {{ language.countryIds }}</span>
            </p>
          </div>
        </div>

        <div class="lang-hero__right">
          <div class="lang-hero__stats">
            <div class="lang-stat">
              <span class="lang-stat__value">{{ activeContributors.length }}</span>
              <span class="lang-stat__label">Members</span>
            </div>
            <div class="lang-stat">
              <span class="lang-stat__value">{{ rootMessages.length }}</span>
              <span class="lang-stat__label">Discussions</span>
            </div>
            <div class="lang-stat">
              <span class="lang-stat__value">{{ effectiveScenarios.length }}</span>
              <span class="lang-stat__label">Storyboards</span>
            </div>
          </div>

          <button v-if="isAuthenticated" type="button" class="follow-btn" :class="{ 'follow-btn--active': isFollowing }" @click="toggleFollow">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              <circle v-if="isFollowing" cx="19" cy="5" r="3" fill="currentColor" stroke="none"/>
            </svg>
            {{ isFollowing ? "Following" : "Follow" }}
          </button>
        </div>
      </div>

      <!-- Onglets -->
      <nav class="lang-tabs">
        <button class="lang-tab" :class="{ 'lang-tab--active': activeTab === 'overview' }" @click="activeTab = 'overview'">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
          Overview
        </button>
        <button class="lang-tab" :class="{ 'lang-tab--active': activeTab === 'discussion' }" @click="activeTab = 'discussion'">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          Discussion
          <span class="lang-tab__count">{{ rootMessages.length }}</span>
        </button>
        <button class="lang-tab" :class="{ 'lang-tab--active': activeTab === 'storyboards' }" @click="activeTab = 'storyboards'">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
          Storyboards
          <span class="lang-tab__count">{{ effectiveScenarios.length }}</span>
        </button>
      </nav>

      <!-- Contenu -->
      <div class="lang-body">

        <!-- Overview -->
        <div v-if="activeTab === 'overview'" class="lang-overview">

          <!-- Métadonnées -->
          <div class="lang-meta-grid">
            <div class="lang-meta-card">
              <span class="lang-meta-card__label">Family</span>
              <RouterLink v-if="language.familyId" :to="`/languages/${language.familyId}`" class="lang-meta-card__value lang-meta-card__value--link">
                {{ language.familyName ?? language.familyId }}
              </RouterLink>
              <span v-else class="lang-meta-card__value">—</span>
            </div>
            <div class="lang-meta-card">
              <span class="lang-meta-card__label">Parent</span>
              <RouterLink v-if="language.parentId" :to="`/languages/${language.parentId}`" class="lang-meta-card__value lang-meta-card__value--link">
                {{ language.parentName ?? language.parentId }}
              </RouterLink>
              <span v-else class="lang-meta-card__value">—</span>
            </div>
            <div class="lang-meta-card">
              <span class="lang-meta-card__label">Level</span>
              <span class="lang-meta-card__value">{{ language.level ?? "—" }}</span>
            </div>
          </div>

          <!-- Description -->
          <section class="card lang-description">
            <div class="lang-description__header">
              <h2>Description</h2>
              <button v-if="canEditLanguage && !isEditing" type="button" class="btn btn--ghost" @click="isEditing = true">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                Edit
              </button>
            </div>
            <template v-if="isEditing">
              <div class="form-grid">
                <label>Name<input v-model="editForm.name"/></label>
                <label>Level<input v-model="editForm.level"/></label>
                <label>ISO 639-3<input v-model="editForm.iso639P3code"/></label>
                <label>Country IDs<input v-model="editForm.countryIds"/></label>
                <label>Family ID<input v-model="editForm.familyId"/></label>
                <label>Parent ID<input v-model="editForm.parentId"/></label>
                <label>Latitude<input v-model="editForm.latitude" type="number" step="any"/></label>
                <label>Longitude<input v-model="editForm.longitude" type="number" step="any"/></label>
                <label class="checkbox-row"><input v-model="editForm.bookkeeping" type="checkbox"/><span>Bookkeeping</span></label>
                <label class="form-grid__full">Description<textarea v-model="editForm.description" rows="6"/></label>
                <label class="form-grid__full">Markup description<textarea v-model="editForm.markupDescription" rows="6"/></label>
              </div>
              <div class="toolbar">
                <button class="btn btn--primary" :disabled="saving" @click="saveLanguage">{{ saving ? "Saving..." : "Save changes" }}</button>
                <button class="btn btn--ghost" :disabled="saving" @click="cancelEdit">Cancel</button>
              </div>
              <BaseAlert v-if="saveSuccess" type="success">{{ saveSuccess }}</BaseAlert>
              <BaseAlert v-if="saveError" type="error">{{ saveError }}</BaseAlert>
            </template>
            <template v-else>
              <p class="text lang-description__text">{{ language.description ?? "No description available for this language." }}</p>
            </template>
          </section>

          <!-- Grille overview -->
          <div class="overview-grid">

            <!-- Discussions populaires -->
            <section class="card overview-section">
              <div class="overview-section__header">
                <h3>Popular discussions</h3>
                <button type="button" class="overview-section__link" @click="activeTab = 'discussion'">View all →</button>
              </div>
              <div v-if="popularDiscussions.length" class="overview-discussions">
                <button v-for="msg in popularDiscussions" :key="msg.id" type="button" class="overview-discussion-item" @click="activeTab = 'discussion'">
                  <div class="overview-discussion-item__avatar" :style="{ background: avatarColor(msg.authorUsername) }">
                    {{ authorInitials(msg.authorUsername) }}
                  </div>
                  <div class="overview-discussion-item__body">
                    <p class="overview-discussion-item__content">{{ msg.content.slice(0, 80) }}{{ msg.content.length > 80 ? "…" : "" }}</p>
                    <p class="overview-discussion-item__meta">
                      {{ msg.authorUsername ?? "Unknown" }} ·
                      <span>{{ replyCountById[String(msg.id)] ?? 0 }} repl{{ (replyCountById[String(msg.id)] ?? 0) === 1 ? "y" : "ies" }}</span>
                    </p>
                  </div>
                </button>
              </div>
              <p v-else class="overview-section__empty">No discussions yet. Be the first to start one!</p>
            </section>

            <!-- Nouveaux storyboards — carousel -->
            <section class="card overview-section">
              <div class="overview-section__header">
                <h3>New storyboards</h3>
                <button type="button" class="overview-section__link" @click="activeTab = 'storyboards'">View all →</button>
              </div>

              <div v-if="recentScenarios.length" class="carousel-wrapper">
                <button type="button" class="carousel-btn" :disabled="carouselIndex === 0" @click="carouselScroll(-1)" aria-label="Previous">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
                </button>

                <div class="carousel-track-outer">
                  <div class="carousel-track" :style="{ transform: `translateX(${carouselOffset}px)` }">
                    <component
                      :is="isDemoMode ? 'div' : RouterLink"
                      v-for="(s, i) in recentScenarios"
                      :key="s.id"
                      v-bind="isDemoMode ? {} : { to: `/scenarios/${s.id}` }"
                      class="carousel-card"
                      :class="{ 'carousel-card--active': i === carouselIndex }"
                    >
                      <div class="carousel-card__image">
                        <img v-if="scenarioThumbnailUrls[s.id]" :src="scenarioThumbnailUrls[s.id]" :alt="s.title" class="carousel-card__img"/>
                        <div v-else class="carousel-card__img-placeholder">
                          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                        </div>
                        <div class="carousel-card__image-badge">
                          <BaseBadge :variant="s.visibilityStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ s.visibilityStatus ?? "DRAFT" }}</BaseBadge>
                        </div>
                        <button
                          v-if="!isDemoMode && s.visibilityStatus === 'PUBLISHED'"
                          type="button"
                          class="carousel-card__read-btn"
                          title="Read scenario"
                          @click.prevent.stop="openReader(s)"
                        >
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                            <polygon points="5 3 19 12 5 21 5 3"/>
                          </svg>
                          Read
                        </button>
                      </div>
                      <div class="carousel-card__body">
                        <p class="carousel-card__title">{{ s.title ?? "Untitled scenario" }}</p>
                        <div class="carousel-card__footer">
                          <span class="carousel-card__author">By {{ s.authorUsername ?? "Unknown" }}</span>
                          <span class="carousel-card__date" v-if="s.createdAt">{{ formatDate(s.createdAt) }}</span>
                        </div>
                      </div>
                    </component>
                  </div>
                </div>

                <button type="button" class="carousel-btn" :disabled="carouselIndex >= recentScenarios.length - 1" @click="carouselScroll(1)" aria-label="Next">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>
                </button>
              </div>

              <div v-if="recentScenarios.length > 1" class="carousel-dots">
                <button v-for="(_, i) in recentScenarios" :key="i" type="button" class="carousel-dot" :class="{ 'carousel-dot--active': i === carouselIndex }" @click="carouselGoTo(i)"/>
              </div>

              <p v-if="!recentScenarios.length" class="overview-section__empty">No published storyboards yet.</p>
            </section>
          </div>

          <!-- Contributeurs actifs -->
          <section class="card overview-section">
            <div class="overview-section__header">
              <h3>Active contributors</h3>
              <span class="overview-section__sub">Based on recent discussions</span>
            </div>
            <div v-if="activeContributors.length" class="contributors-row">
              <div v-for="username in activeContributors" :key="username" class="contributor-avatar" :style="{ background: avatarColor(username) }" :title="username">
                {{ authorInitials(username) }}
              </div>
              <span class="contributors-row__label">{{ activeContributors.length }} active contributor{{ activeContributors.length > 1 ? "s" : "" }}</span>
            </div>
            <p v-else class="overview-section__empty">No contributors yet.</p>
          </section>
        </div>

        <!-- Discussion -->
        <div v-else-if="activeTab === 'discussion'" class="lang-tab-panel">
          <DiscussionThread
            title="Community discussion"
            :subtitle="language.name"
            target-type="LANGUAGE"
            :target-id="props.id"
            empty-title="No messages yet"
            empty-message="Be the first to start a discussion about this language."
          />
        </div>

        <!-- Storyboards -->
        <div v-else-if="activeTab === 'storyboards'" class="lang-tab-panel">
          <div v-if="effectiveScenarios.length" class="lang-scenarios">
            <component
              :is="isDemoMode ? 'div' : RouterLink"
              v-for="s in effectiveScenarios"
              :key="s.id"
              v-bind="isDemoMode ? {} : { to: `/scenarios/${s.id}` }"
              class="lang-scenario-card"
            >
              <div class="lang-scenario-card__left">
                <div class="lang-scenario-card__icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                </div>
                <div>
                  <p class="lang-scenario-card__title">{{ s.title ?? "Untitled scenario" }}</p>
                  <p class="lang-scenario-card__meta">By {{ s.authorUsername ?? "Unknown" }}<span v-if="s.createdAt"> · {{ formatDate(s.createdAt) }}</span></p>
                </div>
              </div>
              <div class="lang-scenario-card__right">
                <BaseBadge :variant="s.visibilityStatus === 'PUBLISHED' ? 'success' : 'warning'">{{ s.visibilityStatus ?? "DRAFT" }}</BaseBadge>
                <button
                  v-if="!isDemoMode && s.visibilityStatus === 'PUBLISHED'"
                  type="button"
                  class="lang-scenario-card__read-btn"
                  title="Read scenario"
                  @click.prevent.stop="openReader(s)"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                  </svg>
                  Read
                </button>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--text-soft)"><path d="m9 18 6-6-6-6"/></svg>
              </div>
            </component>
          </div>
          <BaseEmptyState v-else title="No storyboards yet" message="No scenarios have been created for this language." />
        </div>
      </div>
      </div>
    </template>
  </main>
  <ScenarioReaderModal :scenario="activeScenario" @close="closeReader" />
</template>

<style scoped>
/* ── Page layout ── */
.lang-page {
  max-width: 100%;
  padding: 1.5rem 2rem;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* Banner démo */
.demo-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 10px;
  background: rgba(255, 248, 240, 0.96);
  border: 1px solid rgba(192, 74, 8, 0.3);
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--primary);
  margin-bottom: 0.5rem;
}

/* Hero */
.lang-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 2rem;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid rgba(192, 74, 8, 0.3);
  border-radius: var(--radius);
  box-shadow: 0 4px 24px rgba(42, 21, 0, 0.08);
  margin-bottom: 0.25rem;
  flex-wrap: wrap;
}

.lang-hero__left {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  flex: 1;
  min-width: 0;
}

.lang-hero__icon {
  width: 80px;
  height: 80px;
  border-radius: 22px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-strong) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.6rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  flex-shrink: 0;
  box-shadow: 0 4px 16px rgba(192, 74, 8, 0.25);
}

.lang-hero__badges { display: flex; gap: 6px; margin-bottom: 0.4rem; flex-wrap: wrap; }

.lang-hero__title {
  margin: 0 0 0.3rem;
  font-size: 2rem;
  font-weight: 800;
  line-height: 1.15;
  color: var(--text);
}

.lang-hero__meta {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-soft);
}

.lang-hero__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1rem;
  flex-shrink: 0;
}

.lang-hero__stats { display: flex; gap: 0.75rem; }

.lang-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 0.65rem 1rem;
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: 14px;
  min-width: 70px;
}

.lang-stat__value { font-size: 1.5rem; font-weight: 800; color: var(--text); line-height: 1; }
.lang-stat__label { font-size: 0.72rem; color: var(--text-soft); font-weight: 600; white-space: nowrap; }

/* Bouton suivre */
.follow-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
  border: 1.5px solid var(--border);
  background: #FFFCF7;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--primary);
  cursor: pointer;
  transition: all 0.15s;
}

.follow-btn:hover { border-color: var(--primary); background: rgba(192, 74, 8, 0.08); }
.follow-btn--active { background: var(--primary); border-color: var(--primary); color: #fff; }
.follow-btn--active:hover { background: var(--primary-strong); border-color: var(--primary-strong); }

/* Onglets */
.lang-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--accent-warm);
  margin-bottom: 1.5rem;
}

.lang-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 6px;
  padding: 0.7rem 1.1rem;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-soft);
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -2px;
  white-space: nowrap;
}

.lang-tab:hover { color: var(--text); }
.lang-tab--active { color: var(--primary); border-bottom-color: var(--primary); }

.lang-tab__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--accent-warm);
  border: 1px solid rgba(192, 74, 8, 0.25);
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-soft);
}

/* Corps */
.lang-body { min-height: 400px; flex: 1; display: flex; flex-direction: column; }
.lang-tab-panel { display: flex; flex-direction: column; gap: 1rem; flex: 1; }

/* Overview */
.lang-overview { display: flex; flex-direction: column; gap: 1.5rem; flex: 1; }

.lang-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 0.75rem;
}

.lang-meta-card {
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 0.9rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(42, 21, 0, 0.06);
}

.lang-meta-card__label {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-soft);
}

.lang-meta-card__value { font-size: 0.95rem; font-weight: 600; color: var(--text); }
.lang-meta-card__value--link { color: var(--primary); text-decoration: none; }
.lang-meta-card__value--link:hover { color: var(--primary-strong); text-decoration: underline; }
.lang-meta-card__value--mono { font-family: ui-monospace, monospace; font-size: 0.85rem; }

/* Description */
.lang-description {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border-color: rgba(192, 74, 8, 0.2);
}

.lang-description__header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
.lang-description__header h2 { margin: 0; color: var(--text); }
.lang-description__text { line-height: 1.75; font-size: 0.95rem; color: var(--text); }

/* Grille overview */
.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
}

@media (max-width: 780px) { .overview-grid { grid-template-columns: 1fr; } }

.overview-section {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border-color: rgba(192, 74, 8, 0.2);
}

.overview-section__header { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
.overview-section__header h3 { margin: 0; font-size: 1rem; color: var(--text); }
.overview-section__sub { font-size: 0.78rem; color: var(--text-soft); }

.overview-section__link {
  background: none; border: none; font-size: 0.82rem; font-weight: 600;
  color: var(--primary); cursor: pointer; padding: 0; white-space: nowrap;
}
.overview-section__link:hover { color: var(--primary-strong); text-decoration: underline; }
.overview-section__empty { font-size: 0.85rem; color: var(--text-soft); }

/* Discussions */
.overview-discussions { display: flex; flex-direction: column; gap: 0.5rem; }

.overview-discussion-item {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.7rem 0.85rem;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--surface-alt);
  text-align: left;
  cursor: pointer;
  width: 100%;
  transition: background 0.15s, border-color 0.15s;
}

.overview-discussion-item:hover {
  background: rgba(255, 224, 192, 0.5);
  border-color: rgba(192, 74, 8, 0.35);
}

.overview-discussion-item__avatar {
  width: 30px; height: 30px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 0.72rem; font-weight: 800; color: #fff; flex-shrink: 0;
}

.overview-discussion-item__body { flex: 1; min-width: 0; }

.overview-discussion-item__content {
  margin: 0; font-size: 0.86rem; font-weight: 500; color: var(--text); line-height: 1.4;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}

.overview-discussion-item__meta { margin: 0.2rem 0 0; font-size: 0.75rem; color: var(--text-soft); }

/* Carousel */
.carousel-wrapper { position: relative; display: flex; align-items: center; gap: 6px; }

.carousel-track-outer {
  flex: 1; overflow: hidden; border-radius: 14px;
  mask-image: linear-gradient(to right, transparent 0px, black 28px, black calc(100% - 28px), transparent 100%);
  -webkit-mask-image: linear-gradient(to right, transparent 0px, black 28px, black calc(100% - 28px), transparent 100%);
}

.carousel-track {
  display: flex; gap: 14px;
  transition: transform 0.38s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform; padding: 6px 2px 10px;
}

.carousel-card {
  flex-shrink: 0; width: 220px;
  background: #FFFCF7;
  border: 1.5px solid var(--border);
  border-radius: 14px; overflow: hidden;
  text-decoration: none; color: inherit;
  display: flex; flex-direction: column;
  box-shadow: 0 4px 16px rgba(42, 21, 0, 0.07);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.2s;
  transform: scale(0.93); opacity: 0.65;
  cursor: pointer;
}

.carousel-card--active { transform: scale(1); opacity: 1; border-color: var(--primary); box-shadow: 0 8px 28px rgba(192, 74, 8, 0.18); }
.carousel-card:hover { border-color: var(--primary); box-shadow: 0 10px 32px rgba(192, 74, 8, 0.16); }

.carousel-card__image { position: relative; width: 100%; height: 130px; background: var(--bg); overflow: hidden; }
.carousel-card__img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 0.3s ease; }
.carousel-card:hover .carousel-card__img { transform: scale(1.04); }

.carousel-card__img-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: var(--accent-warm); color: var(--primary); opacity: 0.7;
}

.carousel-card__image-badge { position: absolute; top: 8px; right: 8px; }

.carousel-card__body { padding: 0.75rem; display: flex; flex-direction: column; gap: 0.4rem; flex: 1; }

.carousel-card__title {
  margin: 0; font-weight: 700; font-size: 0.88rem; color: var(--text); line-height: 1.3;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}

.carousel-card__footer {
  display: flex; justify-content: space-between; align-items: center; gap: 4px;
  margin-top: auto; padding-top: 0.35rem;
  border-top: 1px solid rgba(192, 74, 8, 0.12);
}

.carousel-card__author, .carousel-card__date {
  font-size: 0.72rem; color: var(--text-soft);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.carousel-btn {
  width: 32px; height: 32px; border-radius: 50%;
  border: 1.5px solid var(--border);
  background: #FFFCF7;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: var(--text-soft); flex-shrink: 0;
  transition: all 0.15s; box-shadow: 0 2px 8px rgba(42, 21, 0, 0.07);
}

.carousel-btn:hover:not(:disabled) { background: var(--accent-warm); border-color: var(--primary); color: var(--primary); }
.carousel-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.carousel-dots { display: flex; justify-content: center; gap: 6px; margin-top: 4px; }

.carousel-dot {
  width: 6px; height: 6px; border-radius: 50%;
  border: none; background: var(--accent-warm);
  cursor: pointer; padding: 0; transition: all 0.2s;
}

.carousel-dot--active { background: var(--primary); width: 18px; border-radius: 3px; }

/* Contributeurs */
.contributors-row { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }

.contributor-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 0.78rem; font-weight: 800; color: #fff;
  border: 2px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 2px 8px rgba(42, 21, 0, 0.15);
  cursor: default; transition: transform 0.15s;
}

.contributor-avatar:hover { transform: scale(1.12); z-index: 1; }
.contributors-row__label { font-size: 0.85rem; color: var(--text-soft); margin-left: 0.25rem; }

/* Storyboards */
.lang-scenarios { display: flex; flex-direction: column; gap: 0.75rem; }

.lang-scenario-card {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  padding: 1.1rem 1.35rem;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 2px 10px rgba(42, 21, 0, 0.06);
  text-decoration: none; color: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
  cursor: pointer;
}

.lang-scenario-card:hover { border-color: var(--primary); box-shadow: 0 8px 24px rgba(192, 74, 8, 0.14); }

.lang-scenario-card__left { display: flex; align-items: center; gap: 0.9rem; min-width: 0; }

.lang-scenario-card__icon {
  width: 40px; height: 40px; border-radius: 10px;
  background: rgba(192, 74, 8, 0.10);
  border: 1px solid rgba(192, 74, 8, 0.22);
  display: flex; align-items: center; justify-content: center;
  color: var(--primary); flex-shrink: 0;
}

.lang-scenario-card__title { margin: 0; font-weight: 700; font-size: 0.95rem; color: var(--text); }
.lang-scenario-card__meta { margin: 0.2rem 0 0; font-size: 0.8rem; color: var(--text-soft); }
.lang-scenario-card__right { display: flex; align-items: center; gap: 0.5rem; flex-shrink: 0; }

.lang-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 1.25rem;
}

@media (max-width: 600px) {
  .lang-page { padding: 1rem; }
  .lang-hero { flex-direction: column; }
  .lang-hero__right { align-items: flex-start; flex-direction: row; flex-wrap: wrap; }
  .lang-hero__icon { width: 60px; height: 60px; font-size: 1.2rem; }
  .lang-tabs { overflow-x: auto; }
}

/* Read buttons */
.carousel-card__read-btn {
  position: absolute;
  bottom: 8px;
  left: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(20, 8, 4, 0.55);
  backdrop-filter: blur(6px);
  color: #fff;
  font: inherit;
  font-size: 0.7rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.carousel-card__read-btn:hover {
  background: var(--primary);
  border-color: var(--primary);
}

.lang-scenario-card__read-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1.5px solid rgba(192, 74, 8, 0.3);
  background: rgba(192, 74, 8, 0.06);
  color: var(--primary);
  font: inherit;
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.lang-scenario-card__read-btn:hover {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}
</style>