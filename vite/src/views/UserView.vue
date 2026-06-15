<script setup>
import {computed, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";
import {fetchMyProfile, updateMyProfile} from "../api/users";
import {fetchMyScenarios, fetchScenarios} from "../api/scenarios";
import {useAuth} from "../composables/useAuth";
import {createAccreditationRequest, fetchAccreditationRequests} from "../api/community";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useLanguageFollows} from "../composables/useLanguageFollows";

const {currentUser, loadMe} = useAuth();
const { likedIds, bookmarkedIds } = useScenarioInteractions();
const { followedIds, followedLanguagesList } = useLanguageFollows();

// followedLanguagesList est déjà un computed réactif — pas besoin de le wrapper
const followedLanguages = followedLanguagesList;

const profile = ref({
  displayName: "",
  institution: "",
  researchInterests: "",
  bio: "",
  academyAffiliations: "",
  profilePublic: false,
});

const roles = ref([]);
const affiliations = ref([]);
const myScenarios = ref([]);
const allScenarios = ref([]);
const error = ref("");
const success = ref("");
const loading = ref(false);
const loadingWorks = ref(false);

const myRequests = ref([]);
const loadingRequests = ref(false);
const requestError = ref("");
const requestSuccess = ref("");

const accreditationForm = ref({
  motivation: "",
});

// --- Likes & Bookmarks ---
const likedScenarios = computed(() =>
  allScenarios.value.filter((s) => likedIds.value.has(String(s.id)))
);

const bookmarkedScenarios = computed(() =>
  allScenarios.value.filter((s) => bookmarkedIds.value.has(String(s.id)))
);

function statusVariant(status) {
  if (status === "APPROVED") return "badge--success";
  if (status === "REJECTED") return "badge--danger";
  return "badge--info";
}

function statusLabel(status) {
  if (status === "APPROVED") return "Approved";
  if (status === "REJECTED") return "Rejected";
  return "Pending";
}

function scenarioBadgeClass(status) {
  if (status === "PUBLISHED") return "badge--success";
  return "badge--warning";
}

const privateCount = computed(() =>
    myScenarios.value.filter((s) => s.visibilityStatus !== "PUBLISHED").length
);

const publishedCount = computed(() =>
    myScenarios.value.filter((s) => s.visibilityStatus === "PUBLISHED").length
);

const pendingRequestsCount = computed(() =>
    myRequests.value.filter((r) => r.status === "PENDING").length
);

async function loadProfile() {
  const data = await fetchMyProfile();
  profile.value = {
    displayName: data.displayName ?? "",
    institution: data.institution ?? "",
    researchInterests: data.researchInterests ?? "",
    bio: data.bio ?? "",
    academyAffiliations: (data.academyAffiliations ?? []).join("\n"),
    profilePublic: !!data.profilePublic,
  };
  roles.value = data.roles ?? [];
  affiliations.value = data.academyAffiliations ?? [];
}

async function loadMyWorks() {
  loadingWorks.value = true;
  try {
    await loadMe();
    const data = await fetchScenarios();
    allScenarios.value = Array.isArray(data) ? data : [];
    const username = currentUser.value?.username ?? null;
    myScenarios.value = username
        ? allScenarios.value.filter((s) => s.authorUsername === username)
        : [];
  } finally {
    loadingWorks.value = false;
  }
}

