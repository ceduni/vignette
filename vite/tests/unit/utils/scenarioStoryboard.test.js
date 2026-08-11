import {
    buildPlaybackQueue,
    buildSelectedAudios,
    buildStoryboardItems,
    clamp,
    defaultTileLayout,
    nullableInt,
    safeNumber,
    sortByIdxThenId,
    storyboardItemStyle,
} from "@/utils/scenarioStoryboard";

describe("scenarioStoryboard utils", () => {
    it("safeNumber returns fallback for invalid values", () => {
        expect(safeNumber("12", 0)).toBe(12);
        expect(safeNumber("abc", 7)).toBe(7);
        expect(safeNumber(undefined, 4)).toBe(4);
    });

    it("clamp constrains values", () => {
        expect(clamp(5, 1, 10)).toBe(5);
        expect(clamp(-5, 1, 10)).toBe(1);
        expect(clamp(50, 1, 10)).toBe(10);
    });

    it("nullableInt returns null for empty-ish values and truncates numbers", () => {
        expect(nullableInt("")).toBeNull();
        expect(nullableInt(null)).toBeNull();
        expect(nullableInt(undefined)).toBeNull();
        expect(nullableInt("4.9")).toBe(4);
        expect(nullableInt("abc")).toBeNull();
    });

    it("sortByIdxThenId sorts by idx then id", () => {
        const result = sortByIdxThenId([
            {id: 10, idx: 2},
            {id: 5, idx: 1},
            {id: 3, idx: 1},
            {id: 99},
        ]);

        expect(result.map((x) => x.id)).toEqual([3, 5, 10, 99]);
    });

    it("buildSelectedAudios returns sorted audios for selected thumbnail", () => {
        const selectedThumb = {id: 5};
        const audioMap = {
            5: [
                {id: 50, idx: 2},
                {id: 40, idx: 1},
            ],
        };

        const result = buildSelectedAudios(audioMap, selectedThumb);
        expect(result.map((x) => x.id)).toEqual([40, 50]);
    });

    it("buildPlaybackQueue flattens thumbnails and audios in sorted order", () => {
        const thumbnails = [
            {id: 10, idx: 2, title: "B"},
            {id: 5, idx: 1, title: "A"},
        ];

        const audioMap = {
            5: [
                {id: 500, idx: 2, title: "A2"},
                {id: 400, idx: 1, title: "A1"},
            ],
            10: [
                {id: 1000, idx: 1, title: "B1"},
            ],
        };

        const result = buildPlaybackQueue(
            thumbnails,
            audioMap,
            (audio) => `/api/audios/${audio.id}/content`
        );

        expect(result.map((x) => x.audioId)).toEqual([400, 500, 1000]);
        expect(result[0].audioUrl).toBe("/api/audios/400/content");
        expect(result[0].thumbnailId).toBe(5);
    });

    it("defaultTileLayout adapts to portrait and landscape images", () => {
        expect(defaultTileLayout({imageWidth: 1600, imageHeight: 800}, 0, 3)).toEqual({
            columnStart: null,
            columnSpan: 2,
            rowStart: null,
            rowSpan: 2,
            aspectRatio: "16 / 9",
            shape: "feature",
        });

        expect(defaultTileLayout({imageWidth: 800, imageHeight: 1400}, 1, 3)).toEqual({
            columnStart: null,
            columnSpan: 1,
            rowStart: null,
            rowSpan: 2,
            aspectRatio: "3 / 4",
            shape: "portrait",
        });

        expect(defaultTileLayout({imageWidth: 1600, imageHeight: 800}, 1, 3)).toEqual({
            columnStart: null,
            columnSpan: 2,
            rowStart: null,
            rowSpan: 1,
            aspectRatio: "16 / 9",
            shape: "wide",
        });
    });

    it("buildStoryboardItems supports CUSTOM mode", () => {
        const result = buildStoryboardItems({
            thumbnails: [
                {
                    id: 1,
                    idx: 1,
                    gridColumn: 3,
                    gridRow: 2,
                    gridColumnSpan: 2,
                    gridRowSpan: 4,
                },
            ],
            layoutMode: "CUSTOM",
            columns: 3,
        });

        expect(result[0]._layout).toEqual({
            columnStart: 3,
            columnSpan: 2,
            rowStart: 2,
            rowSpan: 4,
            shape: "custom",
        });
    });

    it("storyboardItemStyle formats grid CSS style", () => {
        expect(storyboardItemStyle({
            _layout: {
                columnStart: 2,
                columnSpan: 3,
                rowStart: 4,
                rowSpan: 2,
            },
        })).toEqual({
            gridColumn: "2 / span 3",
            gridRow: "4 / span 2",
            "--storyboard-tile-cols": 3,
            "--storyboard-tile-rows": 2,
        });

        expect(storyboardItemStyle({
            _layout: {
                columnStart: null,
                columnSpan: 2,
                rowStart: null,
                rowSpan: 1,
            },
        })).toEqual({
            gridColumn: "span 2",
            "--storyboard-tile-cols": 2,
            "--storyboard-tile-rows": 1,
        });
    });
});
