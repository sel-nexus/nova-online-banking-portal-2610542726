import { test, expect } from '@playwright/test';
import { captureConsole, uiLogin } from './helpers';

/** Verify real persona portfolios on desktop and mobile projects without network mocking. */
for (const persona of [
  {username:'miguel',total:'142860.25',formatted:'142,860.25',count:4,masks:['•••• 4821','•••• 9034','•••• 1188','•••• 7712']},
  {username:'rosa',total:'54300.00',formatted:'54,300.00',count:2,masks:['•••• 2204','•••• 6640']}
]) {
  test(`${persona.username} sees exact own portfolio and navigates overview with persistent session`, async ({page}, testInfo) => {
    const console = captureConsole(page);
    const summaryResponse = page.waitForResponse(r => r.url().endsWith('/api/v1/portfolio/summary'));
    await uiLogin(page, persona.username);
    const response = await summaryResponse;
    expect(response.status()).toBe(200);
    const summary = await response.json();
    expect(summary.relationshipBalance).toBe(persona.total);
    expect(summary.accounts).toHaveLength(persona.count);
    expect(summary.accounts.map((account:any) => account.maskedNumber)).toEqual(persona.masks);
    await expect(page.locator('[data-testid=relationship-balance]')).toContainText(persona.formatted);
    await expect(page.locator('[data-account-id]')).toHaveCount(persona.count);
    await expect(page.getByRole('heading', {level:1})).toHaveText(summary.greeting);
    for (const account of summary.accounts) {
      await expect(page.locator(`[data-account-id="${account.id}"]`)).toContainText(account.maskedNumber);
      expect(account.accountNumber).toBeUndefined();
    }
    const listResponse = page.waitForResponse(r => r.url().endsWith('/api/v1/accounts'));
    await page.getByRole('link', {name:'View all accounts',exact:true}).click();
    const list = await listResponse;
    expect(list.status()).toBe(200);
    expect((await list.json()).map((account:any) => account.id)).toEqual(summary.accounts.map((account:any) => account.id));
    await expect(page.getByRole('heading', {name:'Accounts Overview',exact:true})).toBeVisible();
    await expect(page.locator('[data-account-id]')).toHaveCount(persona.count);
    const reloadResponse = page.waitForResponse(r => r.url().endsWith('/api/v1/accounts'));
    await page.reload(); expect((await reloadResponse).status()).toBe(200);
    await expect(page.locator('[data-account-id]')).toHaveCount(persona.count);
    await page.getByRole('link', {name:'Back to Home',exact:true}).click();
    await expect(page.locator('[data-testid=relationship-balance]')).toContainText(persona.formatted);
    await page.getByRole('link', {name:'View Transactions',exact:true}).click();
    await expect(page).toHaveURL(/\/accounts$/);
    await page.getByLabel('Search accounts',{exact:true}).fill('Savings');
    await page.getByRole('button',{name:'Search',exact:true}).click();
    await expect(page.getByRole('heading',{name:'Accounts Overview',exact:true})).toBeVisible();
    await page.screenshot({path:testInfo.outputPath('portfolio.png'),fullPage:true});
    expect(console.errors).toEqual([]);
  });
}

test('anonymous portfolio entry requires real UI authentication and mobile overview remains usable', async ({page}) => {
  const console = captureConsole(page);
  await page.setViewportSize({width:390,height:844});
  await page.goto('/accounts');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('User ID',{exact:true}).fill('rosa');
  await page.getByLabel('Password',{exact:true}).fill('demo-only');
  const summaryResponse = page.waitForResponse(r => r.url().endsWith('/api/v1/portfolio/summary'));
  await page.getByRole('button',{name:'SIGN IN',exact:true}).click();
  expect((await summaryResponse).status()).toBe(200);
  await expect(page.locator('[data-testid=relationship-balance]')).toContainText('54,300.00');
  await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Accounts',exact:true}).click();
  await expect(page.locator('[data-account-id]')).toHaveCount(2);

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(console.errors).toEqual([]);
});
