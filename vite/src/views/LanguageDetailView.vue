<script setup>
import {computed, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import worldCountries from "world-countries";
import {ArrowLeft, ArrowUpRight, BookOpenText, Globe2, MapPin, MapPinned, PencilLine} from "lucide-vue-next";
import {fetchLanguage, fetchLanguageScenarios, fetchMyLanguagePermissions, updateLanguage} from "../api/languages";
import {fetchDiscussionMessages} from "../api/community";
import {fetchScenarioThumbnails} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";
import {useAuth} from "../composables/useAuth";
import {useLanguageFollows} from "../composables/useLanguageFollows";
import {useScenarioReader} from "../composables/useScenarioReader";
import {useToast} from "../composables/useToast";
import LanguagePresenceMap from "../components/maps/LanguagePresenceMap.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";

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

const iso2ToIso3 = new Map(
    worldCountries
        .filter((country) => country?.cca2 && country?.cca3)
        .map((country) => [String(country.cca2).toUpperCase(), String(country.cca3).toUpperCase()])
);

const iso3ToCountryName = new Map(
    worldCountries
        .filter((country) => country?.cca3)
        .map((country) => [String(country.cca3).toUpperCase(), country?.name?.common || String(country.cca3).toUpperCase()])
);

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

function parseCountryIds(raw) {
  if (!raw) return [];

  const tokens = String(raw)
      .split(/[\s,;|/]+/)
      .map((token) => token.trim().toUpperCase())
      .filter(Boolean);

  const normalized = tokens
      .map((token) => {
        if (token.length === 3) return token;
        if (token.length === 2 && iso2ToIso3.has(token)) return iso2ToIso3.get(token);
        return null;
      })
      .filter((token) => !!token);

  return Array.from(new Set(normalized));
}

function normalizeLevel(level) {
  const normalized = String(level ?? "").trim().toLowerCase();
  if (normalized.includes("dialect")) return "dialect";
  if (normalized.includes("family")) return "family";
  return "language";
}

const heroDescription = computed(() => {
  const desc = String(language.value?.description ?? "").trim();
  const markupDesc = String(language.value?.markupDescription ?? "").trim();
  return desc || markupDesc || "Discover this language through geography, context, and community scenarios.";
});

const normalizedLevel = computed(() => normalizeLevel(language.value?.level));

const spokenCountries = computed(() => {
  const codes = parseCountryIds(language.value?.countryIds ?? "");
  return codes.map((isoA3) => ({
    isoA3,
    name: iso3ToCountryName.get(isoA3) || isoA3,
  }));
});

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

    try {
      messages.value = await fetchDiscussionMessages("LANGUAGE", id);
    } catch {
      messages.value = [];
    }

    try {
      await loadCarouselThumbnails(
        [...scenarios.value]
          .filter((s) => s.visibilityStatus === "PUBLISHED")
          .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
          .slice(0, 5)
      );
    } catch {
      scenarioThumbnailUrls.value = {};
    }
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
  <main class="page language-detail-page">
    <BaseLoader v-if="loading">Loading language details...</BaseLoader>
    <BaseAlert v-else-if="error" type="error">{{ error }}</BaseAlert>

    <template v-else-if="language">
      <section class="section language-detail-shell">
        <article class="hero-card">
          <div class="hero-actions">
            <RouterLink to="/languages" class="back-link" aria-label="Back to languages catalog">
              <ArrowLeft :size="16"/>
              <span>Back to catalog</span>
            </RouterLink>

            <button
                v-if="canEditLanguage && !isEditing"
                type="button"
                class="edit-btn"
                aria-label="Edit language"
                @click="isEditing = true"
            >
              <PencilLine :size="15"/>
              <span>Edit language</span>
            </button>
          </div>

          <div class="hero-main">
            <div class="hero-copy">
              <p class="hero-kicker">Language profile</p>
              <h1>{{ language.name ?? "Language" }}</h1>
              <p class="hero-description">{{ heroDescription }}</p>

              <div class="hero-stats">
                <div class="hero-stat">
                  <span>{{ spokenCountries.length }}</span>
                  <small>Countries</small>
                </div>

                <div class="hero-stat">
                  <span>{{ scenarios.length }}</span>
                  <small>Scenarios</small>
                </div>

                <div class="hero-stat">
                  <span>{{ language.level ?? "N/A" }}</span>
                  <small>Level</small>
                </div>
              </div>
            </div>

            <aside class="hero-profile-card">
              <p class="profile-kicker">Language identity</p>
              <span class="level-pill" :class="`level-pill--${normalizedLevel}`">
                {{ language.level ?? "Language" }}
              </span>

              <div class="profile-row">
                <span>Family</span>
                <RouterLink v-if="language.familyId" :to="`/languages/${language.familyId}`">
                  {{ language.familyName }}
                </RouterLink>
                <strong v-else>Not specified</strong>
              </div>

              <div class="profile-row">
                <span>Parent</span>
                <RouterLink v-if="language.parentId" :to="`/languages/${language.parentId}`">
                  {{ language.parentName }}
                </RouterLink>
                <strong v-else>Not specified</strong>
              </div>

              <div class="profile-row">
                <span>Countries</span>
                <strong>{{ spokenCountries.length }}</strong>
              </div>

              <div class="profile-row">
                <span>Scenarios</span>
                <strong>{{ scenarios.length }}</strong>
              </div>
            </aside>
          </div>
        </article>

        <div class="top-grid">
          <section class="panel-card">
            <header class="panel-header">
              <div class="panel-title-row">
                <MapPinned :size="18"/>
                <h2>Pays ou cette langue est parlee</h2>
              </div>
              <p class="panel-counter">{{ spokenCountries.length }} pays renseigne(s)</p>
            </header>

            <div class="country-layout">
              <div>
                <div v-if="spokenCountries.length" class="country-pills" aria-label="Countries where this language is spoken">
                  <span
                      v-for="country in spokenCountries"
                      :key="country.isoA3"
                      class="country-pill"
                      :title="`${country.name} (${country.isoA3})`"
                  >
                    <MapPin :size="14" class="country-pin"/>
                    <span class="country-name">{{ country.name }}</span>
                    <span class="country-code">{{ country.isoA3 }}</span>
                  </span>
                </div>
                <p v-else class="empty-copy">Aucun pays renseigne pour cette langue.</p>
              </div>

              <aside class="country-mini-map" aria-label="Mini world map for language coverage">
                <LanguagePresenceMap
                    :country-ids="String(language.countryIds || '')"
                    :latitude="language.latitude"
                    :longitude="language.longitude"
                    :language-name="String(language.name || '')"
                />
              </aside>
            </div>
          </section>

          <section class="panel-card">
            <header class="panel-header">
              <div class="panel-title-row">
                <Globe2 :size="18"/>
                <h2>A propos de la langue</h2>
              </div>
            </header>

            <template v-if="isEditing">
              <div class="form-grid">
                <label>
                  Name
                  <input v-model="editForm.name"/>
                </label>

                <label>
                  Level
                  <input v-model="editForm.level"/>
                </label>

                <label>
                  ISO 639-3
                  <input v-model="editForm.iso639P3code"/>
                </label>

                <label>
                  Country IDs
                  <input v-model="editForm.countryIds"/>
                </label>

                <label>
                  Family ID
                  <input v-model="editForm.familyId"/>
                </label>

                <label>
                  Parent ID
                  <input v-model="editForm.parentId"/>
                </label>

                <label>
                  Latitude
                  <input v-model="editForm.latitude" type="number" step="any"/>
                </label>

                <label>
                  Longitude
                  <input v-model="editForm.longitude" type="number" step="any"/>
                </label>

                <label class="checkbox-row">
                  <input v-model="editForm.bookkeeping" type="checkbox"/>
                  <span>Bookkeeping</span>
                </label>

                <label class="form-grid__full">
                  Description
                  <textarea v-model="editForm.description" rows="6"/>
                </label>

                <label class="form-grid__full">
                  Markup description
                  <textarea v-model="editForm.markupDescription" rows="6"/>
                </label>
              </div>

              <div class="toolbar">
                <button class="btn btn--primary" :disabled="saving" @click="saveLanguage">
                  {{ saving ? "Saving..." : "Save changes" }}
                </button>
                <button class="btn btn--ghost" :disabled="saving" @click="cancelEdit">
                  Cancel
                </button>
              </div>

              <BaseAlert v-if="saveSuccess" type="success">{{ saveSuccess }}</BaseAlert>
              <BaseAlert v-if="saveError" type="error">{{ saveError }}</BaseAlert>
            </template>

            <p v-else class="about-copy">
              {{ language.description || "Aucune description detaillee n'est encore disponible." }}
            </p>
          </section>
        </div>

        <section class="panel-card scenario-section">
          <header class="panel-header">
            <div class="panel-title-row">
              <BookOpenText :size="18"/>
              <h2>Scenarios pour decouvrir cette langue</h2>
            </div>
            <p class="panel-intro">
              Les scenarios permettent de comprendre une langue a travers des situations, des dialogues et des contextes culturels.
            </p>
          </header>

          <div v-if="scenarios.length" class="scenario-grid">
            <RouterLink
                v-for="scenario in scenarios"
                :key="scenario.id"
                :to="`/scenarios/${scenario.id}`"
                class="scenario-card"
                :aria-label="`Open scenario ${scenario.title || 'Untitled scenario'}`"
            >
              <div class="scenario-card__top">
                <div>
                  <p class="scenario-label">Scenario</p>
                  <h3>{{ scenario.title ?? "Untitled scenario" }}</h3>
                </div>
                <ArrowUpRight :size="18"/>
              </div>

              <p class="scenario-desc">
                {{ scenario.description || scenario.markupDescription || "No description available yet." }}
              </p>

              <div class="scenario-meta">
                <span>{{ scenario.authorUsername ?? "Unknown author" }}</span>
                <span>{{ formatDate(scenario.createdAt) }}</span>
              </div>
            </RouterLink>
          </div>

          <div v-else class="scenario-empty">
            <BookOpenText :size="28" class="scenario-empty-icon"/>
            <h3>Aucun scenario n'est encore lie a cette langue.</h3>
            <p>Chaque scenario ouvre une porte vers la culture, la voix et le contexte vivant d'une langue.</p>
          </div>
        </section>
      </section>
    </template>
  </main>
</template>

<style scoped>
.language-detail-page {
  --bg-950: #17100d;
  --bg-900: #231711;
  --panel: rgba(52, 34, 24, 0.7);
  --panel-soft: rgba(66, 43, 30, 0.62);
  --sand: #f8f2e8;
  --sand-muted: #d9c7ad;
  --clay: #9d5f2f;
  --ember: #c06a2f;
  --gold: #d7a15f;
  --border: rgba(216, 178, 126, 0.22);
  --font-display: "Playfair Display", "Lora", Georgia, "Times New Roman", serif;
  --font-body: "Plus Jakarta Sans", "Inter", "Segoe UI", Roboto, Arial, sans-serif;
  position: relative;
  min-height: 100dvh;
  background:
    radial-gradient(circle at 18% 12%, rgba(204, 126, 67, 0.25), transparent 34rem),
    radial-gradient(circle at 82% 18%, rgba(173, 108, 58, 0.2), transparent 30rem),
    linear-gradient(135deg, var(--bg-900) 0%, #322117 48%, var(--bg-950) 100%);
  font-family: var(--font-body);
  color: var(--sand);
  overflow: hidden;
}

.language-detail-page::before {
  content: "";
  position: absolute;
  inset: -30% -20%;
  pointer-events: none;
  opacity: 0.24;
  background:
    radial-gradient(circle at 30% 36%, rgba(255, 213, 170, 0.08) 0 2px, transparent 2px),
    radial-gradient(circle at 70% 52%, rgba(240, 192, 132, 0.06) 0 1.5px, transparent 1.5px),
    repeating-linear-gradient(160deg, rgba(255, 227, 188, 0.03) 0 1px, transparent 1px 24px);
  filter: blur(0.2px);
}

.language-detail-shell {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 2rem 0 3rem;
  display: grid;
  gap: 1.2rem;
  position: relative;
  z-index: 1;
}

.hero-card,
.panel-card {
  border: 1px solid var(--border);
  background: var(--panel);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.32);
  backdrop-filter: blur(14px);
  border-radius: 28px;
}

.hero-card {
  padding: 1.2rem;
  position: relative;
  overflow: hidden;
}

.hero-card::before {
  content: "";
  position: absolute;
  width: 420px;
  height: 420px;
  right: -160px;
  top: -190px;
  border-radius: 999px;
  background:
    radial-gradient(circle, rgba(215, 161, 95, 0.25) 0%, rgba(215, 161, 95, 0) 68%);
  pointer-events: none;
}

.hero-card::after {
  content: "";
  position: absolute;
  right: 120px;
  top: 38px;
  width: 200px;
  height: 200px;
  border-radius: 999px;
  border: 1px dashed rgba(215, 161, 95, 0.24);
  box-shadow: 0 0 0 14px rgba(215, 161, 95, 0.05), 0 0 0 42px rgba(192, 106, 47, 0.04);
  opacity: 0.55;
  pointer-events: none;
}

.hero-actions {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 2rem;
  position: relative;
  z-index: 1;
}

.back-link,
.edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border-radius: 999px;
  padding: 0.58rem 0.85rem;
  font-weight: 700;
  text-decoration: none;
  transition: transform 180ms ease, box-shadow 180ms ease, background 180ms ease, border-color 180ms ease;
}

