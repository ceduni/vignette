<script setup>
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {fetchMyPendingInvitations, acceptInvitation, declineInvitation} from "../api/collaborators";
import {useToast} from "../composables/useToast";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseEmptyState from "../components/ui/BaseEmptyState.vue";

const router = useRouter();
const toast = useToast();

const invitations = ref([]);
const loading = ref(true);
const respondingId = ref(null);

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
          <strong>{{ invite.invitedByUsername }}</strong> invited you as
          <span class="invite-card__role">{{ invite.role }}</span>
        </div>
        <div class="invite-card__actions">
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

.invite-card__role {
  text-transform: lowercase;
  font-weight: 700;
}

.invite-card__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.invite-card__decline,
.invite-card__accept {
  border-radius: 10px;
  padding: 8px 16px;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
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
</style>