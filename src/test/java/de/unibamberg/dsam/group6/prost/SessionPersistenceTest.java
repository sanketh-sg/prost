package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.support.TestData;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises spring-session-jdbc for real, via its SESSION cookie.
 *
 * <p>Deliberately does NOT use {@code @IntegrationTest}, which disables Spring
 * Session so the other tests can use plain servlet sessions. This is the only
 * test that proves session state actually round-trips through the database.
 *
 * <p>It exists because the Boot 3 upgrade moves Spring Session 2.x to 3.x, which
 * changes the SPRING_SESSION schema. Without this, that breakage would only
 * surface by clicking through the running app.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@WithMockUser(username = "admin", roles = "ADMIN")
// Its own database, because it is necessarily its own Spring context. Two
// contexts sharing one in-memory database collide: each boots with
// ddl-auto: create and resets the sequences while the other still holds a
// pre-allocated block of ids (Hibernate 6 allocates 50 at a time), producing
// primary key violations that depend on test execution order.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:prost-session")
class SessionPersistenceTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void springSessionCreatesItsSchema() {
        var count = this.jdbc.queryForObject("select count(*) from SPRING_SESSION", Integer.class);

        assertThat(count).isNotNull();
    }

    @Test
    void cartSurvivesAcrossRequestsViaTheSessionCookie() throws Exception {
        var bottleId = TestData.saveBottle(this.bottles, "SessionBottle", 20).getId();

        var addResult = this.mvc
                .perform(post("/cart/add")
                        .with(csrf())
                        .param("beverageId", bottleId.toString())
                        .param("count", "4"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie[] cookies = addResult.getResponse().getCookies();
        assertThat(cookies).as("expected a SESSION cookie from spring-session").isNotEmpty();

        var cartResult = this.mvc
                .perform(get("/cart").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();

        var cart = (CartDTO) cartResult.getModelAndView().getModel().get("cart");
        assertThat(cart.beverages.values()).containsExactly(4);
    }

    @Test
    void cartIsNotSharedWithoutTheSessionCookie() throws Exception {
        var bottleId = TestData.saveBottle(this.bottles, "IsolatedBottle", 20).getId();

        this.mvc
                .perform(post("/cart/add")
                        .with(csrf())
                        .param("beverageId", bottleId.toString())
                        .param("count", "2"))
                .andExpect(status().is3xxRedirection());

        // No cookie carried over — this is a different session and must be empty.
        var cartResult =
                this.mvc.perform(get("/cart")).andExpect(status().isOk()).andReturn();

        var cart = (CartDTO) cartResult.getModelAndView().getModel().get("cart");
        assertThat(cart.beverages).isEmpty();
    }
}
