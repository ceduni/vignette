import {ref, computed} from "vue";
import {
  fetchCollaborators,
  inviteCollaborator,
  removeCollaborator,
  changeCollaboratorRole,
  fetchInviteLinks,
  createInviteLink,
  revokeInviteLink,
  acceptInvitation,
  declineInvitation,
} from "../api/collaborators";
import {useAuth} from "./useAuth";
import {useToast} from "./useToast";

export function useCollaborators(scenarioId, {authorUsername} = {}) {
  const {currentUser} = useAuth();
  const toast = useToast();

  const collaborators = ref([]);
  const inviteLinks = ref([]);
  const loading = ref(false);

  const myRole = computed(() => {
    if (currentUser.value?.username && currentUser.value.username === authorUsername?.value) {
      return "OWNER";
    }
    const mine = collaborators.value.find(
        (c) => c.username === currentUser.value?.username && c.status === "ACCEPTED"
    );
    return mine?.role ?? null;
  });

  const isOwnerRole = computed(() => myRole.value === "OWNER");
  const canEdit = computed(() => myRole.value === "OWNER" || myRole.value === "EDITOR");

  async function load() {
    if (!scenarioId.value) return;
    loading.value = true;
    try {
      collaborators.value = await fetchCollaborators(scenarioId.value);
    } catch {
      collaborators.value = [];
    } finally {
      loading.value = false;
    }
  }

  async function loadInviteLinks() {
    if (!scenarioId.value || !isOwnerRole.value) return;
    try {
      inviteLinks.value = await fetchInviteLinks(scenarioId.value);
    } catch {
      inviteLinks.value = [];
    }
  }

  async function invite(username, role) {
    try {
      await inviteCollaborator(scenarioId.value, username, role);
      toast.success(`Invitation sent to ${username}.`);
      await load();
    } catch (e) {
      toast.error(e.message || "Failed to send invitation.");
      throw e;
    }
  }

  async function remove(userId) {
    try {
      await removeCollaborator(scenarioId.value, userId);
      toast.success("Collaborator removed.");
      await load();
    } catch (e) {
      toast.error(e.message || "Failed to remove collaborator.");
    }
  }

  async function changeRole(userId, role) {
    try {
      await changeCollaboratorRole(scenarioId.value, userId, role);
      toast.success("Role updated.");
      await load();
    } catch (e) {
      toast.error(e.message || "Failed to update role.");
    }
  }

  async function createLink({role, maxUses}) {
    try {
      const link = await createInviteLink(scenarioId.value, {role, maxUses});
      toast.success("Invite link created.");
      await loadInviteLinks();
      return link;
    } catch (e) {
      toast.error(e.message || "Failed to create invite link.");
      throw e;
    }
  }

  async function revokeLink(linkId) {
    try {
      await revokeInviteLink(linkId);
      toast.success("Invite link revoked.");
      await loadInviteLinks();
    } catch (e) {
      toast.error(e.message || "Failed to revoke invite link.");
    }
  }

  async function accept(collaboratorId) {
    try {
      await acceptInvitation(collaboratorId);
      toast.success("Invitation accepted.");
    } catch (e) {
      toast.error(e.message || "Failed to accept invitation.");
    }
  }

  async function decline(collaboratorId) {
    try {
      await declineInvitation(collaboratorId);
      toast.success("Invitation declined.");
    } catch (e) {
      toast.error(e.message || "Failed to decline invitation.");
    }
  }

  return {
    collaborators,
    inviteLinks,
    loading,
    myRole,
    isOwnerRole,
    canEdit,
    load,
    loadInviteLinks,
    invite,
    remove,
    changeRole,
    createLink,
    revokeLink,
    accept,
    decline,
  };
}