async function save() {
  loading.value = true;
  error.value = "";
  success.value = "";
  try {
    await updateMyProfile({
      displayName: profile.value.displayName,
      institution: profile.value.institution,
      researchInterests: profile.value.researchInterests,
      bio: profile.value.bio,
      academyAffiliations: profile.value.academyAffiliations
          .split("\n").map((s) => s.trim()).filter(Boolean),
      profilePublic: profile.value.profilePublic,
    });
    success.value = "Profile saved.";
    await loadProfile();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function loadWorkspace() {
  loading.value = true;
  error.value = "";
  try {
    await loadMe();
    myScenarios.value = await fetchMyScenarios();
  } catch (e) {
    error.value = e.message || "Failed to load workspace.";
  } finally {
    loading.value = false;
  }
}

async function loadMyRequests() {
  loadingRequests.value = true;
  try {
    myRequests.value = await fetchAccreditationRequests("", "GLOBAL", "");
  } catch {
    // silencieux si pas de droits
  } finally {
    loadingRequests.value = false;
  }
}

async function submitAccreditationRequest() {
  requestError.value = "";
  requestSuccess.value = "";
  try {
    await createAccreditationRequest({
      permissionType: accreditationForm.value.permissionType,
      scopeType: "GLOBAL",
      targetId: null,
      motivation: accreditationForm.value.motivation,
    });
    requestSuccess.value = "Request submitted successfully.";
    accreditationForm.value.motivation = "";
    await loadMyRequests();
  } catch (e) {
    requestError.value = e.message || "Failed to submit request.";
  }
}

onMounted(async () => {
  try {
    await Promise.all([
      loadProfile(),
      loadMyWorks(),
      loadWorkspace(),
      loadMyRequests(),
    ]);
  } catch (e) {
    error.value = e.message;
  }
});
</script>

<template>
  <main class="page">

    <!-- Profil -->
    <section class="section">
      <div class="section-heading">
        <div>
          <h1>User profile</h1>
          <p class="muted">Manage your public presence, affiliations and personal work.</p>
        </div>
      </div>

      <section class="form-card">
        <h2>Profile information</h2>
        <label>Display name<input v-model="profile.displayName"/></label>
        <label>Institution<input v-model="profile.institution"/></label>
        <label>Research interests<input v-model="profile.researchInterests"/></label>
        <label>Biography<textarea v-model="profile.bio" rows="5"/></label>
        <label>Academic affiliations (one per line)<textarea v-model="profile.academyAffiliations" rows="5"/></label>
        <label class="checkbox-row">
          <input v-model="profile.profilePublic" type="checkbox"/>
          <span>Make my profile public</span>
        </label>
        <button class="btn btn--primary" @click="save" :disabled="loading">
          {{ loading ? "Saving..." : "Save profile" }}
        </button>
        <p v-if="success" class="success">{{ success }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </section>

      <div class="card-grid">
        <section class="card">
          <h3>Roles</h3>
          <div v-if="roles.length" class="user-tags">
            <span v-for="r in roles" :key="r" class="badge badge--info">{{ r }}</span>
          </div>
          <p v-else class="muted">No roles available.</p>
        </section>

        <section class="card">
          <h3>Affiliations</h3>
          <div v-if="affiliations.length" class="user-tags">
            <span v-for="a in affiliations" :key="a" class="badge badge--neutral">{{ a }}</span>
          </div>
          <p v-else class="muted">No affiliations listed.</p>
        </section>
      </div>
    </section>

    <hr class="section-divider"/>

    <!-- Workspace -->
    <section class="section">
      <div class="section-heading">
        <div>
          <h2>Workspace</h2>
          <p class="muted">Private overview for your scenarios, publication state and management shortcuts.</p>
        </div>
      </div>

      <div class="stats-row">
        <div class="stat-card">
          <span class="stat-card__value">{{ myScenarios.length }}</span>
          <span class="stat-card__label">Total scenarios</span>
        </div>
        <div class="stat-card stat-card--success">
          <span class="stat-card__value">{{ publishedCount }}</span>
          <span class="stat-card__label">Published</span>
        </div>
        <div class="stat-card stat-card--warning">
          <span class="stat-card__value">{{ privateCount }}</span>
          <span class="stat-card__label">Draft</span>
        </div>
      </div>

      <div v-if="loadingWorks" class="loader-block">
        <span class="loader-spinner"></span>
        <span class="muted">Loading workspace...</span>
      </div>

      <div v-else-if="myScenarios.length" class="card-grid">
        <article v-for="scenario in myScenarios" :key="scenario.id" class="scenario-card">
          <div class="scenario-card__top">
            <h3 class="scenario-card__title">{{ scenario.title || "Untitled scenario" }}</h3>
            <span class="badge" :class="scenarioBadgeClass(scenario.visibilityStatus)">
              {{ scenario.visibilityStatus || "UNKNOWN" }}
            </span>
          </div>
          <p class="scenario-card__desc">{{ scenario.description || "No description provided." }}</p>
          <p class="scenario-card__lang muted">Language ID: {{ scenario.languageId || "-" }}</p>
          <div class="scenario-card__actions">
            <RouterLink :to="`/scenarios/${scenario.id}`" class="btn btn--ghost">Open</RouterLink>
            <RouterLink :to="`/scenarios/${scenario.id}/manage`" class="btn btn--primary">Manage</RouterLink>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">
        <h3>No scenario yet</h3>
        <p class="muted">You have not created any scenario yet.</p>
      </div>
    </section>

    <hr class="section-divider"/>

    <!-- Likes & Bookmarks -->
    <section class="section">
      <div class="section-heading">
        <div>
          <h2>Likes &amp; bookmarks</h2>
          <p class="muted">Scenarios you've liked or saved for later.</p>
        </div>
        <div class="interactions-counters">
          <span class="interaction-counter">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="var(--primary)" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            {{ likedIds.size }} liked
          </span>
          <span class="interaction-counter">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="var(--primary)" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
            </svg>
            {{ bookmarkedIds.size }} saved
          </span>
        </div>
      </div>

      <div class="interactions-grid">

        <!-- Liked -->
        <section class="card interactions-panel">
          <div class="interactions-panel__header">
            <div class="interactions-panel__title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="var(--primary)" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
              <h3>Liked scenarios</h3>
            </div>
            <span class="badge badge--neutral">{{ likedScenarios.length }}</span>
          </div>

          <div v-if="likedScenarios.length" class="interactions-list">
            <RouterLink
              v-for="s in likedScenarios"
              :key="s.id"
              :to="`/scenarios/${s.id}`"
              class="interaction-scenario-row"
            >
              <div class="interaction-scenario-row__info">
                <p class="interaction-scenario-row__title">{{ s.title || "Untitled scenario" }}</p>
                <p class="interaction-scenario-row__meta muted">By {{ s.authorUsername ?? "Unknown" }}</p>
              </div>
              <span class="badge" :class="scenarioBadgeClass(s.visibilityStatus)">
                {{ s.visibilityStatus || "UNKNOWN" }}
              </span>
            </RouterLink>
          </div>

          <div v-else class="interactions-empty">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-soft); opacity: 0.4;">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            <p class="muted">No liked scenarios yet.</p>
          </div>
        </section>

        <!-- Bookmarked -->
        <section class="card interactions-panel">
          <div class="interactions-panel__header">
            <div class="interactions-panel__title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="var(--primary)" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
              </svg>
              <h3>Saved scenarios</h3>
            </div>
            <span class="badge badge--neutral">{{ bookmarkedScenarios.length }}</span>
          </div>

          <div v-if="bookmarkedScenarios.length" class="interactions-list">
            <RouterLink
              v-for="s in bookmarkedScenarios"
              :key="s.id"
              :to="`/scenarios/${s.id}`"
              class="interaction-scenario-row"
            >
              <div class="interaction-scenario-row__info">
                <p class="interaction-scenario-row__title">{{ s.title || "Untitled scenario" }}</p>
                <p class="interaction-scenario-row__meta muted">By {{ s.authorUsername ?? "Unknown" }}</p>
              </div>
              <span class="badge" :class="scenarioBadgeClass(s.visibilityStatus)">
                {{ s.visibilityStatus || "UNKNOWN" }}
              </span>
            </RouterLink>
          </div>

          <div v-else class="interactions-empty">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-soft); opacity: 0.4;">
              <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
            </svg>
            <p class="muted">No saved scenarios yet.</p>
          </div>
        </section>

      </div>
    </section>

    <hr class="section-divider"/>

    <!-- Langages suivis -->
    <section class="section">
      <div class="section-heading">
        <div>
          <h2>Followed languages</h2>
          <p class="muted">Languages you are following for updates and new storyboards.</p>
        </div>
        <span class="interaction-counter">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
          {{ followedLanguages.length }} following
        </span>
      </div>

      <div v-if="followedLanguages.length" class="followed-languages-list">
        <RouterLink
          v-for="lang in followedLanguages"
          :key="lang.id"
          :to="`/languages/${lang.id}`"
          class="followed-language-row"
        >
          <div class="followed-language-row__icon">
            {{ (lang.name ?? "?").slice(0, 2).toUpperCase() }}
          </div>
          <div class="followed-language-row__info">
            <p class="followed-language-row__name">{{ lang.name }}</p>
            <p class="followed-language-row__id muted">ID: {{ lang.id }}</p>
          </div>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-soft); flex-shrink: 0;">
            <path d="m9 18 6-6-6-6"/>
          </svg>
        </RouterLink>
      </div>

      <div v-else class="interactions-empty">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-soft); opacity: 0.4;">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
        <p class="muted">You are not following any language yet.</p>
      </div>
    </section>

    <hr class="section-divider"/>

    <!-- Accréditation -->
    <section class="section">
      <div class="section-heading">
        <div>
          <h2>Accreditation</h2>
          <p class="muted">Request permissions to contribute to languages or scenarios.</p>
        </div>
        <div v-if="pendingRequestsCount > 0" class="badge badge--warning">
          {{ pendingRequestsCount }} pending
        </div>
      </div>

      <div class="accreditation-layout">

        <!-- Formulaire -->
        <section class="form-card accreditation-form">
          <h3>New request</h3>

          <div class="accreditation-form__info">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/><path d="M12 10v6"/><path d="M12 7h.01"/>
            </svg>
            Requesting <strong>Community review</strong> access — allows you to moderate and review community contributions.
          </div>

          <label>
            Motivation
            <textarea
                v-model="accreditationForm.motivation"
                rows="4"
                placeholder="Explain your background and why you need this permission..."
            />
          </label>

          <div class="accreditation-form__footer">
            <span class="muted" style="font-size: 0.83rem;">
              Your request will be reviewed by an administrator.
            </span>
            <button
                class="btn btn--primary"
                :disabled="!accreditationForm.motivation.trim()"
                @click="submitAccreditationRequest"
            >
              Submit request
            </button>
          </div>

          <p v-if="requestSuccess" class="success">{{ requestSuccess }}</p>
          <p v-if="requestError" class="error">{{ requestError }}</p>
        </section>

        <!-- Historique -->
        <div class="accreditation-history">
          <h3 class="accreditation-history__title">Request history</h3>

          <div v-if="loadingRequests" class="loader-block">
            <span class="loader-spinner"></span>
            <span class="muted">Loading...</span>
          </div>

          <div v-else-if="myRequests.length" class="accreditation-list">
            <article v-for="req in myRequests" :key="req.id" class="accreditation-item">
              <div class="accreditation-item__top">
                <div class="accreditation-item__info">
                  <span class="accreditation-item__type">{{ req.permissionType }}</span>
                  <span class="accreditation-item__scope muted">{{ req.scopeType }}</span>
                </div>
                <span class="badge" :class="statusVariant(req.status)">
                  {{ statusLabel(req.status) }}
                </span>
              </div>
              <p class="accreditation-item__motivation">
                {{ req.motivation || "No motivation provided." }}
              </p>
              <p v-if="req.reviewNote" class="accreditation-item__note">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                {{ req.reviewNote }}
              </p>
            </article>
          </div>

          <div v-else class="accreditation-empty">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-soft); opacity: 0.5;">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            <p class="muted">No requests submitted yet.</p>
          </div>
        </div>

      </div>
    </section>

  </main>
