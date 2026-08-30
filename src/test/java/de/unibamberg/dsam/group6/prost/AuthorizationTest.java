package de.unibamberg.dsam.group6.prost;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Characterizes which routes are reachable by whom. */
@IntegrationTest
class AuthorizationTest {
    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/bottles", "/crates", "/login", "/register"})
    void publicPagesAreAnonymouslyAccessible(String path) throws Exception {
        this.mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cart", "/orders", "/user"})
    void protectedPagesRedirectAnonymousUsersToLogin(String path) throws Exception {
        this.mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/action", "/admin/form"})
    void adminRoutesRedirectAnonymousUsersToLogin(String path) throws Exception {
        this.mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "someone", roles = "USER")
    void adminIsForbiddenForAuthenticatedNonAdmins() throws Exception {
        this.mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "someone", roles = "USER")
    void adminActionIsForbiddenForAuthenticatedNonAdmins() throws Exception {
        // csrf() is supplied so a 403 can only mean the authorization rule fired,
        // not a missing token.
        this.mvc.perform(post("/admin/clear").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminIsAccessibleToAdmins() throws Exception {
        this.mvc.perform(get("/admin")).andExpect(status().isOk());
    }
}
