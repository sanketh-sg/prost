package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Registration input is validated before anything is hashed or persisted.
 *
 * <p>Validation previously ran against the built entity, after the password had been encoded, so
 * {@code @NotEmpty} was inspecting a bcrypt string that is never empty and an empty password was
 * accepted. The form's {@code required} attributes hide this from a browser, but not from a
 * crafted POST.
 */
@IntegrationTest
class RegistrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    private MockHttpServletRequestBuilder register(String username, String password, String passwordCheck) {
        return post("/register")
                .with(csrf())
                .param("username", username)
                .param("password", password)
                .param("passwordCheck", passwordCheck)
                .param("birthday", "1990-01-01");
    }

    @Test
    void rejectsAnEmptyPassword() throws Exception {
        this.mvc
                .perform(this.register("emptypass", "", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "password"));

        assertThat(this.users.findUserByUsername("emptypass")).isEmpty();
    }

    @Test
    void rejectsMismatchedPasswords() throws Exception {
        this.mvc
                .perform(this.register("mismatch", "correct-horse", "battery-staple"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));

        assertThat(this.users.findUserByUsername("mismatch")).isEmpty();
    }

    @Test
    void rejectsADuplicateUsername() throws Exception {
        this.mvc
                .perform(this.register("dupeuser", "correct-horse", "correct-horse"))
                .andExpect(status().is3xxRedirection());

        this.mvc
                .perform(this.register("dupeuser", "correct-horse", "correct-horse"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "username"));
    }

    @Test
    void rejectsUnderSixteens() throws Exception {
        this.mvc
                .perform(post("/register")
                        .with(csrf())
                        .param("username", "tooyoung")
                        .param("password", "correct-horse")
                        .param("passwordCheck", "correct-horse")
                        .param(
                                "birthday",
                                java.time.LocalDate.now().minusYears(10).toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "birthday"));

        assertThat(this.users.findUserByUsername("tooyoung")).isEmpty();
    }

    @Test
    void acceptsAValidRegistration() throws Exception {
        this.mvc
                .perform(this.register("gooduser", "correct-horse", "correct-horse"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.users.findUserByUsername("gooduser")).isPresent();
    }

    /**
     * The rendered form must bind to the model object and carry a CSRF token. Every other test
     * here posts with {@code with(csrf())}, which bypasses the form, so nothing else would catch
     * the page rendering without a token or with a broken {@code th:object}.
     */
    @Test
    void theRenderedFormBindsAndCarriesACsrfToken() throws Exception {
        var page = this.mvc
                .perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("form"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("_csrf").contains("name=\"passwordCheck\"");
    }

    /** The stored password must be the bcrypt hash, never the plaintext. */
    @Test
    void storesThePasswordHashed() throws Exception {
        this.mvc
                .perform(this.register("hasheduser", "correct-horse", "correct-horse"))
                .andExpect(status().is3xxRedirection());

        var user = this.users.findUserByUsername("hasheduser").orElseThrow();
        assertThat(user.getPassword()).isNotEqualTo("correct-horse").startsWith("$2");
    }
}