.back-link {
  color: var(--sand-muted);
  background: rgba(28, 19, 13, 0.72);
  border: 1px solid var(--border);
}

.edit-btn {
  border: none;
  color: #fff6ec;
  background: linear-gradient(135deg, var(--clay), var(--ember));
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(192, 106, 47, 0.28);
}

.back-link:hover,
.edit-btn:hover {
  transform: translateY(-1px);
}

.back-link:hover {
  background: rgba(42, 28, 19, 0.9);
  border-color: rgba(216, 178, 126, 0.38);
}

.hero-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 2rem;
  align-items: end;
  position: relative;
  z-index: 1;
}

.hero-kicker,
.scenario-label {
  margin: 0 0 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 0.72rem;
  font-weight: 800;
  color: var(--gold);
}

.hero-copy h1 {
  font-family: var(--font-display);
  margin: 0;
  font-size: clamp(2.5rem, 6vw, 5.4rem);
  line-height: 0.92;
  color: var(--sand);
  text-shadow: 0 10px 34px rgba(0, 0, 0, 0.34);
}

.hero-description {
  max-width: 680px;
  margin: 1.2rem 0 0;
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--sand-muted);
}

.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  margin-top: 1.6rem;
}

.hero-stat {
  min-width: 120px;
  border-radius: 18px;
  padding: 0.85rem 1rem;
  background: rgba(18, 12, 8, 0.72);
  border: 1px solid var(--border);
  box-shadow: inset 0 1px 0 rgba(255, 235, 211, 0.06);
}

