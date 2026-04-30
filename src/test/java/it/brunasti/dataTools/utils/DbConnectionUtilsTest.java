/*
 * Data Tools - Tests for DbConnectionUtils config loading and connection factory.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DbConnectionUtilsTest {

    // -------------------------------------------------------------------------
    // loadMigrateConfig
    // -------------------------------------------------------------------------

    @Test
    void loadMigrateConfig_validFile_returnsPopulatedConfig() throws IOException {
        String path = resource("test-migrate-config.json");
        JsonMigrateConfig config = DbConnectionUtils.loadMigrateConfig(path);

        assertNotNull(config);
        assertNotNull(config.getSource());
        assertEquals("test-source",    config.getSource().getName());
        assertEquals("localhost",       config.getSource().getServer());
        assertEquals("5432",            config.getSource().getPort());
        assertEquals("test_source_db",  config.getSource().getDb_name());

        assertNotNull(config.getTarget());
        assertEquals("test-target",    config.getTarget().getName());
        assertEquals("test_target_db", config.getTarget().getDb_name());

        assertNotNull(config.getTables());
        assertEquals(2, config.getTables().size());
        assertEquals("users",  config.getTables().get(0));
        assertEquals("orders", config.getTables().get(1));
    }

    @Test
    void loadMigrateConfig_emptyTablesList_returnedCorrectly() throws IOException {
        String path = resource("test-migrate-config-no-tables.json");
        JsonMigrateConfig config = DbConnectionUtils.loadMigrateConfig(path);

        assertNotNull(config);
        assertNotNull(config.getTables());
        assertTrue(config.getTables().isEmpty());
    }

    @Test
    void loadMigrateConfig_missingFile_throwsIOException() {
        assertThrows(IOException.class,
                () -> DbConnectionUtils.loadMigrateConfig("no-such-file.json"));
    }

    // -------------------------------------------------------------------------
    // loadCompareConfig
    // -------------------------------------------------------------------------

    @Test
    void loadCompareConfig_validFile_returnsPopulatedConfig() throws IOException {
        String path = resource("test-compare-config.json");
        JsonCompareConfig config = DbConnectionUtils.loadCompareConfig(path);

        assertNotNull(config);
        assertNotNull(config.source);
        assertEquals("test-source",   config.source.name);
        assertEquals("localhost",      config.source.server);
        assertEquals("test_source_db", config.source.db_name);

        assertNotNull(config.target);
        assertEquals("test_target_db", config.target.db_name);

        assertNotNull(config.schemas);
        assertEquals(1, config.schemas.size());
        assertEquals("public", config.schemas.get(0));
    }

    @Test
    void loadCompareConfig_outputFileField_isReadFromJson() throws IOException {
        String path = resource("test-compare-config.json");
        JsonCompareConfig config = DbConnectionUtils.loadCompareConfig(path);

        assertEquals("test-report.md", config.output_file);
    }

    @Test
    void loadCompareConfig_noSchemas_schemasFieldIsNull() throws IOException {
        String path = resource("test-compare-config-no-schemas.json");
        JsonCompareConfig config = DbConnectionUtils.loadCompareConfig(path);

        assertNull(config.schemas);
        assertNull(config.output_file);
    }

    @Test
    void loadCompareConfig_missingFile_throwsIOException() {
        assertThrows(IOException.class,
                () -> DbConnectionUtils.loadCompareConfig("no-such-file.json"));
    }

    // -------------------------------------------------------------------------
    // getConnection — bad host must return null gracefully, not throw
    // -------------------------------------------------------------------------

    @Test
    void getConnection_closedPort_returnsNull() {
        // Port 19999 is almost certainly closed; connection refused is near-instant.
        var result = DbConnectionUtils.getConnection(
                "nonexistent_db", "user", "pass", "127.0.0.1", "19999");
        assertNull(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resource(String name) {
        return getClass().getClassLoader().getResource(name).getPath();
    }
}
