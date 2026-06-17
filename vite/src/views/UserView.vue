<script setup>
import {computed, onMounted, ref} from "vue";
import {fetchMyProfile, updateMyProfile} from "../api/users";
import {useAuth} from "../composables/useAuth";
import {createAccreditationRequest, fetchAccreditationRequests} from "../api/community";
import {useScenarioInteractions} from "../composables/useScenarioInteractions";
import {useLanguageFollows} from "../composables/useLanguageFollows";

const {currentUser} = useAuth();

const profile = ref({
  displayName: "",
  institution: "",
  researchInterests: "",
  bio: "",
  academyAffiliations: "",
  profilePublic: false,
});

const roles = ref([]);
const error = ref("");
const success = ref("");
const loading = ref(false);

const initials = computed(() => {
  const name = profile.value.displayName || currentUser.value?.username || "";
  return name.split(/\s+/).map(w => w[0]).slice(0, 2).join("").toUpperCase() || "?";
});

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
          .split("\n").map(s => s.trim()).filter(Boolean),
      profilePublic: profile.value.profilePublic,
    });
    success.value = "Saved.";
    await loadProfile();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <main class="page up-page">

    <div class="up-identity">
      <div class="up-avatar">{{ initials }}</div>
      <div class="up-identity__text">
        <h1 class="up-name">{{ profile.displayName || currentUser?.username || "My profile" }}</h1>
        <div class="up-meta">
          <span class="up-username">@{{ currentUser?.username }}</span>
          <template v-if="profile.institution">
            <span class="up-sep">·</span>
            <span>{{ profile.institution }}</span>
          </template>
        </div>
        <div v-if="roles.length" class="up-roles">
          <span v-for="r in roles" :key="r" class="up-role">{{ r }}</span>
        </div>
      </div>
    </div>

    <section class="card up-form">
      <h2 class="up-form__title">Edit profile</h2>

      <div class="form-stack">
        <div class="form-grid">
          <label>
            Display name
            <input v-model="profile.displayName" placeholder="Your full name"/>
          </label>
          <label>
            Institution
            <input v-model="profile.institution" placeholder="University or organisation"/>
          </label>
        </div>

        <label>
          Research interests
          <input v-model="profile.researchInterests" placeholder="e.g. phonology, field methods, lexicography"/>
        </label>

        <label>
          Bio
          <textarea v-model="profile.bio" rows="4" placeholder="A short description of your work and background"/>
        </label>

        <label>
          Academic affiliations
          <textarea v-model="profile.academyAffiliations" rows="3" placeholder="One affiliation per line"/>
        </label>

        <div class="up-form__bottom">
          <label class="checkbox-row">
            <input v-model="profile.profilePublic" type="checkbox"/>
            <span>Make my profile visible to other users</span>
          </label>

          <div class="up-form__actions">
            <button class="btn btn--primary" @click="save" :disabled="loading">
              {{ loading ? "Saving…" : "Save changes" }}
            </button>
            <Transition name="fade">
              <span v-if="success" class="success">{{ success }}</span>
            </Transition>
            <span v-if="error" class="error">{{ error }}</span>
          </div>
        </div>
      </div>
    </section>

  </main>
</template>

<style scoped>
.up-page {
  max-width: 720px;
}

.up-identity {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 28px 0 24px;
}

.up-avatar {
  width: 68px;
  height: 68px;
  border-radius: 999px;
  background: #F5D4CE;
  color: #5B1928;
  font-size: 1.4rem;
  font-weight: 800;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  letter-spacing: 0.04em;
}

.up-identity__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.up-name {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--text);
}

.up-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.88rem;
  color: var(--text-soft);
  flex-wrap: wrap;
}

.up-username { font-weight: 600; }

.up-sep { opacity: 0.4; }

.up-roles {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.up-role {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--accent-cool);
  color: var(--primary);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.up-form__title {
  margin: 0 0 18px;
  font-size: 1rem;
  font-weight: 800;
  color: var(--text);
}

.up-form__bottom {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 4px;
}

.up-form__actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.fade-enter-active { transition: opacity 300ms ease; }
.fade-leave-active { transition: opacity 500ms ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 600px) {
  .up-identity { flex-direction: column; align-items: flex-start; }
  .up-avatar { width: 56px; height: 56px; font-size: 1.1rem; }
  .up-name { font-size: 1.2rem; }
}
</style>
