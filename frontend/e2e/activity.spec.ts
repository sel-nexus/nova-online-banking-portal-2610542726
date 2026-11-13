import { test, expect, Page, Response } from '@playwright/test';
import { captureConsole, uiLogin, uiLogout } from './helpers';

const savings = 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02';
/** Wait for the real selected-account ledger response, not a mocked browser route. */
function ledger(page: Page) {
  return page.waitForResponse(response => response.url().includes('/api/v1/accounts/' + savings + '/transactions'));
}
/** Check response data and the corresponding visible table content. */
async function checkLedger(page: Page, response: Response, count: number) {
  expect(response.status()).toBe(200); const body = await response.json();
  expect(body.totalCount).toBe(count); expect(body.accountId).toBe(savings);
  await expect(page.getByRole('heading', {name: body.accountName, exact: true})).toBeVisible();
  for (const row of body.transactions) await expect(page.getByRole('button', {name: row.description, exact: true})).toBeVisible();
  return body;
}

test('login, Accounts, Peso Savings, filters, disclosure, empty/reset and credit badge use persisted ledger', async ({page}, info) => {
  const console = captureConsole(page); await uiLogin(page);
  await page.getByRole('link', {name:'Accounts', exact:true}).click();
  const initial = ledger(page);
  await page.getByRole('link', {name:'View details for Peso Savings •••• 9034', exact:true}).click();
  const body = await checkLedger(page, await initial, 5);
  expect(body.availableBalance).toBe('116620.10'); expect(body.currentBalance).toBe('116620.10');
  expect(body.pendingAmount).toBe('0.00'); expect(body.transactions[0].amount).toBe('48500.00');
  await expect(page.getByText('3.25% p.a.', {exact:true})).toBeVisible();
  await expect(page.locator('.money-in')).toContainText('+ PHP 48,500.00');
  await expect(page.locator('.money-in')).toContainText('Money In');
  const first = body.transactions[0]; const button = page.getByRole('button', {name:first.description, exact:true});
  await button.focus(); await page.keyboard.press('Enter'); await expect(button).toHaveAttribute('aria-expanded','true');
  const drawer = page.locator('#detail-' + first.id);
  await expect(drawer).toContainText(first.referenceCode); await expect(drawer).toContainText(first.postedAt);
  await expect(drawer).toContainText('No real money was moved'); await button.click(); await expect(drawer).toBeHidden();
  let pending = ledger(page); await page.getByLabel('Search merchant or reference').fill('882194');
  await checkLedger(page, await pending, 1); await expect(page.getByRole('button',{name:'Makati Coffee Shop',exact:true})).toBeVisible();
  pending = ledger(page); await page.getByRole('button',{name:'Reset Filters',exact:true}).click(); await checkLedger(page,await pending,5);
  pending = ledger(page); await page.getByLabel('Date range',{exact:true}).selectOption('7'); await checkLedger(page,await pending,4);
  pending = ledger(page); await page.getByLabel('Direction',{exact:true}).selectOption('CREDIT'); await checkLedger(page,await pending,1);
  pending = ledger(page); await page.getByLabel('Category',{exact:true}).selectOption('GROCERIES'); await checkLedger(page,await pending,0);
  await expect(page.getByText('No transactions found in this period. Reset filters or broaden your search.',{exact:true})).toBeVisible();
  pending = ledger(page); await page.getByRole('button',{name:'Reset Filters',exact:true}).click(); await checkLedger(page,await pending,5);
  await page.getByLabel('Date range',{exact:true}).selectOption('custom');
  pending = ledger(page); await page.getByLabel('Start date (UTC)',{exact:true}).fill('2026-10-12'); await checkLedger(page,await pending,2);
  pending = ledger(page); await page.getByLabel('End date (UTC)',{exact:true}).fill('2026-10-12'); await checkLedger(page,await pending,1);
  pending = ledger(page); await page.getByRole('button',{name:'Reset Filters',exact:true}).click(); await checkLedger(page,await pending,5);
  await page.screenshot({path:info.outputPath('activity.png'),fullPage:true});
  await page.getByRole('link',{name:'Back to Accounts Overview',exact:true}).click();
  pending = ledger(page); await page.getByRole('link',{name:'View details for Peso Savings •••• 9034',exact:true}).click(); await checkLedger(page,await pending,5);
  expect(console.errors).toEqual([]);
});

test('dashboard transaction quick link opens a real selected account and revoked/anonymous APIs deny access', async ({page}) => {
  const console = captureConsole(page); const session = await uiLogin(page);
  const response = page.waitForResponse(r=>r.url().includes('/transactions'));
  await page.getByRole('link',{name:'View Transactions',exact:true}).click();
  const read = await response; expect(read.status()).toBe(200); const body = await read.json();
  await expect(page.getByRole('heading',{name:body.accountName,exact:true})).toBeVisible();
  const anonymous = await page.request.get('/api/v1/accounts/' + savings + '/transactions'); expect(anonymous.status()).toBe(401);
  await uiLogout(page,session.token);
  const revoked = await page.request.get('/api/v1/accounts/' + savings + '/transactions',{headers:{Authorization:'Bearer '+session.token}}); expect(revoked.status()).toBe(401);
  expect(console.errors).toEqual([]);
});

test('Rosa API and browser history navigation cannot disclose Miguel account metadata', async ({page}) => {
  const console = captureConsole(page); const session = await uiLogin(page,'rosa');
  const path = '/accounts/' + savings;
  const denied = await page.request.get('/api/v1' + path + '/transactions',{headers:{Authorization:'Bearer '+session.token}});
  expect(denied.status()).toBe(403); const body = await denied.json(); expect(body.message).toBe('Account Not Available');
  expect(JSON.stringify(body)).not.toMatch(/Peso|9034|116620/);
  console.expectedStatuses.add(403);
  const response = ledger(page);
  // Exercise a hostile client-side navigation through the router's browser history boundary, without adding a product link to another customer.
  await page.evaluate(route => { history.pushState(null,'',route); dispatchEvent(new PopStateEvent('popstate')); },path);
  expect((await response).status()).toBe(403);
  await expect(page.getByRole('heading',{name:'Account Not Available',exact:true})).toBeVisible();
  await expect(page.locator('main')).not.toContainText('Peso Savings'); await expect(page.locator('main')).not.toContainText('9034');
  await page.getByRole('link',{name:'Back to Accounts Overview',exact:true}).click(); await expect(page.getByRole('heading',{name:'Accounts Overview',exact:true})).toBeVisible();
  expect(console.errors).toEqual([]);
});

test('anonymous direct account entry redirects to login without account metadata', async ({page}) => {
  const console = captureConsole(page); await page.goto('/accounts/' + savings);
  await expect(page).toHaveURL(/\/login$/); await expect(page.getByRole('button',{name:'SIGN IN',exact:true})).toBeVisible();
  await expect(page.locator('body')).not.toContainText('Peso Savings'); expect(console.errors).toEqual([]);
});
