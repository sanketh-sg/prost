package de.unibamberg.dsam.group6.prost;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Characterizes form login. The admin/admin account is created on every context
 * startup by StartListener, so no seeding is required here.
 */
@IntegrationTest
class AuthenticationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void seededAdminCanLogIn() throws Exception {
        this.mvc
                .perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        this.mvc.perform(formLogin("/login").user("admin").password("wrong")).andExpect(unauthenticated());
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        this.mvc.perform(formLogin("/login").user("nobody").password("nothing")).andExpect(unauthenticated());
    }
}
