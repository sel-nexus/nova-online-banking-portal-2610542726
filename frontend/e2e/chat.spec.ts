import { test, expect, Page } from '@playwright/test';
import { captureConsole, uiLogin, uiLogout } from './helpers';
import { ChatReply } from '../src/app/chat/chat.service';

/** Submit using a real chip or composer and assert both API and rendered reply contracts. */
async function query(page:Page,prompt:string,expected:ChatReply,chip=true){
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/chat/query')&&r.request().method()==='POST');
 if(chip)await page.locator('.chat-topics').getByRole('button',{name:prompt,exact:true}).click();
 else{await page.getByLabel('Message to assistant',{exact:true}).fill(prompt);await page.locator('.chat-composer').getByRole('button',{name:'Send',exact:true}).click();}
 const result=await response;expect(result.status()).toBe(200);expect(result.request().postDataJSON()).toEqual({message:prompt});
 const reply:ChatReply=await result.json();expect(reply).toEqual(expected);
 await expect(page.locator('.chat-stream li').last()).toContainText(reply.message);
 if(reply.actionLabel){const role=reply.actionRoute==='close'?'button':'link';await expect(page.locator('.chat-stream li').last().getByRole(role,{name:reply.actionLabel,exact:true})).toBeVisible();}
 return reply;
}
/** Verify every PRD topic for each customer and retain a conversation only within the live shell. */
for(const persona of ['miguel','rosa'] as const)test(persona+' explores the persistent assistant and simulated handoff',async({page},testInfo)=>{
 const console=captureConsole(page);const session=await uiLogin(page,persona);
 const header=page.getByRole('button',{name:'Chat Assistant',exact:true});
 const floating=page.getByRole('button',{name:'Open Nova Bank Assistant',exact:true});
 await header.click();await expect(page.getByRole('heading',{name:'Nova Bank Assistant',exact:true})).toBeVisible();
 await expect(page.getByText('Online · Scripted demonstration',{exact:true})).toBeVisible();await expect(page.locator('#chat-pii')).toContainText('Do not share PII');
 await page.locator('.chat-composer').getByRole('button',{name:'Send',exact:true}).click();await expect(page.locator('#chat-validation')).toBeVisible();
 const balance:ChatReply={message:persona==='miguel'?'Your available deposit balance is PHP 142,860.25 across 2 deposit accounts. Your latest Salary credit was PHP 48,500.00 on 13 Oct.':'Your available deposit balance is PHP 54,300.00 in your Salary Account.',actionLabel:'View Account Summary',actionRoute:'/accounts'};
 await query(page,'Account Balance',balance);
 await page.getByRole('button',{name:'Minimize chat',exact:true}).click();await expect(header).toBeFocused();await expect(page.locator('.chat-panel')).toHaveCount(0);
 await floating.click();await expect(page.locator('.chat-stream')).toContainText(balance.message);
 await page.locator('.chat-stream').getByRole('link',{name:'View Account Summary',exact:true}).click();await expect(page).toHaveURL(/\/accounts$/);await expect(page.locator('.chat-stream')).toContainText(balance.message);
 const primary=page.getByRole('navigation',{name:'Primary'});
 await page.getByRole('button',{name:'Minimize chat',exact:true}).click();
 await primary.getByRole('link',{name:'Products',exact:true}).click();await expect(page).toHaveURL(/\/products$/);await floating.click();await expect(page.locator('.chat-stream')).toContainText(balance.message);
 const card:ChatReply={message:persona==='miguel'?'Your Rewards Credit Card •••• 1188 has PHP 8,450.00 due and PHP 41,550.00 available credit. Your next payment is due 28 Oct.':'No active credit cards were found for your profile.',actionLabel:'Emergency Lost Card Hotline',actionRoute:'/support'};
 await query(page,'Card Help',card);
 await page.locator('.chat-stream').getByRole('link',{name:'Emergency Lost Card Hotline',exact:true}).click();await expect(page).toHaveURL(/\/support$/);await expect(page.getByText('(+63 2) 8000 0001',{exact:true})).toBeVisible();await expect(page.locator('.chat-stream')).toContainText(card.message);
 const offers:ChatReply={message:persona==='miguel'?'You have 2 personalized campaigns: Payday Ipon Boost and 5% Dining Cashback. These are demonstration offers.':'Your personalized Home Loan Fee Waiver offers 100% appraisal fee waived. This is a demonstration offer.',actionLabel:'Open Offers Page',actionRoute:'/offers'};
 await query(page,'Offers & Promos',offers);await page.locator('.chat-stream').getByRole('link',{name:'Open Offers Page',exact:true}).click();await expect(page).toHaveURL(/\/offers$/);await expect(page.locator('.chat-stream')).toContainText(offers.message);
 await query(page,'aCcOuNt BaLaNcE',balance,false);
 const hostile='<script>alert(1)</script>';
 await query(page,hostile,{message:'Choose Account Balance, Card Help, Offers & Promos, or Speak to Agent. This is a scripted demonstration assistant.',actionLabel:null,actionRoute:null},false);await expect(page.locator('.chat-stream script')).toHaveCount(0);await expect(page.locator('.chat-stream')).toContainText(hostile);
 expect(await page.evaluate(()=>JSON.stringify(sessionStorage)+JSON.stringify(localStorage))).not.toContain(hostile);
 const agent:ChatReply={message:'Handoff is simulated. No live agent was contacted and no support ticket opened.',actionLabel:'Close Chat',actionRoute:'close'};
 await query(page,'Speak to Agent',agent);await page.locator('.chat-stream li').last().getByRole('button',{name:'Close Chat',exact:true}).click();await expect(page.locator('.chat-panel')).toHaveCount(0);await expect(floating).toBeFocused();
 await header.click();await expect(page.locator('.chat-stream li')).toHaveCount(0);await query(page,'Speak to Representative',agent,false);
 await page.screenshot({path:testInfo.outputPath(persona+'-assistant-handoff.png'),fullPage:true});
 await page.getByRole('button',{name:'Close chat',exact:true}).click();await expect(header).toBeFocused();await floating.click();await expect(page.locator('.chat-stream li')).toHaveCount(0);
 await page.getByLabel('Message to assistant',{exact:true}).press('Escape');await expect(page.locator('.chat-panel')).toHaveCount(0);
 await floating.click();await query(page,'Account Balance',balance);
 await page.getByRole('button',{name:'Minimize chat',exact:true}).click();await uiLogout(page,session.token);await expect(page.getByRole('button',{name:'Chat Assistant',exact:true})).toHaveCount(0);await expect(page.locator('app-chat')).toHaveCount(0);expect(console.errors).toEqual([]);
});
/** Prove the protected assistant is absent on login and anonymous chat requests are denied. */
test('anonymous visitor cannot open or query assistant',async({page})=>{
 const console=captureConsole(page);await page.goto('/login');await expect(page.getByRole('button',{name:'Chat Assistant',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Open Nova Bank Assistant',exact:true})).toHaveCount(0);
 const response=await page.request.post('/api/v1/chat/query',{data:{message:'Account Balance'}});expect(response.status()).toBe(401);expect((await response.json()).message).toBe('Authentication required.');expect(console.errors).toEqual([]);
});
