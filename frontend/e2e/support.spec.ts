import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { captureConsole, uiLogin } from './helpers';
import { SupportContent, InquiryReceipt } from '../src/app/support/support.service';
/** Read committed backend state without adding an inquiry-list API or replacing UI authentication.
 * Notes: use the same DB_NAME as the parent-owned live backend; connection values match its environment.
 */
function inquiryRow(reference:string){
 if(!/^NB-[0-9]+$/.test(reference))throw new Error('Invalid receipt');
 const sql=`SELECT json_build_object('count',count(*),'userId',min(user_id::text),'subject',min(subject),'message',min(message),'created',bool_and(created_at IS NOT NULL)) FROM customer_inquiries WHERE reference_number='${reference}'`;
 return JSON.parse(execFileSync('psql',['-h',process.env['PGHOST']??'localhost','-U',process.env['DB_USERNAME']??'postgres','-d',process.env['DB_NAME']??'nova','-At','-c',sql],{encoding:'utf8',env:{...process.env,PGPASSWORD:process.env['DB_PASSWORD']??'postgres'}}).trim());
}
/** Exercise real login, directory, FAQ, enquiry and persisted receipt across navigation. */
test('customer uses support directory and FAQ then sends a persisted simulated enquiry',async({page},testInfo)=>{
 const console=captureConsole(page);const session=await uiLogin(page);
 const read=page.waitForResponse(r=>r.url().endsWith('/api/v1/support/content'));
 await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Support',exact:true}).click();
 const response=await read;expect(response.status()).toBe(200);const content:SupportContent=await response.json();expect(content.contacts).toHaveLength(4);expect(content.branches).toHaveLength(3);expect(content.faqs).toHaveLength(4);
 for(const channel of content.contacts){await expect(page.getByRole('heading',{name:channel.title,exact:true})).toBeVisible();await expect(page.getByText(channel.detail,{exact:true})).toBeVisible();}
 await page.getByRole('link',{name:'View branch and ATM directory',exact:true}).click();
 for(const branch of content.branches){await expect(page.getByRole('heading',{name:branch.name,exact:true})).toBeVisible();await expect(page.getByText(branch.address,{exact:true})).toBeVisible();}
 await page.getByLabel('Search FAQs',{exact:true}).fill('available credit');await expect(page).toHaveURL(/search=available/);
 await page.getByRole('button',{name:'How is available credit calculated?',exact:true}).click();await expect(page.locator('#faq-credit')).toContainText('PHP 50,000.00 − PHP 8,450.00 = PHP 41,550.00.');
 await page.getByLabel('Search FAQs',{exact:true}).fill('not-a-question');await expect(page.getByText('No FAQs found. Broaden your search.',{exact:true})).toBeVisible();
 await page.getByRole('link',{name:'Contact Us',exact:true}).click();await expect(page).toHaveURL(/\/contact$/);
 await page.getByRole('button',{name:'Send Message (Simulated)',exact:true}).click();await expect(page.locator('#subject-error')).toContainText('Subject is required.');
 await page.getByLabel('Topic',{exact:true}).selectOption('CARDS');await page.getByLabel('Subject',{exact:true}).fill('Card Limit Increase');await page.getByLabel('Message',{exact:true}).fill('Inquiring about credit limit adjustments.');
 const send=page.waitForResponse(r=>r.url().endsWith('/api/v1/support/inquiries')&&r.request().method()==='POST');await page.getByRole('button',{name:'Send Message (Simulated)',exact:true}).click();
 const sent=await send;expect(sent.status()).toBe(201);const receipt:InquiryReceipt=await sent.json();expect(receipt).toEqual({referenceNumber:expect.stringMatching(/^NB-[0-9]+$/),status:'RECEIVED',estimatedResponse:'24h'});
 await expect(page.getByText(`Reference #${receipt.referenceNumber} · RECEIVED · Estimated response 24h`,{exact:true})).toBeVisible();await expect(page.getByText('No live agent was contacted. No third-party email or SMS was sent.',{exact:true})).toBeVisible();
 expect(inquiryRow(receipt.referenceNumber)).toEqual({count:1,userId:session.user.id,subject:'Card Limit Increase',message:'Inquiring about credit limit adjustments.',created:true});
 await page.getByRole('link',{name:'Back to Support',exact:true}).click();await page.getByRole('link',{name:'Back to Home',exact:true}).click();await page.getByRole('link',{name:'Customer Support',exact:true}).click();await expect(page.getByRole('heading',{name:'Ayala Branch',exact:true})).toBeVisible();
 expect(inquiryRow(receipt.referenceNumber).count).toBe(1);await page.screenshot({path:testInfo.outputPath('support-directory.png'),fullPage:true});expect(console.errors).toEqual([]);
});
/** Verify login Contact Us is genuinely public and loads no protected directory anonymously. */
test('anonymous Contact Us offers sign-in guidance without protected data',async({page})=>{
 const console=captureConsole(page);const protectedReads:string[]=[];page.on('request',r=>{if(r.url().endsWith('/api/v1/support/content'))protectedReads.push(r.url());});
 await page.goto('/login');await page.getByRole('link',{name:'Contact Us',exact:true}).click();await expect(page).toHaveURL(/\/contact$/);await expect(page.getByRole('heading',{name:'Contact Us',exact:true})).toBeVisible();await expect(page.getByText('Sign in to continue',{exact:true})).toBeVisible();await expect(page.locator('form')).toHaveCount(0);expect(protectedReads).toEqual([]);
 await page.getByRole('link',{name:'Sign in to continue',exact:true}).click();await page.getByLabel('User ID',{exact:true}).fill('rosa');await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByRole('button',{name:'SIGN IN',exact:true}).click();await expect(page).toHaveURL(/\/home$/);await page.getByRole('link',{name:'Customer Support',exact:true}).click();await expect(page.getByRole('heading',{name:'Customer Support',exact:true})).toBeVisible();expect(console.errors).toEqual([]);
});
