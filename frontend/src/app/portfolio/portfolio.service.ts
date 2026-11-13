import { Injectable } from '@angular/core';
import { ApiService } from '../core/api.service';

/** Consume masked accounts and decimal strings; do not perform binary money arithmetic. */
export interface AccountSummary {
  id: string;
  accountName: string;
  accountType: 'DEPOSIT' | 'CREDIT_CARD' | 'LOAN';
  group: 'Savings' | 'Payroll' | 'Cards' | 'Loans';
  maskedNumber: string;
  status: string;
  currency: string;
  availableBalance: string;
  currentBalance: string;
  creditLimit: string | null;
  amountDue: string | null;
  availableCredit: string | null;
  outstandingBalance: string | null;
  annualInterestRate: string | null;
  lastStatementDate: string | null;
}

/** Mirror the authenticated relationship response. */
export interface PortfolioSummary {
  customerName: string;
  greeting: string;
  lastLogin: string | null;
  relationshipBalance: string;
  currency: string;
  accounts: AccountSummary[];
}

/** Retrieve portfolio data through the configured typed API client. */
@Injectable({ providedIn: 'root' })
export class PortfolioService {
  /** Bind the shared HTTP boundary. */
  constructor(private api: ApiService) {}
  /** Retrieve the current customer's deposit-only summary. */
  summary() { return this.api.get<PortfolioSummary>('/portfolio/summary'); }
  /** Retrieve only the current customer's masked accounts. */
  accounts() { return this.api.get<AccountSummary[]>('/accounts'); }
}
