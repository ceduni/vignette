import {apiFetch} from "./rest";

export function fetchScenarios() {
    return apiFetch("/api/scenarios");
}

export function fetchMyScenarios() {
    return apiFetch("/api/scenarios/mine");
}

export function fetchScenario(id) {
    return apiFetch(`/api/scenarios/${id}`);
}

export function createScenario(body) {
    return apiFetch("/api/scenarios", {
        method: "POST",
        body,
    });
}

export function forkScenario(id) {
  return apiFetch(`/api/scenarios/${id}/fork`, {
    method: "POST",
  });
}

export function publishScenario(id) {
    return apiFetch(`/api/scenarios/${id}/publish`, {
        method: "POST",
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

export function uploadThumbnailAudio(id, formData) {
    return apiFetch(`/api/thumbnails/${id}/audios`, {
        method: "POST",
        body: formData,
    });
}

export function updateAudioMarker(audioId, body) {
    return apiFetch(`/api/audios/${audioId}/marker`, {
        method: "PATCH",
        body,
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