import { test, expect } from '@playwright/test';
import { captureConsole, uiLogin } from './helpers';
import { Offer, Interest, Product } from '../src/app/offers/offer.service';

/** Exercise live navigation and persisted simulations for both seeded personas; do not mock APIs. */
for (const persona of [{username:'miguel',count:8,title:'Payday Ipon Bonus'}, {username:'rosa',count:3,title:'Home Loan Fee Waiver'}]) {
 test(`${persona.username} discovers products, filters campaigns, and persists idempotent simulated interest`, async ({page}, testInfo) => {
  const console = captureConsole(page);
  await uiLogin(page, persona.username);
  const productResponse = page.waitForResponse(r=>r.url().endsWith('/api/v1/products'));
  await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Products',exact:true}).click();
  const products = await productResponse;expect(products.status()).toBe(200);
  const catalogue: Product[] = await products.json();expect(catalogue).toHaveLength(2);
  for(const product of catalogue)await expect(page.getByRole('heading',{name:product.title,exact:true})).toBeVisible();
  await page.getByRole('navigation',{name:'Catalogue categories'}).getByRole('link',{name:'Loans',exact:true}).click();
  await expect(page.locator('section[aria-labelledby="catalogue-title"]').getByRole('status')).toContainText('No products in this category');
  const offerResponse=page.waitForResponse(r=>r.url().endsWith('/api/v1/promotions'));
  await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Offers',exact:true}).click();
  const response=await offerResponse;expect(response.status()).toBe(200);const offers:Offer[]=await response.json();expect(offers).toHaveLength(persona.count);
  expect(offers.some(o=>o.title==='New Year Cashback')).toBe(false);
  expect(offers.some(o=>o.title==='Home Loan Fee Waiver')).toBe(persona.username==='rosa');
  await expect(page.locator('[data-offer-id]')).toHaveCount(persona.count);
  for(const category of ['Savings','Cards','Loans','All Offers']){
   await page.getByRole('navigation',{name:'Catalogue categories'}).getByRole('link',{name:category,exact:true}).click();
   const count=category==='All Offers'?offers.length:offers.filter(o=>o.category===category.toUpperCase()).length;
   await expect(page.locator('[data-offer-id]')).toHaveCount(count);
   if(count===0)await expect(page.locator('section[aria-labelledby="catalogue-title"]').getByRole('status')).toContainText('No offers in this category');
  }
  const selected=offers.find(o=>o.title===persona.title)!;
  const detailResponse=page.waitForResponse(r=>r.url().endsWith('/api/v1/promotions/'+selected.id));
  await page.getByRole('link',{name:'View offer: '+persona.title,exact:true}).click();expect((await detailResponse).status()).toBe(200);
  const dialog=page.getByRole('dialog');await expect(dialog).toContainText(selected.terms);await expect(dialog).toContainText(selected.eligibility);
  expect(await page.evaluate(()=>document.body.style.overflow)).toBe('hidden');
  await page.keyboard.press('Escape');await expect(dialog).not.toBeVisible();await expect(page).toHaveURL(/\/offers$/);
  await expect(page.getByRole('link',{name:'View offer: '+persona.title,exact:true})).toBeFocused();
  await page.getByRole('link',{name:'Back to Home',exact:true}).click();
  await page.getByRole('link',{name:'Explore Products',exact:true}).click();await expect(page).toHaveURL(/\/products$/);
  await page.getByRole('link',{name:'Back to Home',exact:true}).click();
  await page.getByRole('link',{name:'Promotions & Offers',exact:true}).click();await expect(page).toHaveURL(/\/offers$/);
  await page.getByRole('link',{name:'Back to Home',exact:true}).click();
  await page.getByRole('link',{name:persona.title,exact:true}).click();await expect(page).toHaveURL(new RegExp('/offers/'+selected.id+'$'));
  await expect(dialog).toContainText(selected.terms);
  const writeResponse=page.waitForResponse(r=>r.url().endsWith('/api/v1/promotions/'+selected.id+'/express-interest')&&r.request().method()==='POST');
  await dialog.getByRole('button',{name:'Express Interest (Simulated)',exact:true}).click();const write=await writeResponse;expect(write.status()).toBe(200);const first:Interest=await write.json();
  expect(first.message).toBe('Interest recorded for this demonstration. No real credit check or application was submitted.');
  await expect(page.locator('.toast')).toContainText(first.message);
  await dialog.getByRole('button',{name:'Close offer details',exact:true}).click();
  await page.getByRole('link',{name:'Back to Home',exact:true}).click();
  await page.getByRole('link',{name:persona.title,exact:true}).click();await expect(dialog).toContainText('Interest recorded (Simulated)');
  const repeatResponse=page.waitForResponse(r=>r.url().endsWith('/api/v1/promotions/'+selected.id+'/express-interest'));
  await dialog.getByRole('button',{name:'Express Interest (Simulated)',exact:true}).click();const repeat=await repeatResponse;expect(repeat.status()).toBe(200);expect(await repeat.json()).toEqual(first);
  await page.screenshot({path:testInfo.outputPath('offers-modal.png'),fullPage:true});
  expect(console.errors).toEqual([]);
 });
}

test('anonymous offers entry is guarded before a real UI login on mobile',async({page})=>{
 const console=captureConsole(page);await page.setViewportSize({width:390,height:844});await page.goto('/offers');await expect(page).toHaveURL(/\/login$/);
 await page.getByLabel('User ID',{exact:true}).fill('rosa');await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByRole('button',{name:'SIGN IN',exact:true}).click();await expect(page).toHaveURL(/\/home$/);
 await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Offers',exact:true}).click();await expect(page.locator('[data-offer-id]')).toHaveCount(3);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);expect(console.errors).toEqual([]);
});
