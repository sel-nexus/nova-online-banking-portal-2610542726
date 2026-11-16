package com.nova.bank;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.bank.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/** Share only real-database test setup and HTTP authentication helpers. */
@SpringBootTest @AutoConfigureMockMvc
public abstract class TestSupport {
 @Autowired protected MockMvc mvc;
 @Autowired protected JdbcTemplate jdbc;
 @Autowired protected ObjectMapper mapper;
 @Autowired protected AuthService auth;
 /** Isolate identity transitions without deleting foreign-key fixture owners. */
 @BeforeEach void resetIdentity(){String database=jdbc.queryForObject("SELECT current_database()",String.class);if(database==null||!database.endsWith("_test"))throw new IllegalStateException("Tests require DB_NAME ending in _test");auth.seedUsers();resetFeatureFixtures();jdbc.update("DELETE FROM active_sessions");jdbc.update("DELETE FROM token_revocations");jdbc.update("UPDATE users SET failed_attempts=0,locked=false,last_login='2026-10-14T08:10:00Z'");}
 /** Extend deterministic fixture reset without duplicating identity infrastructure. */
 protected void resetFeatureFixtures() {}
 /** Obtain a real server-issued bearer through the HTTP surface. */
 protected String login(String username) throws Exception {var response=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("username",username,"password","demo-only")))).andExpect(status().isOk()).andReturn();return mapper.readTree(response.getResponse().getContentAsString()).get("token").asText();}
}
