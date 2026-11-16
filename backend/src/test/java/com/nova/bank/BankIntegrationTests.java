package com.nova.bank;

import com.nova.bank.admin.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verify identity, ownership and persisted activity across the complete HTTP feature chain. */
public class BankIntegrationTests extends TestSupport {
    @Autowired AdminService admin;
    private static final String SAVINGS="b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02";
    private static final String OFFER="d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a20";

    /** Restore all domains through the production reset before each independent journey. */
    @Override protected void resetFeatureFixtures() {
        try { admin.reset(login("admin")); }
        catch (Exception error) { throw new IllegalStateException("Integration fixture reset failed",error); }
    }

    /** Carry one verified customer through portfolio, ledger, statement, offers, support and chat. */
    @Test void customerJourneyPreservesIdentityAcrossDomains() throws Exception {
        String token=login("miguel"), bearer="Bearer "+token;
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value("142860.25"))
            .andExpect(jsonPath("$.accounts.length()").value(4));
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").param("direction","CREDIT").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.transactions[0].amount").value("48500.00"));
        var statement=mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/statement/download").header("Authorization",bearer))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(statement).contains("REF-TX-882191","REF-TX-882195","116620.10").doesNotContain("20938479034");
        mvc.perform(post("/api/v1/promotions/"+OFFER+"/express-interest").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.promotionId").value(OFFER));
        mvc.perform(get("/api/v1/promotions/"+OFFER).header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.interested").value(true));
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization",bearer).contentType("application/json")
            .content("{\"topic\":\"CARDS\",\"subject\":\"Card help\",\"message\":\"Please explain available credit.\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.referenceNumber").value("NB-99214"));
        mvc.perform(post("/api/v1/chat/query").header("Authorization",bearer).contentType("application/json").content("{\"message\":\"Account Balance\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("142,860.25")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests i JOIN users u ON i.user_id=u.id WHERE u.username='miguel'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_inquiries i JOIN users u ON i.user_id=u.id WHERE u.username='miguel' AND reference_number='NB-99214'",Integer.class)).isEqualTo(1);
    }

    /** Reset persisted writes and revoke customer authority without invalidating facilitator access. */
    @Test void resetClearsCrossFeatureActivityAndRevokesAllCustomerReads() throws Exception {
        String customer=login("miguel"), facilitator=login("admin");
        mvc.perform(post("/api/v1/promotions/"+OFFER+"/express-interest").header("Authorization","Bearer "+customer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+customer).contentType("application/json").content("{\"topic\":\"GENERAL\",\"subject\":\"Demo\",\"message\":\"Reset this simulated enquiry.\"}")).andExpect(status().isCreated());
        jdbc.update("UPDATE accounts SET available_balance=1 WHERE id=?::uuid",SAVINGS);
        int before=jdbc.queryForObject("SELECT count(*) FROM audit_events",Integer.class);
        mvc.perform(post("/api/v1/admin/reset").header("Authorization","Bearer "+facilitator)).andExpect(status().isOk());
        for(String path:new String[]{"/portfolio/summary","/accounts","/accounts/"+SAVINGS+"/transactions","/accounts/"+SAVINGS+"/statement/download","/promotions","/support/content"})
            mvc.perform(get("/api/v1"+path).header("Authorization","Bearer "+customer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/chat/query").header("Authorization","Bearer "+customer).contentType("application/json").content("{\"message\":\"Card Help\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+facilitator)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_inquiries",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT available_balance FROM accounts WHERE id=?::uuid",java.math.BigDecimal.class,SAVINGS)).isEqualByComparingTo("116620.10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events",Integer.class)).isGreaterThan(before);
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+login("miguel"))).andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value("142860.25"));
    }

    /** Denied cross-customer reads and writes leave both customers' domain data unchanged. */
    @Test void ownershipErrorsDoNotCreateInterestOrLeakLedger() throws Exception {
        String rosa="Bearer "+login("rosa");
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/transactions").header("Authorization",rosa)).andExpect(status().isForbidden()).andExpect(jsonPath("$.accountName").doesNotExist());
        mvc.perform(get("/api/v1/accounts/"+SAVINGS+"/statement/download").header("Authorization",rosa)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/promotions/"+OFFER+"/express-interest").header("Authorization",rosa)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization",rosa)).andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value("54300.00")).andExpect(jsonPath("$.accounts.length()").value(2));
        mvc.perform(post("/api/v1/chat/query").header("Authorization",rosa).contentType("application/json").content("{\"message\":\"Card Help\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("No active credit cards were found for your profile."));
    }

    /** Simulated upstream errors are isolated and recover without losing persisted interest. */
    @Test void simulationRecoveryPreservesSharedState() throws Exception {
        String customer=login("miguel"), facilitator=login("admin");
        mvc.perform(post("/api/v1/promotions/"+OFFER+"/express-interest").header("Authorization","Bearer "+customer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization","Bearer "+facilitator).contentType("application/json").content("{\"serviceError\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+facilitator)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/promotions/"+OFFER).header("Authorization","Bearer "+customer)).andExpect(status().isOk()).andExpect(jsonPath("$.interested").value(true));
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization","Bearer "+facilitator).contentType("application/json").content("{\"serviceError\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+facilitator)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isEqualTo(1);
    }
}
