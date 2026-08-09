package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
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
