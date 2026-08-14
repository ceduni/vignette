<script setup>
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {fetchMyPendingInvitations, acceptInvitation, declineInvitation} from "../api/collaborators";
import {fetchScenario} from "../api/scenarios";
import {useToast} from "../composables/useToast";
import {useScenarioReader} from "../composables/useScenarioReader";
import ScenarioReaderModal from "../components/scenario/ScenarioReaderModal.vue";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";

const router = useRouter();
const toast = useToast();
const {openReader, activeScenario, closeReader} = useScenarioReader();

const invitations = ref([]);
const loading = ref(true);
const respondingId = ref(null);
const previewInvite = ref(null);

async function load() {
  loading.value = true;
  try {
    invitations.value = await fetchMyPendingInvitations();
  } catch (e) {
    toast.error(e.message || "Failed to load invitations.");
  } finally {
    loading.value = false;
  }
}

async function accept(invite) {
  respondingId.value = invite.id;
  try {
    await acceptInvitation(invite.id);
    toast.success(`Joined "${invite.scenarioTitle || "scenario"}".`);
    invitations.value = invitations.value.filter((i) => i.id !== invite.id);
    router.push(`/scenarios/${invite.scenarioId}`);
  } catch (e) {
    toast.error(e.message || "Failed to accept invitation.");
  } finally {
    respondingId.value = null;
  }
}

async function decline(invite) {
  respondingId.value = invite.id;
  try {
    await declineInvitation(invite.id);
    toast.success("Invitation declined.");
    invitations.value = invitations.value.filter((i) => i.id !== invite.id);
  } catch (e) {
    toast.error(e.message || "Failed to decline invitation.");
  } finally {
    respondingId.value = null;
  }
}

// Quick read-only storyboard preview so an invitee can see what they're
// being asked to join before accepting or declining.
async function openPreview(invite) {
  previewInvite.value = invite;
  openReader({id: invite.scenarioId, title: invite.scenarioTitle, authorUsername: invite.invitedByUsername});
  try {
    const scenario = await fetchScenario(invite.scenarioId);
    if (previewInvite.value?.id !== invite.id) return; // closed/switched while fetching
    openReader({id: invite.scenarioId, title: scenario.title, authorUsername: scenario.authorUsername});
  } catch {
    // Keep the fallback title/author from the invite itself.
  }
}

function closePreview() {
  previewInvite.value = null;
  closeReader();
}

async function acceptFromPreview() {
  if (!previewInvite.value) return;
  await accept(previewInvite.value);
  closePreview();
}

async function declineFromPreview() {
  if (!previewInvite.value) return;
  await decline(previewInvite.value);
  closePreview();
}

onMounted(load);
</script>

<template>
  <main class="page">
    <h1>Collaboration invitations</h1>

    <BaseLoader v-if="loading">Loading invitations...</BaseLoader>

    <BaseEmptyState v-else-if="!invitations.length" title="No pending invitations">
      You don't have any pending collaboration invitations right now.
    </BaseEmptyState>

    <ul v-else class="invite-list">
      <li v-for="invite in invitations" :key="invite.id" class="invite-card">
        <div class="invite-card__info">
          <strong class="invite-card__title">{{ invite.scenarioTitle || "Untitled scenario" }}</strong>
          <span class="invite-card__by">
            <strong>{{ invite.invitedByUsername }}</strong> invited you as
            <span class="invite-card__role">{{ invite.role }}</span>
          </span>
        </div>
        <div class="invite-card__actions">
          <button
              type="button"
              class="invite-card__preview"
              @click="openPreview(invite)"
          >
            Preview
          </button>
          <button
              type="button"
              class="invite-card__decline"
              :disabled="respondingId === invite.id"
              @click="decline(invite)"
          >
            Decline
          </button>
          <button
              type="button"
              class="invite-card__accept"
              :disabled="respondingId === invite.id"
              @click="accept(invite)"
          >
            {{ respondingId === invite.id ? "…" : "Accept" }}
          </button>
        </div>
      </li>
    </ul>

    <ScenarioReaderModal :scenario="activeScenario" @close="closePreview">
      <template v-if="previewInvite" #actions>
        <button
            type="button"
            class="reader-invite-decline"
            :disabled="respondingId === previewInvite.id"
            @click="declineFromPreview"
        >
          Decline
        </button>
        <button
            type="button"
            class="reader-invite-accept"
            :disabled="respondingId === previewInvite.id"
            @click="acceptFromPreview"
        >
          {{ respondingId === previewInvite.id ? "…" : "Accept" }}
        </button>
      </template>
    </ScenarioReaderModal>
  </main>
</template>

<style scoped>
.invite-list {
  list-style: none;
  margin: 20px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.invite-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1.5px solid var(--border);
  border-radius: 12px;
  padding: 14px 18px;
  background: #fff;
}

.invite-card__info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.invite-card__title {
  font-size: 0.98rem;
  color: var(--text);
}

.invite-card__by {
  font-size: 0.85rem;
  color: var(--text-soft);
}

.invite-card__role {
  text-transform: lowercase;
  font-weight: 700;
}

.invite-card__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.invite-card__preview,
.invite-card__decline,
.invite-card__accept {
  border-radius: 10px;
  padding: 8px 16px;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
}

.invite-card__preview {
  border: 1.5px solid var(--border);
  background: transparent;
  color: var(--text);
}

.invite-card__decline {
  border: 1.5px solid var(--border);
  background: transparent;
  color: var(--text-soft);
}

.invite-card__accept {
  border: 0;
  background: var(--primary);
  color: #fff;
}

.invite-card__decline:disabled,
.invite-card__accept:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Accept/decline buttons rendered inside the dark ScenarioReaderModal */
.reader-invite-decline,
.reader-invite-accept {
  border-radius: 999px;
  padding: 0.55rem 1.4rem;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.reader-invite-decline {
  border: 1.5px solid rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 244, 236, 0.8);
}

.reader-invite-decline:hover:not(:disabled) {
  background: rgba(168, 51, 76, 0.25);
  border-color: #A8334C;
  color: #FFF4EC;
}

.reader-invite-accept {
  border: 0;
  background: var(--primary);
  color: #fff;
}

.reader-invite-accept:hover:not(:disabled) {
  background: var(--primary-strong);
}

.reader-invite-decline:disabled,
.reader-invite-accept:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>