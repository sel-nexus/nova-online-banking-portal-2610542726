import { test, expect, Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { uiLogin, uiLogout } from './helpers';
import { AuditEvent } from '../src/app/admin/admin.service';

const notice='Database successfully restored to original demonstration fixtures.';
/** Capture every browser error; permit only the exact intentional 503 request, never a status-wide ignore. */
function errorsFor(page:Page){
 const errors:{text:string;url:string}[]=[];const intentional:string[]=[];
 page.on('pageerror',e=>errors.push({text:e.message,url:''}));
 page.on('console',m=>{if(m.type()==='error')errors.push({text:m.text(),url:m.location().url});});
 return {
  expect503(url:string){intentional.push(url);},
  check(){
   const remaining=[...intentional];
   const unexpected=errors.filter(e=>{
    const index=remaining.indexOf(e.url);
    if(index>=0&&/^Failed to load resource: the server responded with a status of 503(?: \(.*\))?$/.test(e.text)){remaining.splice(index,1);return false;}
    return true;
   });
   expect(unexpected).toEqual([]);
  }
 };
}
/** Read or mutate only local demo fixtures using the backend's configured PostgreSQL connection.
 * Notes: never starts or stops the parent-owned E2E database; commands executing this spec own its lifecycle.
 */
function sql(statement:string){return execFileSync('psql',['-h',process.env['PGHOST']??'localhost','-U',process.env['DB_USERNAME']??'postgres','-d',process.env['DB_NAME']??'nova','-v','ON_ERROR_STOP=1','-At','-c',statement],{encoding:'utf8',env:{...process.env,PGPASSWORD:process.env['DB_PASSWORD']??'postgres'}}).trim();}
/** Navigate through the administrator-only link and verify persistent audit content. */
async function facilitator(page:Page){
 const read=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/audit')&&r.request().method()==='GET');
 await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Facilitator',exact:true}).click();
 const r=await read;expect(r.status()).toBe(200);const events:AuditEvent[]=await r.json();
 await expect(page.getByRole('heading',{name:'Facilitator controls',exact:true})).toBeVisible();
 if(events.length){await expect(page.getByRole('cell',{name:events[0].id,exact:true})).toBeVisible();await expect(page.getByRole('cell',{name:events[0].action,exact:true}).first()).toBeVisible();}
 return events;
}
/** Confirm reset through the real UI and assert the exact API response and refreshed audit. */
async function reset(page:Page){
 await page.getByRole('button',{name:'Facilitator Reset',exact:true}).click();
 await expect(page.getByRole('dialog',{name:'Restore original fixtures?'})).toBeVisible();
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/reset')&&r.request().method()==='POST');
 const audit=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/audit'));
 await page.getByRole('button',{name:'Confirm reset',exact:true}).click();
 const r=await response;expect(r.status()).toBe(200);expect(await r.json()).toEqual({message:notice});expect((await audit).status()).toBe(200);
 await expect(page.locator('.toast')).toContainText(notice);
 await expect(page.getByRole('cell',{name:'RESET_SUCCESS',exact:true}).first()).toBeVisible();
}
/** Sign in again from the logout destination without teleporting or API-login substitution. */
async function reloginAdmin(page:Page){
 await page.getByLabel('User ID',{exact:true}).fill('admin');await page.getByLabel('Password',{exact:true}).fill('demo-only');
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login'));
 await page.getByRole('button',{name:'SIGN IN',exact:true}).click();const r=await response;expect(r.status()).toBe(200);await expect(page).toHaveURL(/\/home$/);return r.json();
}

/** Restore actual mutated financial and activity state, unlock a fixture and prove customer revocation. */
test('facilitator reset restores fixtures, unlocks login, revokes customers and retains historical audit',async({page},testInfo)=>{
 const console=errorsFor(page);const session=await uiLogin(page,'admin');await facilitator(page);await reset(page);
 // Separate customer UI context provides a genuine credential-authenticated session for mutation/replay evidence.
 const context=await page.context().browser()!.newContext({baseURL:new URL(page.url()).origin});const customerPage=await context.newPage();const customerConsole=errorsFor(customerPage);
 try{
  const customer=await uiLogin(customerPage,'miguel');const headers={Authorization:'Bearer '+customer.token};
  const interest=await page.request.post('/api/v1/promotions/d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a20/express-interest',{headers});expect(interest.status()).toBe(200);
  const inquiry=await page.request.post('/api/v1/support/inquiries',{headers,data:{topic:'CARDS',subject:'Reset proof',message:'Simulated enquiry for reset verification.'}});expect(inquiry.status()).toBe(201);expect((await inquiry.json()).referenceNumber).toBe('NB-99214');
  for(let i=0;i<3;i++){const bad=await page.request.post('/api/v1/auth/login',{data:{username:'miguel',password:'wrong'}});expect(bad.status()).toBe(i===2?423:401);}
  sql("UPDATE accounts SET available_balance=1,current_balance=1 WHERE id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'");
  expect(JSON.parse(sql("SELECT json_build_object('interests',(SELECT count(*) FROM user_offer_interests),'inquiries',(SELECT count(*) FROM customer_inquiries),'locked',(SELECT locked FROM users WHERE username='miguel'))"))).toEqual({interests:1,inquiries:1,locked:true});
  const historical=await page.request.get('/api/v1/admin/audit',{headers:{Authorization:'Bearer '+session.token}});expect(historical.status()).toBe(200);const events:AuditEvent[]=await historical.json();
  await reset(page);
  const state=JSON.parse(sql("SELECT json_build_object('interests',(SELECT count(*) FROM user_offer_interests),'inquiries',(SELECT count(*) FROM customer_inquiries),'locked',(SELECT locked FROM users WHERE username='miguel'),'balance',(SELECT available_balance::text FROM accounts WHERE id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'),'accounts',(SELECT count(*) FROM accounts),'transactions',(SELECT count(*) FROM transactions),'promotions',(SELECT count(*) FROM promotions))"));
  expect(state).toEqual({interests:0,inquiries:0,locked:false,balance:'116620.10',accounts:6,transactions:6,promotions:10});
  const replay=await page.request.get('/api/v1/portfolio/summary',{headers});expect(replay.status()).toBe(401);
  const audit=await page.request.get('/api/v1/admin/audit',{headers:{Authorization:'Bearer '+session.token}});expect(audit.status()).toBe(200);const after:AuditEvent[]=await audit.json();expect(after.map(e=>e.id)).toEqual(expect.arrayContaining(events.map(e=>e.id)));
  await uiLogout(page,session.token);await page.getByLabel('User ID',{exact:true}).fill('miguel');await page.getByLabel('Password',{exact:true}).fill('demo-only');const login=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login'));const summary=page.waitForResponse(r=>r.url().endsWith('/api/v1/portfolio/summary'));await page.getByRole('button',{name:'SIGN IN',exact:true}).click();expect((await login).status()).toBe(200);const read=await summary;expect(read.status()).toBe(200);expect((await read.json()).relationshipBalance).toBe('142860.25');await expect(page.getByTestId('relationship-balance')).toContainText('142,860.25');await expect(page.locator('article[data-account-id]')).toHaveCount(4);await expect(page.getByRole('link',{name:'Facilitator',exact:true})).toHaveCount(0);
  customerConsole.check();await page.screenshot({path:testInfo.outputPath('reset-restored-customer.png'),fullPage:true});console.check();
 }finally{await context.close();}
});

/** Switch to a real customer session, prove loss of administrator authority and re-login through UI. */
test('persona switch replaces authority and facilitator is regained only with administrator credentials',async({page})=>{
 const console=errorsFor(page);const admin=await uiLogin(page,'admin');await facilitator(page);await reset(page);
 await page.getByLabel('Customer persona',{exact:true}).selectOption('rosa');await page.getByRole('button',{name:'Switch persona',exact:true}).click();await expect(page.getByRole('dialog',{name:'Replace administrator session?'})).toContainText('Log out and sign in as admin');
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/persona'));const summary=page.waitForResponse(r=>r.url().endsWith('/api/v1/portfolio/summary'));await page.getByRole('button',{name:'Confirm switch',exact:true}).click();const r=await response;expect(r.status()).toBe(200);const customer=await r.json();expect(customer.user.username).toBe('rosa');expect(customer.user.role).toBe('ROLE_CUSTOMER');expect((await summary).status()).toBe(200);
 await expect(page).toHaveURL(/\/home$/);await expect(page.locator('article[data-account-id]')).toHaveCount(2);await expect(page.getByTestId('relationship-balance')).toContainText('54,300.00');await expect(page.getByRole('link',{name:'Facilitator',exact:true})).toHaveCount(0);
 expect(await page.evaluate(()=>sessionStorage.getItem('nova.session'))).not.toContain(admin.token);
 const forbidden=await page.request.post('/api/v1/admin/reset',{headers:{Authorization:'Bearer '+customer.token}});expect(forbidden.status()).toBe(403);
 const oldAdmin=await page.request.get('/api/v1/admin/audit',{headers:{Authorization:'Bearer '+admin.token}});expect(oldAdmin.status()).toBe(401);
 await uiLogout(page,customer.token);await reloginAdmin(page);const audit=await facilitator(page);expect(audit.some(e=>e.action==='PERSONA_SWITCH_ROSA')).toBe(true);console.check();
});

/** Exercise one exact intentional 503, recover via admin controls, retry business reads and test idle warning. */
test('session simulation returns 503, disables without deadlock, retries and warns the current admin',async({page},testInfo)=>{
 const console=errorsFor(page);const admin=await uiLogin(page,'admin');await facilitator(page);await reset(page);
 const enable=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/simulation'));await page.getByRole('button',{name:'Enable service error',exact:true}).click();const enabled=await enable;expect(enabled.status()).toBe(200);expect(await enabled.json()).toEqual({serviceError:true});await expect(page.getByText('Simulation enabled',{exact:true})).toBeVisible();
 const url=new URL('/api/v1/portfolio/summary',page.url()).href;console.expect503(url);
 const failed=page.waitForResponse(r=>r.url()===url&&r.request().method()==='GET');await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Home',exact:true}).click();const failure=await failed;expect(failure.status()).toBe(503);expect((await failure.json()).message).toContain('Simulated service error');await expect(page.getByRole('heading',{name:"We couldn't load your portfolio.",exact:true})).toBeVisible();
 await facilitator(page);const disable=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/simulation'));await page.getByRole('button',{name:'Disable service error',exact:true}).click();const disabled=await disable;expect(disabled.status()).toBe(200);expect(await disabled.json()).toEqual({serviceError:false});
 const retry=page.waitForResponse(r=>r.url()===url);await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Home',exact:true}).click();expect((await retry).status()).toBe(200);await expect(page.getByRole('heading',{name:'No accounts to display',exact:true})).toBeVisible();
 await facilitator(page);await page.getByRole('button',{name:'Test timeout warning',exact:true}).click();await expect(page.getByRole('dialog',{name:'Are you still there?'})).toBeVisible();await expect(page.getByRole('dialog',{name:'Are you still there?'})).toContainText('seconds');await page.getByRole('button',{name:'Keep me signed in',exact:true}).click();await expect(page.getByRole('dialog',{name:'Are you still there?'})).not.toBeVisible();
 const enableAgain=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/simulation'));await page.getByRole('button',{name:'Enable service error',exact:true}).click();expect((await enableAgain).status()).toBe(200);await page.screenshot({path:testInfo.outputPath('facilitator-controls.png'),fullPage:true});await uiLogout(page,admin.token);console.check();
});

/** Assert anonymous administrator entry redirects without protected controls or calls. */
test('anonymous facilitator route redirects to sign in',async({page})=>{
 const console=errorsFor(page);const calls:string[]=[];page.on('request',r=>{if(r.url().includes('/api/v1/admin/'))calls.push(r.url());});await page.goto('/admin');await expect(page).toHaveURL(/\/login$/);await expect(page.getByRole('button',{name:'SIGN IN',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Facilitator Reset',exact:true})).toHaveCount(0);expect(calls).toEqual([]);console.check();
});
