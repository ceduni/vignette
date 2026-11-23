import {apiFetch} from "../api/rest";
import {fetchVignette} from "../api/vignettes";
import {drawScene} from "./vignetteBackgrounds";
import {bubbleSVG, personSVG} from "./vignetteMakerArt";

// The Vignette Maker exports its PNG at twice the stage size.
const MAKER_EXPORT_SCALE = 2;

function safeName(value) {
    return String(value || "")
        .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, 120);
}

/** File name of a scene, e.g. "My story - scene 02.svg". */
export function sceneFileName(scenarioTitle, thumb, position, ext = "svg") {
    const scenario = safeName(scenarioTitle) || "scenario";
    const number = String(position).padStart(2, "0");
    const title = safeName(thumb?.title);
    return title ? `${scenario} - ${number} - ${title}.${ext}` : `${scenario} - scene ${number}.${ext}`;
}

/** Hands a blob to the browser as a file to save. */
export function saveBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.rel = "noopener";
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function blobToDataUrl(blob) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(blob);
    });
}

function imageSize(dataUrl) {
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve({width: img.naturalWidth, height: img.naturalHeight});
        img.onerror = () => reject(new Error("Could not read the scene image"));
        img.src = dataUrl;
    });
}

/**
 * SVG of a Vignette Maker drawing: characters and speech bubbles stay vector shapes.
 * The background is painted on a canvas by the maker, so it is embedded as an image.
 */
export function buildVignetteSvg(state, width, height, backgroundDataUrl) {
    const people = [...(state?.people ?? [])].sort((a, b) => (a.z ?? 0) - (b.z ?? 0));
    const background = backgroundDataUrl
        ? `<image href="${backgroundDataUrl}" x="0" y="0" width="${width}" height="${height}" preserveAspectRatio="none"/>`
        : "";
    return `<?xml version="1.0" encoding="UTF-8"?>\n`
        + `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">`
        + background
        + people.map((p) => personSVG(p, false)).join("")
        + people.map((p) => bubbleSVG(p, false)).join("")
        + `</svg>`;
}

/** SVG that wraps an image that was not made with the Vignette Maker (photo, upload…). */
export function buildImageSvg(dataUrl, width, height) {
    return `<?xml version="1.0" encoding="UTF-8"?>\n`
        + `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">`
        + `<image href="${dataUrl}" x="0" y="0" width="${width}" height="${height}"/>`
        + `</svg>`;
}

function renderBackground(sceneId, width, height) {
    const canvas = document.createElement("canvas");
    canvas.width = width * MAKER_EXPORT_SCALE;
    canvas.height = height * MAKER_EXPORT_SCALE;
    drawScene(sceneId, canvas, true);
    return canvas.toDataURL("image/png");
}

async function vignetteSceneSvg(thumb, deps) {
    const vignette = await deps.fetchVignette(thumb.id);
    const state = JSON.parse(vignette.sceneJson);
    let {imageWidth, imageHeight} = thumb;
    if (!imageWidth || !imageHeight) {
        const blob = await deps.fetchBlob(`/api/thumbnails/${thumb.id}/content`, {}, {responseType: "blob"});
        ({width: imageWidth, height: imageHeight} = await deps.imageSize(await blobToDataUrl(blob)));
    }
    const width = Math.round(imageWidth / MAKER_EXPORT_SCALE);
    const height = Math.round(imageHeight / MAKER_EXPORT_SCALE);
    return buildVignetteSvg(state, width, height, deps.renderBackground(state.sceneId, width, height));
}

async function plainSceneSvg(thumb, deps) {
    const blob = await deps.fetchBlob(`/api/thumbnails/${thumb.id}/content`, {}, {responseType: "blob"});
    if (blob.type === "image/svg+xml") return blob;
    const dataUrl = await blobToDataUrl(blob);
    const {width, height} = await deps.imageSize(dataUrl);
    return buildImageSvg(dataUrl, width, height);
}

/** Downloads one scene as an SVG file (same access rules as displaying it). */
export async function downloadSceneSvg(scenarioTitle, thumb, position, deps = {}) {
    const d = {fetchBlob: apiFetch, fetchVignette, renderBackground, imageSize, ...deps};
    const svg = thumb.vignette ? await vignetteSceneSvg(thumb, d) : await plainSceneSvg(thumb, d);
    const blob = svg instanceof Blob ? svg : new Blob([svg], {type: "image/svg+xml"});
    saveBlob(blob, sceneFileName(scenarioTitle, thumb, position, "svg"));
}

/** Downloads every scene as SVG, one file per scene, in storyboard order. */
export async function downloadAllScenesSvg(scenarioTitle, thumbs, deps = {}) {
    for (let i = 0; i < thumbs.length; i++) {
        await downloadSceneSvg(scenarioTitle, thumbs[i], i + 1, deps);
        // Small pause so browsers don't merge or block consecutive downloads.
        if (i < thumbs.length - 1) await new Promise((resolve) => setTimeout(resolve, 250));
    }
}
