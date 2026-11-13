package com.nova.bank.portfolio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Define safe JSON contracts; every monetary value is a decimal string. */
public final class PortfolioDtos {
    /** Prevent construction of the contract namespace. */
    private PortfolioDtos() {}
    /** Carry masked identifiers and type-specific balances, never an entity or full number. */
    public record AccountSummary(UUID id, String accountName, Account.Type accountType,
            String group, String maskedNumber, String status, String currency,
            String availableBalance, String currentBalance, String creditLimit,
            String amountDue, String availableCredit, String outstandingBalance,
            String annualInterestRate, LocalDate lastStatementDate) {}
    /** Carry the authenticated customer's deposit-only relationship summary. */
    public record Summary(String customerName, String greeting, Instant lastLogin,
            String relationshipBalance, String currency, List<AccountSummary> accounts) {}
}
