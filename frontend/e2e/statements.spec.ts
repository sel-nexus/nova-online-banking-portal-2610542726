import { test, expect, Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { captureConsole, uiLogin, uiLogout } from './helpers';

const savings='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02';
/** Click the real CTA and inspect the browser's saved UTF-8 text, never substituting an API call for download. */
async function downloadText(page:Page, filename:string) {
  const pending=page.waitForEvent('download');
  const response=page.waitForResponse(r=>r.url().endsWith('/statement/download'));
  await page.getByRole('button',{name:'Download Statement',exact:true}).click();
  const download=await pending;expect(download.suggestedFilename()).toBe(filename);expect(await download.failure()).toBeNull();
  const http=await response;expect(http.status()).toBe(200);expect(http.headers()['content-type']).toBe('text/plain;charset=UTF-8');
  expect(http.headers()['content-disposition']).toBe('attachment; filename="'+filename+'"');
  const path=await download.path();expect(path).not.toBeNull();
  const text=await readFile(path!,'utf8');
  expect(text).toContain('Demo notice: This is a demonstration statement, not an official bank statement. No real money was moved.');
  for(const privateId of ['10928374821','20938479034','40928371188','50928377712','10928372204','50928376640']) {
    expect(text).not.toContain(privateId);expect(JSON.stringify(http.headers())).not.toContain(privateId);
  }
  await expect(page.getByText('Your demo statement download has started.',{exact:true})).toBeVisible();
  return text;
}

test('login, Accounts, savings detail and filtered ledger still download the entire exact statement',async({page})=> {
  const console=captureConsole(page);await uiLogin(page);
  await page.getByRole('link',{name:'Accounts',exact:true}).click();
  await page.getByRole('link',{name:'View details for Peso Savings •••• 9034',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Peso Savings',exact:true})).toBeVisible();
  let response=page.waitForResponse(r=>r.url().includes('/'+savings+'/transactions'));
  await page.getByLabel('Rows per page',{exact:true}).selectOption({label:'1'});expect((await response).status()).toBe(200);
  response=page.waitForResponse(r=>r.url().includes('/'+savings+'/transactions'));
  await page.getByLabel('Search merchant or reference',{exact:true}).fill('882194');expect((await response).status()).toBe(200);
  await expect(page.getByText('1 transactions found',{exact:true})).toBeVisible();
  const text=await downloadText(page,'statement-9034-demo.txt');
  for(const value of ['Account: Peso Savings\n','Account number: •••• 9034\n','Currency: PHP\n',
    'Available balance: PHP 116620.10\n','Current / ledger balance: PHP 116620.10\n','Pending transactions: PHP 0.00\n',
    'Annual interest rate: 3.25% p.a.\n','Last statement date: 2026-09-30\n','Transaction count: 5\n'])expect(text).toContain(value);
  const rows=[
    ['882191','2026-10-13T09:30:00Z','Salary credit','INCOME','CREDIT','POSTED','48500.00'],
    ['882192','2026-10-12T14:15:00Z','Manila Supermarket','GROCERIES','DEBIT','POSTED','2126.40'],
    ['882193','2026-10-10T11:00:00Z','Savings goal transfer','SAVINGS','DEBIT','POSTED','5000.00'],
    ['882194','2026-10-09T16:45:00Z','Makati Coffee Shop','DINING','DEBIT','PENDING','185.00'],
    ['882195','2026-10-01T08:20:00Z','Metro Manila Transit','TRANSPORT','DEBIT','POSTED','1200.00']
  ];
  for(const [ref,time,description,category,direction,status,amount] of rows)expect(text).toContain(
    `Reference: REF-TX-${ref}\nPosted (UTC): ${time}\nDescription: ${description}\nCategory: ${category}\nDirection: ${direction}\nStatus: ${status}\nAmount: PHP ${amount}\n`);
  expect(text.match(/^Reference:/gm)).toHaveLength(5);expect(console.errors).toEqual([]);
});

for(const persona of [{username:'miguel',name:'Payroll Account',mask:'4821',balance:'26240.15'},{username:'rosa',name:'Salary Account',mask:'2204',balance:'54300.00'}]) {
  test(`${persona.username} dashboard quick entry opens first owned activity and downloads structured empty statement`,async({page})=> {
    const console=captureConsole(page);await uiLogin(page,persona.username);
    await page.getByRole('link',{name:'Download Statements',exact:true}).click();
    await expect(page.getByRole('heading',{name:persona.name,exact:true})).toBeVisible();
    await expect(page).toHaveURL(/\/accounts\/[^/]+#statement-download$/);
    const text=await downloadText(page,`statement-${persona.mask}-demo.txt`);
    expect(text).toContain(`Account: ${persona.name}\nAccount number: •••• ${persona.mask}\n`);
    expect(text).toContain(`Available balance: PHP ${persona.balance}\n`);
    expect(text).toContain('Transaction count: 0\n\nTransactions\nNo account activity.\n');expect(text).not.toContain('Reference:');
    expect(console.errors).toEqual([]);
  });
}

test('card detail downloads the persisted declined zero-value activity',async({page})=> {
  const console=captureConsole(page);await uiLogin(page);await page.getByRole('link',{name:'Accounts',exact:true}).click();
  await page.getByRole('link',{name:'View details for Rewards Credit Card •••• 1188',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Rewards Credit Card',exact:true})).toBeVisible();
  const text=await downloadText(page,'statement-1188-demo.txt');expect(text).toContain('Account number: •••• 1188');
  expect(text).toContain('Reference: REF-TX-882196\nPosted (UTC): 2026-10-08T21:10:00Z\nDescription: Unknown merchant\nCategory: GENERAL\nDirection: DEBIT\nStatus: DECLINED\nAmount: PHP 0.00\n');expect(console.errors).toEqual([]);
});

test('anonymous, wrong owner and revoked exports return no attachment',async({page})=> {
  const console=captureConsole(page);const session=await uiLogin(page,'rosa');const path='/api/v1/accounts/'+savings+'/statement/download';
  const anonymous=await page.request.get(path);expect(anonymous.status()).toBe(401);expect(anonymous.headers()['content-disposition']).toBeUndefined();
  const denied=await page.request.get(path,{headers:{Authorization:'Bearer '+session.token}});expect(denied.status()).toBe(403);expect(denied.headers()['content-disposition']).toBeUndefined();expect(await denied.text()).not.toMatch(/Peso|9034|116620/);
  await uiLogout(page,session.token);const revoked=await page.request.get(path,{headers:{Authorization:'Bearer '+session.token}});expect(revoked.status()).toBe(401);expect(revoked.headers()['content-disposition']).toBeUndefined();expect(console.errors).toEqual([]);
});
