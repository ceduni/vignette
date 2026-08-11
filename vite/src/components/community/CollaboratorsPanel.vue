<script setup>
import {ref, watch} from "vue";
import {searchUsers} from "../../api/collaborators";
import {useToast} from "../../composables/useToast";

const props = defineProps({
  scenarioId: {type: [String, Number], required: true},
  collaborators: {type: Array, default: () => []},
  inviteLinks: {type: Array, default: () => []},
  isOwner: {type: Boolean, default: false},
  authorUsername: {type: String, default: ""},
});

const emit = defineEmits(["close", "invite", "remove", "change-role", "create-link", "revoke-link"]);

const toast = useToast();

const searchQuery = ref("");
const searchResults = ref([]);
const searching = ref(false);
const selectedRole = ref("EDITOR");
const inviting = ref(false);

let searchTimeout = null;
watch(searchQuery, (q) => {
  clearTimeout(searchTimeout);
  if (!q || q.trim().length < 2) {
    searchResults.value = [];
    return;
  }
  searchTimeout = setTimeout(async () => {
    searching.value = true;
    try {
      searchResults.value = await searchUsers(q.trim());
    } catch (e) {
      console.error("[collaborators] user search failed:", e);
      searchResults.value = [];
    } finally {
      searching.value = false;
    }
  }, 250);
});

async function pickUser(user) {
  inviting.value = true;
  try {
    await emit("invite", user.username, selectedRole.value);
    searchQuery.value = "";
    searchResults.value = [];
  } finally {
    inviting.value = false;
  }
}

const linkRole = ref("EDITOR");
const creatingLink = ref(false);

async function handleCreateLink() {
  creatingLink.value = true;
  try {
    await emit("create-link", {role: linkRole.value});
  } finally {
    creatingLink.value = false;
  }
}

async function copyLink(token) {
  const url = `${window.location.origin}/scenarios/join/${token}`;
  try {
    await navigator.clipboard.writeText(url);
    toast.success("Invite link copied to clipboard.");
  } catch {
    toast.error("Could not copy link.");
  }
}

function roleLabel(role) {
  return {OWNER: "Owner", EDITOR: "Editor", VIEWER: "Viewer"}[role] || role;
}

function statusLabel(status) {
  return {PENDING: "Pending", ACCEPTED: "Active", DECLINED: "Declined", EXPIRED: "Expired"}[status] || status;
}

function formatExpiry(expiresAt) {
  if (!expiresAt) return null;
  const diffMs = new Date(expiresAt).getTime() - Date.now();
  if (diffMs <= 0) return "Expired";
  const days = Math.floor(diffMs / (24 * 3600 * 1000));
  if (days >= 1) return `Expires in ${days}d`;
  const hours = Math.max(1, Math.floor(diffMs / (3600 * 1000)));
  return `Expires in ${hours}h`;
}
</script>

<template>
  <div class="cp-card" role="dialog" aria-modal="true" aria-labelledby="collaborators-title">
    <div class="cp-head">
      <div>
        <p class="cp-eyebrow">Team</p>
        <h2 id="collaborators-title" class="cp-title">Collaborators</h2>
      </div>
      <button type="button" class="cp-close" aria-label="Close" @click="emit('close')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18 6 6 18M6 6l12 12"/>
        </svg>
      </button>
    </div>

    <div v-if="isOwner" class="cp-section">
      <p class="cp-section__label">Invite by username</p>
      <div class="cp-invite-row">
        <input
            v-model="searchQuery"
            class="cp-input"
            placeholder="Search a username…"
            autocomplete="off"
        />
        <select v-model="selectedRole" class="cp-select">
          <option value="EDITOR">Editor</option>
          <option value="VIEWER">Viewer</option>
        </select>
      </div>
      <ul v-if="searchResults.length" class="cp-results">
        <li v-for="user in searchResults" :key="user.id">
          <button type="button" class="cp-result" :disabled="inviting" @click="pickUser(user)">
            <span>{{ user.username }}</span>
            <span class="cp-result__add">+ Invite as {{ roleLabel(selectedRole) }}</span>
          </button>
        </li>
      </ul>
      <p v-else-if="searching" class="cp-hint">Searching…</p>
    </div>

    <div class="cp-section">
      <p class="cp-section__label">Members</p>
      <ul class="cp-list">
        <li class="cp-member cp-member--owner">
          <span class="cp-member__name">{{ authorUsername }} (owner)</span>
          <span class="cp-badge cp-badge--owner">OWNER</span>
        </li>
        <li v-for="c in collaborators" :key="c.id" class="cp-member">
          <div class="cp-member__info">
            <span class="cp-member__name">{{ c.displayName || c.username }}</span>
            <span class="cp-member__status" :class="`cp-member__status--${c.status.toLowerCase()}`">
              {{ statusLabel(c.status) }}
              <template v-if="c.status === 'PENDING' && formatExpiry(c.expiresAt)"> · {{ formatExpiry(c.expiresAt) }}</template>
            </span>
          </div>
          <div class="cp-member__actions">
            <select
                v-if="isOwner && c.status === 'ACCEPTED'"
                class="cp-select cp-select--small"
                :value="c.role"
                @change="emit('change-role', c.userId, $event.target.value)"
            >
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
            <span v-else class="cp-badge" :class="`cp-badge--${c.role.toLowerCase()}`">{{ roleLabel(c.role) }}</span>
            <button
                v-if="isOwner"
                type="button"
                class="cp-remove"
                title="Remove collaborator"
                @click="emit('remove', c.userId)"
            >×</button>
          </div>
        </li>
        <li v-if="!collaborators.length" class="cp-empty">No collaborators yet.</li>
      </ul>
    </div>

    <div v-if="isOwner" class="cp-section">
      <p class="cp-section__label">Shareable links</p>
      <div class="cp-invite-row">
        <select v-model="linkRole" class="cp-select">
          <option value="EDITOR">Editor</option>
          <option value="VIEWER">Viewer</option>
        </select>
        <button type="button" class="cp-create-link" :disabled="creatingLink" @click="handleCreateLink">
          {{ creatingLink ? "Creating…" : "Create link" }}
        </button>
      </div>
      <ul class="cp-list">
        <li v-for="link in inviteLinks" :key="link.id" class="cp-link">
          <div class="cp-link__info">
            <span class="cp-badge" :class="`cp-badge--${link.role.toLowerCase()}`">{{ roleLabel(link.role) }}</span>
            <span class="cp-link__meta">
              {{ link.useCount }} use(s){{ link.active ? "" : " · revoked" }}<template v-if="link.active && formatExpiry(link.expiresAt)"> · {{ formatExpiry(link.expiresAt) }}</template>
            </span>
          </div>
          <div class="cp-link__actions">
            <button type="button" class="cp-link__copy" :disabled="!link.active" @click="copyLink(link.token)">Copy</button>
            <button v-if="link.active" type="button" class="cp-remove" title="Revoke link" @click="emit('revoke-link', link.id)">×</button>
          </div>
        </li>
        <li v-if="!inviteLinks.length" class="cp-empty">No active links.</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.cp-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(480px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  overflow-y: auto;
  border: 3px solid #1E0812;
  border-radius: 20px;
  padding: 24px;
  background: #FFF0EE;
  color: #1E0812;
  box-shadow: 6px 6px 0 #1E0812;
}

