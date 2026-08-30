package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Characterizes catalogue rendering and the admin seeding action. */
@IntegrationTest
class CatalogueTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    CratesRepository crates;

    @BeforeEach
    void seed() {
        TestData.seedCatalogue(this.loader);
    }

    @SuppressWarnings("unchecked")
    private List<Beverage> pageOfBottles(int page) throws Exception {
        var model = this.mvc
                .perform(get("/bottles").param("page", Integer.toString(page)))
                .andExpect(status().isOk())
                .andReturn()
                .getModelAndView()
                .getModel();
        return (List<Beverage>) model.get("beverages");
    }

    /**
     * The real regression net for the paging implementation: page 1 must be the *second* nine
     * items, not an offset of nine pages. Asserting only that a page parameter returns 200 would
     * pass just as happily with the arithmetic wrong.
     */
    @Test
    void pageOneHoldsTheSecondNineBottles() throws Exception {
        var expected = this.bottles.findAll().stream()
                .sorted(java.util.Comparator.comparing(Bottle::getId))
                .skip(9)
                .limit(9)
                .map(Bottle::getId)
                .toList();
        org.assertj.core.api.Assumptions.assumeThat(expected).isNotEmpty();

        var actual = this.pageOfBottles(1).stream().map(Beverage::getId).toList();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void consecutivePagesDoNotOverlap() throws Exception {
        var first = this.pageOfBottles(0).stream().map(Beverage::getId).toList();
        var second = this.pageOfBottles(1).stream().map(Beverage::getId).toList();

        assertThat(first).hasSize(9).doesNotContainAnyElementsOf(second);
    }

    @Test
    void seedingPopulatesBottlesAndCrates() {
        assertThat(this.bottles.findAll()).isNotEmpty();
        assertThat(this.crates.findAll()).isNotEmpty();
    }

    @Test
    void bottlesPageRenders() throws Exception {
        this.mvc
                .perform(get("/bottles"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/bottles"))
                .andExpect(model().attributeExists("beverages"));
    }

    @Test
    void cratesPageRenders() throws Exception {
        this.mvc
                .perform(get("/crates"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/crates"))
                .andExpect(model().attributeExists("beverages"));
    }

    @Test
    void bottlesPageAcceptsAPageParameter() throws Exception {
        this.mvc.perform(get("/bottles").param("page", "0")).andExpect(status().isOk());
    }

    @Test
    void cratesPageAcceptsAPageParameter() throws Exception {
        this.mvc.perform(get("/crates").param("page", "0")).andExpect(status().isOk());
    }
}