.hero-stat span {
  display: block;
  font-size: 1.35rem;
  font-weight: 850;
  color: var(--sand);
}

.hero-stat small {
  color: var(--gold);
  font-weight: 700;
}

.hero-profile-card {
  border-radius: 24px;
  padding: 1rem;
  background: var(--panel-soft);
  border: 1px solid var(--border);
  box-shadow: inset 0 1px 0 rgba(255, 235, 211, 0.07);
}

.profile-kicker {
  margin: 0 0 0.62rem;
  font-size: 0.7rem;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  color: var(--sand-muted);
  font-weight: 800;
}

.level-pill {
  display: inline-flex;
  border-radius: 999px;
  padding: 0.38rem 0.72rem;
  font-size: 0.75rem;
  font-weight: 850;
  text-transform: uppercase;
  border: 1px solid rgba(216, 178, 126, 0.38);
}

.level-pill--language {
  background: rgba(53, 88, 55, 0.35);
  color: #cdf2d0;
}

.level-pill--dialect {
  background: rgba(138, 83, 31, 0.35);
  color: #ffe1c1;
}

.level-pill--family {
  background: rgba(78, 59, 142, 0.32);
  color: #e2d9ff;
}

.profile-row {
  margin-top: 0.9rem;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  color: var(--sand-muted);
}

