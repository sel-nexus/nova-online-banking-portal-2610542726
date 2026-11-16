import { expect, Page, Request } from '@playwright/test';
/** Capture every error; excuse at most one resource console event per exact, observed HTTP failure. */
export function captureConsole(page: Page) {
  const events: {text: string; url?: string; status?: number; at: number}[] = [];
  const allowances: {url: string; method: string; status: number; expires: number;
    request?: Request; requestedAt?: number; respondedAt?: number}[] = [];
  page.on('pageerror', error => events.push({text: error.message, at: Date.now()}));
  page.on('request', request => {
    const at = Date.now();
    const allowance = allowances.find(item => !item.request && at <= item.expires &&
      item.url === request.url() && item.method === request.method());
    if (allowance) { allowance.request = request; allowance.requestedAt = at; }
  });
  page.on('response', response => {
    const allowance = allowances.find(item => item.request === response.request());
    if (allowance && response.status() === allowance.status) allowance.respondedAt = Date.now();
  });
  page.on('console', message => {
    if (message.type() !== 'error') return;
    const status = /^Failed to load resource: the server responded with a status of (\d+)\b/.exec(message.text());
    events.push({text: message.text(), at: Date.now(),
      url: status ? message.location().url : undefined, status: status ? Number(status[1]) : undefined});
  });
  return {
    /** Bind only the next matching browser request within five seconds; APIRequestContext needs no exemption. */
    allowHttpError(url: string, method: string, status: number) {
      allowances.push({url: new URL(url, page.url()).href, method, status, expires: Date.now() + 5000});
    },
    // Reconcile on read so response-before-console and console-before-response are both supported.
    // Keep raw events intact; unmatched errors (including all pageerrors) always remain visible.
    get errors(): string[] {
      const used = new Set<number>();
      return events.filter(event => {
        const index = allowances.findIndex((item, i) => !used.has(i) && item.respondedAt !== undefined &&
          item.requestedAt !== undefined && event.url === item.url && event.status === item.status &&
          event.at >= item.requestedAt && Math.abs(event.at - item.respondedAt) <= 2000);
        if (index < 0) return true;
        used.add(index); return false;
      }).map(event => event.text);
    }
  };
}
/** Authenticate by typing and clicking the UI; return real backend response for content assertions. */
export async function uiLogin(page:Page,username='miguel',remember=false){await page.goto('/login');await page.getByLabel('User ID',{exact:true}).fill(username);await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByLabel('Remember User ID',{exact:true}).setChecked(remember);const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login')&&r.request().method()==='POST');await page.getByRole('button',{name:'SIGN IN',exact:true}).click();const r=await response;expect(r.status()).toBe(200);const data=await r.json();await expect(page).toHaveURL(/\/home$/);await expect(page.locator('summary')).toContainText(data.user.fullName);return data;}
/** Sign out through the profile control and confirm backend revocation using the previously issued bearer. */
export async function uiLogout(page:Page,token:string){await page.locator('summary').click();const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/logout'));await page.getByRole('button',{name:'Log out',exact:true}).click();expect((await response).status()).toBe(200);await expect(page).toHaveURL(/\/login$/);const replay=await page.request.post('/api/v1/auth/logout',{headers:{Authorization:'Bearer '+token}});expect(replay.status()).toBe(401);expect((await replay.json()).message).toBe('Authentication required.');}
