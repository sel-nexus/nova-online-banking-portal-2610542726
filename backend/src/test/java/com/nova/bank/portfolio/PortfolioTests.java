package com.nova.bank.portfolio;

import com.nova.bank.TestSupport;
import com.nova.bank.common.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Verify exact business arithmetic and HTTP isolation against the real PostgreSQL fixture. */
class PortfolioTests extends TestSupport {
    private static final UUID MIGUEL = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID ROSA = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
    private static final UUID ADMIN = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13");
    private static final UUID SAVINGS = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");
    @Autowired private PortfolioService portfolio;
    @Autowired private AccountRepository repository;

    /** Restore accounts after identity fixtures and before each test. */
    @Override protected void resetFeatureFixtures() { jdbc.update("DELETE FROM accounts"); portfolio.seedAccounts(); }

    /** Calculate all seeded type semantics without mixing liabilities into deposits. */
    @Test void serviceReturnsExactSixFixturesAndTypeSpecificAmounts() {
        var miguel = portfolio.summary(MIGUEL);
        assertThat(miguel.relationshipBalance()).isEqualTo("142860.25");
        assertThat(miguel.greeting()).isEqualTo("Magandang umaga, Miguel");
        assertThat(miguel.lastLogin().toString()).isEqualTo("2026-10-14T08:10:00Z");
        assertThat(miguel.accounts()).extracting(PortfolioDtos.AccountSummary::maskedNumber)
                .containsExactly("•••• 4821", "•••• 9034", "•••• 1188", "•••• 7712");
        assertThat(miguel.accounts()).extracting(PortfolioDtos.AccountSummary::group)
                .containsExactly("Payroll", "Savings", "Cards", "Loans");
        assertThat(miguel.accounts().get(0).availableBalance()).isEqualTo("26240.15");
        var savings = miguel.accounts().get(1);
        assertThat(savings.availableBalance()).isEqualTo("116620.10");
        assertThat(savings.currentBalance()).isEqualTo("116620.10");
        assertThat(savings.annualInterestRate()).isEqualTo("3.25");
        assertThat(savings.lastStatementDate().toString()).isEqualTo("2026-09-30");
        var card = miguel.accounts().get(2);
        assertThat(card.amountDue()).isEqualTo("8450.00");
        assertThat(card.creditLimit()).isEqualTo("50000.00");
        assertThat(card.availableCredit()).isEqualTo("41550.00");
        assertThat(miguel.accounts().get(3).outstandingBalance()).isEqualTo("2418450.00");
        var rosa = portfolio.summary(ROSA);
        assertThat(rosa.relationshipBalance()).isEqualTo("54300.00");
        assertThat(rosa.accounts()).extracting(PortfolioDtos.AccountSummary::maskedNumber).containsExactly("•••• 2204", "•••• 6640");
        assertThat(rosa.accounts().get(1).outstandingBalance()).isEqualTo("1250000.00");
        assertThat(repository.count()).isEqualTo(6);
        assertThat(portfolio.listAccounts(MIGUEL)).allMatch(a -> a.status().equals("ACTIVE") && a.currency().equals("PHP"));
    }

    /** Preserve negative deposit balances and calculate over-limit credit exactly. */
    @Test void negativeBalancesAndStaleCardAvailableAreHandledExactly() {
        jdbc.update("UPDATE accounts SET available_balance=-0.10 WHERE id=?", SAVINGS);
        jdbc.update("UPDATE accounts SET current_balance=50000.01,available_balance=999.99 WHERE account_type='CREDIT_CARD'");
        assertThat(portfolio.summary(MIGUEL).relationshipBalance()).isEqualTo("26240.05");
        assertThat(portfolio.listAccounts(MIGUEL).get(2).availableCredit()).isEqualTo("-0.01");
    }

    /** Prove repeat seed does not overwrite persisted user balances or duplicate fixtures. */
    @Test void seedIsIdempotentAndRestoresOnlyMissingAccounts() {
        jdbc.update("UPDATE accounts SET available_balance=1.23 WHERE id=?", SAVINGS);
        portfolio.seedAccounts(); portfolio.seedAccounts();
        assertThat(repository.count()).isEqualTo(6);
        assertThat(repository.findById(SAVINGS).orElseThrow().getAvailableBalance()).isEqualByComparingTo("1.23");
        jdbc.update("DELETE FROM accounts WHERE id=?", SAVINGS);
        portfolio.seedAccounts();
        assertThat(repository.findById(SAVINGS).orElseThrow().getAvailableBalance()).isEqualByComparingTo("116620.10");
    }

