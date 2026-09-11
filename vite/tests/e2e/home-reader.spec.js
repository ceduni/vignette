import {expect, test} from "@playwright/test";

for (const username of [null, "reader", "author"]) {
    test(`homepage carousel opens the ${username === "author" ? "studio for its author" : "viewer for " + (username || "a guest")}`, async ({page}) => {
        const scenario = {id: 77, title: "A community story", authorUsername: "author", visibilityStatus: "PUBLISHED"};
        await page.route("**/api/**", async (route) => {
            const path = new URL(route.request().url()).pathname;
            let body = [];
            if (path === "/api/auth/me") {
                await route.fulfill({status: username ? 200 : 401, json: username ? {id: 1, username} : {}});
                return;
            }
            if (path === "/api/auth/refresh") body = {accessToken: "test"};
            if (path === "/api/scenarios") body = [scenario];
            if (path === "/api/scenarios/77") body = scenario;
            if (path === "/api/scenarios/77/thumbnails") body = [{id: 5, idx: 1, title: "Greeting"}];
            if (path === "/api/thumbnails/5/audios") body = [{id: 500, idx: 1, title: "Voice", transcription: "A transcription on its own", gloss: null, freeTranslation: null}];
            await route.fulfill({status: 200, json: body});
        });
        await page.goto("/");
        await page.locator(".home-card:not(.home-card--skel)").click();
        if (username === "author") {
            await expect(page).toHaveURL(/\/scenarios\/77$/);
            await expect(page.locator(".reader")).toHaveCount(0);
        } else {
            await expect(page.locator(".reader")).toBeVisible();
            await expect(page.locator("#reader-glossary")).toContainText("A transcription on its own");
            await expect(page.locator("#reader-glossary dt")).toHaveText(["Transcription"]);
            if (!username) {
                await page.setViewportSize({width: 390, height: 844});
                await expect(page.locator("#reader-glossary")).toBeVisible();
                await page.screenshot({path: "/tmp/vignette-reader-mobile.png"});
            }
            await page.getByRole("button", {name: "Close (Esc)", exact: true}).click();
            await expect(page.locator(".reader")).toHaveCount(0);
            await expect(page).toHaveURL(/\/$/);
        }
    });
}
