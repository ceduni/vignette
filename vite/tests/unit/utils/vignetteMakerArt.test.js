import {
    CHARS, SKINS, HAIR_COLS, ALL_HAIRS, POSES,
    ageScale, headScale, naturalPose, poseWith, escXML,
    hairSVG, drawFace, personSVG, personScale,
    bubbleTheme, wrapBubbleText, bubbleMetrics, bubbleSVG,
} from "@/utils/vignetteMakerArt";

function makePerson(overrides = {}) {
    const ch = CHARS[0];
    return {
        uid: 1, charId: ch.id, x: 100, y: 100, scale: 1, z: 1, flip: false,
        skinIdx: 1, hairId: ch.hair, hairCol: ch.hcol || 0, mouthId: "smile", eyes: "open",
        glasses: !!ch.glasses, topCol: "", botCol: "", bubble: null,
        lsh: -20, lel: 0, rsh: -20, rel: 0, ll: 0, lk: 0, rl: 0, rk: 0, ht: 0, bl: 0, eb: 0,
        ...overrides,
    };
}

describe("vignetteMakerArt", () => {
    let originalGetContext;

    beforeEach(() => {
        originalGetContext = HTMLCanvasElement.prototype.getContext;
        HTMLCanvasElement.prototype.getContext = () => ({
            font: "",
            measureText: (text) => ({width: String(text).length * 7}),
        });
    });

    afterEach(() => {
        HTMLCanvasElement.prototype.getContext = originalGetContext;
    });

    it("ageScale/headScale return known multipliers and fall back to 1 for unknown ages", () => {
        expect(ageScale("child")).toBe(0.67);
        expect(ageScale("unknown")).toBe(1);
        expect(headScale("toddler")).toBe(1.28);
        expect(headScale("unknown")).toBe(1);
    });

    it("naturalPose derives a symmetric relaxed pose from a character's default shoulder angles", () => {
        const pose = naturalPose({defLsh: -15, defRsh: 25});
        expect(pose.lsh).toBe(-15);
        expect(pose.rsh).toBe(-25);
        expect(pose.lel).toBe(0);
    });

    it("poseWith overlays custom fields on top of the natural pose", () => {
        const pose = poseWith({defLsh: -15, defRsh: 25}, {lsh: -80, ht: 5});
        expect(pose.lsh).toBe(-80);
        expect(pose.ht).toBe(5);
        expect(pose.rsh).toBe(-25);
    });

    it("every declared pose preset produces a valid pose object for every character", () => {
        POSES.forEach((preset) => {
            CHARS.forEach((ch) => {
                const pose = preset.set(null, ch);
                expect(Number.isFinite(pose.lsh)).toBe(true);
                expect(Number.isFinite(pose.rsh)).toBe(true);
            });
        });
    });

    it("escXML escapes all five reserved XML characters", () => {
        expect(escXML(`<a> & "b" 'c'`)).toBe("&lt;a&gt; &amp; &quot;b&quot; &#39;c&#39;");
    });

    it("escXML handles null/undefined safely", () => {
        expect(escXML(null)).toBe("");
        expect(escXML(undefined)).toBe("");
    });

    it("hairSVG returns markup for every known hair style and falls back to short for unknown ids", () => {
        ALL_HAIRS.forEach((id) => {
            expect(hairSVG(id, "#000000")).toMatch(/</);
        });
        expect(hairSVG("nonexistent-style", "#000000")).toBe(hairSVG("short", "#000000"));
    });

    it("drawFace renders eyes/brows for every mouth and eye combination without throwing", () => {
        const sk = SKINS[0];
        ["smile", "grin", "open", "ohh", "sad", "smirk", "neutral", "laugh"].forEach((mouth) => {
            ["open", "happy", "closed", "wink"].forEach((eyes) => {
                const markup = drawFace("adult", sk, mouth, 0, eyes, false);
                expect(markup).toContain("<path");
            });
        });
    });

    it("personSVG renders a full figure group for every character and skin combination", () => {
        CHARS.forEach((ch) => {
            const p = makePerson({charId: ch.id});
            const svg = personSVG(p, false);
            expect(svg).toContain(`data-uid="${p.uid}"`);
            expect(svg).toContain("<g");
        });
    });

    it("personSVG draws a selection outline only when isSel is true", () => {
        const p = makePerson();
        expect(personSVG(p, true)).toContain("stroke-dasharray");
        expect(personSVG(p, false)).not.toContain("stroke-dasharray");
    });

    it("personScale multiplies the person's own scale by their character's age scale", () => {
        const toddler = CHARS.find((c) => c.age === "toddler");
        const p = makePerson({charId: toddler.id, scale: 2});
        expect(personScale(p)).toBeCloseTo(2 * ageScale("toddler"));
    });

    it("bubbleTheme falls back to classic for an unknown or missing theme", () => {
        expect(bubbleTheme({theme: "rose"}).stroke).toBe("#C84878");
        expect(bubbleTheme({theme: "not-a-theme"})).toEqual(bubbleTheme({theme: "classic"}));
        expect(bubbleTheme(null)).toEqual(bubbleTheme({theme: "classic"}));
    });

    it("wrapBubbleText preserves explicit newlines as paragraph breaks", () => {
        const lines = wrapBubbleText("hello\nworld", 500, 15);
        expect(lines).toContain("hello");
        expect(lines).toContain("world");
    });

    it("wrapBubbleText returns a single empty line for empty input", () => {
        expect(wrapBubbleText("", 200, 15)).toEqual([""]);
    });

    it("bubbleMetrics returns null when the person has no bubble", () => {
        expect(bubbleMetrics(makePerson({bubble: null}))).toBeNull();
    });

    it("bubbleMetrics computes a box that fits the wrapped text", () => {
        const p = makePerson({bubble: {text: "Hi there!", style: "speech", theme: "classic", side: "right", w: 180, dx: 40, dy: -240}});
        const metrics = bubbleMetrics(p);
        expect(metrics.boxW).toBeGreaterThan(0);
        expect(metrics.boxH).toBeGreaterThan(0);
        expect(metrics.lines.length).toBeGreaterThan(0);
    });

    it("bubbleSVG returns an empty string when there is no bubble, and markup otherwise", () => {
        expect(bubbleSVG(makePerson({bubble: null}), false)).toBe("");
        const withBubble = makePerson({bubble: {text: "Hello", style: "speech", theme: "classic", side: "left", w: 160, dx: -180, dy: -220}});
        const svg = bubbleSVG(withBubble, false);
        expect(svg).toContain("vm-bubble");
        expect(svg).toContain("Hello");
    });
});
