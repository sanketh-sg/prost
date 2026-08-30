package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;

import de.unibamberg.dsam.group6.prost.util.Redirects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RedirectsTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://evil.example",
                "//evil.example",
                "/\\evil.example",
                "\\\\evil.example",
                "javascript:alert(1)",
                "http://localhost/ok"
            })
    void rejectsAnythingThatCanLeaveTheSite(String hostile) {
        assertThat(Redirects.safe(Optional.of(hostile), "/fallback")).isEqualTo("/fallback");
    }

    /**
     * Browsers strip tab (9), line feed (10) and carriage return (13) while parsing a URL, so a
     * Location header of "/{tab}/evil.example" is fetched as "//evil.example" — scheme-relative,
     * and off-site. The prefix checks alone do not catch these, because the string genuinely
     * starts with a single slash.
     */
    @ParameterizedTest
    @ValueSource(ints = {9, 10, 13})
    void rejectsControlCharactersBrowsersStripFromUrls(int codePoint) {
        var hostile = "/" + (char) codePoint + "/evil.example";
        assertThat(Redirects.safe(Optional.of(hostile), "/fallback")).isEqualTo("/fallback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/cart", "/bottles?page=2", "/admin?p=orders"})
    void allowsSameSitePaths(String path) {
        assertThat(Redirects.safe(Optional.of(path), "/fallback")).isEqualTo(path);
    }

    @Test
    void fallsBackWhenAbsentOrBlank() {
        assertThat(Redirects.safe(Optional.empty(), "/fallback")).isEqualTo("/fallback");
        assertThat(Redirects.safe(Optional.of(""), "/fallback")).isEqualTo("/fallback");
    }
}
