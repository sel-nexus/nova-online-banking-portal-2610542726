/** Capture representative real-server accessibility, computed style and HTML evidence. */
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
/** Record one meaningful state without replacing journey assertions. */
async function capture(page,name,output){
 const tree=await page.accessibility.snapshot();
 const style=await page.evaluate(()=>({font:getComputedStyle(document.body).fontFamily,background:getComputedStyle(document.body).backgroundColor,color:getComputedStyle(document.body).color,width:innerWidth,scrollWidth:document.documentElement.scrollWidth,headings:[...document.querySelectorAll('h1,h2')].map(e=>({text:e.textContent,font:getComputedStyle(e).fontSize})),controls:[...document.querySelectorAll('input,button')].slice(0,8).map(e=>({text:e.getAttribute('aria-label')||e.textContent,padding:getComputedStyle(e).padding}))}));
 output.push('## '+name+'\nTREE\n'+JSON.stringify(tree,null,2)+'\nSTYLE\n'+JSON.stringify(style,null,2)+'\nHTML\n'+await page.content());
 await page.screenshot({path:path.join('preflight',name+'.png'),fullPage:true});
}
/** Use the UI-issued session and actual routes for desktop/mobile captures. */
async function main(){
 await fs.mkdir('preflight',{recursive:true});
 const browser=await chromium.launch({args:['--no-sandbox']});const output=[];
 try{
  for(const viewport of [{width:1440,height:1000},{width:390,height:844}]){
   const page=await browser.newPage({viewport});const tag=viewport.width===390?'mobile':'desktop';
   await page.goto(process.env.E2E_BASE_URL||'http://localhost:4200/login');await page.getByRole('heading',{name:'Welcome back.'}).waitFor();await capture(page,tag+'-login',output);
   await page.getByRole('button',{name:'SIGN IN',exact:true}).click();await page.getByText('User ID is required',{exact:true}).waitFor();await capture(page,tag+'-validation-error',output);
   await page.getByRole('button',{name:'Security Guidance',exact:true}).click();await page.locator('dialog[open]').waitFor();await capture(page,tag+'-guidance-dialog',output);await page.getByRole('button',{name:'Close',exact:true}).click();
   await page.getByLabel('User ID',{exact:true}).fill('miguel');await page.getByLabel('Password',{exact:true}).fill('demo-only');
   const cdp=await page.context().newCDPSession(page);await cdp.send('Network.enable');await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:1500,downloadThroughput:-1,uploadThroughput:-1});
   await page.getByRole('button',{name:'SIGN IN',exact:true}).click();await page.getByRole('button',{name:'Signing in…',exact:true}).waitFor();await capture(page,tag+'-loading',output);await page.getByRole('heading',{name:/Magandang/}).waitFor();await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:0,downloadThroughput:-1,uploadThroughput:-1});await cdp.detach();await capture(page,tag+'-portfolio',output);
   await page.getByRole('link',{name:'View Transactions',exact:true}).click();await page.getByRole('heading',{name:'Payroll Account',exact:true}).waitFor();await page.getByText('No transactions found in this period. Reset filters or broaden your search.',{exact:true}).waitFor();await capture(page,tag+'-empty-ledger',output);
   await page.getByRole('link',{name:'Offers',exact:true}).click();await page.locator('.offer-card').first().waitFor();await capture(page,tag+'-offers',output);
   await page.getByRole('button',{name:'Chat Assistant',exact:true}).click();await capture(page,tag+'-assistant',output);
   await page.close();
  }
 }finally{await browser.close();await fs.writeFile('preflight/review.txt',output.join('\n\n'));}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
