const CRC_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
    }
    return table;
})();

export function crc32(bytes) {
    let crc = 0xffffffff;
    for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
    return (crc ^ 0xffffffff) >>> 0;
}

function dosDateTime(date) {
    const time = (date.getHours() << 11) | (date.getMinutes() << 5) | (date.getSeconds() >> 1);
    const day = ((date.getFullYear() - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
    return {time, day};
}

function header(size, fields) {
    const buffer = new Uint8Array(size);
    const view = new DataView(buffer.buffer);
    for (const [offset, bytes, value] of fields) {
        if (bytes === 4) view.setUint32(offset, value, true);
        else view.setUint16(offset, value, true);
    }
    return buffer;
}

function readBlob(blob) {
    if (typeof blob.arrayBuffer === "function") return blob.arrayBuffer();
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error);
        reader.readAsArrayBuffer(blob);
    });
}

export async function toBytes(data) {
    if (data instanceof Uint8Array) return data;
    if (typeof data === "string") return new TextEncoder().encode(data);
    if (data instanceof ArrayBuffer) return new Uint8Array(data);
    return new Uint8Array(await readBlob(data));
}

export async function createZip(entries, date = new Date()) {
    const encoder = new TextEncoder();
    const {time, day} = dosDateTime(date);
    const parts = [];
    const central = [];
    let offset = 0;

    for (const entry of entries) {
        const name = encoder.encode(entry.name);
        const data = await toBytes(entry.data);
        const crc = crc32(data);
        const local = header(30, [
            [0, 4, 0x04034b50], [4, 2, 20], [6, 2, 0x0800], [8, 2, 0], [10, 2, time], [12, 2, day],
            [14, 4, crc], [18, 4, data.length], [22, 4, data.length], [26, 2, name.length], [28, 2, 0],
        ]);
        central.push({name, crc, size: data.length, offset});
        parts.push(local, name, data);
        offset += local.length + name.length + data.length;
    }

    const directoryStart = offset;
    for (const file of central) {
        const record = header(46, [
            [0, 4, 0x02014b50], [4, 2, 20], [6, 2, 20], [8, 2, 0x0800], [10, 2, 0], [12, 2, time],
            [14, 2, day], [16, 4, file.crc], [20, 4, file.size], [24, 4, file.size], [28, 2, file.name.length],
            [30, 2, 0], [32, 2, 0], [34, 2, 0], [36, 2, 0], [38, 4, 0], [42, 4, file.offset],
        ]);
        parts.push(record, file.name);
        offset += record.length + file.name.length;
    }

    parts.push(header(22, [
        [0, 4, 0x06054b50], [4, 2, 0], [6, 2, 0], [8, 2, central.length], [10, 2, central.length],
        [12, 4, offset - directoryStart], [16, 4, directoryStart], [20, 2, 0],
    ]));
    return new Blob(parts, {type: "application/zip"});
}
