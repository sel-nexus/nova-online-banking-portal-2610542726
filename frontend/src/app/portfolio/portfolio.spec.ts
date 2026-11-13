import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { PortfolioComponent } from './portfolio.component';
import { AccountSummary, PortfolioService, PortfolioSummary } from './portfolio.service';

/** Use independent fixture strings to verify API requests and financial rendering. */
const accounts: AccountSummary[] = [
  {id:'payroll',accountName:'Payroll Account',accountType:'DEPOSIT',group:'Payroll',maskedNumber:'•••• 4821',status:'ACTIVE',currency:'PHP',availableBalance:'26240.15',currentBalance:'26240.15',creditLimit:null,amountDue:null,availableCredit:null,outstandingBalance:null,annualInterestRate:null,lastStatementDate:null},
  {id:'savings',accountName:'Peso Savings',accountType:'DEPOSIT',group:'Savings',maskedNumber:'•••• 9034',status:'ACTIVE',currency:'PHP',availableBalance:'116620.10',currentBalance:'116620.10',creditLimit:null,amountDue:null,availableCredit:null,outstandingBalance:null,annualInterestRate:'3.25',lastStatementDate:'2026-09-30'},
  {id:'card',accountName:'Rewards Credit Card',accountType:'CREDIT_CARD',group:'Cards',maskedNumber:'•••• 1188',status:'ACTIVE',currency:'PHP',availableBalance:'41550.00',currentBalance:'8450.00',creditLimit:'50000.00',amountDue:'8450.00',availableCredit:'41550.00',outstandingBalance:null,annualInterestRate:null,lastStatementDate:null},
  {id:'loan',accountName:'Home Loan',accountType:'LOAN',group:'Loans',maskedNumber:'•••• 7712',status:'ACTIVE',currency:'PHP',availableBalance:'0.00',currentBalance:'2418450.00',creditLimit:null,amountDue:null,availableCredit:null,outstandingBalance:'2418450.00',annualInterestRate:null,lastStatementDate:null}
];
const summary: PortfolioSummary = {customerName:'Miguel De La Cruz',greeting:'Magandang umaga, Miguel',lastLogin:'2026-10-14T08:10:00Z',relationshipBalance:'142860.25',currency:'PHP',accounts};

