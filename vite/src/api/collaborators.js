import {apiFetch} from "./rest";

export function searchUsers(query) {
  return apiFetch(`/api/users/search?q=${encodeURIComponent(query)}`);
}

export function fetchCollaborators(scenarioId) {
  return apiFetch(`/api/scenarios/${scenarioId}/collaborators`);
}

export function fetchMyPendingInvitations() {
  return apiFetch("/api/collaborators/invitations");
}

export function inviteCollaborator(scenarioId, username, role) {
  return apiFetch(`/api/scenarios/${scenarioId}/collaborators/invite`, {
    method: "POST",
    body: {username, role},
  });
}

export function acceptInvitation(collaboratorId) {
  return apiFetch(`/api/collaborators/${collaboratorId}/accept`, {
    method: "POST",
  });
}

export function declineInvitation(collaboratorId) {
  return apiFetch(`/api/collaborators/${collaboratorId}/decline`, {
    method: "POST",
  });
}

export function removeCollaborator(scenarioId, userId) {
  return apiFetch(`/api/scenarios/${scenarioId}/collaborators/${userId}`, {
    method: "DELETE",
  });
}

export function changeCollaboratorRole(scenarioId, userId, role) {
  return apiFetch(`/api/scenarios/${scenarioId}/collaborators/${userId}/role`, {
    method: "PATCH",
    body: {role},
  });
}

export function fetchInviteLinks(scenarioId) {
  return apiFetch(`/api/scenarios/${scenarioId}/invite-links`);
}

export function createInviteLink(scenarioId, {role, maxUses}) {
  return apiFetch(`/api/scenarios/${scenarioId}/invite-links`, {
    method: "POST",
    body: {role, maxUses: maxUses ?? null},
  });
}

export function revokeInviteLink(linkId) {
  return apiFetch(`/api/invite-links/${linkId}`, {
    method: "DELETE",
  });
}

export function joinViaInviteLink(token) {
  return apiFetch(`/api/invite-links/${token}/join`, {
    method: "POST",
  });
}