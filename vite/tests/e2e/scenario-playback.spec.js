import {expect, test} from "@playwright/test";

function wavBuffer() {
    const sampleRate = 8000;
    const samples = 160;
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
            body: JSON.stringify([]),
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
});
