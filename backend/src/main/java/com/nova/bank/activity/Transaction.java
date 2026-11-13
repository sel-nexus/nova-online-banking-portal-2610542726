package com.nova.bank.activity;

import com.nova.bank.portfolio.Account;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persist ledger values without exposing the associated private account. */
@Entity
@Table(name = "transactions")
public class Transaction {
    /** Classify merchant activity. */
    public enum Category { INCOME, GROCERIES, SAVINGS, DINING, TRANSPORT, GENERAL }
    /** Distinguish money received from money spent. */
    public enum Direction { CREDIT, DEBIT }
    /** Preserve posted, pending and declined demonstration activity. */
    public enum Status { POSTED, PENDING, DECLINED }
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false) private Account account;
    @Column(name = "reference_code", nullable = false, length = 50, unique = true) private String referenceCode;
    @Column(name = "posted_at", nullable = false) private Instant postedAt;
    @Column(nullable = false, length = 255) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Direction direction;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    /** Allow Hibernate hydration. */
    protected Transaction() {}
    /** Map public ledger fields while the service transaction is open. */
    public ActivityDtos.Item toItem() {
        return new ActivityDtos.Item(id, referenceCode, postedAt, description, category, direction, status, amount.toPlainString());
    }
    /** Compare stable identities without loading account metadata. */
    @Override public boolean equals(Object other) { return other instanceof Transaction t && id != null && id.equals(t.id); }
    /** Hash stable identity only. */
    @Override public int hashCode() { return id == null ? 0 : id.hashCode(); }
}
