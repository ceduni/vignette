import {renderAmbiencePresetSamples, encodeWavMono, renderAmbiencePresetBlob} from "@/utils/ambienceSynth";

describe("ambienceSynth", () => {
    it("renderAmbiencePresetSamples is deterministic for the same seed", () => {
        const preset = {id: "forest-morning", profile: "forest"};
        const a = renderAmbiencePresetSamples(preset, "seed-1");
        const b = renderAmbiencePresetSamples(preset, "seed-1");

        expect(a.sampleRate).toBe(b.sampleRate);
        expect(Array.from(a.samples)).toEqual(Array.from(b.samples));
    });

    it("renderAmbiencePresetSamples differs for different seeds", () => {
        const preset = {id: "forest-morning", profile: "forest"};
        const a = renderAmbiencePresetSamples(preset, "seed-1");
        const b = renderAmbiencePresetSamples(preset, "seed-2");

        expect(Array.from(a.samples)).not.toEqual(Array.from(b.samples));
    });

    it("keeps samples within [-1, 1]", () => {
        const preset = {id: "rain-window", profile: "rain"};
        const {samples} = renderAmbiencePresetSamples(preset, "rain-seed");

        for (const sample of samples) {
            expect(sample).toBeGreaterThanOrEqual(-1);
            expect(sample).toBeLessThanOrEqual(1);
        }
    });

    it("handles an unknown profile without throwing (silent buffer)", () => {
        const preset = {id: "mystery", profile: "unknown-profile"};
        expect(() => renderAmbiencePresetSamples(preset, "x")).not.toThrow();
    });

    it("encodeWavMono produces a valid RIFF/WAVE header of the expected size", () => {
        const samples = new Float32Array([0, 0.5, -0.5, 1, -1]);
        const buffer = encodeWavMono(samples, 22050);
        const view = new DataView(buffer);

        const readStr = (offset, len) =>
            String.fromCharCode(...new Uint8Array(buffer, offset, len));

        expect(readStr(0, 4)).toBe("RIFF");
        expect(readStr(8, 4)).toBe("WAVE");
        expect(readStr(12, 4)).toBe("fmt ");
        expect(readStr(36, 4)).toBe("data");
        expect(view.getUint32(24, true)).toBe(22050);
        expect(buffer.byteLength).toBe(44 + samples.length * 2);
    });

    it("renderAmbiencePresetBlob returns a WAV blob", () => {
        const preset = {id: "ocean-shore", profile: "ocean"};
        const blob = renderAmbiencePresetBlob(preset, "ocean-seed");

        expect(blob).toBeInstanceOf(Blob);
        expect(blob.type).toBe("audio/wav");
        expect(blob.size).toBeGreaterThan(44);
    });
});
