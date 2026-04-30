/*
 * Data Tools - Tests for CompareDbStructure comparison engine.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import it.brunasti.dataTools.utils.DbConnectionUtils;
import it.brunasti.dataTools.utils.JsonCompareConfig;
import it.brunasti.dataTools.utils.JsonDbRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompareDbStructureTest {

    private ByteArrayOutputStream output;
    private CompareDbStructure comparator;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        comparator = new CompareDbStructure(new PrintStream(output));
    }

    // -------------------------------------------------------------------------
    // Config loading failures — real IO, no mock needed
    // -------------------------------------------------------------------------

    @Test
    void compare_missingConfigFile_returnsFalse() {
        assertFalse(comparator.compare("no-such-file.json"));
    }

    // -------------------------------------------------------------------------
    // Config loading short-circuit — mockStatic only, no Connection mock
    // -------------------------------------------------------------------------

    @Test
    void compare_configLoaded_noConnections_returnsFalse() {
        JsonCompareConfig cfg = buildConfig(List.of("public"));

        try (MockedStatic<DbConnectionUtils> utils = mockStatic(DbConnectionUtils.class)) {
            utils.when(() -> DbConnectionUtils.loadCompareConfig(anyString())).thenReturn(cfg);
            // getConnection not stubbed → returns null → doCompare returns false
            assertFalse(comparator.compare("cfg.json"));
        }
    }

    // -------------------------------------------------------------------------
    // Connection failures — doCompare() directly, no mockStatic
    // -------------------------------------------------------------------------

    @Test
    void doCompare_bothConnectionsNull_returnsFalse() throws SQLException {
        assertFalse(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), null, null));
    }

    @Test
    void doCompare_sourceNull_returnsFalse() throws SQLException {
        Connection tgt = mock(Connection.class);
        assertFalse(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), null, tgt));
    }

    @Test
    void doCompare_targetNull_returnsFalse() throws SQLException {
        Connection src = mock(Connection.class);
        assertFalse(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, null));
    }

    // -------------------------------------------------------------------------
    // Report header
    // -------------------------------------------------------------------------

    @Test
    void doCompare_emptySchemas_reportContainsHeader() throws SQLException {
        JsonCompareConfig cfg = buildConfig(List.of("public"));
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupEmptySchema(src);
        setupEmptySchema(tgt);

        assertTrue(comparator.doCompare(cfg, List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("# DB Structure Comparison"));
        assertTrue(report.contains("src_db"));
        assertTrue(report.contains("tgt_db"));
        assertTrue(report.contains("public"));
        assertTrue(report.contains("## Summary"));
    }

    // -------------------------------------------------------------------------
    // Default schema fallback — doCompare() with null/empty schemas
    // -------------------------------------------------------------------------

    @Test
    void doCompare_nullSchemas_defaultsToPublic() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupEmptySchema(src);
        setupEmptySchema(tgt);

        assertTrue(comparator.doCompare(buildConfig(null), null, src, tgt));
        assertTrue(output.toString().contains("public"));
    }

    @Test
    void doCompare_emptySchemasList_defaultsToPublic() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupEmptySchema(src);
        setupEmptySchema(tgt);

        assertTrue(comparator.doCompare(buildConfig(List.of()), List.of(), src, tgt));
        assertTrue(output.toString().contains("public"));
    }

    // -------------------------------------------------------------------------
    // Identical schemas
    // -------------------------------------------------------------------------

    @Test
    void doCompare_identicalEmptySchemas_reportsZeroDifferences() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupEmptySchema(src);
        setupEmptySchema(tgt);

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("| Tables only in SOURCE | 0 |"));
        assertTrue(report.contains("| Tables only in TARGET | 0 |"));
        assertTrue(report.contains("| Tables with differences | 0 |"));
        assertTrue(report.contains("| Identical tables | 0 |"));
        assertFalse(report.contains("## Table Differences"));
    }

    @Test
    void doCompare_identicalOneTableSchemas_reportedAsIdentical() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupSchemaOneTable(src, "users", "id", "integer");
        setupSchemaOneTable(tgt, "users", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("| Identical tables | 1 |"));
        assertTrue(report.contains("## Identical Tables"));
        assertTrue(report.contains("`users`"));
        assertFalse(report.contains("## Table Differences"));
    }

    // -------------------------------------------------------------------------
    // Source-only / target-only tables
    // -------------------------------------------------------------------------

    @Test
    void doCompare_tableOnlyInSource_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupSchemaOneTable(src, "extra_table", "id", "integer");
        setupEmptySchema(tgt);

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("| Tables only in SOURCE | 1 |"));
        assertTrue(report.contains("## Tables only in SOURCE"));
        assertTrue(report.contains("`extra_table`"));
        assertFalse(report.contains("## Tables only in TARGET"));
    }

    @Test
    void doCompare_tableOnlyInTarget_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);
        setupEmptySchema(src);
        setupSchemaOneTable(tgt, "new_table", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("| Tables only in TARGET | 1 |"));
        assertTrue(report.contains("## Tables only in TARGET"));
        assertTrue(report.contains("`new_table`"));
        assertFalse(report.contains("## Tables only in SOURCE"));
    }

    // -------------------------------------------------------------------------
    // Column differences
    // -------------------------------------------------------------------------

    @Test
    void doCompare_columnOnlyInSource_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithColumns(src, "users",
                col("id",    "integer",            1, null, null, null, "NO",  null),
                col("email", "character varying",   2, 100,  null, null, "YES", null));
        setupSchemaOneTable(tgt, "users", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Columns only in SOURCE"));
        assertTrue(report.contains("`email`"));
        assertTrue(report.contains("character varying(100)"));
    }

    @Test
    void doCompare_columnOnlyInTarget_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaOneTable(src, "users", "id", "integer");
        setupSchemaWithColumns(tgt, "users",
                col("id",         "integer",                      1, null, null, null, "NO",  null),
                col("created_at", "timestamp without time zone",  2, null, null, null, "YES", null));

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Columns only in TARGET"));
        assertTrue(report.contains("`created_at`"));
    }

    @Test
    void doCompare_columnNullabilityDiffers_reportedAsPropertyDiff() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithColumns(src, "users",
                col("email", "character varying", 1, 200, null, null, "YES", null));
        setupSchemaWithColumns(tgt, "users",
                col("email", "character varying", 1, 200, null, null, "NO",  null));

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Column property differences"));
        assertTrue(report.contains("is_nullable"));
        assertTrue(report.contains("YES"));
        assertTrue(report.contains("NO"));
    }

    @Test
    void doCompare_columnTypeDiffers_reportedAsPropertyDiff() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithColumns(src, "users",
                col("age", "integer", 1, null, null, null, "YES", null));
        setupSchemaWithColumns(tgt, "users",
                col("age", "bigint",  1, null, null, null, "YES", null));

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("data_type"));
        assertTrue(report.contains("integer"));
        assertTrue(report.contains("bigint"));
    }

    @Test
    void doCompare_columnDefaultDiffers_reportedAsPropertyDiff() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithColumns(src, "users",
                col("status", "integer", 1, null, null, null, "NO", "0"));
        setupSchemaWithColumns(tgt, "users",
                col("status", "integer", 1, null, null, null, "NO", "1"));

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        assertTrue(output.toString().contains("column_default"));
    }

    @Test
    void doCompare_columnPositionDiffers_reportedAsPropertyDiff() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithColumns(src, "users",
                col("name", "character varying", 1, 100, null, null, "NO", null));
        setupSchemaWithColumns(tgt, "users",
                col("name", "character varying", 2, 100, null, null, "NO", null));

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        assertTrue(output.toString().contains("position"));
    }

    // -------------------------------------------------------------------------
    // Index differences
    // -------------------------------------------------------------------------

    @Test
    void doCompare_indexOnlyInSource_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithIndex(src, "users", "id", "integer",
                "idx_users_email",
                "CREATE INDEX idx_users_email ON public.users USING btree (email)");
        setupSchemaOneTable(tgt, "users", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Index differences"));
        assertTrue(report.contains("idx_users_email"));
        assertTrue(report.contains("SOURCE"));
    }

    @Test
    void doCompare_indexOnlyInTarget_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaOneTable(src, "users", "id", "integer");
        setupSchemaWithIndex(tgt, "users", "id", "integer",
                "idx_users_name",
                "CREATE INDEX idx_users_name ON public.users USING btree (name)");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Index differences"));
        assertTrue(report.contains("TARGET"));
    }

    // -------------------------------------------------------------------------
    // Constraint differences
    // -------------------------------------------------------------------------

    @Test
    void doCompare_constraintOnlyInSource_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithConstraint(src, "users", "id", "integer",
                "users_pkey", "PRIMARY KEY (id)");
        setupSchemaOneTable(tgt, "users", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Constraint differences"));
        assertTrue(report.contains("users_pkey"));
        assertTrue(report.contains("PRIMARY KEY (id)"));
    }

    @Test
    void doCompare_constraintOnlyInTarget_reportedCorrectly() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaOneTable(src, "users", "id", "integer");
        setupSchemaWithConstraint(tgt, "users", "id", "integer",
                "uq_users_email", "UNIQUE (email)");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("Constraint differences"));
        assertTrue(report.contains("uq_users_email"));
        assertTrue(report.contains("TARGET"));
    }

    @Test
    void doCompare_constraintChangedDefinition_reportedAsChanged() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithConstraint(src, "orders", "id", "integer",
                "orders_fk",
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE");
        setupSchemaWithConstraint(tgt, "orders", "id", "integer",
                "orders_fk",
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        String report = output.toString();
        assertTrue(report.contains("SOURCE (changed)"));
        assertTrue(report.contains("TARGET (changed)"));
        assertTrue(report.contains("CASCADE"));
        assertTrue(report.contains("RESTRICT"));
    }

    @Test
    void doCompare_checkConstraintShowsFullExpression() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithConstraint(src, "items", "id", "integer",
                "items_check_qty", "CHECK ((quantity > 0))");
        setupSchemaOneTable(tgt, "items", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public")), List.of("public"), src, tgt));
        assertTrue(output.toString().contains("CHECK ((quantity > 0))"));
    }

    // -------------------------------------------------------------------------
    // Multiple schemas — key uses schema.table notation
    // -------------------------------------------------------------------------

    @Test
    void doCompare_multipleSchemas_usesSchemaQualifiedKey() throws SQLException {
        Connection src = mock(Connection.class);
        Connection tgt = mock(Connection.class);

        setupSchemaWithTableInSchema(src, "public", "events", "id", "integer");
        setupSchemaWithTableInSchema(tgt, "public", "events", "id", "integer");

        assertTrue(comparator.doCompare(buildConfig(List.of("public", "audit")),
                List.of("public", "audit"), src, tgt));
        assertTrue(output.toString().contains("public.events"));
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    @Test
    void constructor_defaultOutputIsSystemOut() {
        assertNotNull(new CompareDbStructure());
    }

    // =========================================================================
    // Schema setup helpers
    // =========================================================================

    private record ColDef(String name, String dataType, int position,
                          Integer charMaxLen, Integer numPrecision, Integer numScale,
                          String isNullable, String columnDefault) {}

    private ColDef col(String name, String dataType, int pos,
                       Integer charMaxLen, Integer numPrecision, Integer numScale,
                       String nullable, String colDefault) {
        return new ColDef(name, dataType, pos, charMaxLen, numPrecision, numScale, nullable, colDefault);
    }

    /** Schema with no tables: 4 empty ResultSets, one per query. */
    private void setupEmptySchema(Connection conn) throws SQLException {
        Statement s1 = mock(Statement.class), s2 = mock(Statement.class),
                  s3 = mock(Statement.class), s4 = mock(Statement.class);
        when(conn.createStatement()).thenReturn(s1, s2, s3, s4);
        ResultSet empty = emptyRs();
        when(s1.executeQuery(anyString())).thenReturn(empty);
        when(s2.executeQuery(anyString())).thenReturn(empty);
        when(s3.executeQuery(anyString())).thenReturn(empty);
        when(s4.executeQuery(anyString())).thenReturn(empty);
    }

    /** One table, one integer column, no indexes, no constraints. */
    private void setupSchemaOneTable(Connection conn, String table,
                                     String colName, String dataType) throws SQLException {
        setupSchemaWithColumns(conn, table,
                col(colName, dataType, 1, null, null, null, "NO", null));
    }

    /** One table in a named schema (for multi-schema tests). */
    private void setupSchemaWithTableInSchema(Connection conn, String schema,
                                              String table, String colName, String dataType)
            throws SQLException {
        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true, false);
        when(tablesRs.getString("table_schema")).thenReturn(schema);
        when(tablesRs.getString("table_name")).thenReturn(table);

        ResultSet colsRs = singleColRs(schema, table, colName, dataType, 1);
        ResultSet empty1 = emptyRs();
        ResultSet empty2 = emptyRs();

        Statement s1 = mock(Statement.class), s2 = mock(Statement.class),
                  s3 = mock(Statement.class), s4 = mock(Statement.class);
        when(conn.createStatement()).thenReturn(s1, s2, s3, s4);
        when(s1.executeQuery(anyString())).thenReturn(tablesRs);
        when(s2.executeQuery(anyString())).thenReturn(colsRs);
        when(s3.executeQuery(anyString())).thenReturn(empty1);
        when(s4.executeQuery(anyString())).thenReturn(empty2);
    }

    /** One table with multiple columns, no indexes, no constraints. */
    private void setupSchemaWithColumns(Connection conn, String table, ColDef... cols)
            throws SQLException {
        ResultSet tablesRs = singleTableRs("public", table);
        ResultSet colsRs   = multiColRs("public", table, cols);
        ResultSet empty1   = emptyRs();
        ResultSet empty2   = emptyRs();

        Statement s1 = mock(Statement.class), s2 = mock(Statement.class),
                  s3 = mock(Statement.class), s4 = mock(Statement.class);
        when(conn.createStatement()).thenReturn(s1, s2, s3, s4);
        when(s1.executeQuery(anyString())).thenReturn(tablesRs);
        when(s2.executeQuery(anyString())).thenReturn(colsRs);
        when(s3.executeQuery(anyString())).thenReturn(empty1);
        when(s4.executeQuery(anyString())).thenReturn(empty2);
    }

    /** One table, one column, one index, no constraints. */
    private void setupSchemaWithIndex(Connection conn, String table,
                                      String colName, String dataType,
                                      String idxName, String idxDef) throws SQLException {
        ResultSet tablesRs = singleTableRs("public", table);
        ResultSet colsRs   = singleColRs("public", table, colName, dataType, 1);

        ResultSet idxRs = mock(ResultSet.class);
        when(idxRs.next()).thenReturn(true, false);
        when(idxRs.getString("schemaname")).thenReturn("public");
        when(idxRs.getString("tablename")).thenReturn(table);
        when(idxRs.getString("indexname")).thenReturn(idxName);
        when(idxRs.getString("indexdef")).thenReturn(idxDef);

        ResultSet empty = emptyRs();

        Statement s1 = mock(Statement.class), s2 = mock(Statement.class),
                  s3 = mock(Statement.class), s4 = mock(Statement.class);
        when(conn.createStatement()).thenReturn(s1, s2, s3, s4);
        when(s1.executeQuery(anyString())).thenReturn(tablesRs);
        when(s2.executeQuery(anyString())).thenReturn(colsRs);
        when(s3.executeQuery(anyString())).thenReturn(idxRs);
        when(s4.executeQuery(anyString())).thenReturn(empty);
    }

    /** One table, one column, no indexes, one constraint. */
    private void setupSchemaWithConstraint(Connection conn, String table,
                                           String colName, String dataType,
                                           String conName, String conDef) throws SQLException {
        ResultSet tablesRs = singleTableRs("public", table);
        ResultSet colsRs   = singleColRs("public", table, colName, dataType, 1);
        ResultSet empty    = emptyRs();

        ResultSet conRs = mock(ResultSet.class);
        when(conRs.next()).thenReturn(true, false);
        when(conRs.getString("table_schema")).thenReturn("public");
        when(conRs.getString("table_name")).thenReturn(table);
        when(conRs.getString("constraint_name")).thenReturn(conName);
        when(conRs.getString("definition")).thenReturn(conDef);

        Statement s1 = mock(Statement.class), s2 = mock(Statement.class),
                  s3 = mock(Statement.class), s4 = mock(Statement.class);
        when(conn.createStatement()).thenReturn(s1, s2, s3, s4);
        when(s1.executeQuery(anyString())).thenReturn(tablesRs);
        when(s2.executeQuery(anyString())).thenReturn(colsRs);
        when(s3.executeQuery(anyString())).thenReturn(empty);
        when(s4.executeQuery(anyString())).thenReturn(conRs);
    }

    // =========================================================================
    // Low-level ResultSet factories
    // =========================================================================

    private ResultSet emptyRs() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        return rs;
    }

    private ResultSet singleTableRs(String schema, String table) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("table_schema")).thenReturn(schema);
        when(rs.getString("table_name")).thenReturn(table);
        return rs;
    }

    private ResultSet singleColRs(String schema, String table,
                                  String colName, String dataType, int position) throws SQLException {
        return multiColRs(schema, table,
                col(colName, dataType, position, null, null, null, "NO", null));
    }

    private ResultSet multiColRs(String schema, String table, ColDef... cols) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        int n = cols.length;

        Boolean[] nextSeq = new Boolean[n + 1];
        Arrays.fill(nextSeq, true);
        nextSeq[n] = false;
        when(rs.next()).thenReturn(nextSeq[0],
                Arrays.copyOfRange(nextSeq, 1, nextSeq.length));

        when(rs.getString("table_schema")).thenReturn(schema);
        when(rs.getString("table_name")).thenReturn(table);

        String[] names     = new String[n];
        String[] types     = new String[n];
        int[]    positions = new int[n];
        Integer[] charMax  = new Integer[n];
        Integer[] numPrec  = new Integer[n];
        Integer[] numScl   = new Integer[n];
        String[] nullable  = new String[n];
        String[] defaults  = new String[n];

        for (int i = 0; i < n; i++) {
            names[i]     = cols[i].name();
            types[i]     = cols[i].dataType();
            positions[i] = cols[i].position();
            charMax[i]   = cols[i].charMaxLen();
            numPrec[i]   = cols[i].numPrecision();
            numScl[i]    = cols[i].numScale();
            nullable[i]  = cols[i].isNullable();
            defaults[i]  = cols[i].columnDefault();
        }

        stubStringSeq(rs, "column_name",    names);
        stubStringSeq(rs, "data_type",      types);
        stubStringSeq(rs, "is_nullable",    nullable);
        stubStringSeq(rs, "column_default", defaults);
        stubIntSeq(rs, "ordinal_position",  positions);
        stubIntOrNullSeq(rs,
                "character_maximum_length", charMax,
                "numeric_precision",        numPrec,
                "numeric_scale",            numScl);

        return rs;
    }

    private void stubStringSeq(ResultSet rs, String col, String... values) throws SQLException {
        if (values.length == 1) {
            when(rs.getString(col)).thenReturn(values[0]);
        } else {
            when(rs.getString(col)).thenReturn(values[0],
                    Arrays.copyOfRange(values, 1, values.length));
        }
    }

    private void stubIntSeq(ResultSet rs, String col, int... values) throws SQLException {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) boxed[i] = values[i];
        if (boxed.length == 1) {
            when(rs.getInt(col)).thenReturn(boxed[0]);
        } else {
            when(rs.getInt(col)).thenReturn(boxed[0],
                    Arrays.copyOfRange(boxed, 1, boxed.length));
        }
    }

    private void stubIntOrNullSeq(ResultSet rs,
                                  String col1, Integer[] vals1,
                                  String col2, Integer[] vals2,
                                  String col3, Integer[] vals3) throws SQLException {
        int n = vals1.length;
        int[] raw1 = new int[n], raw2 = new int[n], raw3 = new int[n];
        for (int i = 0; i < n; i++) {
            raw1[i] = vals1[i] != null ? vals1[i] : 0;
            raw2[i] = vals2[i] != null ? vals2[i] : 0;
            raw3[i] = vals3[i] != null ? vals3[i] : 0;
        }
        stubIntSeq(rs, col1, raw1);
        stubIntSeq(rs, col2, raw2);
        stubIntSeq(rs, col3, raw3);

        Boolean[] wasNulls = new Boolean[n * 3];
        for (int i = 0; i < n; i++) {
            wasNulls[i * 3]     = (vals1[i] == null);
            wasNulls[i * 3 + 1] = (vals2[i] == null);
            wasNulls[i * 3 + 2] = (vals3[i] == null);
        }
        if (wasNulls.length == 1) {
            when(rs.wasNull()).thenReturn(wasNulls[0]);
        } else {
            when(rs.wasNull()).thenReturn(wasNulls[0],
                    Arrays.copyOfRange(wasNulls, 1, wasNulls.length));
        }
    }

    // =========================================================================
    // Config builder
    // =========================================================================

    private JsonCompareConfig buildConfig(List<String> schemas) {
        JsonCompareConfig cfg = new JsonCompareConfig();
        cfg.source = dbRecord("src", "src_db");
        cfg.target = dbRecord("tgt", "tgt_db");
        cfg.schemas = schemas;
        return cfg;
    }

    private JsonDbRecord dbRecord(String name, String dbName) {
        JsonDbRecord r = new JsonDbRecord();
        r.name = name; r.server = "localhost"; r.port = "5432";
        r.db_name = dbName; r.login = "user"; r.password = "pass";
        return r;
    }
}