.profile-row span {
  color: var(--gold);
  font-weight: 750;
}

.profile-row a,
.profile-row strong {
  color: var(--sand);
  font-weight: 850;
  text-decoration: none;
}

.top-grid {
  margin-top: 1.2rem;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.2rem;
}

.panel-card {
  padding: 1.15rem;
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.panel-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 18px 46px rgba(0, 0, 0, 0.38);
  border-color: rgba(216, 178, 126, 0.34);
}

.panel-header {
  margin-bottom: 0.45rem;
}

.panel-title-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--sand);
}

.panel-title-row :deep(svg) {
  color: var(--gold);
}

.panel-title-row h2 {
  margin: 0;
  font-size: 1.1rem;
}

.panel-counter {
  margin: 0.62rem 0 0;
  color: var(--sand-muted);
  font-size: 0.84rem;
  font-weight: 750;
}

.country-pills {
  margin-top: 1rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.country-layout {
  margin-top: 0.88rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 320px);
  gap: 0.9rem;
  align-items: start;
}

.country-mini-map {
  border-radius: 16px;
  overflow: hidden;
}

.country-mini-map :deep(.map-card) {
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(24, 16, 12, 0.8);
  box-shadow: none;
}

.country-mini-map :deep(.map-content) {
  padding: 0.45rem;
}

