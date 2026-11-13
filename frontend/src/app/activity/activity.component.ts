import { Component, DestroyRef, Inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, distinctUntilChanged, map, of, startWith, Subject, switchMap, tap } from 'rxjs';
import { ACTIVITY_DEMO_DATE, Activity, ActivityFilters, ActivityService } from './activity.service';

/** Render owner-safe account activity with URL state and cancellable reads. */
@Component({selector: 'app-activity', standalone: true, imports: [DatePipe, RouterLink, ReactiveFormsModule],
  templateUrl: './activity.component.html'})
export class ActivityComponent implements OnInit {
  readonly data = signal<Activity | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly expanded = signal('');
  readonly range = signal('30');
  readonly categories = ['INCOME', 'GROCERIES', 'SAVINGS', 'DINING', 'TRANSPORT', 'GENERAL'];
  readonly form = new FormGroup({search: new FormControl('', {nonNullable: true}),
    category: new FormControl('', {nonNullable: true}), direction: new FormControl('', {nonNullable: true}),
    startDate: new FormControl('', {nonNullable: true}), endDate: new FormControl('', {nonNullable: true}),
    page: new FormControl(0, {nonNullable: true}), size: new FormControl(20, {nonNullable: true})});
  private readonly retry = new Subject<number>();
  private retryCount = 0;
  /** Bind route state, authenticated transport and component lifetime. */
  constructor(private client: ActivityService, private route: ActivatedRoute, private router: Router,
    private destroyRef: DestroyRef, @Inject(ACTIVITY_DEMO_DATE) private demoDate: string) {}
  /** Restore browser history filters and cancel obsolete searches or account reads. */
  ngOnInit() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.navigate({...this.form.getRawValue(), page: 0}));
    combineLatest([this.route.paramMap, this.route.queryParamMap, this.retry.pipe(startWith(undefined))]).pipe(
      tap(([, query]) => {
        const filters: ActivityFilters = {search: query.get('search') || '', category: query.get('category') || '',
          direction: query.get('direction') || '', startDate: query.get('startDate') || '', endDate: query.get('endDate') || '',
          page: Number(query.get('page') ?? 0), size: Number(query.get('size') ?? 20)};
        this.form.setValue(filters, {emitEvent: false});
        this.range.set(query.get('range') || (filters.startDate || filters.endDate ? 'custom' : '30'));
        this.expanded.set(query.get('expanded') || '');
        
      }),
      map(([params,, retry]) => ({id: params.get('id') || '', filters: this.form.getRawValue(), retry})),
      distinctUntilChanged((a, b) => a.id === b.id && JSON.stringify(a.filters) === JSON.stringify(b.filters) && a.retry === b.retry),
      tap(request => { this.loading.set(true); this.error.set(''); if (this.data()?.accountId !== request.id) this.data.set(null); }),
      switchMap(request => this.client.list(request.id, request.filters).pipe(
        map(data => ({data, error: ''})),
        catchError(error => of({data: null, error: [403,404].includes(error.status) ? 'Account Not Available' : 'We could not load account activity. Please retry.'})))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => { this.data.set(result.data); this.error.set(result.error); this.loading.set(false); });
  }
  /** Write navigable filter state without reloading the document. */
  private navigate(filters: ActivityFilters) {
    void this.router.navigate([], {relativeTo: this.route, queryParams: {...filters, range: this.range(), expanded: this.expanded() || null}});
  }
  /** Use fixture-relative inclusive calendar ranges, with server defaults for the initial thirty days. */
  setRange(value: string) {
    this.range.set(value);
    if (value === 'custom') { this.navigate(this.form.getRawValue()); return; }
    const end = this.demoDate;
    const start = new Date(end + 'T00:00:00Z'); start.setUTCDate(start.getUTCDate() - Number(value) + 1);
    this.navigate({...this.form.getRawValue(), startDate: start.toISOString().slice(0,10), endDate: end, page: 0});
  }
  /** Restore the server's default thirty-day view and clear disclosure state. */
  reset() {
    this.range.set('30'); this.expanded.set('');
    this.navigate({search: '', category: '', direction: '', startDate: '', endDate: '', page: 0, size: 20});
  }
  /** Retry the current account and filters after a recoverable failure. */
  load() { this.retry.next(++this.retryCount); }
  /** Preserve pagination in the URL without resetting other filters. */
  page(delta: number) { this.navigate({...this.form.getRawValue(), page: this.form.controls.page.value + delta}); }
  /** Toggle a keyboard-accessible inline drawer and preserve its identity in the URL. */
  disclose(id: string) {
    this.expanded.set(this.expanded() === id ? '' : id);
    this.navigate(this.form.getRawValue());
  }
  /** Group decimal characters without floating-point conversion. */
  money(value: string) {
    const [whole, cents = '00'] = value.split('.');
    return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + cents;
  }
}
