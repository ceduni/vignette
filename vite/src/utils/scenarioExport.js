import {apiFetch} from "../api/rest";
import {fetchScenarioBackgroundAudios, fetchScenarioThumbnails, fetchThumbnailAudios} from "../api/scenarios";
import {saveBlob, sceneSvgBlob} from "./sceneDownload";
import {createZip} from "./zip";

const AUDIO_EXTENSIONS = {
    "audio/webm": "webm",
    "audio/ogg": "ogg",
    "audio/mp4": "m4a",
    "audio/aac": "aac",
    "audio/mpeg": "mp3",
    "audio/mp3": "mp3",
    "audio/wav": "wav",
    "audio/x-wav": "wav",
    "audio/wave": "wav",
    "audio/flac": "flac",
    "audio/x-flac": "flac",
    "audio/x-m4a": "m4a",
    "audio/opus": "opus",
    "audio/aiff": "aiff",
    "audio/x-aiff": "aiff",
};

export function safeName(value, fallback = "") {
    const cleaned = String(value || "")
        .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, 80);
    return cleaned || fallback;
}

export function audioExtension(mime) {
    return AUDIO_EXTENSIONS[String(mime || "").split(";")[0].trim().toLowerCase()] || "audio";
}

export function takeLabel(index) {
    let label = "";
    let n = index;
    do {
        label = String.fromCharCode(65 + (n % 26)) + label;
        n = Math.floor(n / 26) - 1;
    } while (n >= 0);
    return label;
}

function byIdx(a, b) {
    return (a.idx ?? a.id) - (b.idx ?? b.id);
}

function sceneFolder(thumb, position) {
    const number = String(position).padStart(2, "0");
    return `${number} - ${safeName(thumb.title, `scene ${number}`)}`;
}

function describeAudio(lines, label, file, audio) {
    lines.push(`  ${label} : ${file}`);
    if (audio.transcription) lines.push(`      Transcription : ${audio.transcription}`);
    if (audio.gloss) lines.push(`      Gloss : ${audio.gloss}`);
    if (audio.freeTranslation) lines.push(`      Translation : ${audio.freeTranslation}`);
}

const defaultDeps = () => ({
    fetchBlob: (path) => apiFetch(path, {}, {responseType: "blob"}),
    fetchThumbnailAudios,
    fetchScenarioBackgroundAudios,
    sceneSvgBlob,
});

export async function buildScenarioZipEntries(scenario, scenes, options = {}, deps = {}) {
    const d = {...defaultDeps(), ...deps};
    const entries = [];
    const lines = [scenario.title || "Scenario"];
    if (scenario.authorUsername) lines.push(`By ${scenario.authorUsername}`);
    lines.push("");

    for (const {thumb, position} of scenes) {
        const folder = sceneFolder(thumb, position);
        entries.push({name: `${folder}/scene.svg`, data: await d.sceneSvgBlob(thumb)});
        lines.push(`Scene ${position}${thumb.title ? ` - ${thumb.title}` : ""}`);
        lines.push(`  Image : ${folder}/scene.svg`);

        const audios = [...((await d.fetchThumbnailAudios(thumb.id)) ?? [])].sort(byIdx);
        for (let i = 0; i < audios.length; i++) {
            const audio = audios[i];
            const label = `Take ${takeLabel(i)}`;
            const file = `${folder}/${label}${audio.title ? ` - ${safeName(audio.title)}` : ""}.${audioExtension(audio.mime)}`;
            entries.push({name: file, data: await d.fetchBlob(`/api/audios/${audio.id}/content`)});
            describeAudio(lines, label, file, audio);
        }
        if (!audios.length) lines.push("  No audio");
        lines.push("");
    }

    if (options.includeAmbience !== false) {
        const ambiences = [...((await d.fetchScenarioBackgroundAudios(scenario.id)) ?? [])].sort(byIdx);
        const active = ambiences.find((a) => a.active) ?? ambiences[0];
        if (active) {
            const file = `ambience - ${safeName(active.title, "background")}.${audioExtension(active.mime)}`;
            entries.push({name: file, data: await d.fetchBlob(active.contentUrl || `/api/audios/${active.id}/content`)});
            lines.push(`Ambience : ${file}`);
        }
    }

    entries.unshift({name: "scenario.txt", data: lines.join("\n") + "\n"});
    return entries;
}

export async function downloadScenarioZip(scenario, thumbs = null, deps = {}) {
    const list = thumbs ?? (await fetchScenarioThumbnails(scenario.id)) ?? [];
    if (!list.length) throw new Error("This scenario has no scenes to download.");
    const scenes = [...list].sort(byIdx).map((thumb, i) => ({thumb, position: i + 1}));
    const entries = await buildScenarioZipEntries(scenario, scenes, {}, deps);
    saveBlob(await createZip(entries), `${safeName(scenario.title, "scenario")}.zip`);
}

export async function downloadSceneZip(scenario, thumb, position, deps = {}) {
    const entries = await buildScenarioZipEntries(scenario, [{thumb, position}], {}, deps);
    const number = String(position).padStart(2, "0");
    saveBlob(await createZip(entries), `${safeName(scenario.title, "scenario")} - scene ${number}.zip`);
}
