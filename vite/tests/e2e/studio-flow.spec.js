import {expect, test} from "@playwright/test";

function json(body, status = 200) {
    return {
        status,
        contentType: "application/json",
        body: JSON.stringify(body),
    };
}

function tinyImage() {
    return Buffer.from("R0lGODlhAQABAAAAACw=", "base64");
}

function wavBuffer() {
    const sampleRate = 8000;
    const samples = 800;
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
    for (let i = 0; i < samples; i += 1) {
        const value = Math.round(Math.sin((i / sampleRate) * 440 * Math.PI * 2) * 0x2000);
        buffer.writeInt16LE(value, 44 + i * 2);
    }
    return buffer;
}

test("studio flow creates a maker scene, imports audio, adds ambience, opens player, and publishes", async ({page}) => {
    let scenario = {
        id: 88,
        title: "Studio Flow",
        description: "Full studio flow",
        authorUsername: "ownerUser",
        languageId: "swah1253",
        visibilityStatus: "DRAFT",
        storyboardLayoutMode: "PRESET",
        storyboardPreset: "GRID_3",
        storyboardColumns: 3,
        tags: [],
    };
    let thumbnails = [];
    let sceneAudios = [];
    let backgroundAudios = [];
    let sawConvertedSceneImport = false;
    let sawBackgroundUpload = false;

    await page.route("**/api/auth/me", async (route) => {
        await route.fulfill(json({id: 1, username: "ownerUser"}));
    });

    await page.route("**/api/auth/refresh", async (route) => {
        await route.fulfill(json({accessToken: "test-token"}));
    });

    await page.route("**/api/languages/swah1253", async (route) => {
        await route.fulfill(json({id: "swah1253", name: "Swahili"}));
    });

    await page.route("**/api/community/accreditation-requests**", async (route) => {
        await route.fulfill(json([]));
    });

    await page.route("**/api/scenarios/88/thumbnails", async (route) => {
        const method = route.request().method();
        if (method === "GET") {
            await route.fulfill(json(thumbnails));
            return;
        }
        if (method === "POST") {
            thumbnails = [{
                id: 501,
                idx: 1,
                title: "Studio Flow scene",
                imageWidth: 1400,
                imageHeight: 1000,
            }];
            await route.fulfill(json(thumbnails[0], 201));
            return;
        }
        await route.fulfill(json({message: "Unsupported thumbnail method"}, 405));
    });

    await page.route("**/api/thumbnails/501/audios", async (route) => {
        const method = route.request().method();
        if (method === "GET") {
            await route.fulfill(json(sceneAudios));
            return;
        }
        if (method === "POST") {
            const body = route.request().postDataBuffer()?.toString("latin1") ?? "";
            sawConvertedSceneImport = body.includes("Content-Type: audio/wav") && body.includes("take-import.wav");
            sceneAudios = [{
                id: 900,
                idx: 1,
                title: "Take A",
                mime: "audio/wav",
                markerX: null,
                markerY: null,
                markerLabel: null,
            }];
            await route.fulfill(json(sceneAudios[0], 201));
            return;
        }
        await route.fulfill(json({message: "Unsupported audio method"}, 405));
    });

    await page.route("**/api/audios/*/ambience", (route) => route.fulfill({status: 204}));

    await page.route("**/api/scenarios/88/background-audios", async (route) => {
        const method = route.request().method();
        if (method === "GET") {
            await route.fulfill(json(backgroundAudios));
            return;
        }
        if (method === "POST") {
            sawBackgroundUpload = true;
            backgroundAudios = [{
                id: 701,
                idx: 1,
                title: "Market morning",
                mime: "audio/wav",
                sourceLabel: "Vignette preset",
                sourceUrl: "",
            }];
            await route.fulfill(json(backgroundAudios[0], 201));
            return;
        }
        await route.fulfill(json({message: "Unsupported background method"}, 405));
    });

    await page.route("**/api/scenarios/88/publish", async (route) => {
        scenario = {...scenario, visibilityStatus: "PUBLISHED"};
        await route.fulfill(json(scenario));
    });

    await page.route("**/api/scenarios/88", async (route) => {
        await route.fulfill(json(scenario));
    });

    await page.route("**/api/thumbnails/*/content", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "image/gif",
            body: tinyImage(),
        });
    });

    await page.route("**/api/audios/*/content", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "audio/wav",
            body: wavBuffer(),
        });
    });

    await page.goto("/scenarios/88");

    await expect(page.getByText("Start with your first scene")).toBeVisible();
    await page.getByRole("button", {name: "Add a scene"}).click();

    await page.getByRole("button", {name: /create an image with vignette/i}).click();
    await expect(page.locator(".vm-overlay")).toBeVisible();
    await page.getByRole("button", {name: /use this image/i}).click();

    await expect(page.getByRole("button", {name: /add scene to storyboard/i})).toBeEnabled();
    await page.getByRole("button", {name: /add scene to storyboard/i}).click();

    await expect(page.locator(".vg-root")).toBeVisible();
    await expect(page.getByText("Studio Flow scene")).toBeVisible();
    await expect(page.locator(".studio-recorder")).toBeVisible();

    await page.locator(".studio-recorder .rec-collapse-toggle").click();
    const audioChooserPromise = page.waitForEvent("filechooser");
    await page.locator(".studio-recorder .rec-import-btn", {hasText: "Audio file"}).click();
    await (await audioChooserPromise).setFiles({
        name: "take-import.dat",
        mimeType: "application/octet-stream",
        buffer: wavBuffer(),
    });

    await expect.poll(() => sawConvertedSceneImport).toBe(true);
    await expect(page.locator('button[title="Preview scenario"]')).toBeEnabled();

    await page.locator('button[title="Vignette ambience"]').click();
    await expect(page.locator(".amb-card")).toBeVisible();
    await page.getByRole("button", {name: /use preset/i}).click();
    await expect.poll(() => sawBackgroundUpload).toBe(true);
    await expect(page.getByRole("heading", {name: "Market morning"})).toBeVisible();
    await page.locator(".amb-close").click();

    await page.locator('button[title="Preview scenario"]').click();
    await expect(page.locator(".reader-grid")).toBeVisible();
    await expect(page.locator(".reader-grid-card__title", {hasText: "Studio Flow scene"})).toBeVisible();
    await page.locator('.reader-header__actions button[title="Close (Esc)"]').click();
    await expect(page.locator(".reader-backdrop")).toBeHidden();

    await page.getByRole("button", {name: /publish/i}).click();
    await expect(page.locator(".vg-status--pub", {hasText: "Published"})).toBeVisible();

    const primaryNav = page.getByLabel("Primary");
    await primaryNav.getByRole("link", {name: "About", exact: true}).click({noWaitAfter: true});
    await expect(page).toHaveURL(/\/about$/);
    await expect(page.getByRole("heading", {name: "About Vignette"})).toBeVisible();
    await expect(page.locator(".page--studio")).toHaveCount(0);

    await page.goBack();
    await expect(page.locator(".page--studio")).toBeVisible();

    await page.getByLabel("Primary").getByRole("link", {name: "Home", exact: true}).click({noWaitAfter: true});
    await expect(page).toHaveURL(/\/$/);
    await expect(page.locator(".home-page")).toBeVisible();
    await expect(page.locator(".page--studio")).toHaveCount(0);
});
