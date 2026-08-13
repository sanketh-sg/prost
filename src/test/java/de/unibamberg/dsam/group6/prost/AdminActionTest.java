package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admin action dispatcher resolves a method by name via reflection from a
 * query parameter, so a typo is an ordinary occurrence rather than an edge case.
 *
 * <p>Toasts are asserted through the rendered page rather than by reading
 * UserErrorManager directly: the message only matters if the user sees it, and
 * the toast list is session-scoped and cleared on read.
 */
@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class AdminActionTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    MockHttpSession session;

    @BeforeEach
    void setUp() {
        this.session = new MockHttpSession();
    }

    /** Runs an action, then returns the admin page the user is redirected to. */
    private String runActionAndRenderAdmin(String actionParam) throws Exception {
        return this.runActionAndRenderAdmin(actionParam, false);
    }

    /**
     * @param await block until the action finishes. DatabaseLoader's actions are
     *     {@code @Async}, so without this an assertion races the work.
     */
    private String runActionAndRenderAdmin(String actionParam, boolean await) throws Exception {
        var request = get("/admin/action").session(this.session);
        if (actionParam != null) {
            request = request.param("a", actionParam);
        }
        if (await) {
            request = request.param("await", "true");
        }
        this.mvc.perform(request).andExpect(status().is3xxRedirection());

        return this.mvc
                .perform(get("/admin").session(this.session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void validActionSeedsTheCatalogue() throws Exception {
        this.runActionAndRenderAdmin("databaseLoader::importBottles", true);

        assertThat(this.bottles.findAll()).isNotEmpty();
    }

    @Test
    void unknownInstanceIsReportedToTheUser() throws Exception {
        var page = this.runActionAndRenderAdmin("noSuchBean::importBottles");

        assertThat(page).containsIgnoringCase("noSuchBean");
    }

    @Test
    void malformedActionIsReportedToTheUser() throws Exception {
        var page = this.runActionAndRenderAdmin("missingSeparator");

        assertThat(page).containsIgnoringCase("missingSeparator");
    }

    @Test
    void missingActionIsReportedToTheUser() throws Exception {
        var page = this.runActionAndRenderAdmin(null);

        assertThat(page).containsIgnoringCase("no action");
    }
}
