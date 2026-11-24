import {apiFetch} from "../api/rest";
import {drawScene} from "./vignetteBackgrounds";
import {bubbleSVG, personSVG} from "./vignetteMakerArt";

const BACKGROUND_SCALE = 2;
const SVG_TYPE = "image/svg+xml";

function safeName(value) {
    return String(value || "")
        .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, 120);
}

export function sceneFileName(scenarioTitle, thumb, position, ext = "svg") {
    const scenario = safeName(scenarioTitle) || "scenario";
    const number = String(position).padStart(2, "0");
    const title = safeName(thumb?.title);
    return title ? `${scenario} - ${number} - ${title}.${ext}` : `${scenario} - scene ${number}.${ext}`;
}

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

export function buildImageSvg(dataUrl, width, height) {
    return `<?xml version="1.0" encoding="UTF-8"?>\n`
        + `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">`
        + `<image href="${dataUrl}" x="0" y="0" width="${width}" height="${height}"/>`
        + `</svg>`;
}

export function renderBackground(sceneId, width, height) {
    const canvas = document.createElement("canvas");
    canvas.width = width * BACKGROUND_SCALE;
    canvas.height = height * BACKGROUND_SCALE;
    drawScene(sceneId, canvas, true);
    return canvas.toDataURL("image/png");
}

async function rasterSceneSvg(blob, deps) {
    const dataUrl = await blobToDataUrl(blob);
    const {width, height} = await deps.imageSize(dataUrl);
    return new Blob([buildImageSvg(dataUrl, width, height)], {type: SVG_TYPE});
}

export async function sceneSvgBlob(thumb, deps = {}) {
    const d = {fetchBlob: apiFetch, imageSize, ...deps};
    const blob = await d.fetchBlob(`/api/thumbnails/${thumb.id}/content`, {}, {responseType: "blob"});
    return blob.type === SVG_TYPE ? blob : rasterSceneSvg(blob, d);
}

export async function downloadSceneSvg(scenarioTitle, thumb, position, deps = {}) {
    saveBlob(await sceneSvgBlob(thumb, deps), sceneFileName(scenarioTitle, thumb, position, "svg"));
}

export async function downloadAllScenesSvg(scenarioTitle, thumbs, deps = {}) {
    for (let i = 0; i < thumbs.length; i++) {
        await downloadSceneSvg(scenarioTitle, thumbs[i], i + 1, deps);
        if (i < thumbs.length - 1) await new Promise((resolve) => setTimeout(resolve, 250));
    }
}
