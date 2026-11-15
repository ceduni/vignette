import {expect, test} from '@playwright/test';

test('creation explains actual draft visibility and submits only supported settings', async ({page}) => {
    let submitted;
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        let body = [];
        if (path === '/api/auth/me') body = {id: 1, username: 'author', roles: ['ROLE_USER']};
        if (path === '/api/auth/refresh') body = {accessToken: 'test-token'};
        if (path === '/api/languages/options') body = {content: [{id: 'stan1290', name: 'French'}]};
        if (path === '/api/scenarios' && route.request().method() === 'POST') {
            submitted = route.request().postDataJSON();
            await route.fulfill({status: 201, json: {id: 550, ...submitted}});
            return;
        }
        if (path === '/api/scenarios/550') body = {id: 550, title: 'Field story', languageId: 'stan1290', authorUsername: 'author', visibilityStatus: 'DRAFT', canEdit: true};
        await route.fulfill({status: 200, json: body});
    });
    await page.goto('/create-scenario');
    await expect(page.getByText(/Publishing makes its images, recordings and any annotations public/)).toBeVisible();
    await expect(page.getByRole('checkbox', {name: 'Show transcription'})).toHaveCount(0);
    await expect(page.getByRole('checkbox', {name: 'Allow download'})).toHaveCount(0);
    await expect(page.getByRole('button', {name: /Only visible to me/})).toHaveCount(0);
    await page.getByRole('button', {name: 'Create scenario', exact: true}).click();
    await expect(page.getByText('A title is required.', {exact: true})).toBeVisible();
    await page.getByPlaceholder('e.g. Market scene, morning greetings').fill('Field story');
    await page.locator('.cs-combo__input').fill('French');
    await page.locator('.cs-combo__opt').filter({hasText: /^French$/}).click();
    await page.getByRole('button', {name: 'Create scenario', exact: true}).click();
    await expect(page).toHaveURL(/\/scenarios\/550$/);
    expect(submitted).toEqual({title: 'Field story', description: '', languageId: 'stan1290', tags: []});
});