</template>

<style scoped>
.section-divider {
  border: none;
  border-top: 1px solid var(--border);
  margin: 0.5rem 1rem;
}

/* Tags */
.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 1rem;
}

.stat-card {
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-card--success {
  border-color: #b7e4c7;
  background: linear-gradient(180deg, #FFFCF7 0%, #f0fff6 100%);
}

.stat-card--warning {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #FFFCF7 0%, #fff7ef 100%);
}

.stat-card__value {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--text);
  line-height: 1;
}

.stat-card__label {
  font-size: 0.82rem;
  color: var(--text-soft);
  font-weight: 500;
}

/* Scénarios workspace */
.scenario-card {
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 1.1rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.scenario-card:hover {
  border-color: rgba(192, 74, 8, 0.22);
  box-shadow: 0 12px 32px rgba(42, 21, 0, 0.10);
}

.scenario-card__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
}

.scenario-card__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
}

.scenario-card__desc {
  margin: 0;
  font-size: 0.88rem;
  color: var(--text-soft);
  line-height: 1.5;
}

.scenario-card__lang {
  margin: 0;
  font-size: 0.82rem;
}

.scenario-card__actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

/* Likes & Bookmarks */
.interactions-counters {
  display: flex;
  gap: 0.75rem;
}

.interaction-counter {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-soft);
}

