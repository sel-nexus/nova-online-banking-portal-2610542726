import { Component, DestroyRef, OnInit, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AccountSummary, PortfolioService, PortfolioSummary } from './portfolio.service';

/** Render distinct dashboard and grouped overview views with recoverable async states. */
@Component({
  selector: 'app-portfolio', standalone: true, imports: [DatePipe, RouterLink],
  templateUrl: './portfolio.component.html'
})
export class PortfolioComponent implements OnInit {
  readonly overview: boolean;
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly accounts = signal<AccountSummary[]>([]);
  readonly search = signal('');
  /** Match display names, groups and masked identifiers, never balances or internal IDs. */
  readonly filteredAccounts = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.accounts().filter(account => [account.accountName, account.group, account.maskedNumber].some(value => value.toLowerCase().includes(query)));
  });
  readonly groups = ['Savings', 'Payroll', 'Cards', 'Loans'] as const;

  /** Read page identity from the real route, not a secondary client-side page switch. */
  constructor(private client: PortfolioService, route: ActivatedRoute, private destroyRef: DestroyRef) {
    this.overview = route.snapshot.data['overview'] === true;
    route.queryParamMap.pipe(takeUntilDestroyed(destroyRef)).subscribe(params => this.search.set(params.get('search') || ''));
  }
  /** Load the route's own endpoint on entry. */
  ngOnInit() { this.load(); }
  /** Retry failed reads and clear stale financial data while loading. */
  load() {
    this.loading.set(true); this.error.set(false); this.summary.set(null); this.accounts.set([]);
    if (this.overview) {
      this.client.accounts().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: accounts => { this.accounts.set(accounts); this.loading.set(false); },
        error: () => { this.error.set(true); this.loading.set(false); }
      });
    } else {
      this.client.summary().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: summary => { this.summary.set(summary); this.accounts.set(summary.accounts); this.loading.set(false); },
        error: () => { this.error.set(true); this.loading.set(false); }
      });
    }
  }
  /** Select each overview group while retaining stable account IDs. */
  grouped(group: string) { return this.filteredAccounts().filter(account => account.group === group); }
  /** Format decimal strings by grouping characters, preserving cents and negative values exactly. */
  money(value: string | null) {
    if (value === null) return '—';
    const [whole, cents = '00'] = value.split('.');
    return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + cents;
  }
}
