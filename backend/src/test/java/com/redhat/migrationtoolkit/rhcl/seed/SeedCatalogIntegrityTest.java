package com.redhat.migrationtoolkit.rhcl.seed;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards drift between catalog.yaml, expectations.yaml, and export fixtures. */
class SeedCatalogIntegrityTest {

    @Test
    void catalogSystemNames_matchExpectationKeys() {
        Set<String> catalog = SeedCatalogFixtureSupport.loadCatalogSystemNames();
        Set<String> expectations = SeedCatalogFixtureSupport.loadExpectationSystemNames();

        assertEquals(catalog, expectations,
                () -> diffMessage("catalog", catalog, "expectations", expectations));
    }

    @Test
    @Disabled("Enable when #280 export JSON fixtures are committed under testdata/exports/")
    void exportFixtures_existForEveryCatalogProduct() {
        Set<String> catalog = SeedCatalogFixtureSupport.loadCatalogSystemNames();
        Set<String> missing = SeedCatalogFixtureSupport.missingExportFiles(catalog);

        assertTrue(missing.isEmpty(),
                "Missing testdata/exports/{system_name}.json for: " + missing
                        + " — run scripts/refresh-seed-exports.sh after seeding lab tenant");
    }

    private static String diffMessage(String leftLabel, Set<String> left,
                                      String rightLabel, Set<String> right) {
        Set<String> onlyLeft = new TreeSet<>(left);
        onlyLeft.removeAll(right);
        Set<String> onlyRight = new TreeSet<>(right);
        onlyRight.removeAll(left);
        return "Drift between " + leftLabel + " and " + rightLabel
                + " — only in " + leftLabel + ": " + onlyLeft
                + "; only in " + rightLabel + ": " + onlyRight;
    }
}
