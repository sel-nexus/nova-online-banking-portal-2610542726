import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { AuthService, authInterceptor } from '../auth/auth.service';
import { SupportComponent } from './support.component';
import { SupportContent, SupportService } from './support.service';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
/** Independent transport fixtures contain plain text rather than trusted HTML. */
const data:SupportContent={contacts:[{title:'Call Us',detail:'(+63 2) 8000 0000',hours:'Mon–Fri 08:00–17:00',description:'Sample hotline'},{title:'Secure Message',detail:'Online enquiry simulation',hours:'Estimated response 24h',description:'No external message is sent'}],branches:[{name:'Ayala Branch',address:'Tower One, Ayala Avenue, Makati City',description:'Full Service Branch',hours:'Mon–Fri 09:00–16:30'}],faqs:[{id:'login',question:'How do I sign in?',answer:'miguel / demo-only and rosa / demo-only'},{id:'credit',question:'How is available credit calculated?',answer:'PHP 50,000.00 − PHP 8,450.00 = PHP 41,550.00.'}],topics:['LOGIN','ACCOUNTS','TRANSACTIONS','CARDS','LOANS','OFFERS','GENERAL'],notice:'Demonstration only.'};
/** Test native interactions using real HttpClient and its test transport. */
describe('Support',()=>{
 let fixture:ComponentFixture<SupportComponent>;let http:HttpTestingController;let query:BehaviorSubject<ReturnType<typeof convertToParamMap>>;
 /** Bind live route state and an optional real AuthService session. */
 function setup(contact=false,signedIn=true,search=''){
  sessionStorage.clear();query=new BehaviorSubject(convertToParamMap({search}));
  TestBed.configureTestingModule({imports:[SupportComponent],providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),provideRouter([]),{provide:ActivatedRoute,useValue:{queryParamMap:query,fragment:of(null),snapshot:{data:{contact}}}}]});
  http=TestBed.inject(HttpTestingController);
  spyOn(TestBed.inject(Router),'navigate').and.callFake((commands,extras)=>{if(extras?.queryParams)query.next(convertToParamMap({search:query.value.get('search'),...extras.queryParams}));return Promise.resolve(true);});
  spyOn(TestBed.inject(Router),'navigateByUrl').and.returnValue(Promise.resolve(true));
  if(signedIn)TestBed.inject(AuthService).installSession({token:'signed-token',tokenType:'Bearer',expiresIn:86400,user:{id:'owner',username:'miguel',fullName:'Miguel',role:'ROLE_CUSTOMER',lastLogin:null}});
  fixture=TestBed.createComponent(SupportComponent);fixture.detectChanges();
 }
 /** Resolve the real client's protected read. */
 function loaded(){const read=http.expectOne('/api/v1/support/content');expect(read.request.method).toBe('GET');expect(read.request.headers.get('Authorization')).toBe('Bearer signed-token');read.flush(data);fixture.detectChanges();}
 /** Type or select through the DOM. */
 function input(id:string,value:string){const el=fixture.nativeElement.querySelector('#'+id) as HTMLInputElement;el.value=value;el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));fixture.detectChanges();}
 /** Submit native reactive form. */
 function send(){fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));fixture.detectChanges();}
 /** Enter the PRD example. */
 function valid(){input('inquiry-topic','CARDS');input('inquiry-subject','Card Limit Increase');input('inquiry-message','Inquiring about credit limit adjustments.');}
 afterEach(()=>{http.verify();fixture.destroy();TestBed.inject(AuthService).ngOnDestroy();sessionStorage.clear();});
 it('loads and renders protected directory and navigation',()=>{setup();expect(fixture.nativeElement.textContent).toContain('Loading support content');loaded();expect(fixture.nativeElement.textContent).toContain('(+63 2) 8000 0000');expect(fixture.nativeElement.textContent).toContain('Tower One, Ayala Avenue, Makati City');expect(fixture.nativeElement.querySelector('a[href="/contact"]')).toBeTruthy();});
 it('restores URL search and toggles accessible accordion answers',()=>{setup(false,true,'credit');loaded();expect(fixture.nativeElement.querySelectorAll('.faq-item').length).toBe(1);const button=fixture.nativeElement.querySelector('.faq-item button');button.click();fixture.detectChanges();expect(button.getAttribute('aria-expanded')).toBe('true');expect(fixture.nativeElement.querySelector('#faq-credit').hidden).toBeFalse();button.click();fixture.detectChanges();expect(fixture.nativeElement.querySelector('#faq-credit').hidden).toBeTrue();input('faq-search','MIGUEL');expect(fixture.nativeElement.querySelectorAll('.faq-item').length).toBe(1);input('faq-search','');expect(fixture.nativeElement.querySelectorAll('.faq-item').length).toBe(2);});
 it('searches answer text and handles hostile unmatched search without HTML',()=>{setup();loaded();input('faq-search','41,550');expect(fixture.nativeElement.querySelectorAll('.faq-item').length).toBe(1);input('faq-search','<script>');expect(fixture.nativeElement.textContent).toContain('No FAQs found. Broaden your search.');expect(fixture.nativeElement.querySelector('script')).toBeNull();});
 it('retries failed directory reads',()=>{setup();http.expectOne('/api/v1/support/content').flush({}, {status:503,statusText:'Unavailable'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('We could not load support content');fixture.nativeElement.querySelector('button').click();loaded();expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();});
 it('keeps anonymous contact public without loading directory and points to sign-in',()=>{setup(true,false);http.expectNone('/api/v1/support/content');expect(fixture.nativeElement.textContent).toContain('Contact Us');expect(fixture.nativeElement.textContent).toContain('Please sign in');expect(fixture.nativeElement.textContent).not.toContain('8000 0000');expect(fixture.nativeElement.querySelector('form')).toBeNull();expect(fixture.nativeElement.querySelector('a[href="/login"]')).toBeTruthy();fixture.componentInstance.send();http.expectNone('/api/v1/support/inquiries');});
 it('lists all topics and exposes each missing field inline',()=>{setup(true);loaded();expect(Array.from(fixture.nativeElement.querySelectorAll('option')).map(el=>(el as HTMLOptionElement).value)).toEqual(['',...data.topics]);send();for(const name of ['topic','subject','message']){expect(fixture.nativeElement.querySelector('#'+name+'-error').textContent).not.toBe('');expect(fixture.nativeElement.querySelector('#inquiry-'+name).getAttribute('aria-invalid')).toBe('true');}http.expectNone('/api/v1/support/inquiries');});
 for(const name of ['subject','message'])it('rejects whitespace and oversized '+name+' independently',()=>{setup(true);loaded();valid();input('inquiry-'+name,'   ');send();http.expectNone('/api/v1/support/inquiries');expect(fixture.nativeElement.querySelector('#'+name+'-error').textContent).toContain('required');input('inquiry-'+name,'x'.repeat(name==='subject'?151:5001));send();http.expectNone('/api/v1/support/inquiries');expect(fixture.nativeElement.querySelector('#'+name+'-error').textContent).toContain(name==='subject'?'150':'5000');});
 it('sends exact form body, blocks pending duplicates and confirms receipt',()=>{setup(true);loaded();valid();send();const write=http.expectOne('/api/v1/support/inquiries');expect(write.request.method).toBe('POST');expect(write.request.body).toEqual({topic:'CARDS',subject:'Card Limit Increase',message:'Inquiring about credit limit adjustments.'});expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();send();http.expectNone('/api/v1/support/inquiries');write.flush({referenceNumber:'NB-99214',status:'RECEIVED',estimatedResponse:'24h'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Reference #NB-99214 · RECEIVED · Estimated response 24h');expect(fixture.nativeElement.textContent).toContain('No third-party email or SMS was sent.');expect(fixture.nativeElement.querySelector('#inquiry-subject').value).toBe('');});
 for(const status of [400,401,503])it('handles '+status+' without a false confirmation',()=>{setup(true);loaded();valid();send();http.expectOne('/api/v1/support/inquiries').flush({errors:status===400?{subject:'Check subject.'}:{}},{status,statusText:'Failure'});fixture.detectChanges();expect(fixture.nativeElement.textContent).not.toContain('Message recorded (Simulated)');expect(fixture.nativeElement.textContent).toContain(status===401?'Please sign in':'We could not record your message');if(status===400){expect(fixture.nativeElement.querySelector('#subject-error').textContent).toContain('Check subject.');input('inquiry-subject','Fixed');send();http.expectOne('/api/v1/support/inquiries').flush({referenceNumber:'NB-99215',status:'RECEIVED',estimatedResponse:'24h'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('#NB-99215');}});
 it('preserves malicious text in the client and propagates errors',()=>{setup(true);loaded();let failed=false;TestBed.inject(SupportService).submit({topic:'GENERAL',subject:'SQL text',message:'<script>alert(1)</script>'}).subscribe({error:()=>failed=true});const request=http.expectOne('/api/v1/support/inquiries');expect(request.request.body.message).toBe('<script>alert(1)</script>');request.flush({}, {status:400,statusText:'Bad Request'});expect(failed).toBeTrue();});
 it('cancels pending reads on departure',()=>{setup();const read=http.expectOne('/api/v1/support/content');fixture.destroy();expect(read.cancelled).toBeTrue();});
});

/** Exercise the real route branches without wrapping Support itself in a duplicate shell. */
describe('Contact routing',()=>{
 let harness:RouterTestingHarness;let http:HttpTestingController;
 /** Install optional authentication before recognizing application routes. */
 async function setup(signedIn:boolean){
  sessionStorage.clear();
  TestBed.configureTestingModule({providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),provideRouter(routes)]});
  http=TestBed.inject(HttpTestingController);
  if(signedIn)TestBed.inject(AuthService).installSession({token:'signed-token',tokenType:'Bearer',expiresIn:86400,user:{id:'owner',username:'miguel',fullName:'Miguel',role:'ROLE_CUSTOMER',lastLogin:null}});
  harness=await RouterTestingHarness.create();
 }
 /** Resolve the signed-in content read. */
 function loaded(){const read=http.expectOne('/api/v1/support/content');expect(read.request.headers.get('Authorization')).toBe('Bearer signed-token');read.flush(data);harness.detectChanges();}
 afterEach(()=>{http.verify();TestBed.inject(AuthService).ngOnDestroy();sessionStorage.clear();});
 it('renders signed-in direct Contact inside one shell with both assistant triggers',async()=>{
  await setup(true);await harness.navigateByUrl('/contact');loaded();
  const root=harness.fixture.nativeElement as HTMLElement;
  expect(root.querySelectorAll('app-shell').length).toBe(1);expect(root.querySelectorAll('app-chat').length).toBe(1);
  expect(root.querySelector('.app-header')?.textContent).toContain('Miguel');
  expect(root.querySelector('.app-header button[aria-controls="nova-chat"]')?.textContent).toContain('Chat Assistant');
  expect(root.querySelector('.chat-launcher')?.textContent).toContain('Open Nova Bank Assistant');
  expect(root.querySelector('nova-support h1')?.textContent).toContain('Contact Us');expect(root.querySelector('#inquiry-subject')).toBeTruthy();
 });
 it('preserves the same shell and open assistant between Support and Contact',async()=>{
  await setup(true);await harness.navigateByUrl('/support');loaded();
  const root=harness.fixture.nativeElement as HTMLElement;const shell=root.querySelector('app-shell');const assistant=root.querySelector('app-chat');
  (root.querySelector('.app-header button[aria-controls="nova-chat"]') as HTMLButtonElement).click();harness.detectChanges();
  expect(root.querySelector('#nova-chat')).toBeTruthy();
  await harness.navigateByUrl('/contact');loaded();
  expect(root.querySelector('app-shell')).toBe(shell);expect(root.querySelector('app-chat')).toBe(assistant);expect(root.querySelector('#nova-chat')).toBeTruthy();
  await harness.navigateByUrl('/support');loaded();expect(root.querySelector('app-shell')).toBe(shell);
 });
 it('keeps branch-link navigation on Support with content, shell and FAQ query state intact',async()=>{
  await setup(true);await harness.navigateByUrl('/support?search=credit&faq=credit');
  http.expectOne('/api/v1/support/content').flush({...data,contacts:[...data.contacts,{title:'Find a Branch / ATM',detail:'Metro Manila',hours:'Sample hours',description:'Directory'}]});harness.detectChanges();
  const root=harness.fixture.nativeElement as HTMLElement;const shell=root.querySelector('app-shell');const support=root.querySelector('nova-support');
  const link=Array.from(root.querySelectorAll('a')).find(a=>a.textContent==='View branch and ATM directory')!;
  expect(link.getAttribute('href')).toBe('/support?search=credit&faq=credit#branches');
  link.click();await harness.fixture.whenStable();harness.detectChanges();
  expect(TestBed.inject(Router).url).toBe('/support?search=credit&faq=credit#branches');
  expect(root.querySelector('app-shell')).toBe(shell);expect(root.querySelector('nova-support')).toBe(support);
  expect(root.querySelector('#branches h3')?.textContent).toBe('Ayala Branch');
  expect(root.querySelector<HTMLInputElement>('#faq-search')?.value).toBe('credit');
  expect(root.querySelector('.faq-item button')?.getAttribute('aria-expanded')).toBe('true');
  http.expectNone('/api/v1/support/content');
 });
 it('restores a direct branch fragment and scrolls only after the async directory renders',async()=>{
  const scroll=spyOn(HTMLElement.prototype,'scrollIntoView');
  await setup(true);await harness.navigateByUrl('/support?search=credit&faq=credit#branches');
  expect(scroll).not.toHaveBeenCalled();loaded();await harness.fixture.whenStable();harness.detectChanges();
  const root=harness.fixture.nativeElement as HTMLElement;
  expect(root.querySelector('#branches h3')?.textContent).toBe('Ayala Branch');
  expect(TestBed.inject(Router).url).toBe('/support?search=credit&faq=credit#branches');
  expect(scroll).toHaveBeenCalled();
  expect(scroll.calls.mostRecent().object as unknown).toBe(root.querySelector('#branches'));
 });
 it('keeps anonymous Contact public without shell, assistant or protected content',async()=>{
  await setup(false);await harness.navigateByUrl('/contact');harness.detectChanges();
  const root=harness.fixture.nativeElement as HTMLElement;
  expect(TestBed.inject(Router).url).toBe('/contact');expect(root.textContent).toContain('Contact Us');expect(root.querySelector('a[href="/login"]')).toBeTruthy();
  expect(root.querySelector('app-shell')).toBeNull();expect(root.querySelector('.app-header')).toBeNull();expect(root.querySelector('app-chat')).toBeNull();expect(root.querySelector('form')).toBeNull();expect(root.textContent).not.toContain('8000 0000');
  http.expectNone('/api/v1/support/content');http.expectNone('/api/v1/support/inquiries');
 });
});
