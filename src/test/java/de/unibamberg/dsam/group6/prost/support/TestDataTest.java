package de.unibamberg.dsam.group6.prost.support;

import static org.assertj.core.api.Assertions.assertThat;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TestDataTest {
    @Autowired
    DatabaseLoader loader;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    CratesRepository crates;

    @Test
    void seedsCatalogueFromDataJson() {
        TestData.seedCatalogue(this.loader);

        assertThat(this.bottles.findAll()).isNotEmpty();
        assertThat(this.crates.findAll()).isNotEmpty();
        assertThat(TestData.firstBottleId(this.bottles)).isNotNull();
    }

    @Test
    void savesASingleBottleWithKnownStock() {
        var bottle = TestData.saveBottle(this.bottles, "KnownBottle", 7);

        assertThat(bottle.getId()).isNotNull();
        assertThat(bottle.getInStock()).isEqualTo(7);
        assertThat(this.bottles.findById(bottle.getId())).isPresent();
    }
}
