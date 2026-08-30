package de.unibamberg.dsam.group6.prost.support;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import java.math.BigDecimal;

/**
 * Seeding helpers for integration tests.
 *
 * <p>Two strategies, because the tests need both: {@link #seedCatalogue} drives
 * the real DatabaseLoader (what the admin panel does), while {@link #saveBottle}
 * creates one bottle with known values for tests that need a predictable id and
 * stock level.
 */
public final class TestData {
    private TestData() {}

    /** Seeds bottles and crates from data.json. */
    public static void seedCatalogue(DatabaseLoader loader) {
        try {
            loader.importBottles();
            loader.importCrates();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed test catalogue", e);
        }
    }

    /** Persists one bottle with known values and returns it. */
    public static Bottle saveBottle(BottlesRepository bottles, String name, int inStock) {
        return bottles.save(Bottle.builder()
                // Bottle.name is constrained to \w+ — spaces fail validation.
                .name(name)
                .bottlePic("https://example.invalid/bottle.png")
                .volume(0.5)
                .volumePercent(5.0)
                .price(BigDecimal.valueOf(2.0))
                .supplier("TestSupplier")
                .inStock(inStock)
                .build());
    }

    /** Returns the id of an arbitrary seeded bottle. Fails if none exist. */
    public static Long firstBottleId(BottlesRepository bottles) {
        return bottles.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No bottles seeded"))
                .getId();
    }
}
