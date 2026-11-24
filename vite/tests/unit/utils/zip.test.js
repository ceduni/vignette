import {describe, expect, it} from "vitest";
import {crc32, createZip, toBytes} from "../../../src/utils/zip.js";

function bytesOf(blob) {
    return toBytes(blob);
}

function readUint32(bytes, offset) {
    return new DataView(bytes.buffer, bytes.byteOffset).getUint32(offset, true);
}

function readUint16(bytes, offset) {
    return new DataView(bytes.buffer, bytes.byteOffset).getUint16(offset, true);
}

describe("zip", () => {
    it("computes the standard CRC-32", () => {
        expect(crc32(new TextEncoder().encode("123456789"))).toBe(0xcbf43926);
        expect(crc32(new Uint8Array(0))).toBe(0);
    });

    it("converts strings, blobs and buffers to bytes", async () => {
        expect(Array.from(await toBytes("ab"))).toEqual([97, 98]);
        expect(Array.from(await toBytes(new Blob([new Uint8Array([1, 2])])))).toEqual([1, 2]);
        expect(Array.from(await toBytes(new Uint8Array([3]).buffer))).toEqual([3]);
    });

    it("writes stored entries with a central directory", async () => {
        const zip = await createZip([
            {name: "scenario.txt", data: "hello"},
            {name: "01 - Scène/scene.svg", data: new Blob(["<svg/>"])},
        ]);
        expect(zip.type).toBe("application/zip");
        const bytes = await bytesOf(zip);

        expect(readUint32(bytes, 0)).toBe(0x04034b50);
        expect(readUint32(bytes, 14)).toBe(crc32(new TextEncoder().encode("hello")));
        expect(readUint32(bytes, 18)).toBe(5);

        const end = bytes.length - 22;
        expect(readUint32(bytes, end)).toBe(0x06054b50);
        expect(readUint16(bytes, end + 10)).toBe(2);
        const directoryStart = readUint32(bytes, end + 16);
        expect(readUint32(bytes, directoryStart)).toBe(0x02014b50);
        expect(readUint32(bytes, end + 12)).toBe(end - directoryStart);

        const text = new TextDecoder().decode(bytes);
        expect(text).toContain("01 - Scène/scene.svg");
        expect(text).toContain("<svg/>");
    });
});
