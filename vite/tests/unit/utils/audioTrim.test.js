import {encodeAudioBufferRangeAsWav} from "@/utils/audioTrim";

function fakeAudioBuffer({channels = 2, frames = 100, sampleRate = 100} = {}) {
    const samples = Array.from({length: channels}, (_, channel) =>
        Float32Array.from({length: frames}, (_, frame) => ((frame + channel) % 20 - 10) / 10)
    );
    return {
        length: frames,
        numberOfChannels: channels,
        sampleRate,
        getChannelData: (channel) => samples[channel],
    };
}

describe("audioTrim", () => {
    it("encodes only the selected audio range as a valid PCM WAV", async () => {
        const {blob, durationSeconds} = encodeAudioBufferRangeAsWav(fakeAudioBuffer(), 25, 75);
        const buffer = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.addEventListener("load", () => resolve(reader.result), {once: true});
            reader.addEventListener("error", () => reject(reader.error), {once: true});
            reader.readAsArrayBuffer(blob);
        });
        const bytes = new Uint8Array(buffer);
        const header = String.fromCharCode(...bytes.slice(0, 4));
        const format = String.fromCharCode(...bytes.slice(8, 12));
        const dataSize = new DataView(bytes.buffer).getUint32(40, true);

        expect(blob.type).toBe("audio/wav");
        expect(header).toBe("RIFF");
        expect(format).toBe("WAVE");
        expect(durationSeconds).toBe(0.5);
        expect(dataSize).toBe(50 * 2 * 2);
        expect(bytes.byteLength).toBe(44 + dataSize);
    });

    it("rejects an empty or reversed range", () => {
        expect(() => encodeAudioBufferRangeAsWav(fakeAudioBuffer(), 70, 30))
            .toThrow("The cut end must be after the start.");
    });
});
