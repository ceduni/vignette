import {apiFetch} from "./rest";

/** Saves a Vignette Maker scene into a scenario: the image becomes a scene, the drawing is kept with it. */
export function saveScenarioVignette(scenarioId, {image, sceneJson, title = ""}) {
    const fd = new FormData();
    fd.append("title", title);
    fd.append("sceneJson", sceneJson);
    fd.append("image", image);
    return apiFetch(`/api/scenarios/${scenarioId}/vignettes`, {method: "POST", body: fd});
}

/** The drawing of a scene made with the Vignette Maker, to reopen it in the editor. */
export function fetchVignette(thumbnailId) {
    return apiFetch(`/api/thumbnails/${thumbnailId}/vignette`);
}

/** Replaces the image and drawing of a Vignette Maker scene (position, title and audios are kept). */
export function updateVignette(thumbnailId, {image, sceneJson}) {
    const fd = new FormData();
    fd.append("sceneJson", sceneJson);
    fd.append("image", image);
    return apiFetch(`/api/thumbnails/${thumbnailId}/vignette`, {method: "PUT", body: fd});
}
