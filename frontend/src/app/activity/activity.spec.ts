import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ActivityComponent } from './activity.component';
import { ACTIVITY_DEMO_DATE, Activity, ActivityService } from './activity.service';

/** Use independent decimal and timestamp fixtures for ledger rendering assertions. */
const activity: Activity = {accountId: 'savings', accountName: 'Peso Savings', maskedNumber: '•••• 9034',
  accountType: 'DEPOSIT', status: 'ACTIVE', currency: 'PHP', availableBalance: '116620.10', currentBalance: '116620.10',
  pendingAmount: '0.00', annualInterestRate: '3.25', lastStatementDate: '2026-09-30', totalCount: 2,
  transactions: [{id: 'credit', referenceCode: 'REF-TX-882191', postedAt: '2026-10-13T09:30:00Z',
    description: 'Salary credit', category: 'INCOME', direction: 'CREDIT', status: 'POSTED', amount: '48500.00'},
  {id: 'debit', referenceCode: 'REF-TX-882194', postedAt: '2026-10-09T16:45:00Z',
    description: 'Makati Coffee Shop', category: 'DINING', direction: 'DEBIT', status: 'PENDING', amount: '185.00'}]};

/** Exercise real Angular transport, forms and template behavior at the controlled network boundary. */
describe('Activity', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ActivityComponent>;
  let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigation: jasmine.Spy;
  /** Configure route observables and apply navigations as a router would. */
  function setup(initial: Record<string, string> = {}, demoDate = '2026-10-14') {
    query = new BehaviorSubject(convertToParamMap(initial)); params = new BehaviorSubject(convertToParamMap({id: 'savings'}));
    TestBed.configureTestingModule({imports: [ActivityComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), {provide: ACTIVITY_DEMO_DATE, useValue: demoDate},
      {provide: ActivatedRoute, useValue: {paramMap: params, queryParamMap: query, snapshot: {}}}]});
    http = TestBed.inject(HttpTestingController);
    navigation = spyOn(TestBed.inject(Router), 'navigate').and.callFake((_commands, extras) => {
      query.next(convertToParamMap(extras?.queryParams || {})); return Promise.resolve(true);
    });
    fixture = TestBed.createComponent(ActivityComponent); fixture.detectChanges();
  }
  /** Retrieve the current pending account read. */
  function request() { return http.expectOne(r => r.url === '/api/v1/accounts/savings/transactions'); }
  /** Complete the read and render its observable result. */
  function populated() { request().flush(activity); fixture.detectChanges(); }
  /** Verify no active requests survive test teardown. */
  afterEach(() => http.verify());

  it('shows loading then exact masked snapshot, dates and non-color money meaning', () => {
    setup(); expect(fixture.nativeElement.textContent).toContain('Loading account activity');
    expect(fixture.nativeElement.querySelector('section').getAttribute('aria-busy')).toBe('true');
    const read = request(); expect(read.request.method).toBe('GET'); expect(read.request.params.get('page')).toBe('0');
    expect(read.request.params.get('size')).toBe('20'); expect(read.request.params.has('startDate')).toBeFalse();
    read.flush(activity); fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    for (const value of ['Peso Savings','•••• 9034','116,620.10','0.00','3.25% p.a.','30 Sep 2026','+ PHP 48,500.00','− PHP 185.00','Money In','Money Out','PENDING']) expect(text).toContain(value);
    expect(fixture.nativeElement.querySelector('.money-in').textContent).toContain('Money In');
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/accounts');
    expect(text).not.toContain('20938479034'); expect(fixture.nativeElement.querySelectorAll('th[scope=col]').length).toBe(5);
  });
  it('expands actual reference and UTC timestamp with a keyboard button and collapses without a network reload', () => {
    setup(); populated(); const button: HTMLButtonElement = fixture.nativeElement.querySelector('tbody button');
    expect(button.getAttribute('aria-expanded')).toBe('false'); button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    const drawer: HTMLElement = fixture.nativeElement.querySelector('#detail-credit');
    expect(drawer.hidden).toBeFalse(); expect(drawer.textContent).toContain('REF-TX-882191');
    expect(drawer.textContent).toContain('2026-10-13T09:30:00Z'); expect(drawer.textContent).toContain('No real money was moved');
    expect(query.value.get('expanded')).toBe('credit'); button.click(); fixture.detectChanges(); expect(drawer.hidden).toBeTrue();
    http.expectNone(r => r.url.includes('/transactions'));
  });
  it('restores every filter, page, size and expanded identity from route state', () => {
    setup({search:'Coffee', category:'DINING', direction:'DEBIT', startDate:'2026-10-01', endDate:'2026-10-14', page:'2', size:'1', expanded:'debit'});
    const read = request();
    for (const [key,value] of Object.entries({search:'Coffee',category:'DINING',direction:'DEBIT',startDate:'2026-10-01',endDate:'2026-10-14',page:'2',size:'1'})) expect(read.request.params.get(key)).toBe(value);
    read.flush(activity); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#txn-search').value).toBe('Coffee');
    expect(fixture.nativeElement.querySelector('#txn-start')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#detail-debit').hidden).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Page 3');
  });
  it('applies real-time search, cancels races, and preserves the input identity while loading', () => {
    setup(); populated(); const input: HTMLInputElement = fixture.nativeElement.querySelector('#txn-search');
    input.value = 'first'; input.dispatchEvent(new Event('input')); fixture.detectChanges(); const first = request();
    expect(first.request.params.get('search')).toBe('first'); expect(fixture.nativeElement.querySelector('#txn-search')).toBe(input);
    input.value = 'second'; input.dispatchEvent(new Event('input')); fixture.detectChanges(); const second = request();
    expect(first.cancelled).toBeTrue(); expect(second.request.params.get('search')).toBe('second');
    second.flush({...activity,totalCount:0,transactions:[]}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No transactions found in this period. Reset filters or broaden your search.');
    expect(fixture.nativeElement.textContent).not.toContain('Salary credit');
  });
  for (const [control,value] of [['category','DINING'],['direction','DEBIT'],['startDate','2026-10-01'],['endDate','2026-10-12']] as const) {
    it(`applies ${control} independently and resets pagination`, () => {
      setup({page:'3'}); populated(); fixture.componentInstance.form.controls[control].setValue(value);
      const read = request(); expect(read.request.params.get(control)).toBe(value); expect(read.request.params.get('page')).toBe('0'); read.flush(activity);
    });
  }
  for (const [range,start] of [['7','2026-10-08'],['30','2026-09-15'],['90','2026-07-17']]) {
    it(`applies inclusive ${range}-day range and persists it`, () => {
      setup(); populated(); fixture.componentInstance.setRange(range); const read = request();
      expect(read.request.params.get('startDate')).toBe(start); expect(read.request.params.get('endDate')).toBe('2026-10-14');
      expect(query.value.get('range')).toBe(range); read.flush(activity);
    });
  }
  it('custom range exposes date inputs and reset restores defaults and clears disclosure', () => {
    setup(); populated(); fixture.componentInstance.setRange('custom'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#txn-start')).not.toBeNull();
    fixture.componentInstance.form.controls.search.setValue('missing'); request().flush({...activity,totalCount:0,transactions:[]}); fixture.detectChanges();
    fixture.nativeElement.querySelector('form button').click(); const read = request();
    expect(read.request.params.has('search')).toBeFalse(); expect(read.request.params.has('startDate')).toBeFalse();
    expect(read.request.params.get('page')).toBe('0'); expect(read.request.params.get('size')).toBe('20'); read.flush(activity); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#txn-start')).toBeNull(); expect(fixture.nativeElement.querySelector('#txn-range').value).toBe('30');
  });
  it('enforces pagination controls and page-size changes through URL state', () => {
    setup({size:'1'}); populated(); fixture.componentInstance.page(1); const read = request();
    expect(read.request.params.get('page')).toBe('1'); read.flush({...activity,transactions:[activity.transactions[1]]}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.activity-pagination button')[1].disabled).toBeTrue();
    fixture.componentInstance.form.controls.size.setValue(100); const resized = request();
    expect(resized.request.params.get('size')).toBe('100'); expect(resized.request.params.get('page')).toBe('0'); resized.flush(activity);
  });
  for (const status of [403,404,503,400]) {
    it(`clears all metadata for ${status} and retries successfully`, () => {
      setup(); request().flush({}, {status,statusText:'Failure'}); fixture.detectChanges();
      const text = fixture.nativeElement.textContent;
      expect(text).toContain(status === 403 || status === 404 ? 'Account Not Available' : 'We could not load account activity');
      expect(text).not.toContain('Peso Savings'); expect(fixture.nativeElement.querySelector('dl')).toBeNull();
      fixture.nativeElement.querySelector('[role=alert] button').click(); populated(); expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();
    });
  }
  it('handles null rate and date, empty account, and literal XSS descriptions', () => {
    setup(); request().flush({...activity,annualInterestRate:null,lastStatementDate:null,transactions:[{...activity.transactions[0],description:'<script>alert(1)</script>'}]}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Not applicable'); expect(fixture.nativeElement.textContent).toContain('Not available');
    expect(fixture.nativeElement.textContent).toContain('<script>alert(1)</script>'); expect(fixture.nativeElement.querySelector('script')).toBeNull();
    query.next(convertToParamMap({search:'none'})); request().flush({...activity,totalCount:0,transactions:[]}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No transactions found'); expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });
  it('cancels obsolete account reads and removes old metadata before a new account resolves', () => {
    setup(); populated(); query.next(convertToParamMap({search:'slow'})); const slow = request();
    params.next(convertToParamMap({id:'other'})); fixture.detectChanges(); expect(slow.cancelled).toBeTrue();
    expect(fixture.nativeElement.textContent).not.toContain('Peso Savings');
    http.expectOne(r => r.url === '/api/v1/accounts/other/transactions').flush({}, {status:403,statusText:'Forbidden'}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Account Not Available');
  });
  it('uses deployment demo calendar for relative dates instead of the machine clock', () => {
    setup({}, '2027-01-20'); populated(); fixture.componentInstance.setRange('7');
    const read = request(); expect(read.request.params.get('startDate')).toBe('2027-01-14');
    expect(read.request.params.get('endDate')).toBe('2027-01-20'); read.flush(activity);
  });
  it('tears down HTTP and form subscriptions on destruction', () => {
    setup(); const pending = request(); fixture.destroy(); expect(pending.cancelled).toBeTrue();
    const count = navigation.calls.count(); fixture.componentInstance.form.controls.search.setValue('after destroy');
    query.next(convertToParamMap({search:'after destroy'})); expect(navigation.calls.count()).toBe(count); http.expectNone(r => r.url.includes('/transactions'));
  });
  it('client encodes path traversal as a single account segment and propagates failures', () => {
    setup(); populated(); let status = 0;
    TestBed.inject(ActivityService).list('../../private', {search:'%_ <script>',category:'',direction:'',startDate:'',endDate:'',page:0,size:20})
      .subscribe({error:error => status = error.status});
    const read = http.expectOne(r => r.url === '/api/v1/accounts/..%2F..%2Fprivate/transactions');
    expect(read.request.params.get('search')).toBe('%_ <script>'); read.flush({}, {status:400,statusText:'Bad Request'}); expect(status).toBe(400);
  });
});
