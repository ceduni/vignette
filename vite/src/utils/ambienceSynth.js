function hashPresetSeed(text) {
    let hash = 2166136261;
    for (let i = 0; i < text.length; i++) {
        hash ^= text.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
}

function seededRandom(seedText) {
    let seed = hashPresetSeed(seedText);
    return () => {
        seed += 0x6D2B79F5;
        let t = seed;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

function addSmoothNoise(buffer, rng, gain, smooth = 0.006, modulator = null) {
    let value = 0;
    for (let i = 0; i < buffer.length; i++) {
        value += ((rng() * 2 - 1) - value) * smooth;
        const amount = modulator ? modulator(i) : 1;
        buffer[i] += value * gain * amount;
    }
}

function addNoiseBurst(buffer, sampleRate, rng, start, duration, gain, smooth = 0.04) {
    const startSample = Math.max(0, Math.floor(start * sampleRate));
    const endSample = Math.min(buffer.length, startSample + Math.floor(duration * sampleRate));
    let value = 0;
    for (let i = startSample; i < endSample; i++) {
        const local = (i - startSample) / Math.max(1, endSample - startSample);
        const fade = Math.sin(Math.PI * local);
        value += ((rng() * 2 - 1) - value) * smooth;
        buffer[i] += value * gain * fade;
    }
}

function addSine(buffer, sampleRate, frequency, gain, modFrequency = 0, phase = 0) {
    for (let i = 0; i < buffer.length; i++) {
        const time = i / sampleRate;
        const mod = modFrequency ? 0.55 + 0.45 * Math.sin(Math.PI * 2 * modFrequency * time + phase) : 1;
        buffer[i] += Math.sin(Math.PI * 2 * frequency * time + phase) * gain * mod;
    }
}

function addChirp(buffer, sampleRate, start, duration, startFrequency, endFrequency, gain) {
    const startSample = Math.max(0, Math.floor(start * sampleRate));
    const endSample = Math.min(buffer.length, startSample + Math.floor(duration * sampleRate));
    let phase = 0;
    for (let i = startSample; i < endSample; i++) {
        const local = (i - startSample) / Math.max(1, endSample - startSample);
        const frequency = startFrequency + (endFrequency - startFrequency) * local;
        const envelope = Math.sin(Math.PI * local);
        phase += (Math.PI * 2 * frequency) / sampleRate;
        buffer[i] += Math.sin(phase) * gain * envelope;
    }
}

export function renderAmbiencePresetSamples(preset, seedText = preset.id) {
    const sampleRate = 22050;
    const duration = 12;
    const buffer = new Float32Array(sampleRate * duration);
    const rng = seededRandom(seedText);

    if (preset.profile === "forest") {
        addSmoothNoise(buffer, rng, 0.16, 0.002, (i) => 0.55 + 0.25 * Math.sin((Math.PI * 2 * i) / (sampleRate * 5)));
        for (let i = 0; i < 18; i++) {
            const start = rng() * (duration - 0.8);
            addChirp(buffer, sampleRate, start, 0.08 + rng() * 0.18, 1100 + rng() * 900, 1800 + rng() * 1800, 0.08);
        }
        for (let i = 0; i < 16; i++) addNoiseBurst(buffer, sampleRate, rng, rng() * duration, 0.2 + rng() * 0.5, 0.04, 0.03);
    } else if (preset.profile === "rain") {
        addSmoothNoise(buffer, rng, 0.24, 0.12);
        addSmoothNoise(buffer, rng, 0.12, 0.018);
        for (let i = 0; i < 160; i++) addNoiseBurst(buffer, sampleRate, rng, rng() * duration, 0.008 + rng() * 0.03, 0.07, 0.7);
    } else if (preset.profile === "street") {
        addSmoothNoise(buffer, rng, 0.13, 0.004);
        addSine(buffer, sampleRate, 58, 0.035, 0.07);
        addSine(buffer, sampleRate, 92, 0.025, 0.11, 0.7);
        for (let i = 0; i < 14; i++) addNoiseBurst(buffer, sampleRate, rng, rng() * duration, 0.5 + rng() * 1.2, 0.09, 0.009);
        for (let i = 0; i < 4; i++) addChirp(buffer, sampleRate, rng() * (duration - 1), 0.18, 360 + rng() * 180, 300 + rng() * 100, 0.035);
    } else if (preset.profile === "market" || preset.profile === "cafe") {
        addSmoothNoise(buffer, rng, preset.profile === "market" ? 0.18 : 0.12, 0.005);
        for (let voice = 0; voice < 9; voice++) {
            addSine(buffer, sampleRate, 115 + rng() * 180, 0.012 + rng() * 0.012, 0.16 + rng() * 0.36, rng() * Math.PI);
        }
        for (let i = 0; i < (preset.profile === "market" ? 24 : 12); i++) {
            addNoiseBurst(buffer, sampleRate, rng, rng() * duration, 0.08 + rng() * 0.22, 0.035, 0.05);
        }
    } else if (preset.profile === "room") {
        addSmoothNoise(buffer, rng, 0.055, 0.003);
        addSine(buffer, sampleRate, 60, 0.008, 0.02);
        addSine(buffer, sampleRate, 124, 0.004, 0.03, 1.2);
    } else if (preset.profile === "night") {
        addSmoothNoise(buffer, rng, 0.075, 0.002);
        for (let i = 0; i < 42; i++) {
            const start = rng() * (duration - 0.25);
            addChirp(buffer, sampleRate, start, 0.045 + rng() * 0.08, 2600 + rng() * 900, 2300 + rng() * 700, 0.055);
            if (rng() > 0.45) addChirp(buffer, sampleRate, start + 0.09, 0.04, 2500 + rng() * 800, 2200 + rng() * 800, 0.04);
        }
    } else if (preset.profile === "ocean") {
        addSmoothNoise(buffer, rng, 0.24, 0.006, (i) => {
            const t = i / sampleRate;
            return 0.25 + Math.pow((Math.sin(Math.PI * 2 * 0.18 * t) + 1) / 2, 2) * 0.95;
        });
        addSmoothNoise(buffer, rng, 0.08, 0.04);
    }

    let peak = 0;
    for (let i = 0; i < buffer.length; i++) peak = Math.max(peak, Math.abs(buffer[i]));
    const scale = peak > 0 ? Math.min(1, 0.82 / peak) : 1;
    const fadeSamples = Math.floor(sampleRate * 0.2);
    for (let i = 0; i < buffer.length; i++) {
        const fadeIn = i < fadeSamples ? i / fadeSamples : 1;
        const fadeOut = i > buffer.length - fadeSamples ? (buffer.length - i) / fadeSamples : 1;
        buffer[i] = Math.max(-1, Math.min(1, buffer[i] * scale * Math.min(fadeIn, fadeOut)));
    }

    return {samples: buffer, sampleRate};
}

export function encodeWavMono(samples, sampleRate) {
    const bytesPerSample = 2;
    const buffer = new ArrayBuffer(44 + samples.length * bytesPerSample);
    const view = new DataView(buffer);
    const writeString = (offset, string) => {
        for (let i = 0; i < string.length; i++) view.setUint8(offset + i, string.charCodeAt(i));
    };

    writeString(0, "RIFF");
    view.setUint32(4, 36 + samples.length * bytesPerSample, true);
    writeString(8, "WAVE");
    writeString(12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * bytesPerSample, true);
    view.setUint16(32, bytesPerSample, true);
    view.setUint16(34, 16, true);
    writeString(36, "data");
    view.setUint32(40, samples.length * bytesPerSample, true);

    let offset = 44;
    for (let i = 0; i < samples.length; i++) {
        const sample = Math.max(-1, Math.min(1, samples[i]));
        view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
        offset += bytesPerSample;
    }

    return buffer;
}

export function renderAmbiencePresetBlob(preset, seedText = preset.id) {
    const rendered = renderAmbiencePresetSamples(preset, seedText);
    return new Blob([encodeWavMono(rendered.samples, rendered.sampleRate)], {type: "audio/wav"});
}
