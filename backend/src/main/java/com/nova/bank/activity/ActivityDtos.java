package com.nova.bank.activity;

import com.nova.bank.portfolio.Account;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Define safe ledger contracts using exact decimal strings. */
public final class ActivityDtos {
    /** Prevent namespace construction. */
    private ActivityDtos() {}
    /** Carry only public transaction details. */
    public record Item(UUID id, String referenceCode, Instant postedAt, String description,
            Transaction.Category category, Transaction.Direction direction, Transaction.Status status, String amount) {}
    /** Carry the selected masked account snapshot and filtered count. */
    public record Activity(UUID accountId, String accountName, String maskedNumber, Account.Type accountType,
            String status, String currency, String availableBalance, String currentBalance, String pendingAmount,
            String annualInterestRate, LocalDate lastStatementDate, long totalCount, List<Item> transactions) {}
}
