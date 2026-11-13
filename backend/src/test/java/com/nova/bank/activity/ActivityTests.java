package com.nova.bank.activity;

import com.nova.bank.TestSupport;
import com.nova.bank.common.ApiException;
import com.nova.bank.portfolio.PortfolioService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise real JPA queries, HTTP security, state transitions and PostgreSQL constraints. */
class ActivityTests extends TestSupport {
    static final UUID SAVINGS = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");
    static final UUID MIGUEL = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    @Autowired ActivityService service;
    @Autowired PortfolioService portfolio;
    /** Restore only this slice's fixtures in the isolated database. */
    @Override protected void resetFeatureFixtures() {
        portfolio.seedAccounts(); jdbc.update("DELETE FROM transactions"); service.seedTransactions();
    }
    /** Query the service directly rather than asserting DTO constructors. */
    ActivityDtos.Activity read(String search, String category, String direction, String start, String end, int page, int size) {
        return service.activity(SAVINGS, MIGUEL, false, search, category, direction, start, end, page, size);
    }
    @Test void defaultReadReturnsExactSafeSnapshotAndAllFiveRows() throws Exception {
        var result = read(null,null,null,null,null,0,20);
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.availableBalance()).isEqualTo("116620.10");
        assertThat(result.currentBalance()).isEqualTo("116620.10");
        assertThat(result.pendingAmount()).isEqualTo("0.00");
        assertThat(result.annualInterestRate()).isEqualTo("3.25");
        assertThat(result.maskedNumber()).isEqualTo("•••• 9034");
        assertThat(result.transactions()).extracting(ActivityDtos.Item::amount).containsExactly("48500.00","2126.40","5000.00","185.00","1200.00");
        assertThat(result.transactions().getFirst().postedAt().toString()).isEqualTo("2026-10-13T09:30:00Z");
        assertThat(mapper.writeValueAsString(result)).doesNotContain("20938479034", "userId", "accountNumber");
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization","Bearer "+login("miguel")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(5)).andExpect(jsonPath("$.transactions[0].referenceCode").value("REF-TX-882191"));
    }
    @Test void allOptionalFilterCombinationsAreAppliedTogether() {
        for (int mask=0;mask<128;mask++) {
            String search=(mask&1)!=0 ? "REF-TX-882194" : null;
            String category=(mask&2)!=0 ? "DINING" : null;
            String direction=(mask&4)!=0 ? "DEBIT" : null;
            String start=(mask&8)!=0 ? "2026-10-09" : null;
            String end=(mask&16)!=0 ? "2026-10-09" : null;
            int page=(mask&32)!=0 ? 1 : 0;
            int size=(mask&64)!=0 ? 1 : 20;
            var result=read(search,category,direction,start,end,page,size);
            var expected=new java.util.ArrayList<String>();
            String[] references={"REF-TX-882191","REF-TX-882192","REF-TX-882193","REF-TX-882194","REF-TX-882195"};
            String[] categories={"INCOME","GROCERIES","SAVINGS","DINING","TRANSPORT"};
            String[] dates={"2026-10-13","2026-10-12","2026-10-10","2026-10-09","2026-10-01"};
            for(int row=0;row<5;row++) {
                if(search!=null && !references[row].equals(search)) continue;
                if(category!=null && !categories[row].equals(category)) continue;
                if(direction!=null && row==0) continue;
                if(start!=null && dates[row].compareTo(start)<0) continue;
                if(end!=null && dates[row].compareTo(end)>0) continue;
                expected.add(references[row]);
            }
            assertThat(result.totalCount()).as("combination %s",mask).isEqualTo(expected.size());
            var pageItems=expected.stream().skip((long)page*size).limit(size).toList();
            assertThat(result.transactions()).extracting(ActivityDtos.Item::referenceCode).containsExactlyElementsOf(pageItems);
        }
    }
    @Test void allSixSeedIdsReferencesTimesAndStatusesMatchCanonicalLedger() {
        String[] times={"2026-10-13T09:30:00Z","2026-10-12T14:15:00Z","2026-10-10T11:00:00Z","2026-10-09T16:45:00Z","2026-10-01T08:20:00Z","2026-10-08T21:10:00Z"};
        String[] amounts={"48500.00","2126.40","5000.00","185.00","1200.00","0.00"};
        for(int index=0;index<6;index++) {
            var item=repository.findById(UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+(10+index))).orElseThrow().toItem();
            assertThat(item.referenceCode()).isEqualTo("REF-TX-"+(882191+index));
            assertThat(item.postedAt().toString()).isEqualTo(times[index]); assertThat(item.amount()).isEqualTo(amounts[index]);
            assertThat(item.status()).isEqualTo(index==3?Transaction.Status.PENDING:index==5?Transaction.Status.DECLINED:Transaction.Status.POSTED);
        }
    }
    @ParameterizedTest @CsvSource({"INCOME,1","GROCERIES,1","SAVINGS,1","DINING,1","TRANSPORT,1","GENERAL,0"})
    void categoryIndividuallyFilters(String category,int count) { assertThat(read(null,category,null,null,null,0,20).totalCount()).isEqualTo(count); }
    @ParameterizedTest @CsvSource({"CREDIT,1","DEBIT,4"})
    void directionIndividuallyFilters(String direction,int count) { assertThat(read(null,null,direction,null,null,0,20).totalCount()).isEqualTo(count); }
    @ParameterizedTest @CsvSource({"salary,1","SUPERMARKET,1","882194,1","missing,0"})
    void searchMatchesDescriptionOrReferenceCaseInsensitively(String search,int count) { assertThat(read(search,null,null,null,null,0,20).totalCount()).isEqualTo(count); }
    @Test void everyParameterCombinesAndPaginationRetainsFilteredCount() throws Exception {
        var result=read("REF-TX","GROCERIES","DEBIT","2026-10-12","2026-10-12",0,1);
        assertThat(result.totalCount()).isEqualTo(1); assertThat(result.transactions().getFirst().description()).isEqualTo("Manila Supermarket");
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization","Bearer "+login("miguel"))
            .param("search","REF-TX").param("category","GROCERIES").param("direction","DEBIT").param("startDate","2026-10-12").param("endDate","2026-10-12").param("page","0").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.transactions[0].amount").value("2126.40"));
    }
    @Test void datesIndividuallyAndTogetherUseInclusiveUtcDays() {
        assertThat(read(null,null,null,"2026-10-10",null,0,20).totalCount()).isEqualTo(3);
        assertThat(read(null,null,null,null,"2026-10-10",0,20).totalCount()).isEqualTo(3);
        assertThat(read(null,null,null,"2026-10-09","2026-10-09",0,20).totalCount()).isEqualTo(1);
        assertThat(read(null,null,null,"2026-11-01","2026-11-30",0,20).transactions()).isEmpty();
    }
    @Test void paginationHandlesFirstLastBeyondAndMaximumSize() {
        assertThat(read(null,null,null,null,null,0,1).transactions().getFirst().amount()).isEqualTo("48500.00");
        assertThat(read(null,null,null,null,null,4,1).transactions().getFirst().amount()).isEqualTo("1200.00");
        assertThat(read(null,null,null,null,null,5,1).transactions()).isEmpty();
        assertThat(read(null,null,null,null,null,1,2).transactions()).extracting(ActivityDtos.Item::amount).containsExactly("5000.00","185.00");
        assertThat(read("","","",null,null,0,100).transactions()).hasSize(5);
    }
    @ParameterizedTest @ValueSource(strings={"%","_","!","' OR 1=1 --","<script>alert(1)</script>","../../etc/passwd"})
    void hostileSearchIsLiteralAndDoesNotLeakRows(String input) throws Exception {
        assertThat(read(input,null,null,null,null,0,20).totalCount()).isZero();
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization","Bearer "+login("miguel")).param("search",input))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
    }
    @Test void literalWildcardCanFindPersistedMerchantAndXssRemainsData() {
        jdbc.update("UPDATE transactions SET description=? WHERE reference_code='REF-TX-882191'", "100%_! <script>alert(1)</script>");
        assertThat(read("%_!",null,null,null,null,0,20).totalCount()).isEqualTo(1);
        assertThat(read("<script>",null,null,null,null,0,20).transactions().getFirst().description()).contains("<script>");
    }
    @ParameterizedTest @CsvSource({"page,-1","page,abc","page,2147483648","size,0","size,101","size,abc","category,OTHER","direction,IN","startDate,2026-02-30","endDate,2026-10-14T00:00:00Z","startDate,../../etc/passwd","endDate,9999-12-31"})
    void malformedFiltersReturn400(String key,String value) throws Exception {
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization","Bearer "+login("miguel")).param(key,value))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
    @Test void directServiceRejectsEveryValidationBranch() {
        assertThatThrownBy(()->read("x".repeat(256),null,null,null,null,0,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->read(null,"other",null,null,null,0,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->read(null,null,"other",null,null,0,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->read(null,null,null,"bad",null,0,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->read(null,null,null,"2026-02-30",null,0,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->read(null,null,null,"2026-10-15","2026-10-14",0,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->read(null,null,null,"0000-01-01",null,0,20)).isInstanceOf(ApiException.class);
        for(int[] pair:new int[][]{{-1,20},{0,0},{0,101}}) assertThatThrownBy(()->read(null,null,null,null,null,pair[0],pair[1])).isInstanceOf(ApiException.class);
    }
    @Test void malformedUuidOversizedSearchAndReversedDatesAre400() throws Exception {
        String token=login("miguel");
        mvc.perform(get("/api/v1/accounts/not-a-uuid/transactions").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization","Bearer "+token).param("search","x".repeat(256))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization","Bearer "+token).param("startDate","2026-10-15").param("endDate","2026-10-14")).andExpect(status().isBadRequest());
    }
    @Test void ownerAdminUnknownAnonymousRevokedAndOtherCustomerAreEnforced() throws Exception {
        var rosa=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
        assertThatThrownBy(()->service.activity(SAVINGS,rosa,false,null,null,null,null,null,0,20)).isInstanceOf(ApiException.class).hasMessage("Account Not Available");
        assertThat(service.activity(SAVINGS,rosa,true,null,null,null,null,null,0,20).totalCount()).isEqualTo(5);
        assertThatThrownBy(()->service.activity(UUID.randomUUID(),MIGUEL,false,null,null,null,null,null,0,20)).isInstanceOf(ApiException.class).hasMessage("Account Not Available");
        String path="/api/v1/accounts/"+SAVINGS+"/transactions";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        var forbidden=mvc.perform(get(path).header("Authorization","Bearer "+login("rosa"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Account Not Available")).andReturn();
        assertThat(forbidden.getResponse().getContentAsString()).doesNotContain("Peso","9034","116620");
        mvc.perform(get(path).header("Authorization","Bearer "+login("admin"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/"+UUID.randomUUID()+"/transactions").header("Authorization","Bearer "+login("miguel"))).andExpect(status().isNotFound());
        String token=login("miguel");mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void emptyAccountAndDeclinedCardPreserveNullableRateAndStatuses() {
        var empty=service.activity(UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"),MIGUEL,false,null,null,null,null,null,0,20);
        assertThat(empty.transactions()).isEmpty();assertThat(empty.annualInterestRate()).isNull();
        var card=service.activity(UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a03"),MIGUEL,false,null,"GENERAL","DEBIT",null,null,0,20);
        assertThat(card.transactions().getFirst().status()).isEqualTo(Transaction.Status.DECLINED);
        assertThat(card.transactions().getFirst().amount()).isEqualTo("0.00");
    }
    @Test void repeatSeedDoesNotOverwriteRuntimeRowsAndQueriesReflectTransitions() {
        jdbc.update("UPDATE transactions SET status='POSTED',amount=200.01 WHERE reference_code='REF-TX-882194'");
        service.seedTransactions();service.seedTransactions();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions",Integer.class)).isEqualTo(6);
        assertThat(read("882194",null,null,null,null,0,20).transactions().getFirst().amount()).isEqualTo("200.01");
        assertThat(read("882194",null,null,null,null,0,20).transactions().getFirst().status()).isEqualTo(Transaction.Status.POSTED);
        assertThat(read(null,null,null,null,null,0,20).pendingAmount()).isEqualTo("0.00");
    }
    @Test void utcMidnightAndFinalMicrosecondAreIncludedNextDayExcludedAndTiesStable() {
        jdbc.update("UPDATE transactions SET posted_at='2026-10-14T00:00:00Z' WHERE reference_code='REF-TX-882191'");
        jdbc.update("UPDATE transactions SET posted_at='2026-10-14T23:59:59.999999Z' WHERE reference_code='REF-TX-882192'");
        jdbc.update("UPDATE transactions SET posted_at='2026-10-15T00:00:00Z' WHERE reference_code='REF-TX-882193'");
        assertThat(read(null,null,null,"2026-10-14","2026-10-14",0,20).transactions()).extracting(ActivityDtos.Item::referenceCode).containsExactly("REF-TX-882192","REF-TX-882191");
        jdbc.update("UPDATE transactions SET posted_at='2026-10-14T00:00:00Z' WHERE reference_code='REF-TX-882192'");
        assertThat(read(null,null,null,"2026-10-14","2026-10-14",0,20).transactions()).extracting(ActivityDtos.Item::referenceCode).containsExactly("REF-TX-882192","REF-TX-882191");
    }
    @ParameterizedTest @ValueSource(strings={"category='OTHER'","direction='IN'","status='DONE'","amount=-1","amount=10000000000000","description=NULL","posted_at=NULL","account_id=NULL","reference_code=NULL","reference_code='REF-TX-882192'","account_id='00000000-0000-0000-0000-000000000000'"})
    void databaseRejectsInvalidLedgerState(String assignment) {
        assertThatThrownBy(()->jdbc.update("UPDATE transactions SET "+assignment+" WHERE reference_code='REF-TX-882191'"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(read(null,null,null,null,null,0,20).transactions()).hasSize(5);
    }
    @Test void disabledStartupDoesNotSeedAndConfiguredCalendarControlsDefaults() throws Exception {
        jdbc.update("DELETE FROM transactions");
        service.activitySeed(service,auth,portfolio,false).run(new org.springframework.boot.DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions",Integer.class)).isZero();
        service.seedTransactions();
        var future = new ActivityService(repository,portfolio,jdbc,java.time.LocalDate.parse("2027-01-20"));
        assertThat(future.activity(SAVINGS,MIGUEL,false,null,null,null,null,null,0,20).totalCount()).isZero();
    }
    @Autowired TransactionRepository repository;
    @Test void ledgerIdentityEqualityUsesOnlyStablePersistedIds() {
        var rows=repository.findAll();var first=rows.getFirst();
        assertThat(first).isEqualTo(repository.findById(first.toItem().id()).orElseThrow());
        assertThat(first.hashCode()).isEqualTo(first.toItem().id().hashCode());
        assertThat(first).isNotEqualTo(rows.get(1)).isNotEqualTo("unrelated").isNotEqualTo(new Transaction());
        var empty=new Transaction();assertThat(empty.equals(empty)).isFalse();assertThat(empty.hashCode()).isZero();
    }
    @Test void numericScaleIsExactAndAccountDeleteCascadesLedger() {
        jdbc.update("UPDATE transactions SET amount=12.345 WHERE reference_code='REF-TX-882191'");
        assertThat(read("882191",null,null,null,null,0,20).transactions().getFirst().amount()).isEqualTo("12.35");
        jdbc.update("DELETE FROM accounts WHERE id=?",SAVINGS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_id=?",Integer.class,SAVINGS)).isZero();
        portfolio.seedAccounts();service.seedTransactions();assertThat(read(null,null,null,null,null,0,20).totalCount()).isEqualTo(5);
    }
}
