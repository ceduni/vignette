import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    buildImageSvg,
    buildVignetteSvg,
    downloadAllScenesSvg,
    downloadSceneSvg,
    sceneFileName,
} from "../../../src/utils/sceneDownload.js";
import {CHARS} from "../../../src/utils/vignetteMakerArt.js";

describe("sceneFileName", () => {
    it("uses the scenario title, position and svg extension", () => {
        expect(sceneFileName("essai1", {id: 1}, 1)).toBe("essai1 - scene 01.svg");
        expect(sceneFileName("essai1", {id: 1, title: "Market"}, 3)).toBe("essai1 - 03 - Market.svg");
    });

    it("removes characters that are not allowed in file names", () => {
        expect(sceneFileName('a/b:c*"d', {id: 1}, 2)).toBe("a b c d - scene 02.svg");
    });

    it("falls back to a default name", () => {
        expect(sceneFileName("", {id: 1}, 1)).toBe("scenario - scene 01.svg");
    });
});

describe("SVG builders", () => {
    it("keeps vignette characters and bubbles as vector shapes", () => {
        const ch = CHARS[0];
        const person = {
            uid: 1, charId: ch.id, x: 100, y: 200, scale: 1, z: 0, flip: false,
            skinIdx: 1, hairId: ch.hair, hairCol: ch.hcol || 0, mouthId: "smile", eyes: "open",
            glasses: !!ch.glasses, topCol: "", botCol: "", bubble: null,
            lsh: -20, lel: 0, rsh: -20, rel: 0, ll: 0, lk: 0, rl: 0, rk: 0, ht: 0, bl: 0, eb: 0,
        };
        const svg = buildVignetteSvg({sceneId: "living", people: [person], nextId: 2}, 700, 500,
            "data:image/png;base64,AAA");
        expect(svg).toContain('viewBox="0 0 700 500"');
        expect(svg).toContain('<image href="data:image/png;base64,AAA"');
        expect(svg.indexOf('data-uid="1"')).toBeGreaterThan(svg.indexOf("<image"));
        expect(svg.trim().endsWith("</svg>")).toBe(true);
    });

    it("produces well-formed XML, so the server sanitizer accepts it", () => {
        const original = HTMLCanvasElement.prototype.getContext;
        HTMLCanvasElement.prototype.getContext = () => ({font: "", measureText: (t) => ({width: String(t).length * 7})});
        try {
            const ch = CHARS[0];
            const person = {
                uid: 1, charId: ch.id, x: 100, y: 200, scale: 1, z: 0, flip: false,
                skinIdx: 1, hairId: ch.hair, hairCol: ch.hcol || 0, mouthId: "smile", eyes: "open",
                glasses: !!ch.glasses, topCol: "", botCol: "",
                bubble: {text: 'Bonjour <Alice> & "Bob"', style: "speech", theme: "classic", side: "left", w: 160, dx: -180, dy: -220},
                lsh: -20, lel: 0, rsh: -20, rel: 0, ll: 0, lk: 0, rl: 0, rk: 0, ht: 0, bl: 0, eb: 0,
            };
            const svg = buildVignetteSvg({sceneId: "living", people: [person]}, 700, 500, "data:image/png;base64,AAA=");
            const doc = new DOMParser().parseFromString(svg, "image/svg+xml");
            expect(doc.getElementsByTagName("parsererror")).toHaveLength(0);
            expect(doc.documentElement.getAttribute("width")).toBe("700");
            const text = doc.documentElement.textContent.replace(/\s+/g, " ");
            expect(text).toContain("<Alice>");
            expect(text).toContain('"Bob"');
            expect(text).toContain("&");
        } finally {
            HTMLCanvasElement.prototype.getContext = original;
        }
    });

    it("wraps other images in an SVG of the same size", () => {
        const svg = buildImageSvg("data:image/jpeg;base64,BBB", 612, 400);
        expect(svg).toContain('viewBox="0 0 612 400"');
        expect(svg).toContain('href="data:image/jpeg;base64,BBB"');
    });
});

describe("downloading scenes as SVG", () => {
    let saved;

    beforeEach(() => {
        saved = [];
        vi.useFakeTimers();
        globalThis.URL.createObjectURL = vi.fn((blob) => {
            saved.push({blob});
            return "blob:fake";
        });
        globalThis.URL.revokeObjectURL = vi.fn();
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function () {
            saved[saved.length - 1].name = this.download;
        });
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it("saves a vignette stored as SVG as it is, without rebuilding it", async () => {
        const stored = new Blob(["<svg/>"], {type: "image/svg+xml"});
        const fetchBlob = vi.fn().mockResolvedValue(stored);
        await downloadSceneSvg("essai1", {id: 7, vignette: true}, 1, {fetchBlob});
        expect(saved[0].blob).toBe(stored);
    });

    it("keeps an uploaded SVG as it is", async () => {
        const original = new Blob(["<svg/>"], {type: "image/svg+xml"});
        const fetchBlob = vi.fn().mockResolvedValue(original);
        await downloadSceneSvg("s", {id: 3, vignette: false}, 2, {fetchBlob});
        expect(fetchBlob).toHaveBeenCalledWith("/api/thumbnails/3/content", {}, {responseType: "blob"});
        expect(saved[0].blob).toBe(original);
        expect(saved[0].name).toBe("s - scene 02.svg");
    });

    it("wraps a PNG scene in an SVG", async () => {
        const fetchBlob = vi.fn().mockResolvedValue(new Blob(["png"], {type: "image/png"}));
        const imageSize = vi.fn().mockResolvedValue({width: 10, height: 20});
        const done = downloadAllScenesSvg("s", [{id: 1}, {id: 2}], {fetchBlob, imageSize});
        await vi.runAllTimersAsync();
        await done;
        expect(saved.map((s) => s.name)).toEqual(["s - scene 01.svg", "s - scene 02.svg"]);
        expect(saved[0].blob.type).toBe("image/svg+xml");
    });
});
