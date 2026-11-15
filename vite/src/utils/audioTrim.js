function writeAscii(view, offset, value) {
    for (let index = 0; index < value.length; index += 1) {
        view.setUint8(offset + index, value.charCodeAt(index));
    }
}

function normalizedPercent(value, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.max(0, Math.min(100, number));
}

export function encodeAudioBufferRangeAsWav(audioBuffer, startPercent, endPercent) {
    if (!audioBuffer?.length || !audioBuffer?.numberOfChannels || !audioBuffer?.sampleRate) {
        throw new Error("The selected audio could not be decoded.");
    }

    const start = normalizedPercent(startPercent, 0);
    const end = normalizedPercent(endPercent, 100);
    if (end <= start) {
        throw new Error("The cut end must be after the start.");
    }

    const startFrame = Math.min(audioBuffer.length - 1, Math.floor(audioBuffer.length * start / 100));
    const endFrame = Math.min(audioBuffer.length, Math.max(startFrame + 1, Math.ceil(audioBuffer.length * end / 100)));
    const frameCount = endFrame - startFrame;
    const channelCount = audioBuffer.numberOfChannels;
    const bytesPerSample = 2;
    const dataLength = frameCount * channelCount * bytesPerSample;
    const wav = new ArrayBuffer(44 + dataLength);
    const view = new DataView(wav);

    writeAscii(view, 0, "RIFF");
    view.setUint32(4, 36 + dataLength, true);
    writeAscii(view, 8, "WAVE");
    writeAscii(view, 12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, channelCount, true);
    view.setUint32(24, audioBuffer.sampleRate, true);
    view.setUint32(28, audioBuffer.sampleRate * channelCount * bytesPerSample, true);
    view.setUint16(32, channelCount * bytesPerSample, true);
    view.setUint16(34, 16, true);
    writeAscii(view, 36, "data");
    view.setUint32(40, dataLength, true);

    const channels = Array.from(
        {length: channelCount},
        (_, channel) => audioBuffer.getChannelData(channel)
    );
    let offset = 44;

    for (let frame = startFrame; frame < endFrame; frame += 1) {
        for (let channel = 0; channel < channelCount; channel += 1) {
            const sample = Math.max(-1, Math.min(1, channels[channel][frame] || 0));
            view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
            offset += bytesPerSample;
        }
    }

    return {
        blob: new Blob([wav], {type: "audio/wav"}),
        durationSeconds: frameCount / audioBuffer.sampleRate,
    };
}

export async function trimAudioSourceToWav(source, startPercent, endPercent) {
    const response = await fetch(source, {credentials: "include"});
    if (!response.ok) {
        throw new Error(`Could not load the audio (HTTP ${response.status}).`);
    }

    const AudioContextConstructor = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextConstructor) {
        throw new Error("Audio cutting is not supported by this browser.");
    }

    const context = new AudioContextConstructor();
    try {
        const encodedAudio = await response.arrayBuffer();
        const decodedAudio = await context.decodeAudioData(encodedAudio.slice(0));
        return encodeAudioBufferRangeAsWav(decodedAudio, startPercent, endPercent);
    } catch (error) {
        if (error?.message && !/decode/i.test(error.message)) throw error;
        throw new Error("The selected audio could not be decoded.");
    } finally {
        await context.close?.();
    }
}
