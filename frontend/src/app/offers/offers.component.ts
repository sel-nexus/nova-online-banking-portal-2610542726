import { Component, DestroyRef, ElementRef, OnDestroy, OnInit, ViewChild, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { Offer, OfferService, Product } from './offer.service';
import { NotificationService } from '../core/notification.service';

/** Render real product and offer routes with shareable filters and native full-detail dialogs. */
@Component({selector: 'app-offers', standalone: true, imports: [DatePipe, TitleCasePipe, RouterLink], templateUrl: './offers.component.html',
 styles: [`.offer-grid{display:grid;grid-template-columns:1fr;gap:20px}.offer-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:24px;display:flex;flex-direction:column;min-width:0}.offer-card>a{margin-top:auto}.offer-filters{display:flex;gap:16px;flex-wrap:wrap;margin:32px 0}.offer-filters a[aria-current]{font-weight:600}.offer-banner{border-block:1px solid var(--border);padding:24px 0;margin-bottom:32px}.offer-dialog{max-width:640px;max-height:85dvh;overflow:auto}.offer-dialog p{overflow-wrap:anywhere}.offer-badge{font-size:.8rem;color:var(--foreground-muted)}@media(min-width:768px){.offer-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(min-width:1024px){.offer-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}`]})
export class OffersComponent implements OnInit, OnDestroy {
  @ViewChild('detailDialog', {static: true}) dialog!: ElementRef<HTMLDialogElement>;
  @ViewChild('catalogueTitle', {static: true}) heading!: ElementRef<HTMLElement>;
  readonly productsMode: boolean;
  readonly categories = ['ALL', 'SAVINGS', 'CARDS', 'LOANS'] as const;
  readonly category = signal('ALL');
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly items = signal<Product[]>([]);
  readonly offers = signal<Offer[]>([]);
  readonly selected = signal<Offer | null>(null);
  readonly detailLoading = signal(false);
  readonly detailError = signal('');
  readonly submitting = signal(false);
  readonly interestError = signal(false);
  private detailId: string | null = null;
  private restoreId: string | null = null;
  private oldOverflow = '';
  private detailRead?: Subscription;
  private catalogueRead?: Subscription;
  /** Read route mode and bind only existing shared services. */
  constructor(private client: OfferService, private route: ActivatedRoute, private router: Router,
              public notices: NotificationService, private destroyRef: DestroyRef) {
    this.productsMode = route.snapshot.data['products'] === true;
  }
  /** Follow category and resource URL changes, cancelling obsolete reads. */
  ngOnInit() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(query => {
      const value = query.get('category')?.toUpperCase() || 'ALL';
      this.category.set(this.categories.some(c => c === value) ? value : 'ALL');
    });
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.detailId = params.get('offerId');
      if (this.detailId) this.loadDetail(); else this.releaseDialog();
    });
    this.load();
  }
  /** Retry the catalogue without retaining stale items on failure. */
  load() {
    this.catalogueRead?.unsubscribe(); this.loading.set(true); this.error.set(false); this.items.set([]); this.offers.set([]);
    const read = this.productsMode ? this.client.products() : this.client.list();
    this.catalogueRead = read.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => { this.items.set(items); if (!this.productsMode) this.offers.set(items as Offer[]); this.loading.set(false); this.restoreCatalogueFocus(); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }
  /** Select the URL category without discarding browser navigation semantics. */
  visibleProducts() { return this.items().filter(item => this.category() === 'ALL' || item.category === this.category()); }
  /** Filter only already-authorized campaigns. */
  visibleOffers() { return this.offers().filter(item => this.category() === 'ALL' || item.category === this.category()); }
  /** Remember the semantic trigger identity across route component replacement. */
  remember(id: string) { this.restoreId = id; }
  /** Open a loading dialog immediately; unavailable resources never show stale terms. */
  loadDetail() {
    if (!this.detailId) return;
    this.detailRead?.unsubscribe(); this.selected.set(null); this.detailError.set(''); this.interestError.set(false); this.detailLoading.set(true);
    const element = this.dialog.nativeElement;
    if (!element.open) { this.oldOverflow = document.body.style.overflow; element.showModal(); document.body.style.overflow = 'hidden'; }
    this.detailRead = this.client.detail(this.detailId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: offer => { this.selected.set(offer); this.detailLoading.set(false); },
      error: error => { this.detailError.set(error.status === 403 || error.status === 404 ? 'Offer Not Available' : 'We could not load this offer.'); this.detailLoading.set(false); }
    });
  }
  /** Return to the catalogue while retaining the category and restoring a meaningful focus target. */
  close() {
    const id = this.restoreId || this.detailId;
    this.releaseDialog();
    void this.router.navigate(['/offers'], {queryParams: {category: this.category() === 'ALL' ? null : this.category()}, state: {offerFocus: id}}).then(() => {
      (document.getElementById('offer-link-' + id) || this.heading.nativeElement).focus();
    });
  }
  /** Close only clicks on the native backdrop, not content inside the dialog. */
  backdrop(event: MouseEvent) { if (event.target === this.dialog.nativeElement) this.close(); }
  /** Publish the exact backend notice only after a persisted simulation succeeds. */
  expressInterest() {
    const offer = this.selected(); if (!offer || this.submitting()) return;
    this.submitting.set(true); this.interestError.set(false);
    this.client.expressInterest(offer.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.selected.set({...offer, interested: true}); this.offers.update(items => items.map(item => item.id === offer.id ? {...item, interested: true} : item)); this.notices.show(result.message); this.submitting.set(false); },
      error: () => { this.interestError.set(true); this.submitting.set(false); }
    });
  }
  /** Restore focus after a routed catalogue replacement finishes loading. */
  private restoreCatalogueFocus() {
    const id = history.state?.offerFocus as string | undefined;
    if (!this.detailId && id) setTimeout(() => (document.getElementById('offer-link-' + id) || this.heading.nativeElement).focus());
  }
  /** Cancel detail work and release native modal/body state without changing routes. */
  private releaseDialog() {
    this.detailRead?.unsubscribe();
    if (this.dialog.nativeElement.open) { this.dialog.nativeElement.close(); document.body.style.overflow = this.oldOverflow; }
    this.selected.set(null); this.detailLoading.set(false);
  }
  /** Release scroll locking and outstanding reads when leaving the feature. */
  ngOnDestroy() { this.catalogueRead?.unsubscribe(); this.releaseDialog(); }
}
