package com.nova.bank.portfolio;

import com.nova.bank.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persist exact balances and private identifiers inside the database boundary. */
@Entity
@Table(name = "accounts")
public class Account {
    /** Distinguish deposits from liabilities for relationship arithmetic. */
    public enum Type { DEPOSIT, CREDIT_CARD, LOAN }

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "account_number", nullable = false, length = 20, unique = true) private String accountNumber;
    @Column(name = "masked_number", nullable = false, length = 20) private String maskedNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30) private Type accountType;
    @Column(name = "account_name", nullable = false, length = 100) private String accountName;
    @Column(name = "available_balance", nullable = false, precision = 15, scale = 2) private BigDecimal availableBalance;
    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2) private BigDecimal currentBalance;
    @Column(name = "credit_limit", precision = 15, scale = 2) private BigDecimal creditLimit;
    @Column(name = "interest_rate", precision = 5, scale = 2) private BigDecimal interestRate;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "last_statement_date") private LocalDate lastStatementDate;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    /** Allow Hibernate to hydrate the private record. */
    protected Account() {}
    /** Return the stable resource identifier for subsequent activity requests. */
    public UUID getId() { return id; }
    /** Return ownership without initializing the owner's profile. */
    public UUID getUserId() { return user.getId(); }
    /** Return the account classification used in exact totals. */
    public Type getAccountType() { return accountType; }
    /** Return the public product name. */
    public String getAccountName() { return accountName; }
    /** Mask from the private identifier, never trusting a stale stored mask. */
    public String getMaskedNumber() { return "•••• " + accountNumber.substring(accountNumber.length() - 4); }
    /** Return the persisted available balance within the service boundary. */
    public BigDecimal getAvailableBalance() { return availableBalance; }
    /** Return the ledger balance, amount due for cards, or outstanding loan principal. */
    public BigDecimal getCurrentBalance() { return currentBalance; }
    /** Return the nullable borrowing ceiling. */
    public BigDecimal getCreditLimit() { return creditLimit; }
    /** Return the nullable annual rate. */
    public BigDecimal getInterestRate() { return interestRate; }
    /** Return the textual account lifecycle status. */
    public String getStatus() { return status; }
    /** Return the latest statement calendar date. */
    public LocalDate getLastStatementDate() { return lastStatementDate; }
    /** Compare stable account identities without touching financial data. */
    @Override public boolean equals(Object other) { return other instanceof Account a && id != null && id.equals(a.getId()); }
    /** Hash only the stable identity. */
    @Override public int hashCode() { return id == null ? 0 : id.hashCode(); }
}