/** Exercise the typed client and route-dependent component with controlled HTTP responses. */
describe('Portfolio', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<PortfolioComponent>;
  /** Configure only real Angular client plumbing, with the network test boundary substituted. */
  function setup(overview = false) {
    TestBed.configureTestingModule({imports:[PortfolioComponent],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),{provide:ActivatedRoute,useValue:{snapshot:{data:{overview}}}}]});
    http = TestBed.inject(HttpTestingController);
  }
  /** Enter the component and initiate the route's request. */
  function render() { fixture = TestBed.createComponent(PortfolioComponent); fixture.detectChanges(); }
  afterEach(() => { http.verify(); });

  it('requests typed summary and accounts from the versioned API without customer overrides', () => {
    setup(); const client = TestBed.inject(PortfolioService);
    let result: PortfolioSummary | undefined;
    client.summary().subscribe(value => result = value);
    const request = http.expectOne('/api/v1/portfolio/summary');
    expect(request.request.method).toBe('GET'); expect(request.request.params.keys()).toEqual([]);
    request.flush(summary); expect(result?.relationshipBalance).toBe('142860.25');
    client.accounts().subscribe(value => expect(value.length).toBe(4));
    const list = http.expectOne('/api/v1/accounts'); expect(list.request.method).toBe('GET'); list.flush(accounts);
  });
  it('shows accessible loading followed by dashboard greeting exact totals and masked type balances', () => {
    setup(); render();
    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toContain('Loading your portfolio');
    expect(fixture.nativeElement.querySelector('section').getAttribute('aria-busy')).toBe('true');
    http.expectOne('/api/v1/portfolio/summary').flush(summary); fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    for (const value of ['Magandang umaga, Miguel','14 Oct, 8:10 AM','142,860.25','26,240.15','116,620.10','8,450.00','50,000.00','41,550.00','2,418,450.00','Sum of available deposit balances.','•••• 9034','Payday Ipon Bonus','Cashback Week']) expect(text).toContain(value);
    expect(fixture.nativeElement.querySelectorAll('[data-account-id]').length).toBe(4);
    expect(fixture.nativeElement.querySelector('[role=status]')).toBeNull();
    expect(fixture.nativeElement.querySelector('#recommendations')).not.toBeNull();
  });
  it('uses the overview endpoint and groups every account without dashboard previews', () => {
    setup(true); render(); http.expectOne('/api/v1/accounts').flush(accounts); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Accounts Overview');
    expect(Array.from(fixture.nativeElement.querySelectorAll('.account-section h2')).map((h:any) => h.textContent)).toEqual(['Savings','Payroll','Cards','Loans']);
    expect(fixture.nativeElement.querySelector('[data-testid=relationship-balance]')).toBeNull();
    expect(fixture.nativeElement.querySelector('#offer-previews')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.account-card a').length).toBe(4);
  });
  it('renders overview empty state and no invented groups', () => {
    setup(true); render(); http.expectOne('/api/v1/accounts').flush([]); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No accounts to display');
    expect(fixture.nativeElement.querySelectorAll('.account-section').length).toBe(0);
  });
  it('renders empty dashboard zero balance and absent last login safely', () => {
    setup(); render(); http.expectOne('/api/v1/portfolio/summary').flush({...summary,customerName:'Platform Facilitator',greeting:'Magandang umaga, Platform',lastLogin:null,relationshipBalance:'0.00',accounts:[]}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No previous login');
    expect(fixture.nativeElement.textContent).toContain('No accounts to display');
    expect(fixture.nativeElement.querySelector('[data-testid=relationship-balance]').textContent).toContain('0.00');
  });
  for (const overview of [true, false]) {
    it(`shows error and retries the ${overview ? 'overview' : 'dashboard'} without stale data`, () => {
      setup(overview); render(); const path = overview ? '/api/v1/accounts' : '/api/v1/portfolio/summary';
      http.expectOne(path).flush({}, {status:503,statusText:'Unavailable'}); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain("couldn't load");
      fixture.nativeElement.querySelector('button').click(); fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Loading your');
      http.expectOne(path).flush(overview ? accounts : summary); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();
      expect(fixture.nativeElement.querySelectorAll('[data-account-id]').length).toBe(4);
    });
  }
  it('renders Rosa values and relevant previews without Miguel campaigns or empty groups', () => {
    setup(); render(); http.expectOne('/api/v1/portfolio/summary').flush({...summary,customerName:'Rosa Santos',greeting:'Magandang umaga, Rosa',relationshipBalance:'54300.00',accounts:[{...accounts[0],accountName:'Salary Account',maskedNumber:'•••• 2204',availableBalance:'54300.00'},{...accounts[3],maskedNumber:'•••• 6640',outstandingBalance:'1250000.00'}]}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('54,300.00'); expect(fixture.nativeElement.textContent).toContain('1,250,000.00');
    expect(fixture.nativeElement.textContent).toContain('Home Loan Fee Waiver'); expect(fixture.nativeElement.textContent).not.toContain('Cashback Week');
    expect(fixture.nativeElement.querySelectorAll('[data-account-id]').length).toBe(2);
  });
  it('preserves negative signs cents and large decimal precision when formatting', () => {
    setup(); const component = TestBed.createComponent(PortfolioComponent).componentInstance;
    expect(component.money('-0.01')).toBe('-0.01'); expect(component.money('9999999999999.99')).toBe('9,999,999,999,999.99'); expect(component.money(null)).toBe('—');
  });
});
