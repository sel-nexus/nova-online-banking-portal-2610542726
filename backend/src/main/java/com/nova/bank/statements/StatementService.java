package com.nova.bank.statements;

import com.nova.bank.activity.Transaction;
import com.nova.bank.activity.TransactionRepository;
import com.nova.bank.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Export an authorized account snapshot and its entire persisted ledger. */
@Service
public class StatementService {
    private final PortfolioService portfolio;
    private final TransactionRepository transactions;
    /** Bind the existing ownership and real JPA boundaries. */
    public StatementService(PortfolioService portfolio, TransactionRepository transactions) {
        this.portfolio = portfolio; this.transactions = transactions;
    }
    /** Carry only masked text and a last-four attachment name across the HTTP boundary. */
    public record Download(String filename, String content) {}
    /** Read all rows in one transaction, independent of activity dates, filters and pagination.
     * Args: principal and admin come only from verified authentication.
     * Raises: ApiException when the account is missing or not owned.
     */
    @Transactional(readOnly = true)
    public Download download(UUID accountId, UUID principal, boolean admin) {
        var account = portfolio.requireAccount(accountId, principal, admin);
        var rows = transactions.findAll((root, query, cb) -> cb.equal(root.get("account").get("id"), accountId),
                Sort.by(Sort.Direction.DESC, "postedAt", "id"));
        String mask = account.getMaskedNumber();
        StringBuilder text = new StringBuilder("Nova Bank — Demo Statement\n");
        text.append("Account: ").append(line(account.getAccountName())).append("\n")
            .append("Account number: ").append(mask).append("\n")
            .append("Account type: ").append(account.getAccountType()).append("\n")
            .append("Status: ").append(line(account.getStatus())).append("\n")
            .append("Currency: PHP\n")
            .append("Available balance: PHP ").append(decimal(account.getAvailableBalance())).append("\n")
            .append("Current / ledger balance: PHP ").append(decimal(account.getCurrentBalance())).append("\n")
            .append("Pending transactions: PHP 0.00\n")
            .append("Annual interest rate: ").append(account.getInterestRate() == null ? "Not applicable" : decimal(account.getInterestRate()) + "% p.a.").append("\n")
            .append("Last statement date: ").append(account.getLastStatementDate() == null ? "Not available" : account.getLastStatementDate()).append("\n")
            .append("Activity scope: All account activity (UTC), newest first; no filters or pagination.\n")
            .append("Transaction count: ").append(rows.size()).append("\n\nTransactions\n");
        if (rows.isEmpty()) text.append("No account activity.\n");
        for (Transaction row : rows) {
            var item = row.toItem();
            text.append("Reference: ").append(item.referenceCode()).append("\n")
                .append("Posted (UTC): ").append(item.postedAt()).append("\n")
                .append("Description: ").append(line(item.description())).append("\n")
                .append("Category: ").append(item.category()).append("\n")
                .append("Direction: ").append(item.direction()).append("\n")
                .append("Status: ").append(item.status()).append("\n")
                .append("Amount: PHP ").append(item.amount()).append("\n\n");
        }
        text.append("Demo notice: This is a demonstration statement, not an official bank statement. No real money was moved.\n");
        return new Download("statement-" + mask.substring(mask.length() - 4) + "-demo.txt", text.toString());
    }
    /** Keep free text on one structured line without control-character injection. */
    private static String line(String value) { return value.replaceAll("[\\p{Cntrl}]", " "); }
    /** Preserve exact database-scale amounts without rounding or scientific notation. */
    private static String decimal(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
}
