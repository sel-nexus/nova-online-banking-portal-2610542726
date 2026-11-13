package com.nova.bank.activity;

import com.nova.bank.auth.AuthService;
import com.nova.bank.common.ApiException;
import com.nova.bank.portfolio.PortfolioService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorize ledger reads before executing literal, validated database filters. */
@Service
public class ActivityService {
    private final TransactionRepository transactions;
    private final PortfolioService portfolio;
    private final JdbcTemplate jdbc;
    private final LocalDate demoDate;
    /** Bind persistence and the configured fixture-relative calendar. */
    public ActivityService(TransactionRepository transactions, PortfolioService portfolio, JdbcTemplate jdbc,
            @Value("${nova.demo-date}") LocalDate demoDate) {
        this.transactions = transactions; this.portfolio = portfolio; this.jdbc = jdbc; this.demoDate = demoDate;
    }
    /** Return a safe account snapshot and paginated ledger.
     * Args: principal and admin must originate from verified authentication.
     * Raises: ApiException for invalid filters or unavailable accounts.
     */
    @Transactional(readOnly = true)
    public ActivityDtos.Activity activity(UUID accountId, UUID principal, boolean admin, String search,
            String category, String direction, String startDate, String endDate, int page, int size) {
        var account = portfolio.requireAccount(accountId, principal, admin);
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 255)) throw invalid();
        var cat = enumeration(category, Transaction.Category.class);
        var dir = enumeration(direction, Transaction.Direction.class);
        LocalDate end = date(endDate, demoDate);
        LocalDate start = date(startDate, end.minusDays(29));
        if (start.isAfter(end) || end.getYear() > 9998 || start.getYear() < 1) throw invalid();
        var from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        var until = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        String literal = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = "%" + literal.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = transactions.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("account").get("id"), accountId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("postedAt"), from));
            predicates.add(cb.lessThan(root.get("postedAt"), until));
            if (cat != null) predicates.add(cb.equal(root.get("category"), cat));
            if (dir != null) predicates.add(cb.equal(root.get("direction"), dir));
            if (!literal.isEmpty()) predicates.add(cb.or(cb.like(cb.lower(root.get("description")), pattern, '!'),
                    cb.like(cb.lower(root.get("referenceCode")), pattern, '!')));
            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postedAt", "id")));
        return new ActivityDtos.Activity(account.getId(), account.getAccountName(), account.getMaskedNumber(),
                account.getAccountType(), account.getStatus(), "PHP", decimal(account.getAvailableBalance()),
                decimal(account.getCurrentBalance()), "0.00", decimal(account.getInterestRate()),
                account.getLastStatementDate(), result.getTotalElements(), result.stream().map(Transaction::toItem).toList());
    }
    /** Parse optional enums strictly without echoing submitted values. */
    private static <E extends Enum<E>> E enumeration(String value, Class<E> type) {
        if (value == null || value.isEmpty()) return null;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException e) { throw invalid(); }
    }
    /** Parse strict UTC calendar dates, rejecting timestamps and impossible dates. */
    private static LocalDate date(String value, LocalDate fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        try { return LocalDate.parse(value); } catch (java.time.DateTimeException e) { throw invalid(); }
    }
    /** Preserve the portfolio decimal-string convention. */
    private static String decimal(BigDecimal value) { return value == null ? null : value.setScale(2).toPlainString(); }
    /** Provide safe filter guidance with no submitted content. */
    private static ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "Invalid transaction filters."); }
    /** Insert missing fixture identities without changing runtime ledger rows or balance snapshots. */
    @Transactional
    public void seedTransactions() {
        String[][] rows = {
            {"10","2","2026-10-13T09:30:00Z","Salary credit","INCOME","CREDIT","POSTED","48500.00"},
            {"11","2","2026-10-12T14:15:00Z","Manila Supermarket","GROCERIES","DEBIT","POSTED","2126.40"},
            {"12","2","2026-10-10T11:00:00Z","Savings goal transfer","SAVINGS","DEBIT","POSTED","5000.00"},
            {"13","2","2026-10-09T16:45:00Z","Makati Coffee Shop","DINING","DEBIT","PENDING","185.00"},
            {"14","2","2026-10-01T08:20:00Z","Metro Manila Transit","TRANSPORT","DEBIT","POSTED","1200.00"},
            {"15","3","2026-10-08T21:10:00Z","Unknown merchant","GENERAL","DEBIT","DECLINED","0.00"}
        };
        int reference = 882191;
        for (String[] row : rows) jdbc.update("""
            INSERT INTO transactions(id,account_id,reference_code,posted_at,description,category,direction,status,amount)
            VALUES (?,?,?,?::timestamptz,?,?,?,?,?::numeric) ON CONFLICT(id) DO NOTHING
            """, UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a" + row[0]),
                UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a0" + row[1]), "REF-TX-" + reference++,
                row[2], row[3], row[4], row[5], row[6], row[7]);
    }
    /** Explicitly seed identities then accounts then ledger, regardless of older runner order. */
    @Bean @Order(100)
    ApplicationRunner activitySeed(ActivityService service, AuthService auth, PortfolioService accounts,
            @Value("${nova.seed-on-startup}") boolean enabled) {
        return args -> { if (enabled) { auth.seedUsers(); accounts.seedAccounts(); service.seedTransactions(); } };
    }
}