.country-mini-map :deep(.country-name) {
  min-height: 1.2rem;
  margin: 0 0 0.35rem;
  color: var(--sand-muted);
  font-size: 0.75rem;
  letter-spacing: 0.01em;
}

.country-mini-map :deep(.map-svg) {
  border-radius: 12px;
  border: 1px solid rgba(216, 178, 126, 0.16);
  background:
      radial-gradient(circle at 24% 20%, rgba(192, 106, 47, 0.22), rgba(192, 106, 47, 0) 36%),
      linear-gradient(180deg, #19120d 0%, #120d0a 100%);
}

.country-mini-map :deep(.country-shape) {
  fill: #3a2a1f;
  stroke: #8f7359;
}

.country-mini-map :deep(.country-shape:hover),
.country-mini-map :deep(.country-shape.is-hovered),
.country-mini-map :deep(.country-shape:focus-visible) {
  fill: #705239;
  stroke: #c6a177;
  filter: drop-shadow(0 0 3px rgba(215, 161, 95, 0.24));
}

.country-mini-map :deep(.country-shape.is-highlighted) {
  fill: #c06a2f;
  stroke: #ffd4a7;
}

.country-mini-map :deep(.country-shape.is-highlighted:hover),
.country-mini-map :deep(.country-shape.is-highlighted.is-hovered),
.country-mini-map :deep(.country-shape.is-highlighted:focus-visible) {
  fill: #d78647;
  stroke: #ffdfbe;
}

.country-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border-radius: 999px;
  padding: 0.5rem 0.68rem;
  background: rgba(25, 17, 12, 0.72);
  border: 1px solid var(--border);
  color: var(--sand);
  font-weight: 750;
  transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
}

.country-pill:hover {
  transform: translateY(-1px);
  border-color: rgba(216, 178, 126, 0.42);
  background: rgba(33, 22, 15, 0.84);
}

