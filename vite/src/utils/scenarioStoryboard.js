export function safeNumber(value, fallback = 0) {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
}

export function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

export function nullableInt(value) {
    if (value === "" || value === null || value === undefined) return null;
    const n = Number(value);
    return Number.isFinite(n) ? Math.trunc(n) : null;
}

export function sortByIdxThenId(items) {
    return [...(items || [])].sort((a, b) => {
        const aIdx = safeNumber(a?.idx, Number.MAX_SAFE_INTEGER);
        const bIdx = safeNumber(b?.idx, Number.MAX_SAFE_INTEGER);
        if (aIdx !== bIdx) return aIdx - bIdx;
        return safeNumber(a?.id, 0) - safeNumber(b?.id, 0);
    });
}

export function buildSelectedAudios(audioMap, selectedThumb) {
    if (!selectedThumb) return [];
    return sortByIdxThenId(audioMap?.[selectedThumb.id] || []);
}

export function buildPlaybackQueue(thumbnails, audioMap, buildAudioUrl) {
    return sortByIdxThenId(thumbnails).flatMap((thumb) => {
        const audios = sortByIdxThenId(audioMap?.[thumb.id] || []);

        return audios.map((audio) => ({
            thumbnailId: thumb.id,
            thumbnailIdx: thumb.idx ?? null,
            thumbnailTitle: thumb.title ?? "",
            audioId: audio.id,
            audioIdx: audio.idx ?? null,
            audioTitle: audio.title ?? "",
            audioUrl: buildAudioUrl(audio),
        }));
    });
}

export function defaultTileLayout(thumb, index, columns) {
    const width = safeNumber(thumb?.imageWidth, 0);
    const height = safeNumber(thumb?.imageHeight, 0);

    const portrait = height > width * 1.15;
    const landscape = width > height * 1.2;
    const aspectRatio = width > 0 && height > 0 ? width / height : 16 / 10;

    if (index === 0) {
        return {
            columnStart: null,
            columnSpan: Math.min(columns, 2),
            rowStart: null,
            rowSpan: landscape ? 2 : 1,
            aspectRatio: landscape ? "16 / 9" : "4 / 3",
            shape: "feature",
        };
    }

    if (portrait) {
        return {
            columnStart: null,
            columnSpan: 1,
            rowStart: null,
            rowSpan: 2,
            aspectRatio: "3 / 4",
            shape: "portrait",
        };
    }

    if (landscape) {
        return {
            columnStart: null,
            columnSpan: Math.min(columns, 2),
            rowStart: null,
            rowSpan: 1,
            aspectRatio: "16 / 9",
            shape: "wide",
        };
    }

    return {
        columnStart: null,
        columnSpan: 1,
        rowStart: null,
        rowSpan: 1,
        aspectRatio: aspectRatio > 1.15 ? "4 / 3" : "1 / 1",
        shape: "standard",
    };
}

export function presetColumns(preset, fallbackColumns = 3) {
    void fallbackColumns;
    const normalizedPreset = String(preset || "").toUpperCase();

    if (normalizedPreset === "GRID_2") return 12;
    if (normalizedPreset === "CINEMATIC") return 12;
    if (normalizedPreset === "MANGA") return 12;
    return 12;
}

