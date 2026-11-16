package com.nova.bank.statements;

import com.nova.bank.TestSupport;
import com.nova.bank.activity.ActivityService;
import com.nova.bank.activity.TransactionRepository;
import com.nova.bank.common.ApiException;
import com.nova.bank.portfolio.PortfolioService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verify actual transactional exports and HTTP security against PostgreSQL. */
class StatementTests extends TestSupport {
    static final UUID SAVINGS = account(2);
    static final UUID MIGUEL = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    static final UUID ROSA = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
    @Autowired StatementService service;
    @Autowired PortfolioService portfolio;
    @Autowired ActivityService activity;
    @Autowired TransactionRepository repository;
    /** Select a canonical account fixture. */
    static UUID account(int index) { return UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a0" + index); }
    /** Restore canonical balances and ledger without changing shared test infrastructure. */
    @Override protected void resetFeatureFixtures() {
        jdbc.update("DELETE FROM transactions"); jdbc.update("DELETE FROM accounts");
        portfolio.seedAccounts(); activity.seedTransactions();
    }
    /** Independently specify the exact savings export including every ledger field. */
    String savingsText() {
        return """
            Nova Bank — Demo Statement
            Account: Peso Savings
            Account number: •••• 9034
            Account type: DEPOSIT
            Status: ACTIVE
            Currency: PHP
            Available balance: PHP 116620.10
            Current / ledger balance: PHP 116620.10
            Pending transactions: PHP 0.00
            Annual interest rate: 3.25% p.a.
            Last statement date: 2026-09-30
            Activity scope: All account activity (UTC), newest first; no filters or pagination.
            Transaction count: 5

            Transactions
            Reference: REF-TX-882191
            Posted (UTC): 2026-10-13T09:30:00Z
            Description: Salary credit
            Category: INCOME
            Direction: CREDIT
            Status: POSTED
            Amount: PHP 48500.00

            Reference: REF-TX-882192
            Posted (UTC): 2026-10-12T14:15:00Z
            Description: Manila Supermarket
            Category: GROCERIES
            Direction: DEBIT
            Status: POSTED
            Amount: PHP 2126.40

            Reference: REF-TX-882193
            Posted (UTC): 2026-10-10T11:00:00Z
            Description: Savings goal transfer
            Category: SAVINGS
            Direction: DEBIT
            Status: POSTED
            Amount: PHP 5000.00

            Reference: REF-TX-882194
            Posted (UTC): 2026-10-09T16:45:00Z
            Description: Makati Coffee Shop
            Category: DINING
            Direction: DEBIT
            Status: PENDING
            Amount: PHP 185.00

            Reference: REF-TX-882195
            Posted (UTC): 2026-10-01T08:20:00Z
            Description: Metro Manila Transit
            Category: TRANSPORT
            Direction: DEBIT
            Status: POSTED
            Amount: PHP 1200.00

            Demo notice: This is a demonstration statement, not an official bank statement. No real money was moved.
            """;
    }
    @Test void serviceAndHttpReturnExactSavingsUtf8AttachmentAndNoPrivateIdentifier() throws Exception {
        var result = service.download(SAVINGS,MIGUEL,false);
        assertThat(result.filename()).isEqualTo("statement-9034-demo.txt");
        assertThat(result.content()).isEqualTo(savingsText());
        for (String user : new String[]{"miguel","admin"}) {
            var response = mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/statement/download")
                .header("Authorization","Bearer "+login(user))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type","text/plain;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition","attachment; filename=\"statement-9034-demo.txt\""))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
            assertThat(response.getContentAsByteArray()).isEqualTo(savingsText().getBytes(StandardCharsets.UTF_8));
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("20938479034","40928371188","userId","accountNumber");
            response.getHeaderNames().forEach(name -> assertThat(response.getHeader(name)).doesNotContain("20938479034"));
        }
    }
    @ParameterizedTest @CsvSource({"1,miguel,4821,Payroll Account,26240.15","4,miguel,7712,Home Loan,2418450.00","5,rosa,2204,Salary Account,54300.00","6,rosa,6640,Home Loan,1250000.00"})
    void emptyAccountsProduceStructuredExactMaskedStatements(int index,String owner,String last4,String name,String balance) throws Exception {
        var result=service.download(account(index),owner.equals("rosa")?ROSA:MIGUEL,false);
        assertThat(result.filename()).isEqualTo("statement-"+last4+"-demo.txt");
        assertThat(result.content()).contains("Account: "+name+"\n","Account number: •••• "+last4+"\n",
            "Current / ledger balance: PHP "+balance+"\n","Pending transactions: PHP 0.00\n",
            "Annual interest rate: Not applicable\n","Transaction count: 0\n\nTransactions\nNo account activity.\n","Demo notice:");
        mvc.perform(get("/api/v1/accounts/"+account(index)+"/statement/download").header("Authorization","Bearer "+login(owner)))
            .andExpect(status().isOk()).andExpect(content().bytes(result.content().getBytes(StandardCharsets.UTF_8)))
            .andExpect(header().string("Content-Disposition","attachment; filename=\""+result.filename()+"\""));
    }
    @Test void cardIncludesSixthFixtureWithFullReferenceUtcAndDeclinedExactZero() throws Exception {
        var text=service.download(account(3),MIGUEL,false).content();
        assertThat(text).contains("Account number: •••• 1188\n","Available balance: PHP 41550.00\n",
            "Current / ledger balance: PHP 8450.00\n","Transaction count: 1\n",
            "Reference: REF-TX-882196\nPosted (UTC): 2026-10-08T21:10:00Z\nDescription: Unknown merchant\nCategory: GENERAL\nDirection: DEBIT\nStatus: DECLINED\nAmount: PHP 0.00\n");
        mvc.perform(get("/api/v1/accounts/"+account(3)+"/statement/download").header("Authorization","Bearer "+login("miguel")))
            .andExpect(status().isOk()).andExpect(content().bytes(text.getBytes(StandardCharsets.UTF_8)))
            .andExpect(header().string("Content-Disposition","attachment; filename=\"statement-1188-demo.txt\""));
    }
    @Test void allPersistedRowsOutsideDatesAndBeyondPaginationAreExportedAndRequeried() throws Exception {
        for(int i=0;i<105;i++) {
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO transactions(id,account_id,reference_code,posted_at,description,category,direction,status,amount) VALUES (?,?,?,'2020-01-01T00:00:00Z','Old ledger','GENERAL','CREDIT','POSTED',123.45)",id,SAVINGS,"OLD-REF-"+i);
            var stored=repository.findById(id).orElseThrow().toItem();
            assertThat(stored.referenceCode()).isEqualTo("OLD-REF-"+i); assertThat(stored.amount()).isEqualTo("123.45");
            assertThat(stored.postedAt().toString()).isEqualTo("2020-01-01T00:00:00Z");
        }
        var text=service.download(SAVINGS,MIGUEL,false).content();
        assertThat(text).contains("Transaction count: 110\n");
        for(int i=0;i<105;i++) assertThat(text).contains("Reference: OLD-REF-"+i+"\n");
        var response=mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/statement/download").header("Authorization","Bearer "+login("miguel"))
            .param("page","99").param("size","1").param("startDate","2030-01-01").param("search","not found").param("category","DINING"))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_id=?",Integer.class,SAVINGS)).isEqualTo(110);
    }
    @Test void missingAndOtherOwnerFailClosedInRealServiceAndHttp() throws Exception {
        UUID missing=UUID.randomUUID();
        assertThatThrownBy(()->service.download(missing,MIGUEL,false)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(404));
        assertThatThrownBy(()->service.download(SAVINGS,ROSA,false)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(403));
        assertThat(service.download(SAVINGS,ROSA,true).content()).isEqualTo(savingsText());
        for(var entry:java.util.Map.of(missing,404,SAVINGS,403).entrySet()) {
            var response=mvc.perform(get("/api/v1/accounts/"+entry.getKey()+"/statement/download").header("Authorization","Bearer "+login("rosa")))
                .andExpect(status().is(entry.getValue())).andExpect(jsonPath("$.message").value("Account Not Available")).andReturn().getResponse();
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsString()).doesNotContain("9034","Peso","116620","20938479034");
        }
    }
    @Test void anonymousInvalidRevokedAndMalformedUuidAreRejectedWithoutAttachment() throws Exception {
        String path="/api/v1/accounts/"+SAVINGS+"/statement/download";
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Content-Disposition"));
        mvc.perform(get(path).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        String token=login("miguel");
        mvc.perform(get("/api/v1/accounts/not-a-uuid/statement/download").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_revocations",Integer.class)).isEqualTo(1);
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Content-Disposition"));
    }
    @Test void nullableDateAndControlCharactersAreFormattedAsSafeStructuredLines() {
        jdbc.update("UPDATE accounts SET last_statement_date=NULL WHERE id=?",SAVINGS);
        jdbc.update("UPDATE transactions SET description=? WHERE reference_code='REF-TX-882191'","Salary\nInjected\ttext");
        assertThat(service.download(SAVINGS,MIGUEL,false).content()).contains("Last statement date: Not available\n","Description: Salary Injected text\n");
    }
}
