import { Injectable, InjectionToken } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../core/api.service';

/** Read the same demo calendar configured on the backend; deployments may override the runtime value. */
export const ACTIVITY_DEMO_DATE = new InjectionToken<string>('Activity demo date', {
  providedIn: 'root', factory: () => (window as Window & {NOVA_CONFIG?: {demoDate?: string}}).NOVA_CONFIG?.demoDate ?? '2026-10-14'
});

/** Carry shareable ledger filters without accepting a customer override. */
export interface ActivityFilters {
  search: string; category: string; direction: string; startDate: string; endDate: string; page: number; size: number;
}
/** Carry safe public transaction details and exact money. */
export interface TransactionItem {
  id: string; referenceCode: string; postedAt: string; description: string; category: string;
  direction: 'CREDIT' | 'DEBIT'; status: 'POSTED' | 'PENDING' | 'DECLINED'; amount: string;
}
/** Carry the selected masked snapshot and filtered ledger. */
export interface Activity {
  accountId: string; accountName: string; maskedNumber: string; accountType: string; status: string; currency: string;
  availableBalance: string; currentBalance: string; pendingAmount: string; annualInterestRate: string | null;
  lastStatementDate: string | null; totalCount: number; transactions: TransactionItem[];
}
/** Query the versioned ledger endpoint through shared authenticated transport. */
@Injectable({providedIn: 'root'})
export class ActivityService {
  /** Bind configured API transport. */
  constructor(private api: ApiService) {}
  /** Serialize every filter; omit empty optional values so server defaults remain authoritative. */
  list(id: string, filters: ActivityFilters) {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) if (value !== '') params = params.set(key, String(value));
    return this.api.get<Activity>('/accounts/' + encodeURIComponent(id) + '/transactions', params);
  }
}
