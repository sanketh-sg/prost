package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Admin seeding actions are explicit POST endpoints.
 *
 * <p>They were previously one GET endpoint that resolved a method by name from a query parameter
 * and invoked it reflectively, which made every destructive operation pre-fetchable and exempt from
 * CSRF protection. These tests pin the properties that replaced it: the operations are POST, they
 * require a CSRF token, and the reflective entry point is gone.
 */
@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class AdminActionTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    CratesRepository crates;

    MockHttpSession session;

    @BeforeEach
    void setUp() {
        this.session = new MockHttpSession();
    }

    @Test
    void theReflectiveDispatcherIsGone() throws Exception {
        this.mvc
                .perform(get("/admin/action").param("a", "databaseLoader::importBottles"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importsCannotBeTriggeredByGet() throws Exception {
        this.mvc.perform(get("/admin/import/all")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void importsRequireACsrfToken() throws Exception {
        var before = this.bottles.count();

        this.mvc.perform(post("/admin/import/all")).andExpect(status().isForbidden());

        assertThat(this.bottles.count()).isEqualTo(before);
    }

    @Test
    void importAllSeedsTheCatalogue() throws Exception {
        this.mvc
                .perform(post("/admin/import/all").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        assertThat(this.bottles.count()).isPositive();
        assertThat(this.crates.count()).isPositive();
    }

    @Test
    void importBottlesSeedsOnlyBottles() throws Exception {
        var cratesBefore = this.crates.count();

        this.mvc
                .perform(post("/admin/import/bottles").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(this.bottles.count()).isPositive();
        assertThat(this.crates.count()).isEqualTo(cratesBefore);
    }

    @Test
    void clearDatabaseRequiresACsrfToken() throws Exception {
        this.mvc.perform(post("/admin/clear")).andExpect(status().isForbidden());
    }

    /**
     * Wipes users as well as the catalogue, including the admin account StartListener creates at
     * context startup. The H2 database is shared by every test in the JVM, so the context has to be
     * rebuilt afterwards or every later test class loses its login.
     */
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void clearDatabaseEmptiesTheCatalogue() throws Exception {
        this.mvc
                .perform(post("/admin/import/all").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(this.bottles.count()).isPositive();

        this.mvc
                .perform(post("/admin/clear").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        assertThat(this.bottles.count()).isZero();
        assertThat(this.crates.count()).isZero();
    }

    /**
     * The rendered buttons must carry a CSRF token. Every other test here supplies one via
     * {@code with(csrf())}, which bypasses the form, so without this nothing would catch the
     * forms rendering without a token and every admin button 403-ing in the real application.
     */
    @Test
    void theRenderedFormsCarryACsrfToken() throws Exception {
        var page = this.mvc
                .perform(get("/admin").session(this.session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("/admin/import/all").contains("_csrf");
    }

    /** The result message reaches the user as a toast on the page they land on. */
    @Test
    void theResultIsReportedToTheUser() throws Exception {
        this.mvc.perform(post("/admin/import/bottles").session(this.session).with(csrf()));

        var page = this.mvc
                .perform(get("/admin").session(this.session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).containsIgnoringCase("Bottles imported");
    }
}
