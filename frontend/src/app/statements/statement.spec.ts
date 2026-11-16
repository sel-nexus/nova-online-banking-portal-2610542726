import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { StatementService } from './statement.service';
import { ActivityComponent } from '../activity/activity.component';
import { Activity } from '../activity/activity.service';
import { AuthService, authInterceptor } from '../auth/auth.service';
import { PortfolioComponent } from '../portfolio/portfolio.component';

const account: Activity = {accountId:'savings',accountName:'Peso Savings',maskedNumber:'•••• 9034',accountType:'DEPOSIT',status:'ACTIVE',currency:'PHP',availableBalance:'116620.10',currentBalance:'116620.10',pendingAmount:'0.00',annualInterestRate:'3.25',lastStatementDate:'2026-09-30',totalCount:0,transactions:[]};
/** Exercise authenticated transport and actual UI actions, isolating only the network/browser download boundary. */
describe('Statements', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ActivityComponent>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let clicked: jasmine.Spy;
  let created: jasmine.Spy;
  let revoked: jasmine.Spy;
  let anchor: HTMLAnchorElement | undefined;
  /** Bind real services and interceptor; install an illustrative session only at the unit-test boundary. */
  beforeEach(() => {
    sessionStorage.clear();
    params=new BehaviorSubject(convertToParamMap({id:'savings'})); query=new BehaviorSubject(convertToParamMap({}));
    TestBed.configureTestingModule({imports:[ActivityComponent,PortfolioComponent],providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),provideRouter([]),
      {provide:ActivatedRoute,useValue:{paramMap:params,queryParamMap:query,snapshot:{data:{}}}}]});
    http=TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).installSession({token:'test-bearer',tokenType:'Bearer',expiresIn:900,user:{id:'owner',username:'miguel',fullName:'Miguel',role:'CUSTOMER',lastLogin:null}});
    spyOn(TestBed.inject(Router),'navigate').and.resolveTo(true);
    created=spyOn(URL,'createObjectURL').and.returnValue('blob:statement'); revoked=spyOn(URL,'revokeObjectURL');
    clicked=spyOn(HTMLAnchorElement.prototype,'click').and.callFake(function(this:HTMLAnchorElement) {anchor=this;});
  });
  /** Check every active HTTP request was handled. */
  afterEach(() => {http.verify();sessionStorage.clear();});
  /** Render real activity and complete its ledger read. */
  function render() {fixture=TestBed.createComponent(ActivityComponent);fixture.detectChanges();http.expectOne(r=>r.url.endsWith('/transactions')).flush(account);fixture.detectChanges();}
  /** Resolve a pending nonempty UTF-8 statement. */
  function flush(header='attachment; filename="statement-9034-demo.txt"') {
    const request=http.expectOne('/api/v1/accounts/savings/statement/download');
    expect(request.request.method).toBe('GET');expect(request.request.responseType).toBe('blob');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-bearer');expect(request.request.params.keys()).toEqual([]);
    request.flush(new Blob(['Account number: •••• 9034\nAmount: PHP 48500.00'],{type:'text/plain;charset=UTF-8'}),{headers:{'Content-Type':'text/plain;charset=UTF-8','Content-Disposition':header}});
  }
  it('clicks an enabled CTA, blocks duplicates, starts a safe download and cleans resources', fakeAsync(() => {
    render();expect(fixture.nativeElement.textContent).toContain('All account activity');
    const button:HTMLButtonElement=fixture.nativeElement.querySelector('#statement-download button');button.click();fixture.detectChanges();
    expect(button.disabled).toBeTrue();expect(button.textContent).toContain('Downloading');expect(button.getAttribute('aria-busy')).toBe('true');
    fixture.componentInstance.downloadStatement();flush();fixture.detectChanges();
    expect(clicked).toHaveBeenCalledTimes(1);expect(anchor?.download).toBe('statement-9034-demo.txt');expect(anchor?.isConnected).toBeFalse();
    expect(created).toHaveBeenCalled();expect(button.disabled).toBeFalse();expect(fixture.nativeElement.textContent).toContain('Your demo statement download has started');
    tick();expect(revoked).toHaveBeenCalledOnceWith('blob:statement');
  }));
  for(const header of ['','attachment; filename="../../secret.txt"','attachment; filename="statement-20938479034-demo.txt"',"attachment; filename*=UTF-8''%ZZ",'attachment; filename="statement-9034-demo.txt.exe"']) {
    it(`uses a safe generic filename for rejected header ${header}`,fakeAsync(()=> {
      TestBed.inject(StatementService).download('savings').subscribe();flush(header);expect(anchor?.download).toBe('statement-demo.txt');tick();expect(revoked).toHaveBeenCalled();
    }));
  }
  it('prefers valid RFC UTF-8 filename over a plain fallback',fakeAsync(()=> {
    TestBed.inject(StatementService).download('savings').subscribe();flush("attachment; filename=bad.txt; filename*=UTF-8''statement-9034-demo.txt");expect(anchor?.download).toBe('statement-9034-demo.txt');tick();
  }));
  for(const status of [0,400,401,403,404,503]) {
    it(`reports ${status} without saving a file and allows retry`,()=> {
      render();fixture.nativeElement.querySelector('#statement-download button').click();
      const request=http.expectOne(r=>r.url.endsWith('/statement/download'));
      if(status===0)request.error(new ProgressEvent('error'));else request.flush(new Blob(['Failure'],{type:'application/json'}),{status,statusText:'Failure'});
      fixture.detectChanges();expect(fixture.nativeElement.querySelector('#statement-download [role=alert]').textContent).toContain('No file was saved');
      expect(created).not.toHaveBeenCalled();expect(clicked).not.toHaveBeenCalled();expect(fixture.componentInstance.downloading()).toBeFalse();
      if(status===401) expect(TestBed.inject(AuthService).session()).toBeNull();
    });
  }
  for(const body of [null,new Blob([],{type:'text/plain'}),new Blob(['error'],{type:'application/json'})]) {
    it('rejects missing, empty or non-text success bodies without generating a download',()=> {
      render();fixture.nativeElement.querySelector('#statement-download button').click();http.expectOne(r=>r.url.endsWith('/statement/download')).flush(body);fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('No file was saved');expect(created).not.toHaveBeenCalled();
    });
  }
  it('recovers from a download failure on the next actual button click',fakeAsync(()=> {
    render();fixture.nativeElement.querySelector('#statement-download button').click();http.expectOne(r=>r.url.endsWith('/statement/download')).flush(new Blob(['failure']),{status:503,statusText:'Unavailable'});
    fixture.detectChanges();fixture.nativeElement.querySelector('#statement-download button').click();flush();fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#statement-download [role=alert]')).toBeNull();expect(clicked).toHaveBeenCalled();tick();
  }));
  it('cancels a statement for an obsolete account and resets notice state',()=> {
    render();fixture.componentInstance.downloadStatement();const pending=http.expectOne(r=>r.url.endsWith('/statement/download'));
    params.next(convertToParamMap({id:'salary'}));fixture.detectChanges();expect(pending.cancelled).toBeTrue();expect(created).not.toHaveBeenCalled();expect(fixture.componentInstance.downloading()).toBeFalse();
    http.expectOne(r=>r.url.endsWith('/salary/transactions')).flush({...account,accountId:'salary'});
  });
  it('cancels on destruction without saving a stale file',()=> {
    render();fixture.componentInstance.downloadStatement();const pending=http.expectOne(r=>r.url.endsWith('/statement/download'));fixture.destroy();expect(pending.cancelled).toBeTrue();expect(created).not.toHaveBeenCalled();
  });
  it('does not start before account data or during a ledger refresh',()=> {
    fixture=TestBed.createComponent(ActivityComponent);fixture.detectChanges();fixture.componentInstance.downloadStatement();http.expectNone(r=>r.url.endsWith('/statement/download'));
    http.expectOne(r=>r.url.endsWith('/transactions')).flush(account);fixture.componentInstance.loading.set(true);fixture.componentInstance.downloadStatement();http.expectNone(r=>r.url.endsWith('/statement/download'));
  });
  it('encodes a hostile account path as one segment and propagates the server error',()=> {
    let status=0;TestBed.inject(StatementService).download('../../private').subscribe({error:e=>status=e.status});
    http.expectOne('/api/v1/accounts/..%2F..%2Fprivate/statement/download').flush(null,{status:400,statusText:'Bad Request'});expect(status).toBe(400);expect(clicked).not.toHaveBeenCalled();
  });
  it('releases object URL and anchor even if browser activation throws',fakeAsync(()=> {
    clicked.and.throwError('Download unavailable');let failed=false;TestBed.inject(StatementService).download('savings').subscribe({error:()=>failed=true});flush();expect(failed).toBeTrue();expect(document.querySelector('a[download]')).toBeNull();tick();expect(revoked).toHaveBeenCalled();
  }));
  it('dashboard statement quick entry targets the first owned activity CTA and is absent when empty',()=> {
    const dashboard=TestBed.createComponent(PortfolioComponent);dashboard.detectChanges();
    http.expectOne('/api/v1/portfolio/summary').flush({customerName:'Miguel',greeting:'Hello',lastLogin:null,relationshipBalance:'116620.10',currency:'PHP',accounts:[{...account,id:'owned',group:'Savings'}]});dashboard.detectChanges();
    const link=Array.from(dashboard.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>).find(a=>a.textContent==='Download Statements');expect(link?.getAttribute('href')).toBe('/accounts/owned#statement-download');
    expect(dashboard.nativeElement.textContent).not.toContain('Statement downloads will be available');dashboard.componentInstance.accounts.set([]);dashboard.detectChanges();expect(dashboard.nativeElement.textContent).not.toContain('Download Statements');
  });
});
