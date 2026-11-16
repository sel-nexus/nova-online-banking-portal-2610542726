import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { OffersComponent } from './offers.component';
import { Offer, OfferService, Interest } from './offer.service';
import { NotificationService } from '../core/notification.service';

/** Supply independent typed catalogue and confirmation fixtures. */
const offer: Offer = {id:'d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a20',offerKey:'payday-ipon-boost',title:'Payday Ipon Boost',category:'SAVINGS',benefit:'Demonstration bonus',terms:'Extra goal interest upon monthly target achievement.',eligibility:'Active Peso Savings accounts',validUntil:'2026-12-31',personalization:'Personalized for you',interested:false};
const card: Offer = {...offer,id:'card',title:'Cashback Week',category:'CARDS'};
const notice = 'Interest recorded for this demonstration. No real credit check or application was submitted.';
const interest: Interest = {id:'registration',promotionId:offer.id,registeredAt:'2026-10-14T00:00:00Z',message:notice};

/** Exercise typed transport and real native dialog/template user interactions. */
describe('Offers', () => {
 let http: HttpTestingController;
 let fixture: ComponentFixture<OffersComponent>;
 let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
 let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
 let navigation: jasmine.Spy;
 /** Configure route state and mirror actual navigation into route observables. */
 function setup(products=false, initialId='', category='') {
  params=new BehaviorSubject(convertToParamMap(initialId?{offerId:initialId}:{}));query=new BehaviorSubject(convertToParamMap(category?{category}:{}));
  TestBed.configureTestingModule({imports:[OffersComponent],providers:[provideHttpClient(),provideHttpClientTesting(),provideRouter([]),{provide:ActivatedRoute,useValue:{paramMap:params,queryParamMap:query,snapshot:{data:{products}}}}]});
  http=TestBed.inject(HttpTestingController);
  navigation=spyOn(TestBed.inject(Router),'navigate').and.callFake((commands,extras)=>{
   query.next(convertToParamMap(extras?.queryParams||{}));params.next(convertToParamMap(commands.length>1?{offerId:String(commands[1])}:{}));return Promise.resolve(true);
  });
  spyOn(TestBed.inject(Router),'navigateByUrl').and.returnValue(Promise.resolve(true));
  fixture=TestBed.createComponent(OffersComponent);document.body.appendChild(fixture.nativeElement);fixture.detectChanges();
 }
 /** Finish catalogue loading with typed campaigns. */
 function catalogue(items: Offer[]=[offer,card]) { http.expectOne('/api/v1/promotions').flush(items);fixture.detectChanges(); }
 /** Click an actual rendered semantic control by its text. */
 function click(text: string, selector='button') { const element=Array.from(fixture.nativeElement.querySelectorAll(selector)).find(el=>(el as HTMLElement).textContent?.trim()===text) as HTMLElement;expect(element).toBeTruthy();element.click();fixture.detectChanges(); }
 /** Open detail from an actual card link while applying the route transition. */
 function open() { const link: HTMLAnchorElement=fixture.nativeElement.querySelector('[data-offer-id] a');link.focus();link.click();params.next(convertToParamMap({offerId:offer.id}));fixture.detectChanges();http.expectOne('/api/v1/promotions/'+offer.id).flush(offer);fixture.detectChanges(); }
 /** Ensure requests and body locks do not survive teardown. */
 afterEach(()=>{http.verify();fixture.destroy();fixture.nativeElement.remove();expect(document.body.style.overflow).not.toBe('hidden');});
 it('loads typed current offers and full dates with exact featured copy',()=>{
  setup();expect(fixture.nativeElement.textContent).toContain('Loading offers');const read=http.expectOne('/api/v1/promotions');expect(read.request.method).toBe('GET');read.flush([offer,card]);fixture.detectChanges();
  expect(fixture.nativeElement.querySelectorAll('[data-offer-id]').length).toBe(2);expect(fixture.nativeElement.textContent).toContain('Save toward your financial goals and receive a demonstration bonus! Valid through 31 Dec 2026.');expect(fixture.nativeElement.textContent).toContain('Personalized for you');expect(fixture.nativeElement.textContent).toContain('31 Dec 2026');
 });
 it('products route calls its own typed API and filters categories including empty Loans',()=>{
  setup(true);const read=http.expectOne('/api/v1/products');expect(read.request.method).toBe('GET');read.flush([{title:'Ipon Goal Saver',category:'SAVINGS',benefit:'Build savings automatically on payday'},{title:'Rewards Credit Card',category:'CARDS',benefit:'Earn points on dining & groceries'}]);fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Retail products');expect(fixture.nativeElement.querySelectorAll('.offer-card').length).toBe(2);
  click('Loans','a');query.next(convertToParamMap({category:'LOANS'}));fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('No products in this category');
  click('All Products','a');query.next(convertToParamMap({}));fixture.detectChanges();expect(fixture.nativeElement.querySelectorAll('.offer-card').length).toBe(2);
 });
 for(const category of ['SAVINGS','CARDS','LOANS','ALL'])it('restores and clicks '+category+' URL filter without reloading the API',()=>{
  setup(false,'',category);catalogue();expect(fixture.nativeElement.querySelectorAll('[data-offer-id]').length).toBe(category==='ALL'?2:category==='LOANS'?0:1);
  click('Cards','a');query.next(convertToParamMap({category:'CARDS'}));fixture.detectChanges();expect(fixture.nativeElement.querySelector('[data-offer-id]').textContent).toContain('Cashback Week');http.expectNone('/api/v1/promotions');
 });
 it('falls back safely for invalid category and handles empty catalogue',()=>{setup(false,'','<script>');catalogue([]);expect(fixture.nativeElement.textContent).toContain('No offers in this category');expect(fixture.nativeElement.querySelector('script')).toBeNull();});
 it('opens full detail by card click, traps focus natively and restores it after Escape',fakeAsync(()=>{
  setup();catalogue();open();const dialog:HTMLDialogElement=fixture.nativeElement.querySelector('dialog');expect(dialog.open).toBeTrue();expect(document.body.style.overflow).toBe('hidden');expect(dialog.contains(document.activeElement)).toBeTrue();expect(dialog.textContent).toContain(offer.terms);expect(dialog.textContent).toContain(offer.eligibility);
  const event=new Event('cancel',{cancelable:true});dialog.dispatchEvent(event);tick();fixture.detectChanges();expect(event.defaultPrevented).toBeTrue();expect(dialog.open).toBeFalse();expect(navigation).toHaveBeenCalledWith(['/offers'],jasmine.objectContaining({state:{offerFocus:offer.id}}));expect((document.activeElement as HTMLElement).id).toBe('offer-link-'+offer.id);
 }));
 it('opens a shareable deep link immediately and closes to the filtered catalogue',fakeAsync(()=>{
  setup(false,offer.id,'SAVINGS');expect(fixture.nativeElement.querySelector('dialog').open).toBeTrue();expect(fixture.nativeElement.textContent).toContain('Loading offer details');catalogue();http.expectOne('/api/v1/promotions/'+offer.id).flush(offer);fixture.detectChanges();click('Close offer details');tick();expect(navigation).toHaveBeenCalledWith(['/offers'],jasmine.objectContaining({queryParams:{category:'SAVINGS'}}));
 }));
 for(const status of [400,403,404,503])it('clears unavailable detail for '+status+' and retries from the modal',()=>{
  setup(false,offer.id);catalogue();http.expectOne('/api/v1/promotions/'+offer.id).flush({}, {status,statusText:'Failure'});fixture.detectChanges();const dialog=fixture.nativeElement.querySelector('dialog');expect(dialog.textContent).toContain(status===403||status===404?'Offer Not Available':'We could not load this offer');expect(dialog.textContent).not.toContain(offer.terms);expect(dialog.querySelector('.primary')).toBeNull();click('Retry offer details');http.expectOne('/api/v1/promotions/'+offer.id).flush(offer);fixture.detectChanges();expect(dialog.textContent).toContain(offer.terms);
 });
 for(const products of [false,true])it('retries '+(products?'products':'offers')+' catalogue failures and handles empty success',()=>{
  setup(products);const path=products?'/api/v1/products':'/api/v1/promotions';http.expectOne(path).flush({}, {status:503,statusText:'Unavailable'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('We could not load the catalogue');click('Retry catalogue');http.expectOne(path).flush([]);fixture.detectChanges();expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();expect(fixture.nativeElement.textContent).toContain('No '+(products?'products':'offers')+' in this category');
 });
 it('clicks simulated interest, disables pending submission and publishes only the exact server confirmation',()=>{
  setup();catalogue();open();click('Express Interest (Simulated)');const write=http.expectOne('/api/v1/promotions/'+offer.id+'/express-interest');expect(write.request.method).toBe('POST');expect(write.request.body).toEqual({});expect(fixture.nativeElement.querySelector('.primary').disabled).toBeTrue();write.flush(interest);fixture.detectChanges();expect(TestBed.inject(NotificationService).message()).toBe(notice);expect(fixture.nativeElement.querySelector('dialog').textContent).toContain('Interest recorded (Simulated)');
  click('Express Interest (Simulated)');http.expectOne('/api/v1/promotions/'+offer.id+'/express-interest').flush(interest);fixture.detectChanges();expect(TestBed.inject(NotificationService).message()).toBe(notice);
 });
 it('shows interest failure without claiming success and allows a real retry',()=>{
  setup();catalogue();open();click('Express Interest (Simulated)');http.expectOne('/api/v1/promotions/'+offer.id+'/express-interest').flush({}, {status:503,statusText:'Unavailable'});fixture.detectChanges();expect(fixture.nativeElement.querySelector('dialog').textContent).toContain('We could not record your interest');expect(TestBed.inject(NotificationService).message()).toBe('');click('Express Interest (Simulated)');http.expectOne('/api/v1/promotions/'+offer.id+'/express-interest').flush(interest);fixture.detectChanges();expect(fixture.nativeElement.querySelector('dialog [role=alert]')).toBeNull();
 });
 it('cancels obsolete detail and catalogue reads and removes body locking on departure',()=>{
  setup(false,offer.id);const first=http.expectOne('/api/v1/promotions/'+offer.id);const list=http.expectOne('/api/v1/promotions');params.next(convertToParamMap({offerId:'other'}));expect(first.cancelled).toBeTrue();const other=http.expectOne('/api/v1/promotions/other');fixture.destroy();expect(other.cancelled).toBeTrue();expect(list.cancelled).toBeTrue();
 });
 it('backdrop clicks close while content clicks keep the dialog open',fakeAsync(()=>{
  setup();catalogue();open();const dialog:HTMLDialogElement=fixture.nativeElement.querySelector('dialog');dialog.querySelector('h2')?.dispatchEvent(new MouseEvent('click',{bubbles:true}));expect(dialog.open).toBeTrue();dialog.dispatchEvent(new MouseEvent('click',{bubbles:true}));tick();expect(dialog.open).toBeFalse();
 }));
 it('encodes malicious path identities in both client calls and propagates errors',()=>{
  setup();catalogue();const service=TestBed.inject(OfferService);let errors=0;service.detail('../private').subscribe({error:e=>{expect(e.status).toBe(400);errors++;}});http.expectOne('/api/v1/promotions/..%2Fprivate').flush({}, {status:400,statusText:'Bad Request'});service.expressInterest('../private').subscribe({error:e=>{expect(e.status).toBe(400);errors++;}});http.expectOne('/api/v1/promotions/..%2Fprivate/express-interest').flush({}, {status:400,statusText:'Bad Request'});expect(errors).toBe(2);
 });
});