.country-pin {
  color: var(--gold);
  flex-shrink: 0;
}

.country-name {
  color: var(--sand);
}

.country-code {
  color: #f8d6ab;
  font-size: 0.72rem;
  font-weight: 850;
  padding: 0.06rem 0.38rem;
  border-radius: 999px;
  border: 1px solid rgba(216, 178, 126, 0.26);
  background: rgba(56, 37, 26, 0.7);
}

.empty-copy,
.about-copy {
  margin: 1rem 0 0;
  line-height: 1.7;
  color: var(--sand-muted);
}

.about-copy {
  white-space: pre-line;
}

.panel-intro {
  margin: 0.42rem 0 0;
  color: var(--sand-muted);
  font-size: 0.9rem;
  line-height: 1.45;
}

.scenario-grid {
  margin-top: 1rem;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.scenario-card {
  min-height: 210px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-radius: 22px;
  padding: 1rem;
  text-decoration: none;
  background: linear-gradient(150deg, rgba(38, 25, 17, 0.78), rgba(27, 18, 13, 0.88));
  border: 1px solid var(--border);
  color: var(--sand);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    border-color 180ms ease;
}

.scenario-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 18px 34px rgba(0, 0, 0, 0.38);
  border-color: rgba(215, 161, 95, 0.48);
}

.scenario-card:focus-visible {
  outline: 2px solid rgba(215, 161, 95, 0.38);
  outline-offset: 2px;
}

.scenario-card__top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}

.scenario-card__top :deep(svg) {
  color: var(--gold);
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.scenario-card h3 {
  margin: 0;
  font-size: 1.05rem;
  line-height: 1.35;
}

.scenario-desc {
  margin: 1rem 0;
  color: var(--sand-muted);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.scenario-meta {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  flex-wrap: wrap;
  color: var(--gold);
  font-size: 0.82rem;
  font-weight: 750;
}

.scenario-empty {
  margin-top: 1rem;
  border-radius: 22px;
  padding: 2rem;
  background: linear-gradient(150deg, rgba(28, 19, 13, 0.78), rgba(20, 13, 10, 0.88));
  border: 1px solid var(--border);
  color: var(--sand-muted);
  text-align: center;
  box-shadow: inset 0 1px 0 rgba(255, 227, 188, 0.06);
}

.scenario-empty h3 {
  margin: 0.75rem 0 0;
  color: var(--sand);
  font-size: 1rem;
}

.scenario-empty p {
  margin: 0.58rem 0 0;
  color: var(--sand-muted);
  font-size: 0.9rem;
}

.scenario-empty-icon {
  color: var(--gold);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.62rem;
}

.form-grid label {
  display: grid;
  gap: 0.28rem;
  font-size: 0.8rem;
  color: var(--sand-muted);
}

.form-grid input,
.form-grid textarea {
  border-radius: 10px;
  border: 1px solid var(--border);
  background: rgba(21, 14, 10, 0.84);
  color: var(--sand);
  padding: 0.45rem 0.52rem;
  font: inherit;
}

.form-grid input:focus,
.form-grid textarea:focus {
  outline: none;
  border-color: rgba(215, 161, 95, 0.62);
  box-shadow: 0 0 0 3px rgba(192, 106, 47, 0.22);
}

.form-grid__full {
  grid-column: 1 / -1;
}

.checkbox-row {
  align-items: center;
  grid-auto-flow: column;
  justify-content: start;
  gap: 0.5rem;
}

.toolbar {
  margin-top: 0.7rem;
  display: flex;
  gap: 0.52rem;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .hero-main,
  .top-grid,
  .scenario-grid {
    grid-template-columns: 1fr;
  }

  .country-layout {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .language-detail-shell {
    width: min(1180px, calc(100% - 1.25rem));
    padding-top: 1.15rem;
  }

  .hero-card,
  .panel-card {
    border-radius: 22px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