.cp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.cp-eyebrow {
  margin: 0 0 3px;
  font-size: 0.65rem;
  font-weight: 900;
  color: #485B38;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.cp-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 950;
  color: #1E0812;
  letter-spacing: -0.02em;
}
.cp-close {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 1.5px solid #D4E5CA;
  border-radius: 999px;
  background: transparent;
  color: #785068;
  cursor: pointer;
}
.cp-close:hover { background: #1E0812; color: #FFF0EE; }
.cp-close svg { width: 14px; height: 14px; }

.cp-section { display: flex; flex-direction: column; gap: 10px; }
.cp-section__label {
  font-size: 0.65rem;
  font-weight: 900;
  color: #785068;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin: 0;
}

.cp-invite-row {
  display: flex;
  width: 100%;
  gap: 8px;
  box-sizing: border-box;
}
.cp-input {
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 9px 12px;
  background: #fff;
  color: #1E0812;
  font: inherit;
  font-size: 0.88rem;
  outline: none;
}
.cp-input:focus { border-color: #485B38; }

.cp-select {
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 9px 10px;
  background: #fff;
  color: #1E0812;
  font: inherit;
  font-size: 0.85rem;
}
.cp-select--small { padding: 5px 8px; font-size: 0.78rem; }

.cp-results {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 160px;
  overflow-y: auto;
}
.cp-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 8px 12px;
  background: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 0.85rem;
  color: #1E0812;
  transition: border-color 140ms ease;
}
.cp-result:hover:not(:disabled) { border-color: #485B38; }
.cp-result:disabled { opacity: 0.6; cursor: not-allowed; }
.cp-result__add { font-size: 0.72rem; color: #785068; }

.cp-hint { margin: 0; font-size: 0.8rem; color: #785068; }

.cp-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cp-member, .cp-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1.5px solid #D4E5CA;
  border-radius: 10px;
  padding: 9px 12px;
  background: #fff;
}

.cp-member__info { display: flex; flex-direction: column; gap: 2px; }
.cp-member__name { font-size: 0.86rem; font-weight: 700; color: #1E0812; }
.cp-member__status { font-size: 0.7rem; color: #785068; }
.cp-member__status--pending { color: #8B3010; }
.cp-member__status--declined { color: #A8334C; }
.cp-member__status--expired { color: #785068; }

.cp-member__actions, .cp-link__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cp-badge {
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  background: #D4E5CA;
  color: #1E0812;
}
.cp-badge--owner { background: #1E0812; color: #FFF0EE; }
.cp-badge--editor { background: rgba(74,103,65,0.15); color: #4A6741; }
.cp-badge--viewer { background: rgba(120,80,104,0.15); color: #785068; }

.cp-remove {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1.5px solid rgba(168,51,76,0.3);
  border-radius: 8px;
  background: transparent;
  color: #A8334C;
  font-size: 15px;
  font-weight: 900;
  cursor: pointer;
  line-height: 1;
}
.cp-remove:hover { background: rgba(168,51,76,0.08); }

.cp-create-link {
  border: 0;
  border-radius: 10px;
  padding: 9px 16px;
  background: #4A6741;
  color: #FFF0EE;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}
.cp-create-link:hover:not(:disabled) { background: #3d5534; }
.cp-create-link:disabled { opacity: 0.6; cursor: not-allowed; }

.cp-link__meta { font-size: 0.72rem; color: #785068; }
.cp-link__copy {
  border: 1.5px solid #D4E5CA;
  border-radius: 8px;
  padding: 5px 10px;
  background: transparent;
  color: #1E0812;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
}
.cp-link__copy:hover:not(:disabled) { border-color: #485B38; }
.cp-link__copy:disabled { opacity: 0.5; cursor: not-allowed; }

.cp-empty {
  font-size: 0.82rem;
  color: #785068;
  padding: 8px 0;
}
</style>