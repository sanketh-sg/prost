package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Pagination must work without JavaScript: the controls are real links. */
@IntegrationTest
class PaginationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    DatabaseLoader loader;

    @BeforeEach
    void seed() {
        TestData.seedCatalogue(this.loader);
    }

    private String render(String path, String... params) throws Exception {
        var request = get(path);
        for (var i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return this.mvc
                .perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void paginationRendersHrefsNotOnclickHandlers() throws Exception {
        var page = this.render("/bottles");

        assertThat(page).contains("href=\"/bottles?page=1\"").doesNotContain("prostLib.pagination");
    }

    @Test
    void filterSurvivesPageNavigation() throws Exception {
        var page = this.render("/bottles", "containsAlcohol", "true");

        assertThat(page).contains("containsAlcohol=true");
    }

    /**
     * Follows a rendered link for real. Asserting on href strings alone would not catch a URL
     * that renders plausibly but 400s when requested — an empty trailing parameter, say.
     */
    @Test
    void aRenderedPaginationLinkIsActuallyRequestable() throws Exception {
        var page = this.render("/bottles");

        var marker = "href=\"";
        var start = page.indexOf(marker, page.indexOf("Pagination__container")) + marker.length();
        var href = page.substring(start, page.indexOf('"', start)).replace("&amp;", "&");

        assertThat(href).startsWith("/bottles");
        this.mvc.perform(get(href)).andExpect(status().isOk());
    }

    /**
     * On the first page the backward controls must be marked disabled. The class is what makes
     * them inert — {@code a.disabled { pointer-events: none }} in index.css — so losing it would
     * silently turn them back into working links to page 0.
     */
    @Test
    void backwardControlsAreDisabledOnTheFirstPage() throws Exception {
        var firstPage = this.render("/bottles", "page", "0");
        var container = firstPage.substring(firstPage.indexOf("Pagination__container"));
        var firstLink = container.substring(container.indexOf("<a"), container.indexOf("</a>"));

        assertThat(firstLink).contains("disabled");
    }

    @Test
    void forwardControlsAreNotDisabledOnTheFirstPage() throws Exception {
        var firstPage = this.render("/bottles", "page", "0");
        var container = firstPage.substring(firstPage.indexOf("Pagination__container"));
        var lastLink = container.substring(container.lastIndexOf("<a"), container.lastIndexOf("</a>"));

        assertThat(lastLink).doesNotContain("disabled");
    }

    @Test
    void cratesUseTheSameFragment() throws Exception {
        var page = this.render("/crates");

        assertThat(page).contains("href=\"/crates?page=").doesNotContain("prostLib.pagination");
    }
}
