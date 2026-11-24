import {describe, expect, it, vi} from "vitest";
import {
    audioExtension,
    buildScenarioZipEntries,
    safeName,
    takeLabel,
} from "../../../src/utils/scenarioExport.js";

function deps(overrides = {}) {
    return {
        sceneSvgBlob: vi.fn(async (thumb) => new Blob([`<svg id="${thumb.id}"/>`], {type: "image/svg+xml"})),
        fetchThumbnailAudios: vi.fn(async () => []),
        fetchScenarioBackgroundAudios: vi.fn(async () => []),
        fetchBlob: vi.fn(async (path) => new Blob([path], {type: "audio/webm"})),
        ...overrides,
    };
}

describe("scenarioExport helpers", () => {
    it("labels takes like the studio (A, B, ... Z, AA)", () => {
        expect([0, 1, 25, 26, 27].map(takeLabel)).toEqual(["A", "B", "Z", "AA", "AB"]);
    });

    it("maps audio types to file extensions", () => {
        expect(audioExtension("audio/webm;codecs=opus")).toBe("webm");
        expect(audioExtension("audio/mpeg")).toBe("mp3");
        expect(audioExtension("audio/x-wav")).toBe("wav");
        expect(audioExtension("")).toBe("audio");
    });

    it("cleans file names", () => {
        expect(safeName('a/b:c*"d')).toBe("a b c d");
        expect(safeName("", "scene 01")).toBe("scene 01");
    });
});

describe("buildScenarioZipEntries", () => {
    const scenario = {id: 5, title: "essai1", authorUsername: "Alice"};

    it("puts each scene's SVG and audio takes in its own folder, with the ambience", async () => {
        const d = deps({
            fetchThumbnailAudios: vi.fn(async (id) => id === 1
                ? [
                    {id: 11, idx: 2, title: "Second", mime: "audio/ogg"},
                    {id: 10, idx: 1, title: "", mime: "audio/webm", transcription: "Azul", freeTranslation: "Hello"},
                ]
                : []),
            fetchScenarioBackgroundAudios: vi.fn(async () => [
                {id: 152, idx: 1, title: "Forest morning", mime: "audio/wav", contentUrl: "/api/audios/152/content", active: true},
            ]),
        });

        const entries = await buildScenarioZipEntries(scenario, [
            {thumb: {id: 1, title: "Market"}, position: 1},
            {thumb: {id: 2, title: ""}, position: 2},
        ], {}, d);

        expect(entries.map((e) => e.name)).toEqual([
            "scenario.txt",
            "01 - Market/scene.svg",
            "01 - Market/Take A.webm",
            "01 - Market/Take B - Second.ogg",
            "02 - scene 02/scene.svg",
            "ambience - Forest morning.wav",
        ]);
        expect(d.fetchBlob).toHaveBeenCalledWith("/api/audios/10/content");
        expect(d.fetchBlob).toHaveBeenCalledWith("/api/audios/152/content");

        const summary = entries[0].data;
        expect(summary).toContain("essai1");
        expect(summary).toContain("By Alice");
        expect(summary).toContain("Transcription : Azul");
        expect(summary).toContain("Translation : Hello");
        expect(summary).toContain("No audio");
        expect(summary).toContain("Ambience : ambience - Forest morning.wav");
    });

    it("works without audio or ambience", async () => {
        const entries = await buildScenarioZipEntries(scenario, [{thumb: {id: 3, title: "Solo"}, position: 4}], {}, deps());
        expect(entries.map((e) => e.name)).toEqual(["scenario.txt", "04 - Solo/scene.svg"]);
    });
});
