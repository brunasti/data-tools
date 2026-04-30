/*
 * Data Tools - Tests for MigrateData migration engine.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import it.brunasti.dataTools.utils.DbConnectionUtils;
import it.brunasti.dataTools.utils.JsonDbRecord;
import it.brunasti.dataTools.utils.JsonMigrateConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MigrateDataTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ByteArrayOutputStream output;
    private MigrateData migrateData;

    private void setUp() {
        output = new ByteArrayOutputStream();
        migrateData = new MigrateData(new PrintStream(output));
    }

    private JsonMigrateConfig config(List<String> tables) {
        JsonDbRecord src = new JsonDbRecord();
        src.name = "src"; src.server = "localhost"; src.port = "5432";
        src.db_name = "srcdb"; src.login = "u"; src.password = "p";

        JsonDbRecord tgt = new JsonDbRecord();
        tgt.name = "tgt"; tgt.server = "localhost"; tgt.port = "5432";
        tgt.db_name = "tgtdb"; tgt.login = "u"; tgt.password = "p";

        JsonMigrateConfig cfg = new JsonMigrateConfig();
        cfg.source = src;
        cfg.target = tgt;
        cfg.tables = tables;
        return cfg;
    }

    /** Stubs source to serve one table with one integer column and one data row. */
    private void setupOneRowCopy(Connection src, Connection tgt, String table) throws SQLException {
        Statement srcStmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(src.createStatement()).thenReturn(srcStmt);
        when(srcStmt.executeQuery("SELECT * FROM " + table)).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnName(1)).thenReturn("id");
        when(rs.getObject(1)).thenReturn(1);

        PreparedStatement insStmt = mock(PreparedStatement.class);
        when(tgt.prepareStatement(anyString())).thenReturn(insStmt);
    }

    /** Stubs source to serve one empty table. */
    private void setupEmptyTableCopy(Connection src, Connection tgt, String table) throws SQLException {
        Statement srcStmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(src.createStatement()).thenReturn(srcStmt);
        when(srcStmt.executeQuery("SELECT * FROM " + table)).thenReturn(rs);
        when(rs.next()).thenReturn(false);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnName(1)).thenReturn("id");

        PreparedStatement insStmt = mock(PreparedStatement.class);
        when(tgt.prepareStatement(anyString())).thenReturn(insStmt);
    }

    private ResultSet emptyRs() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        return rs;
    }

    // -------------------------------------------------------------------------
    // Config loading failures — real IO, no mock needed
    // -------------------------------------------------------------------------

    @Test
    void migrate_missingConfigFile_returnsFalse() {
        setUp();
        assertFalse(migrateData.migrate("no-such-file.json", false));
    }

    // -------------------------------------------------------------------------
    // Config validation — mockStatic only, no Connection mock
    // -------------------------------------------------------------------------

    @Test
    void migrate_nullSource_returnsFalse() {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        cfg.source = null;

        try (MockedStatic<DbConnectionUtils> utils = mockStatic(DbConnectionUtils.class)) {
            utils.when(() -> DbConnectionUtils.loadMigrateConfig(anyString())).thenReturn(cfg);
            assertFalse(migrateData.migrate("cfg.json", false));
        }
        assertTrue(output.toString().contains("ERROR"));
    }

    @Test
    void migrate_nullTarget_returnsFalse() {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        cfg.target = null;

        try (MockedStatic<DbConnectionUtils> utils = mockStatic(DbConnectionUtils.class)) {
            utils.when(() -> DbConnectionUtils.loadMigrateConfig(anyString())).thenReturn(cfg);
            assertFalse(migrateData.migrate("cfg.json", false));
        }
        assertTrue(output.toString().contains("ERROR"));
    }

    @Test
    void migrate_emptyTablesList_returnsTrueWithWarning() {
        setUp();
        JsonMigrateConfig cfg = config(List.of());

        try (MockedStatic<DbConnectionUtils> utils = mockStatic(DbConnectionUtils.class)) {
            utils.when(() -> DbConnectionUtils.loadMigrateConfig(anyString())).thenReturn(cfg);
            assertTrue(migrateData.migrate("cfg.json", false));
        }
        assertTrue(output.toString().contains("WARNING"));
    }

    // -------------------------------------------------------------------------
    // Connection failures — doMigrate() directly, no mockStatic
    // -------------------------------------------------------------------------

    @Test
    void doMigrate_nullSource_returnsFalse() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection tgt = mock(Connection.class);

        assertFalse(migrateData.doMigrate(cfg, null, tgt, false));
        assertTrue(output.toString().contains("ERROR"));
    }

    @Test
    void doMigrate_nullTarget_returnsFalse() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);

        assertFalse(migrateData.doMigrate(cfg, src, null, false));
        assertTrue(output.toString().contains("ERROR"));
    }

    @Test
    void doMigrate_bothNull_returnsFalse() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));

        assertFalse(migrateData.doMigrate(cfg, null, null, false));
        assertTrue(output.toString().contains("ERROR"));
    }

    // -------------------------------------------------------------------------
    // Successful migration — no clean — doMigrate() directly
    // -------------------------------------------------------------------------

    @Test
    void doMigrate_emptyTable_noClean_returnsTrue() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupEmptyTableCopy(src, tgt, "users");

        assertTrue(migrateData.doMigrate(cfg, src, tgt, false));
        String out = output.toString();
        assertTrue(out.contains("0 rows migrated"));
        assertTrue(out.contains("COMPLETED successfully"));
    }

    @Test
    void doMigrate_oneRow_noClean_returnsTrue() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupOneRowCopy(src, tgt, "users");

        assertTrue(migrateData.doMigrate(cfg, src, tgt, false));
        String out = output.toString();
        assertTrue(out.contains("1 rows migrated"));
        assertTrue(out.contains("COMPLETED successfully"));
        verify(tgt).commit();
    }

    @Test
    void doMigrate_oneRow_batchIsExecuted() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement srcStmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(src.createStatement()).thenReturn(srcStmt);
        when(srcStmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnName(1)).thenReturn("id");
        when(rs.getObject(1)).thenReturn(42);

        PreparedStatement insStmt = mock(PreparedStatement.class);
        when(tgt.prepareStatement(anyString())).thenReturn(insStmt);

        assertTrue(migrateData.doMigrate(cfg, src, tgt, false));
        verify(insStmt).setObject(1, 42);
        verify(insStmt).addBatch();
        verify(insStmt).executeBatch();
    }

    @Test
    void doMigrate_multipleTables_allCopied() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users", "orders"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement usersStmt = mock(Statement.class);
        Statement ordersStmt = mock(Statement.class);
        when(src.createStatement()).thenReturn(usersStmt, ordersStmt);

        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnName(1)).thenReturn("id");

        ResultSet usersRs = emptyRs();
        ResultSet ordersRs = emptyRs();
        when(usersRs.getMetaData()).thenReturn(meta);
        when(ordersRs.getMetaData()).thenReturn(meta);
        when(usersStmt.executeQuery("SELECT * FROM users")).thenReturn(usersRs);
        when(ordersStmt.executeQuery("SELECT * FROM orders")).thenReturn(ordersRs);

        PreparedStatement ins = mock(PreparedStatement.class);
        when(tgt.prepareStatement(anyString())).thenReturn(ins);

        assertTrue(migrateData.doMigrate(cfg, src, tgt, false));
        String out = output.toString();
        assertTrue(out.contains("users"));
        assertTrue(out.contains("orders"));
        assertTrue(out.contains("COMPLETED successfully"));
    }

    // -------------------------------------------------------------------------
    // Successful migration — with clean — doMigrate() directly
    // -------------------------------------------------------------------------

    @Test
    void doMigrate_emptyTable_withClean_returnsTrue() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement delStmt = mock(Statement.class);
        when(delStmt.executeUpdate("DELETE FROM users")).thenReturn(0);
        when(tgt.createStatement()).thenReturn(delStmt);

        setupEmptyTableCopy(src, tgt, "users");

        assertTrue(migrateData.doMigrate(cfg, src, tgt, true));
        String out = output.toString();
        assertTrue(out.contains("0 rows deleted"));
        assertTrue(out.contains("COMPLETED successfully"));
    }

    @Test
    void doMigrate_withClean_deletesRowsThenCopies() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement delStmt = mock(Statement.class);
        when(delStmt.executeUpdate("DELETE FROM users")).thenReturn(5);
        when(tgt.createStatement()).thenReturn(delStmt);

        setupOneRowCopy(src, tgt, "users");

        assertTrue(migrateData.doMigrate(cfg, src, tgt, true));
        String out = output.toString();
        assertTrue(out.contains("5 rows deleted"));
        assertTrue(out.contains("1 rows migrated"));
        verify(tgt, times(2)).commit();
    }

    @Test
    void doMigrate_withClean_reverseOrderForDeletion() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("a", "b"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement delB = mock(Statement.class), delA = mock(Statement.class);
        when(tgt.createStatement()).thenReturn(delB, delA);
        when(delB.executeUpdate("DELETE FROM b")).thenReturn(0);
        when(delA.executeUpdate("DELETE FROM a")).thenReturn(0);

        Statement srcA = mock(Statement.class), srcB = mock(Statement.class);
        when(src.createStatement()).thenReturn(srcA, srcB);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnName(1)).thenReturn("id");
        ResultSet rsA = emptyRs(), rsB = emptyRs();
        when(rsA.getMetaData()).thenReturn(meta);
        when(rsB.getMetaData()).thenReturn(meta);
        when(srcA.executeQuery("SELECT * FROM a")).thenReturn(rsA);
        when(srcB.executeQuery("SELECT * FROM b")).thenReturn(rsB);
        PreparedStatement ins = mock(PreparedStatement.class);
        when(tgt.prepareStatement(anyString())).thenReturn(ins);

        assertTrue(migrateData.doMigrate(cfg, src, tgt, true));
        verify(delB).executeUpdate("DELETE FROM b");
        verify(delA).executeUpdate("DELETE FROM a");
    }

    // -------------------------------------------------------------------------
    // Partial failures — doMigrate() directly
    // -------------------------------------------------------------------------

    @Test
    void doMigrate_copyFails_returnsFalseWithErrorMessage() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("bad_table"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement srcStmt = mock(Statement.class);
        when(src.createStatement()).thenReturn(srcStmt);
        when(srcStmt.executeQuery(anyString())).thenThrow(new SQLException("table not found"));

        assertFalse(migrateData.doMigrate(cfg, src, tgt, false));
        assertTrue(output.toString().contains("COMPLETED WITH ERRORS"));
    }

    @Test
    void doMigrate_cleanFails_continuesAndReturnsFalse() throws SQLException {
        setUp();
        JsonMigrateConfig cfg = config(List.of("users"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        Statement delStmt = mock(Statement.class);
        when(tgt.createStatement()).thenReturn(delStmt);
        when(delStmt.executeUpdate(anyString())).thenThrow(new SQLException("delete failed"));

        setupEmptyTableCopy(src, tgt, "users");

        assertFalse(migrateData.doMigrate(cfg, src, tgt, true));
        assertTrue(output.toString().contains("FAILED"));
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    @Test
    void constructor_defaultOutputIsSystemOut() {
        assertNotNull(new MigrateData());
    }
}