    /** Enforce owner/admin access and generic failures for later activity service consumers. */
    @Test void requireAccountHasOwnerAdminUnknownAndWrongOwnerBranches() {
        assertThat(portfolio.requireAccount(SAVINGS, MIGUEL, false).getId()).isEqualTo(SAVINGS);
        assertThat(portfolio.requireAccount(SAVINGS, ADMIN, true).getId()).isEqualTo(SAVINGS);
        assertThatThrownBy(() -> portfolio.requireAccount(SAVINGS, ROSA, false))
                .isInstanceOfSatisfying(ApiException.class, e -> { assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN); assertThat(e.getMessage()).isEqualTo("Account Not Available"); });
        assertThatThrownBy(() -> portfolio.requireAccount(UUID.randomUUID(), MIGUEL, false))
                .isInstanceOfSatisfying(ApiException.class, e -> { assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND); assertThat(e.getMessage()).isEqualTo("Account Not Available"); });
    }

    /** Distinguish an existing empty facilitator from an absent customer. */
    @Test void emptyAndMissingCustomerAreDistinct() {
        assertThat(portfolio.summary(ADMIN).relationshipBalance()).isEqualTo("0.00");
        assertThat(portfolio.listAccounts(ADMIN)).isEmpty();
        for (boolean summary : new boolean[]{true, false}) {
            assertThatThrownBy(() -> { if(summary) portfolio.summary(UUID.randomUUID()); else portfolio.listAccounts(UUID.randomUUID()); })
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    /** Check both endpoints' content, masked boundary, persona isolation and no query override. */
    @Test void httpOwnerAndAdminResponsesAreSafeAndExact() throws Exception {
        String miguel = login("miguel"), rosa = login("rosa"), admin = login("admin");
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", "Bearer " + miguel))
                .andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value("142860.25"))
                .andExpect(jsonPath("$.customerName").value("Miguel De La Cruz"))
                .andExpect(jsonPath("$.accounts.length()").value(4)).andExpect(jsonPath("$.accounts[2].availableCredit").value("41550.00"));
        var response = mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + miguel))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].maskedNumber").value("•••• 9034"))
                .andExpect(jsonPath("$[0].accountNumber").doesNotExist()).andReturn().getResponse().getContentAsString();
        for (String number : new String[]{"10928374821", "20938479034", "40928371188", "50928377712"}) assertThat(response).doesNotContain(number);
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", "Bearer " + rosa).param("customerId", MIGUEL.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value("54300.00"))
                .andExpect(jsonPath("$.accounts.length()").value(2));
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + rosa))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].accountName").value("Salary Account")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value("0.00")).andExpect(jsonPath("$.accounts").isEmpty());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    /** Reject anonymous, invalid bearer and authenticated missing-customer reads on both endpoints. */
    @Test void httpAuthenticationAndMissingCustomerFailuresAreStructured() throws Exception {
        for (String path : new String[]{"/api/v1/portfolio/summary", "/api/v1/accounts"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));
            mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
            var principal = new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
            mvc.perform(get(path).with(authentication(principal))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Customer Not Available"));
        }
    }

    /** Recompute masks from private identifiers even when stored masks contain sensitive data. */
    @Test void storedMaskCannotLeakFullIdentifier() throws Exception {
        jdbc.update("UPDATE accounts SET masked_number=account_number WHERE id=?", SAVINGS);
        assertThat(portfolio.listAccounts(MIGUEL).get(1).maskedNumber()).isEqualTo("•••• 9034");
        assertThat(mapper.writeValueAsString(portfolio.summary(MIGUEL))).doesNotContain("20938479034");
    }

    /** Verify real database uniqueness, ownership FK, not-null, enum and numeric constraints. */
    @Test void postgresEnforcesAccountIntegrityAndCascades() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).endsWith("_test");
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET account_number='10928374821' WHERE id=?", SAVINGS)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET user_id=? WHERE id=?", UUID.randomUUID(), SAVINGS)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET account_name=NULL WHERE id=?", SAVINGS)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET account_type='UNKNOWN' WHERE id=?", SAVINGS)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET credit_limit=-1 WHERE id=?", SAVINGS)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET credit_limit=NULL WHERE account_type='CREDIT_CARD'")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET available_balance=10000000000000 WHERE id=?", SAVINGS)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM users WHERE id=?", ROSA);
        assertThat(repository.findByUserIdOrderByIdAsc(ROSA)).isEmpty();
        auth.seedUsers();
    }
}
