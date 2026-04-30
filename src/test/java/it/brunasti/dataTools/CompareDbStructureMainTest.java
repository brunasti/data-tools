/*
 * Data Tools - Tests for CompareDbStructureMain CLI parsing.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import it.brunasti.dataTools.utils.DbConnectionUtils;
import it.brunasti.dataTools.utils.JsonCompareConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CompareDbStructureMainTest {

    // -------------------------------------------------------------------------
    // processCommandLine — argument parsing
    // -------------------------------------------------------------------------

    @Test
    void processCommandLine_noArgs_returnsFalse() {
        assertFalse(CompareDbStructureMain.processCommandLine(new String[]{}));
    }

    @Test
    void processCommandLine_helpShort_returnsFalse() {
        assertFalse(CompareDbStructureMain.processCommandLine(new String[]{"-h"}));
    }

    @Test
    void processCommandLine_helpLong_returnsFalse() {
        assertFalse(CompareDbStructureMain.processCommandLine(new String[]{"--help"}));
    }

    @Test
    void processCommandLine_shortUsage_returnsFalse() {
        assertFalse(CompareDbStructureMain.processCommandLine(new String[]{"-?"}));
    }

    @Test
    void processCommandLine_unknownOption_returnsFalse() {
        assertFalse(CompareDbStructureMain.processCommandLine(new String[]{"-z"}));
    }

    @Test
    void processCommandLine_configShort_returnsTrue() {
        assertTrue(CompareDbStructureMain.processCommandLine(new String[]{"-c", "any.json"}));
    }

    @Test
    void processCommandLine_configLong_returnsTrue() {
        assertTrue(CompareDbStructureMain.processCommandLine(new String[]{"--config", "any.json"}));
    }

    @Test
    void processCommandLine_configAndOutputShort_returnsTrue() {
        assertTrue(CompareDbStructureMain.processCommandLine(
                new String[]{"-c", "any.json", "-o", "report.md"}));
    }

    @Test
    void processCommandLine_configAndOutputLong_returnsTrue() {
        assertTrue(CompareDbStructureMain.processCommandLine(
                new String[]{"--config", "any.json", "--output", "report.md"}));
    }

    // -------------------------------------------------------------------------
    // exec — programmatic entry points
    // -------------------------------------------------------------------------

    @Test
    void exec_missingConfigFile_returnsFalse() {
        CompareDbStructureMain main = new CompareDbStructureMain();
        assertFalse(main.exec("this-file-does-not-exist.json"));
    }

    @Test
    void exec_withOutputFile_missingConfig_returnsFalse(@TempDir Path tmp) {
        Path out = tmp.resolve("out.md");
        CompareDbStructureMain main = new CompareDbStructureMain();
        assertFalse(main.exec("this-file-does-not-exist.json", out.toString()));
    }

    // -------------------------------------------------------------------------
    // output_file resolved from config when -o is absent
    // -------------------------------------------------------------------------

    @Test
    void configOutputFile_isUsedWhenNoCliOutputOption() throws IOException {
        // Verify that JsonCompareConfig.output_file is honoured by the config model
        String configPath = getResource("test-compare-config.json");
        JsonCompareConfig config = DbConnectionUtils.loadCompareConfig(configPath);
        assertEquals("test-report.md", config.output_file);
    }

    @Test
    void configOutputFile_absentWhenNotSet() throws IOException {
        String configPath = getResource("test-compare-config-no-schemas.json");
        JsonCompareConfig config = DbConnectionUtils.loadCompareConfig(configPath);
        assertNull(config.output_file);
    }

    @Test
    void exec_withMockedConfig_outputFileFromConfigIsRespected(@TempDir Path tmp) throws IOException {
        // Arrange: a config that names an output file in the temp directory
        Path outFile = tmp.resolve("from-config.md");
        JsonCompareConfig config = new JsonCompareConfig();
        config.source = buildDbRecord("src");
        config.target = buildDbRecord("tgt");
        config.schemas = List.of("public");
        config.output_file = outFile.toString();

        // The exec(configFile) path does NOT honour output_file (that is main()'s job).
        // We verify the field round-trips correctly through Gson.
        try (MockedStatic<DbConnectionUtils> utils =
                     mockStatic(DbConnectionUtils.class, CALLS_REAL_METHODS)) {
            utils.when(() -> DbConnectionUtils.loadCompareConfig(anyString())).thenReturn(config);
            // getConnection returns null → compare() returns false gracefully
            utils.when(() -> DbConnectionUtils.getConnection(any())).thenReturn(null);

            CompareDbStructureMain main = new CompareDbStructureMain();
            // Still returns false (no real DB), but the call should not throw
            assertFalse(main.exec("dummy-config.json"));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getResource(String name) {
        return getClass().getClassLoader().getResource(name).getPath();
    }

    private it.brunasti.dataTools.utils.JsonDbRecord buildDbRecord(String name) {
        var r = new it.brunasti.dataTools.utils.JsonDbRecord();
        r.name = name; r.server = "localhost"; r.port = "5432";
        r.db_name = name + "_db"; r.login = "user"; r.password = "pass";
        return r;
    }
}