.interactions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
}

@media (max-width: 780px) {
  .interactions-grid {
    grid-template-columns: 1fr;
  }
}

.interactions-panel {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
}

.interactions-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.interactions-panel__title {
  display: flex;
  align-items: center;
  gap: 7px;
}

.interactions-panel__title h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
}

.interactions-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.interaction-scenario-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--border);
  border-radius: 10px;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.15s, background 0.15s;
}

.interaction-scenario-row:hover {
  border-color: rgba(192, 74, 8, 0.3);
  background: rgba(255, 224, 192, 0.2);
}

.interaction-scenario-row__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.interaction-scenario-row__title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.interaction-scenario-row__meta {
  margin: 0;
  font-size: 0.76rem;
}

.interactions-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem 1rem;
  text-align: center;
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}

/* Accréditation */
.accreditation-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
  align-items: start;
}

@media (max-width: 780px) {
  .accreditation-layout { grid-template-columns: 1fr; }
  .stats-row { grid-template-columns: 1fr; }
}

.accreditation-form { gap: 1rem; }

.accreditation-form__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.permission-option:hover {
  background: var(--accent-warm);
  border-color: rgba(192, 74, 8, 0.25);
}

.accreditation-history {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.accreditation-history__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
}

.accreditation-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.accreditation-item {
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 1rem 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  transition: border-color 0.15s;
}

