import { TestBed, fakeAsync, tick, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService, Session, authInterceptor, sessionGuard } from './auth.service';
import { LoginComponent } from './login.component';
import { ShellComponent } from '../core/shell.component';
import { ApiService } from '../core/api.service';
/** Verify auth form, real transport wiring, tab storage, guards and idle lifecycle with controlled HTTP responses. */
describe('Authentication slice',()=>{
 let auth:AuthService; let http:HttpTestingController; let router:Router;
 const session:Session={token:'server-issued-test-response',tokenType:'Bearer',expiresIn:86400,user:{id:'user-id',username:'miguel',fullName:'Miguel De La Cruz',role:'ROLE_CUSTOMER',lastLogin:'2026-10-14T08:10:00Z'}};
 /** Start each test with isolated browser storage and the same functional interceptor registration as production. */
 beforeEach(()=>{localStorage.clear();sessionStorage.clear();TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});auth=TestBed.inject(AuthService);http=TestBed.inject(HttpTestingController);router=TestBed.inject(Router);spyOn(router,'navigate').and.resolveTo(true);});
 /** Fail outstanding HTTP calls and release interval/listener ownership. */
 afterEach(()=>{http.verify();auth.ngOnDestroy();document.body.style.overflow='';localStorage.clear();sessionStorage.clear();});
 /** Submit through the DOM rather than calling the handler directly. */
 function submit(f:ComponentFixture<LoginComponent>,username:string,password:string){for(const [id,value] of [['username',username],['password',password]]){const input=f.nativeElement.querySelector('#'+id) as HTMLInputElement;input.value=value;input.dispatchEvent(new Event('input'));}f.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));f.detectChanges();}
 it('blocks blank submit with associated inline errors',()=>{const f=TestBed.createComponent(LoginComponent);f.detectChanges();submit(f,'','');expect(f.nativeElement.textContent).toContain('User ID is required');expect(f.nativeElement.textContent).toContain('Password is required.');expect(f.nativeElement.querySelector('#username').getAttribute('aria-invalid')).toBe('true');http.expectNone('/api/v1/auth/login');});
 it('submits happy credentials and remembers username only',()=>{const f=TestBed.createComponent(LoginComponent);f.detectChanges();f.componentInstance.form.controls.remember.setValue(true);submit(f,'miguel','demo-only');expect(f.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();const r=http.expectOne('/api/v1/auth/login');expect(r.request.body).toEqual({username:'miguel',password:'demo-only'});expect(r.request.headers.has('Authorization')).toBeFalse();r.flush(session);f.detectChanges();expect(localStorage.length).toBe(1);expect(localStorage.getItem('nova.username')).toBe('miguel');expect(JSON.stringify(localStorage)).not.toContain('demo-only');expect(f.componentInstance.form.controls.password.value).toBe('');expect(router.navigate).toHaveBeenCalledWith(['/home']);});
 for(const status of [401,423,500])it('renders login rejection '+status+' without redirect',()=>{const f=TestBed.createComponent(LoginComponent);f.detectChanges();submit(f,'miguel','wrong');http.expectOne('/api/v1/auth/login').flush({}, {status,statusText:'Rejected'});f.detectChanges();expect(f.componentInstance.error()).toContain(status===401?'Invalid User ID':status===423?'Account locked':'Unable to sign in');expect(router.navigate).not.toHaveBeenCalled();expect(auth.session()).toBeNull();expect(f.componentInstance.busy()).toBeFalse();});
 it('clears the old username preference when remember is unchecked',()=>{localStorage.setItem('nova.username','rosa');const f=TestBed.createComponent(LoginComponent);f.detectChanges();expect(f.componentInstance.form.controls.username.value).toBe('rosa');f.componentInstance.form.controls.remember.setValue(false);submit(f,'miguel','demo-only');http.expectOne('/api/v1/auth/login').flush(session);expect(localStorage.getItem('nova.username')).toBeNull();});
 it('toggles password visibility and translates only login controls',()=>{const f=TestBed.createComponent(LoginComponent);f.detectChanges();f.nativeElement.querySelector('.password-row button').click();f.detectChanges();expect(f.nativeElement.querySelector('#password').type).toBe('text');const select=f.nativeElement.querySelector('#language');select.value='fil';select.dispatchEvent(new Event('change'));f.detectChanges();expect(f.nativeElement.textContent).toContain('MAG-SIGN IN');expect(f.nativeElement.textContent).toContain('Fraud Monitoring Simulation');});
 it('opens a native guidance dialog and restores focus on Escape cancellation',()=>{const f=TestBed.createComponent(LoginComponent);f.detectChanges();document.body.appendChild(f.nativeElement);const trigger=f.nativeElement.querySelector('.recovery-links button') as HTMLButtonElement;trigger.focus();trigger.click();f.detectChanges();const d=f.nativeElement.querySelector('dialog') as HTMLDialogElement;expect(d.open).toBeTrue();expect(d.contains(document.activeElement)).toBeTrue();d.dispatchEvent(new Event('cancel',{cancelable:true}));expect(d.open).toBeFalse();expect(document.activeElement).toBe(trigger);f.nativeElement.remove();});
 it('guards anonymous, active and expired sessions',()=>{expect(TestBed.runInInjectionContext(()=>sessionGuard({} as never,{} as never))).not.toBeTrue();auth.installSession(session);expect(TestBed.runInInjectionContext(()=>sessionGuard({} as never,{} as never))).toBeTrue();auth.session.set({...session,expiresAt:Date.now()-1});expect(auth.authenticated()).toBeFalse();expect(sessionStorage.getItem('nova.session')).toBeNull();});
 it('attaches bearer to configured API only and expires on protected 401',()=>{auth.installSession(session);const api=TestBed.inject(ApiService);api.get('/protected').subscribe({error:()=>{}});const r=http.expectOne('/api/v1/protected');expect(r.request.headers.get('Authorization')).toBe('Bearer '+session.token);r.flush({}, {status:401,statusText:'Unauthorized'});expect(auth.session()).toBeNull();TestBed.inject(HttpClient).get('https://unrelated.example/data').subscribe();const outside=http.expectOne('https://unrelated.example/data');expect(outside.request.headers.has('Authorization')).toBeFalse();outside.flush({});});
 it('revokes logout with bearer and preserves username preference',()=>{auth.installSession(session);localStorage.setItem('nova.username','miguel');auth.logout();const r=http.expectOne('/api/v1/auth/logout');expect(r.request.headers.get('Authorization')).toBe('Bearer '+session.token);r.flush({message:'Logged out successfully.'});expect(auth.session()).toBeNull();expect(sessionStorage.getItem('nova.session')).toBeNull();expect(localStorage.getItem('nova.username')).toBe('miguel');});
 it('clears locally and reports unconfirmed server revocation on network failure',()=>{auth.installSession(session);auth.logout();http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));expect(auth.session()).toBeNull();expect(TestBed.inject(NotificationService).message()).toContain('could not be confirmed');});
 it('warns at 120s, counts down 30s and revokes unanswered session',fakeAsync(()=>{auth.ngOnDestroy();TestBed.resetTestingModule();TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});auth=TestBed.inject(AuthService);http=TestBed.inject(HttpTestingController);spyOn(TestBed.inject(Router),'navigate').and.resolveTo(true);auth.installSession(session);tick(119000);expect(auth.warning()).toBeFalse();tick(1000);expect(auth.warning()).toBeTrue();expect(auth.countdown()).toBe(30);tick(29000);expect(auth.countdown()).toBe(1);tick(1000);http.expectOne('/api/v1/auth/logout').flush({message:'Logged out successfully.'});expect(auth.session()).toBeNull();auth.ngOnDestroy();tick(8000);expect(TestBed.inject(NotificationService).message()).toBe('');}));
 it('activity resets idle before warning but does not dismiss an open warning',fakeAsync(()=>{auth.ngOnDestroy();TestBed.resetTestingModule();TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});auth=TestBed.inject(AuthService);http=TestBed.inject(HttpTestingController);spyOn(TestBed.inject(Router),'navigate').and.resolveTo(true);auth.installSession(session);tick(119000);document.dispatchEvent(new MouseEvent('mousemove'));tick(119000);expect(auth.warning()).toBeFalse();tick(1000);expect(auth.warning()).toBeTrue();document.dispatchEvent(new KeyboardEvent('keydown'));expect(auth.warning()).toBeTrue();auth.keepAlive();tick(119000);expect(auth.warning()).toBeFalse();tick(1000);expect(auth.warning()).toBeTrue();auth.ngOnDestroy();}));
 it('keepalive and Escape close native idle warning without logout',()=>{auth.installSession(session);const f=TestBed.createComponent(ShellComponent);f.detectChanges();auth.warnNow();f.detectChanges();const dialog=f.nativeElement.querySelector('dialog') as HTMLDialogElement;expect(dialog.open).toBeTrue();dialog.dispatchEvent(new Event('cancel',{cancelable:true}));f.detectChanges();expect(dialog.open).toBeFalse();expect(auth.warning()).toBeFalse();http.expectNone('/api/v1/auth/logout');});
 /** Reload through a fresh injector so the actual constructor consumes persisted state. */
 function reloadStored(value:string){
  auth.ngOnDestroy();TestBed.resetTestingModule();sessionStorage.setItem('nova.session',value);
  localStorage.setItem('nova.username','miguel');
  TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()]});
  router=TestBed.inject(Router);spyOn(router,'navigate').and.resolveTo(true);
  auth=TestBed.inject(AuthService);http=TestBed.inject(HttpTestingController);
 }
 it('reload restores the exact live tab session without extending expiry',()=>{
  const stored={...session,expiresAt:Date.now()+60000};const value=JSON.stringify(stored);reloadStored(value);
  expect(auth.session()).toEqual(stored);expect(auth.authenticated()).toBeTrue();
  expect(sessionStorage.getItem('nova.session')).toBe(value);expect(router.navigate).not.toHaveBeenCalled();
 });
 for(const kind of ['malformed','null','empty object','array','scalar','missing token','empty token','missing user','null user','missing expiry','zero expiry','expired','expiry at now']){
  it('reload discards '+kind+' storage and remains anonymous',()=>{
   const now=Date.now();spyOn(Date,'now').and.returnValue(now);
   const stored:Record<string,unknown>={...session,expiresAt:now+60000};
   let value:string;
   switch(kind){
    case 'malformed':value='{';break;
    case 'null':value='null';break;
    case 'empty object':value='{}';break;
    case 'array':value='[]';break;
    case 'scalar':value='123';break;
    default:
     if(kind==='missing token')delete stored['token'];
     if(kind==='empty token')stored['token']='';
     if(kind==='missing user')delete stored['user'];
     if(kind==='null user')stored['user']=null;
     if(kind==='missing expiry')delete stored['expiresAt'];
     if(kind==='zero expiry')stored['expiresAt']=0;
     if(kind==='expired')stored['expiresAt']=now-1;
     if(kind==='expiry at now')stored['expiresAt']=now;
     value=JSON.stringify(stored);
   }
   reloadStored(value);
   expect(auth.session()).toBeNull();expect(auth.authenticated()).toBeFalse();
   expect(sessionStorage.getItem('nova.session')).toBeNull();expect(localStorage.getItem('nova.username')).toBe('miguel');
   const guard=TestBed.runInInjectionContext(()=>sessionGuard({} as never,{} as never));
   expect(router.serializeUrl(guard as import('@angular/router').UrlTree)).toBe('/login');
   http.expectNone('/api/v1/auth/logout');expect(router.navigate).not.toHaveBeenCalled();
  });
 }

});
import { HttpClient } from '@angular/common/http';
import { NotificationService } from '../core/notification.service';
