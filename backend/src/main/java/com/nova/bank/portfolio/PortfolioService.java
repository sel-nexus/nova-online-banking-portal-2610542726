package com.nova.bank.portfolio;

import com.nova.bank.auth.AuthService;
import com.nova.bank.auth.User;
import com.nova.bank.auth.UserRepository;
import com.nova.bank.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculate exact owner-bound portfolio views and provision repeatable fixtures. */
@Service
public class PortfolioService {
    private final AccountRepository accounts;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    /** Bind persisted account and customer boundaries. */
    public PortfolioService(AccountRepository accounts, UserRepository users, JdbcTemplate jdbc) {
        this.accounts = accounts; this.users = users; this.jdbc = jdbc;
    }

    /** Sum available deposits only; missing customers fail without profile metadata. */
    @Transactional(readOnly = true)
    public PortfolioDtos.Summary summary(UUID principal) {
        User user = requireCustomer(principal);
        List<Account> owned = accounts.findByUserIdOrderByIdAsc(principal);
        BigDecimal balance = owned.stream().filter(a -> a.getAccountType() == Account.Type.DEPOSIT)
                .map(Account::getAvailableBalance).reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new PortfolioDtos.Summary(user.getFullName(), "Magandang umaga, " + user.getFullName().split(" ")[0],
                user.getLastLogin(), decimal(balance), "PHP", owned.stream().map(this::toSummary).toList());
    }

    /** List only the verified identity's accounts, including an empty facilitator portfolio. */
    @Transactional(readOnly = true)
    public List<PortfolioDtos.AccountSummary> listAccounts(UUID principal) {
        requireCustomer(principal);
        return accounts.findByUserIdOrderByIdAsc(principal).stream().map(this::toSummary).toList();
    }

    /** Authorize later activity access: unknown is 404, another owner's account is 403.
     * Args: accountId identifies the resource; principal is the verified UUID; admin comes from ROLE_ADMIN.
     * Returns: the entity for internal transactional activity use only.
     * Raises: ApiException with a safe generic message, never account metadata.
     */
    @Transactional(readOnly = true)
    public Account requireAccount(UUID accountId, UUID principal, boolean admin) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account Not Available"));
        if (!admin && !account.getUserId().equals(principal))
            throw new ApiException(HttpStatus.FORBIDDEN, "Account Not Available");
        return account;
    }

    /** Reject an absent customer rather than interpreting it as a valid empty portfolio. */
    private User requireCustomer(UUID principal) {
        return users.findById(principal).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer Not Available"));
    }

    /** Derive card credit and loan outstanding independently of deposit totals. */
    private PortfolioDtos.AccountSummary toSummary(Account account) {
        boolean card = account.getAccountType() == Account.Type.CREDIT_CARD;
        boolean loan = account.getAccountType() == Account.Type.LOAN;
        String group = switch (account.getAccountType()) {
            case CREDIT_CARD -> "Cards";
            case LOAN -> "Loans";
            case DEPOSIT -> account.getAccountName().contains("Savings") ? "Savings" : "Payroll";
        };
        return new PortfolioDtos.AccountSummary(account.getId(), account.getAccountName(), account.getAccountType(),
                group, account.getMaskedNumber(), account.getStatus(), "PHP", decimal(account.getAvailableBalance()),
                decimal(account.getCurrentBalance()), decimal(account.getCreditLimit()),
                card ? decimal(account.getCurrentBalance()) : null,
                card ? decimal(account.getCreditLimit().subtract(account.getCurrentBalance())) : null,
                loan ? decimal(account.getCurrentBalance()) : null,
                decimal(account.getInterestRate()), account.getLastStatementDate());
    }

    /** Serialize exact database-scale decimals without scientific notation or floating point. */
    private static String decimal(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** Insert only absent fixture IDs; existing balances survive repeated starts. */
    @Transactional
    public void seedAccounts() {
        String[][] fixtures = {
            {"1", "1", "10928374821", "Payroll Account", "DEPOSIT", "26240.15", "26240.15", null, null},
            {"2", "1", "20938479034", "Peso Savings", "DEPOSIT", "116620.10", "116620.10", null, "3.25"},
            {"3", "1", "40928371188", "Rewards Credit Card", "CREDIT_CARD", "41550.00", "8450.00", "50000.00", null},
            {"4", "1", "50928377712", "Home Loan", "LOAN", "0.00", "2418450.00", null, null},
            {"5", "2", "10928372204", "Salary Account", "DEPOSIT", "54300.00", "54300.00", null, null},
            {"6", "2", "50928376640", "Home Loan", "LOAN", "0.00", "1250000.00", null, null}
        };
        for (String[] f : fixtures) {
            jdbc.update("""
                INSERT INTO accounts(id,user_id,account_number,masked_number,account_name,account_type,
                    available_balance,current_balance,credit_limit,interest_rate,last_statement_date)
                VALUES (?,?,?,?,?,?,?::numeric,?::numeric,?::numeric,?::numeric,DATE '2026-09-30')
                ON CONFLICT(id) DO NOTHING
                """, UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a0" + f[0]),
                    UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a1" + f[1]), f[2],
                    "•••• " + f[2].substring(f[2].length() - 4), f[3], f[4], f[5], f[6], f[7], f[8]);
        }
    }

    /** Run late after schema initialization; ensure identity fixtures precede account fixtures.
     * Notes: the existing identity runner is unordered, so the idempotent identity call makes ordering explicit.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    ApplicationRunner portfolioSeed(PortfolioService service, AuthService auth,
            @Value("${nova.seed-on-startup}") boolean enabled) {
        return args -> { if (enabled) { auth.seedUsers(); service.seedAccounts(); } };
    }
}
