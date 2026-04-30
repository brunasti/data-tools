/*
 * Data Tools - Tests for MigrateDataMain CLI parsing.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MigrateDataMainTest {

    // -------------------------------------------------------------------------
    // processCommandLine — argument parsing
    // -------------------------------------------------------------------------

    @Test
    void processCommandLine_noArgs_returnsFalse() {
        assertFalse(MigrateDataMain.processCommandLine(new String[]{}));
    }

    @Test
    void processCommandLine_helpShort_returnsFalse() {
        assertFalse(MigrateDataMain.processCommandLine(new String[]{"-h"}));
    }

    @Test
    void processCommandLine_helpLong_returnsFalse() {
        assertFalse(MigrateDataMain.processCommandLine(new String[]{"--help"}));
    }

    @Test
    void processCommandLine_shortUsage_returnsFalse() {
        assertFalse(MigrateDataMain.processCommandLine(new String[]{"-?"}));
    }

    @Test
    void processCommandLine_unknownOption_returnsFalse() {
        assertFalse(MigrateDataMain.processCommandLine(new String[]{"-z"}));
    }

    @Test
    void processCommandLine_configShort_returnsTrue() {
        assertTrue(MigrateDataMain.processCommandLine(new String[]{"-c", "any.json"}));
    }

    @Test
    void processCommandLine_configLong_returnsTrue() {
        assertTrue(MigrateDataMain.processCommandLine(new String[]{"--config", "any.json"}));
    }

    @Test
    void processCommandLine_configAndCleanShort_returnsTrue() {
        assertTrue(MigrateDataMain.processCommandLine(new String[]{"-c", "any.json", "-x"}));
    }

    @Test
    void processCommandLine_configAndCleanLong_returnsTrue() {
        assertTrue(MigrateDataMain.processCommandLine(new String[]{"--config", "any.json", "--clean"}));
    }

    // -------------------------------------------------------------------------
    // exec — programmatic entry point
    // -------------------------------------------------------------------------

    @Test
    void exec_missingConfigFile_returnsFalse() {
        MigrateDataMain main = new MigrateDataMain();
        assertFalse(main.exec("this-file-does-not-exist.json", false));
    }

    @Test
    void exec_missingConfigFileWithClean_returnsFalse() {
        MigrateDataMain main = new MigrateDataMain();
        assertFalse(main.exec("this-file-does-not-exist.json", true));
    }
}