.accreditation-item__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.accreditation-item__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.accreditation-item__type {
  font-weight: 700;
  font-size: 0.9rem;
  color: var(--text);
}

.accreditation-item__scope { font-size: 0.78rem; }

.accreditation-item__motivation {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text-soft);
  line-height: 1.5;
  font-style: italic;
}

.accreditation-item__note {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-soft);
  display: flex;
  align-items: flex-start;
  gap: 5px;
  padding: 6px 10px;
  background: var(--accent-warm);
  border-radius: 8px;
}

.accreditation-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 2rem 1rem;
  text-align: center;
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}

.badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 0.2rem 0.65rem;
  font-size: 0.82rem;
  font-weight: 700;
  background: var(--surface-alt);
  white-space: nowrap;
}

.badge--success { background: #ebfff3; color: #067647; border-color: #b7e4c7; }
.badge--warning { background: #fff7e6; color: #b54708; border-color: #fed7aa; }
.badge--danger { background: #fff1f2; color: #b42318; border-color: #fecdd3; }
.badge--info { background: #fff5e0; color: #7a4a00; border-color: #f5d090; }
.badge--neutral { background: #fbf6f0; color: #6a4a28; border-color: var(--border); }

.toolbar--spread { justify-content: space-between; align-items: center; }

.accreditation-form__info {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  background: var(--accent-warm);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 0.88rem;
  color: var(--text-soft);
  line-height: 1.5;
}

/* Followed languages */
.followed-languages-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.followed-language-row {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem 1rem;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  border: 1px solid var(--border);
  border-radius: 12px;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.followed-language-row:hover {
  border-color: rgba(192, 74, 8, 0.3);
  box-shadow: 0 4px 16px rgba(42, 21, 0, 0.07);
}

.followed-language-row__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-strong) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.82rem;
  font-weight: 800;
  flex-shrink: 0;
}

.followed-language-row__info {
  flex: 1;
  min-width: 0;
}

.followed-language-row__name {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text);
}

.followed-language-row__id {
  margin: 0;
  font-size: 0.76rem;
}
</style>