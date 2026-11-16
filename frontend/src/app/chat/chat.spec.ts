import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { AuthService, authInterceptor } from '../auth/auth.service';
import { ShellComponent } from '../core/shell.component';
import { ChatComponent } from './chat.component';
import { ChatReply, ChatService } from './chat.service';

/** Render a routed child without unrelated feature HTTP requests. */
@Component({selector:'chat-test-child',standalone:true,template:'<h1>Routed child</h1>'})
class ChildComponent {}
/** Mount a real router for shell lifecycle assertions. */
@Component({selector:'chat-test-host',standalone:true,imports:[RouterOutlet],template:'<router-outlet/>'})
class HostComponent {}
/** Define independent plain-text transport data. */
const balance:ChatReply={message:'Your available deposit balance is PHP 142,860.25 across 2 deposit accounts.',actionLabel:'View Account Summary',actionRoute:'/accounts'};
/** Exercise native controls and the actual typed HTTP client. */
/** Dispose AuthService intervals inside the same fakeAsync zone that created them. */
function timed(run:()=>void){return fakeAsync(()=>{try{run();}finally{TestBed.inject(AuthService).ngOnDestroy();}});}
describe('Chat',()=>{
 let fixture:ComponentFixture<ChatComponent>;let http:HttpTestingController;let auth:AuthService;
 /** Configure the real client, interceptor and session with only HTTP transport replaced. */
 function setup(signedIn=true){
  sessionStorage.clear();localStorage.clear();
  TestBed.configureTestingModule({imports:[ChatComponent],providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),provideRouter([{path:'accounts',component:ChildComponent},{path:'support',component:ChildComponent},{path:'offers',component:ChildComponent},{path:'login',component:ChildComponent}])]});
  http=TestBed.inject(HttpTestingController);auth=TestBed.inject(AuthService);if(signedIn)install();
  fixture=TestBed.createComponent(ChatComponent);fixture.detectChanges();
 }
 /** Install a server-shaped session, changing token on persona replacement. */
 function install(username='miguel'){auth.installSession({token:username+'-token',tokenType:'Bearer',expiresIn:86400,user:{id:username,username,fullName:username,role:'ROLE_CUSTOMER',lastLogin:null}});}
 /** Find and click a native button by its visible or accessible name. */
 function click(name:string){const button=Array.from(fixture.nativeElement.querySelectorAll('button')).find(el=>(el as HTMLButtonElement).getAttribute('aria-label')===name||(el as HTMLButtonElement).textContent?.trim()===name) as HTMLButtonElement;expect(button).withContext(name).toBeTruthy();button.click();fixture.detectChanges();}
 /** Type through a native input event rather than setting component form state. */
 function input(value:string){const el=fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;el.value=value;el.dispatchEvent(new Event('input',{bubbles:true}));fixture.detectChanges();}
 /** Resolve and validate the actual request. */
 function reply(prompt:string,data:ChatReply=balance){const request=http.expectOne('/api/v1/chat/query');expect(request.request.method).toBe('POST');expect(request.request.body).toEqual({message:prompt});expect(request.request.headers.get('Authorization')).toBe('Bearer miguel-token');request.flush(data);fixture.detectChanges();}
 afterEach(()=>{http.verify();fixture?.destroy();auth?.ngOnDestroy();sessionStorage.clear();localStorage.clear();});
 it('is unavailable anonymously and never calls the API',()=>{setup(false);expect(fixture.nativeElement.querySelector('button')).toBeNull();fixture.componentInstance.show();fixture.componentInstance.topic('Account Balance');http.expectNone('/api/v1/chat/query');});
 it('opens with welcome privacy online chips PII reminder and non-modal polite stream',timed(()=>{setup();click('Open Nova Bank Assistant');tick();expect(fixture.nativeElement.textContent).toContain('Conversations are not retained');expect(fixture.nativeElement.textContent).toContain('Online');expect(fixture.nativeElement.textContent).toContain('Do not share PII');expect(fixture.nativeElement.textContent).toContain('passwords, PINs, full account or card numbers');expect(fixture.nativeElement.querySelectorAll('.chat-topics button').length).toBe(4);expect(fixture.nativeElement.querySelector('[aria-modal]')).toBeNull();expect(fixture.nativeElement.querySelector('.chat-stream').getAttribute('aria-live')).toBe('polite');expect(document.activeElement).toBe(fixture.nativeElement.querySelector('textarea'));}));
 for(const topic of ['Account Balance','Card Help','Offers & Promos','Speak to Agent'])it('sends suggested topic '+topic+' via the authenticated typed client',()=>{setup();click('Open Nova Bank Assistant');click(topic);expect(fixture.nativeElement.textContent).toContain('Getting assistant reply');expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();reply(topic);expect(fixture.nativeElement.textContent).toContain(balance.message);expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/accounts');});
 it('sends typed text, clears composer and blocks pending duplicates',()=>{setup();click('Open Nova Bank Assistant');input('  Account Balance  ');click('Send');expect(fixture.nativeElement.querySelector('textarea').value).toBe('');click('Send');fixture.componentInstance.topic('Card Help');reply('  Account Balance  ');expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(2);});
 for(const value of ['', ' \t\n', 'x'.repeat(501)])it('rejects invalid composer length '+value.length+' without querying',()=>{setup();click('Open Nova Bank Assistant');input(value);click('Send');http.expectNone('/api/v1/chat/query');expect(fixture.nativeElement.querySelector('#chat-validation').hidden).toBeFalse();expect(fixture.nativeElement.querySelector('textarea').getAttribute('aria-invalid')).toBe('true');});
 for(const value of ['x','x'.repeat(500)])it('accepts inclusive composer length '+value.length,()=>{setup();click('Open Nova Bank Assistant');input(value);click('Send');reply(value,{message:'Choose a suggested topic.',actionLabel:null,actionRoute:null});expect(fixture.nativeElement.textContent).toContain('Choose a suggested topic.');});
 for(const status of [400,503])it('shows safe '+status+' error and retries without duplicate user text',()=>{setup();click('Open Nova Bank Assistant');input('Account Balance');click('Send');http.expectOne('/api/v1/chat/query').flush({message:'private database parser text'}, {status,statusText:'Failure'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('We could not load an assistant reply');expect(fixture.nativeElement.textContent).not.toContain('private database');click('Retry reply');reply('Account Balance');expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(2);expect(fixture.nativeElement.querySelector('[role=alert].field-error:not([hidden])')).toBeNull();});
 it('clears conversation and cancels work on unauthorized session expiration',()=>{setup();click('Open Nova Bank Assistant');click('Account Balance');http.expectOne('/api/v1/chat/query').flush({}, {status:401,statusText:'Unauthorized'});fixture.detectChanges();expect(auth.session()).toBeNull();expect(fixture.nativeElement.textContent.trim()).toBe('');install('rosa');fixture.detectChanges();click('Open Nova Bank Assistant');expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(0);});
 it('minimizes without losing messages and restores focus',timed(()=>{setup();click('Open Nova Bank Assistant');tick();click('Account Balance');reply('Account Balance');tick();click('Minimize chat');tick();expect(fixture.nativeElement.querySelector('.chat-panel')).toBeNull();expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.chat-launcher'));click('Open Nova Bank Assistant');tick();expect(fixture.nativeElement.textContent).toContain(balance.message);}));
 it('preserves a pending reply while minimized',()=>{setup();click('Open Nova Bank Assistant');click('Account Balance');click('Minimize chat');reply('Account Balance');click('Open Nova Bank Assistant');expect(fixture.nativeElement.textContent).toContain(balance.message);});
 it('closes and cancels a pending request then starts clean',timed(()=>{setup();click('Open Nova Bank Assistant');tick();click('Account Balance');const request=http.expectOne('/api/v1/chat/query');click('Close chat');tick();expect(request.cancelled).toBeTrue();expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.chat-launcher'));click('Open Nova Bank Assistant');tick();expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(0);}));
 it('Escape closes only the flyout and clears transient history',timed(()=>{setup();click('Open Nova Bank Assistant');tick();click('Account Balance');reply('Account Balance');tick();fixture.nativeElement.querySelector('textarea').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));fixture.detectChanges();tick();expect(fixture.nativeElement.querySelector('.chat-panel')).toBeNull();click('Open Nova Bank Assistant');tick();expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(0);}));
 for(const route of ['/accounts','/support','/offers'] as const)it('navigates a real action link to '+route,timed(()=>{setup();click('Open Nova Bank Assistant');tick();click('Account Balance');reply('Account Balance',{message:'Reply',actionLabel:'Go to page',actionRoute:route});tick();fixture.nativeElement.querySelector('a').click();tick();expect(TestBed.inject(Router).url).toBe(route);expect(fixture.nativeElement.querySelector('.chat-panel')).toBeTruthy();}));
 it('handoff uses a Close Chat button, not a bogus route',()=>{setup();click('Open Nova Bank Assistant');click('Speak to Agent');reply('Speak to Agent',{message:'Handoff is simulated. No live agent was contacted and no support ticket opened.',actionLabel:'Close Chat',actionRoute:'close'});expect(fixture.nativeElement.querySelector('a')).toBeNull();click('Close Chat');expect(fixture.nativeElement.querySelector('.chat-panel')).toBeNull();});
 it('renders hostile prompts and replies as text and rejects unsafe action URLs',()=>{setup();click('Open Nova Bank Assistant');const xss='<img src=x onerror=alert(1)><script>alert(1)</script>';input(xss);click('Send');reply(xss,{message:xss,actionLabel:'Unsafe',actionRoute:'javascript:alert(1)' as ChatReply['actionRoute']});expect(fixture.nativeElement.textContent).toContain(xss);expect(fixture.nativeElement.querySelector('script,img,a')).toBeNull();});
 it('clears state on persona switch and never writes conversation storage',()=>{setup();click('Open Nova Bank Assistant');input('private-chat-text');click('Send');reply('private-chat-text');expect(JSON.stringify(localStorage)).not.toContain('private-chat-text');expect(JSON.stringify(sessionStorage)).not.toContain('private-chat-text');install('rosa');fixture.detectChanges();click('Open Nova Bank Assistant');expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(0);expect(fixture.nativeElement.querySelector('textarea').value).toBe('');});
 it('cancels requests on component destruction and does not restore history',()=>{setup();click('Open Nova Bank Assistant');click('Card Help');const request=http.expectOne('/api/v1/chat/query');fixture.destroy();expect(request.cancelled).toBeTrue();fixture=TestBed.createComponent(ChatComponent);fixture.detectChanges();click('Open Nova Bank Assistant');expect(fixture.nativeElement.querySelectorAll('.chat-stream li').length).toBe(0);});
 it('typed client propagates errors instead of substituting replies',()=>{setup();let failed=false;TestBed.inject(ChatService).query('test').subscribe({error:()=>failed=true});http.expectOne('/api/v1/chat/query').flush({}, {status:503,statusText:'Unavailable'});expect(failed).toBeTrue();});
});

/** Verify persistent integration with real guarded-style child routing and logout lifecycle. */
describe('Chat authenticated shell',()=>{
 let host:ComponentFixture<HostComponent>;let http:HttpTestingController;let auth:AuthService;
 beforeEach(()=>{
  sessionStorage.clear();
  TestBed.configureTestingModule({imports:[HostComponent,ShellComponent],providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),provideRouter([{path:'',component:ShellComponent,children:[{path:'home',component:ChildComponent},{path:'accounts',component:ChildComponent},{path:'support',component:ChildComponent},{path:'products',component:ChildComponent},{path:'offers',component:ChildComponent}]},{path:'login',component:ChildComponent}])]});
  http=TestBed.inject(HttpTestingController);auth=TestBed.inject(AuthService);auth.installSession({token:'token',tokenType:'Bearer',expiresIn:86400,user:{id:'miguel',username:'miguel',fullName:'Miguel',role:'ROLE_CUSTOMER',lastLogin:null}});host=TestBed.createComponent(HostComponent);host.detectChanges();
 });
 afterEach(()=>{http.verify();host.destroy();auth.ngOnDestroy();sessionStorage.clear();});
 it('opens from header, preserves history across child routes, restores header focus and clears on logout',fakeAsync(()=>{
  const router=TestBed.inject(Router);void router.navigateByUrl('/home');tick();host.detectChanges();
  const header=Array.from(host.nativeElement.querySelectorAll('button')).find(el=>(el as HTMLButtonElement).textContent==='Chat Assistant') as HTMLButtonElement;
  header.click();host.detectChanges();tick();
  let chat:DebugElement=host.debugElement.query(By.directive(ChatComponent));const original=chat.componentInstance;
  (chat.nativeElement.querySelector('.chat-topics button') as HTMLButtonElement).click();host.detectChanges();http.expectOne('/api/v1/chat/query').flush(balance);host.detectChanges();tick();
  for(const path of ['/accounts','/offers','/products','/support']){
   const link=host.nativeElement.querySelector('nav a[href="'+path+'"]') as HTMLAnchorElement;link.click();tick();host.detectChanges();expect(router.url).toBe(path);chat=host.debugElement.query(By.directive(ChatComponent));expect(chat.componentInstance).toBe(original);expect(chat.nativeElement.textContent).toContain(balance.message);
  }
  chat.nativeElement.querySelector('[aria-label="Minimize chat"]').click();host.detectChanges();tick();expect(document.activeElement).toBe(header);
  header.click();host.detectChanges();tick();expect(chat.nativeElement.textContent).toContain(balance.message);
  auth.logout();http.expectOne('/api/v1/auth/logout').flush({message:'Logged out successfully.'});tick();host.detectChanges();expect(router.url).toBe('/login');expect(host.debugElement.query(By.directive(ChatComponent))).toBeNull();
  auth.installSession({token:'rosa-token',tokenType:'Bearer',expiresIn:86400,user:{id:'rosa',username:'rosa',fullName:'Rosa',role:'ROLE_CUSTOMER',lastLogin:null}});void router.navigateByUrl('/home');tick();host.detectChanges();chat=host.debugElement.query(By.directive(ChatComponent));chat.nativeElement.querySelector('.chat-launcher').click();host.detectChanges();tick();expect(chat.nativeElement.querySelectorAll('.chat-stream li').length).toBe(0);
 }));
});
