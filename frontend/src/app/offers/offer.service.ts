import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
/** Constrain retail categories shared by both catalogues. */
export type Category = 'SAVINGS' | 'CARDS' | 'LOANS';
/** Describe the bounded recommendation contract. */
export interface Product { title: string; category: Category; benefit: string; }
/** Describe safe full campaign detail and owner-bound registration state. */
export interface Offer extends Product { id: string; offerKey: string; terms: string; eligibility: string; validUntil: string; personalization: string; interested: boolean; }
/** Describe the durable, idempotent simulation confirmation. */
export interface Interest { id: string; promotionId: string; registeredAt: string; message: string; }
/** Keep discovery on the existing configured bearer transport. */
@Injectable({providedIn: 'root'})
export class OfferService {
  /** Bind the shared API boundary. */
  constructor(private api: ApiService) {}
  /** Retrieve only approved product recommendations. */
  products(): Observable<Product[]> { return this.api.get<Product[]>('/products'); }
  /** Retrieve the authenticated segment's current campaigns. */
  list(): Observable<Offer[]> { return this.api.get<Offer[]>('/promotions'); }
  /** Encode resource identity as one path segment. */
  detail(id: string): Observable<Offer> { return this.api.get<Offer>('/promotions/' + encodeURIComponent(id)); }
  /** Record only a simulated registration; propagate failures to the UI. */
  expressInterest(id: string): Observable<Interest> { return this.api.post<Interest>('/promotions/' + encodeURIComponent(id) + '/express-interest', {}); }
}
