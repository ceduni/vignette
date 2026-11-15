import {expect, test} from "@playwright/test";

test("protected route redirects anonymous user to login", async ({page}) => {
    await page.route("**/api/auth/me", async (route) => {
        await route.fulfill({
            status: 401,
            contentType: "application/json",
            body: JSON.stringify({
                message: "Unauthorized",
            }),
        });
    });

    await page.goto("/user");

    await expect(page).toHaveURL(/\/login\?redirect=(%2Fuser|\/user)/);
});

test("guest-only login route redirects authenticated user home", async ({page}) => {
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

    await page.goto("/login");

    await expect(page).toHaveURL(/\/$/);
});
