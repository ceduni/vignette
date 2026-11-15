import {expect, test} from "@playwright/test";

function wavBuffer() {
    const sampleRate = 8000;
    const samples = 8000;
    const buffer = Buffer.alloc(44 + samples * 2);
    buffer.write("RIFF", 0);
    buffer.writeUInt32LE(36 + samples * 2, 4);
    buffer.write("WAVE", 8);
    buffer.write("fmt ", 12);
    buffer.writeUInt32LE(16, 16);
    buffer.writeUInt16LE(1, 20);
    buffer.writeUInt16LE(1, 22);
    buffer.writeUInt32LE(sampleRate, 24);
    buffer.writeUInt32LE(sampleRate * 2, 28);
    buffer.writeUInt16LE(2, 32);
    buffer.writeUInt16LE(16, 34);
    buffer.write("data", 36);
    buffer.writeUInt32LE(samples * 2, 40);
    return buffer;
}

test("scenario page loads, selects scenes, and opens the studio player", async ({page}) => {
    await page.route("**/api/auth/me", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                id: 1,
                username: "ownerUser",
            }),
        });
    });

    await page.route("**/api/auth/refresh", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                accessToken: "test-token",
            }),
        });
    });

    await page.route("**/api/scenarios/77", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                id: 77,
                title: "Scenario Alpha",
                description: "Scenario description",
                authorUsername: "ownerUser",
                languageId: 42,
                visibilityStatus: "DRAFT",
                storyboardLayoutMode: "PRESET",
                storyboardPreset: "GRID_3",
                storyboardColumns: 3,
            }),
        });
    });

    await page.route("**/api/languages/42", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                id: 42,
                name: "Chuj",
            }),
        });
    });

    await page.route("**/api/community/accreditation-requests**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([]),
        });
    });

    await page.route("**/api/scenarios/77/background-audios", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
                {
                    id: 900,
                    idx: 1,
                    title: "Rain ambience",
                    contentUrl: "/api/audios/900/content",
                    active: false,
                },
                {
                    id: 901,
                    idx: 2,
                    title: "Forest ambience",
                    contentUrl: "/api/audios/901/content",
                    active: true,
                    volume: 35,
                    loop: true,
                },
            ]),
        });
    });

    await page.route("**/api/scenarios/77/thumbnails", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
                {
                    id: 5,
                    idx: 1,
                    title: "First",
                    imageWidth: 800,
                    imageHeight: 1200,
                },
                {
                    id: 10,
                    idx: 2,
                    title: "Second",
                    imageWidth: 1200,
                    imageHeight: 800,
                },
            ]),
        });
    });

    await page.route("**/api/thumbnails/5/audios", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
                {
                    id: 500,
                    idx: 1,
                    title: "Audio A",
                    markerX: 20,
                    markerY: 30,
                    markerLabel: "A",
                    transcription: "Hola",
                    gloss: "hello",
                    freeTranslation: "Good morning",
                },
                {
                    id: 501,
                    idx: 2,
                    title: "Audio A second take",
                    markerX: 60,
                    markerY: 30,
                    markerLabel: "B",
                    transcription: "Adiós",
                    gloss: "goodbye",
                },
            ]),
        });
    });

    await page.route("**/api/thumbnails/10/audios", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
                {
                    id: 1000,
                    idx: 1,
                    title: "Audio B",
                    markerX: null,
                    markerY: null,
                    markerLabel: null,
                },
            ]),
        });
    });

    await page.route("**/api/thumbnails/*/content", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "image/png",
            body: "",
        });
    });

    await page.route("**/api/audios/*/content", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "audio/wav",
            body: wavBuffer(),
        });
    });

    await page.goto("/scenarios/77");

    await expect(page.getByRole("heading", {name: "Scenario Alpha"})).toBeVisible();
    await expect(page.locator(".fiche-card")).toHaveCount(2);
    await expect(page.locator(".studio-recorder")).toContainText("First");

    await page.locator(".fiche-card").nth(1).click();
    await expect(page.locator(".studio-recorder")).toContainText("Second");

    await expect(page.locator('button[title="Preview scenario"]')).toBeEnabled();
    await page.locator('button[title="Preview scenario"]').click();
    await expect(page.locator(".reader-grid")).toBeVisible();
    await expect(page.getByRole("button", {name: "Ambience on"})).toBeVisible();
    await expect(page.locator("audio[data-reader-ambience]")).toHaveAttribute("src", "/api/audios/901/content");

    await expect(page.locator("#reader-glossary")).toContainText("Good morning");
    await expect(page.locator("#reader-glossary")).toContainText("Adiós");

    await page.locator("audio[data-reader-voice]").evaluate((audio) => {
        window.readerVoiceSources = [audio.getAttribute("src")];
        audio.addEventListener("loadstart", () => {
            window.readerVoiceSources.push(new URL(audio.src).pathname);
        });
    });

    await page.getByRole("button", {name: "Play from start"}).click();
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => !audio.paused)).toBe(true);
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => audio.volume)).toBeCloseTo(0.35, 2);
    await expect.poll(async () => {
        const sources = await page.evaluate(() => window.readerVoiceSources || []);
        return [...new Set(sources.map((source) => Number(source.match(/\/audios\/(\d+)\/content/)?.[1])).filter(Boolean))];
    }, {timeout: 6000}).toEqual([500, 501, 1000]);
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => audio.paused)).toBe(true);
    await page.locator("#reader-glossary").getByRole("button", {name: "First · Take 1"}).click();
    await expect(page.locator("#reader-glossary")).toContainText("Hola");
    await expect(page.locator("#reader-glossary")).not.toContainText("Adiós");
    await page.getByLabel("Glossary take").selectOption("1");
    await expect(page.locator("#reader-glossary")).toContainText("Adiós");
    await expect(page.locator("#reader-glossary")).not.toContainText("Hola");
    await page.getByRole("button", {name: "Glossary", exact: true}).click();
    await expect(page.locator("#reader-glossary")).toHaveCount(0);
    await page.getByRole("button", {name: "Ambience on", exact: true}).click();
    await expect(page.getByRole("button", {name: "Ambience off", exact: true})).toBeVisible();
    await page.getByRole("button", {name: "Close (Esc)", exact: true}).click();
    await expect(page.locator(".reader")).toHaveCount(0);

    // A published story can have ambiance and images without any voice takes.
    await page.route("**/api/thumbnails/*/audios", (route) => route.fulfill({
        status: 200, contentType: "application/json", body: "[]",
    }));
    await page.locator('button[title="Preview scenario"]').click();
    await expect(page.locator(".reader-grid")).toBeVisible();
    await expect(page.getByRole("button", {name: "Glossary", exact: true})).toHaveCount(0);
    await page.getByRole("button", {name: "Play from start"}).click();
    await expect(page.locator(".reader-scene")).toBeVisible();
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => !audio.paused)).toBe(true);
    await page.getByRole("button", {name: "Ambience on", exact: true}).click();
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => audio.paused)).toBe(true);
    await page.getByRole("button", {name: "Ambience off", exact: true}).click();
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => !audio.paused)).toBe(true);
    await page.getByRole("button", {name: "Stop", exact: true}).click();
    await expect.poll(() => page.locator("audio[data-reader-ambience]").evaluate((audio) => audio.paused)).toBe(true);
    await page.getByRole("button", {name: "Close (Esc)", exact: true}).click();
});
