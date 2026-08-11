import {apiFetch} from "./rest";

export function fetchMyScenes() {
    return apiFetch("/api/vignette-scenes");
}

export function saveScene(name, sceneJson) {
    return apiFetch("/api/vignette-scenes", {
        method: "POST",
        body: {name, sceneJson},
    });
}

export function updateScene(id, name, sceneJson) {
    return apiFetch(`/api/vignette-scenes/${id}`, {
        method: "PATCH",
        body: {name, sceneJson},
    });
}

export function deleteScene(id) {
    return apiFetch(`/api/vignette-scenes/${id}`, {
        method: "DELETE",
    });
}
