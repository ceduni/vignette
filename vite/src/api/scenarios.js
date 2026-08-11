import {apiFetch} from "./rest";
export function fetchScenarios() {
return apiFetch("/api/scenarios");
}
export function fetchMyScenarios() {
return apiFetch("/api/scenarios/mine");
}
export function fetchSharedWithMeScenarios() {
return apiFetch("/api/scenarios/shared-with-me");
}
export function fetchScenariosByAuthor(username) {
return apiFetch(`/api/scenarios/by-author/${encodeURIComponent(username)}`);
}
export function fetchScenario(id) {
return apiFetch(`/api/scenarios/${id}`);
}
export function fetchScenarioHistory(id) {
return apiFetch(`/api/scenarios/${id}/history`);
}
export function createScenario(body) {
return apiFetch("/api/scenarios", {
        method: "POST",
body,
});
}
export function forkScenario(id, title) {
return apiFetch(`/api/scenarios/${id}/fork`, {
    method: "POST",
    body: title ? {title} : undefined,
});
}
export function publishScenario(id) {
return apiFetch(`/api/scenarios/${id}/publish`, {
        method: "POST",
});
}
export function approveFork(id, comment) {
return apiFetch(`/api/scenarios/${id}/review/approve`, {
        method: "POST",
        body: comment ? {comment} : undefined,
});
}
export function rejectFork(id, comment) {
return apiFetch(`/api/scenarios/${id}/review/reject`, {
        method: "POST",
        body: comment ? {comment} : undefined,
});
}
export function updateScenarioStoryboard(id, body) {
return apiFetch(`/api/scenarios/${id}/storyboard`, {
        method: "PATCH",
body,
});
}
export function updateScenarioMetadata(id, body) {
return apiFetch(`/api/scenarios/${id}/metadata`, {
        method: "PATCH",
body,
});
}
export function fetchScenarioThumbnails(id) {
return apiFetch(`/api/scenarios/${id}/thumbnails`);
}
export function uploadScenarioThumbnail(id, formData) {
return apiFetch(`/api/scenarios/${id}/thumbnails`, {
        method: "POST",
        body: formData,
});
}
export function updateThumbnailLayout(id, body) {
return apiFetch(`/api/thumbnails/${id}/layout`, {
        method: "PATCH",
body,
});
}
export function fetchThumbnailAudios(id) {
return apiFetch(`/api/thumbnails/${id}/audios`);
}
export function fetchScenarioBackgroundAudios(id) {
return apiFetch(`/api/scenarios/${id}/background-audios`);
}
export function uploadThumbnailAudio(id, formData) {
return apiFetch(`/api/thumbnails/${id}/audios`, {
        method: "POST",
        body: formData,
});
}
export function uploadScenarioBackgroundAudio(id, formData) {
return apiFetch(`/api/scenarios/${id}/background-audios`, {
        method: "POST",
        body: formData,
});
}
export function reorderScenarioThumbnails(id, thumbnailIds) {
return apiFetch(`/api/scenarios/${id}/thumbnails/reorder`, {
        method: "PATCH",
        body: {thumbnailIds},
});
}
export function updateAudioGloss(audioId, body) {
return apiFetch(`/api/audios/${audioId}/gloss`, {
        method: "PATCH",
body,
});
}
export function deleteScenario(id) {
return apiFetch(`/api/scenarios/${id}`, {
        method: "DELETE",
});
}
export function deleteThumbnail(thumbnailId) {
return apiFetch(`/api/thumbnails/${thumbnailId}`, {
        method: "DELETE",
});
}
export function deleteAudio(audioId) {
return apiFetch(`/api/audios/${audioId}`, {
        method: "DELETE",
});
}