export function presetTileLayout(thumb, index, columns, preset) {
    const normalizedPreset = String(preset || "GRID_3").toUpperCase();

    if (normalizedPreset === "GRID_2") {
        const strip = [
            {columnSpan: 3, rowSpan: 4},
            {columnSpan: 3, rowSpan: 4},
            {columnSpan: 3, rowSpan: 4},
            {columnSpan: 3, rowSpan: 4},
        ];
        const shape = strip[index % strip.length];
        return {
            columnStart: null,
            rowStart: null,
            columnSpan: shape.columnSpan,
            rowSpan: shape.rowSpan,
            shape: "strip",
        };
    }

    if (normalizedPreset === "CINEMATIC") {
        if (index === 0) {
            return {
                columnStart: null,
                columnSpan: Math.min(columns, 12),
                rowStart: null,
                rowSpan: 5,
                shape: "hero",
            };
        }

        if (index % 4 === 3) {
            return {
                columnStart: null,
                columnSpan: 12,
                rowStart: null,
                rowSpan: 3,
                shape: "final",
            };
        }

        return {
            columnStart: null,
            columnSpan: 6,
            rowStart: null,
            rowSpan: 3,
            shape: "wide",
        };
    }

    if (normalizedPreset === "MANGA") {
        const manga = [
            {columnSpan: 4, rowSpan: 3},
            {columnSpan: 4, rowSpan: 3},
            {columnSpan: 4, rowSpan: 6},
            {columnSpan: 4, rowSpan: 3},
            {columnSpan: 4, rowSpan: 3},
            {columnSpan: 12, rowSpan: 3},
        ];
        const shape = manga[index % manga.length];
        return {
            columnStart: null,
            rowStart: null,
            columnSpan: shape.columnSpan,
            rowSpan: shape.rowSpan,
            shape: "manga",
        };
    }

    const classic = [
        {columnSpan: 6, rowSpan: 3},
        {columnSpan: 6, rowSpan: 3},
        {columnSpan: 4, rowSpan: 3},
        {columnSpan: 4, rowSpan: 3},
        {columnSpan: 4, rowSpan: 3},
    ];
    const shape = classic[index % classic.length];
    return {
        columnStart: null,
        rowStart: null,
        columnSpan: shape.columnSpan,
        rowSpan: shape.rowSpan,
        shape: "classic",
    };
}

function customTileLayout(thumb) {
    const savedColumnSpan = safeNumber(thumb?.gridColumnSpan, 0);
    const savedRowSpan = safeNumber(thumb?.gridRowSpan, 0);
    const hasSavedComicSize = savedColumnSpan > 1 || savedRowSpan > 1;
    const fallback = presetTileLayout(thumb, safeNumber(thumb?._sceneIndex, 0), 12, "GRID_3");

    return {
        columnStart: thumb.gridColumn ?? null,
        columnSpan: hasSavedComicSize ? savedColumnSpan : fallback.columnSpan,
        rowStart: thumb.gridRow ?? null,
        rowSpan: hasSavedComicSize ? savedRowSpan : fallback.rowSpan,
        shape: "custom",
    };
}

export function buildStoryboardItems({
                                         thumbnails,
                                         layoutMode,
                                         preset,
                                         columns,
                                     }) {
    const normalizedMode = String(layoutMode || "PRESET").toUpperCase();
    const normalizedPreset = String(preset || "GRID_3").toUpperCase();
    const normalizedColumns = presetColumns(normalizedPreset, columns);

    return sortByIdxThenId(thumbnails).map((thumb, index) => {
        if (normalizedMode === "CUSTOM") {
            return {
                ...thumb,
                _layout: customTileLayout({...thumb, _sceneIndex: index}),
                _sceneNumber: index + 1,
            };
        }

        return {
            ...thumb,
            _layout: presetTileLayout(thumb, index, normalizedColumns, normalizedPreset),
            _sceneNumber: index + 1,
        };
    });
}

export function storyboardItemStyle(item) {
    const layout = item?._layout ?? {
        columnStart: null,
        columnSpan: 1,
        rowStart: null,
        rowSpan: 1,
    };

    return {
        gridColumn: layout.columnStart
            ? `${layout.columnStart} / span ${layout.columnSpan}`
            : `span ${layout.columnSpan}`,
        ...(layout.rowStart
            ? {gridRow: `${layout.rowStart} / span ${layout.rowSpan}`}
            : (layout.rowSpan > 1 ? {gridRow: `span ${layout.rowSpan}`} : {})),
        "--storyboard-tile-cols": layout.columnSpan,
        "--storyboard-tile-rows": layout.rowSpan,
    };
}
