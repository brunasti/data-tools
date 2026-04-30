/*
 * Data Tools - CompareDbStructure executor.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import it.brunasti.dataTools.utils.DbConnectionUtils;
import it.brunasti.dataTools.utils.JsonCompareConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares the schema structure of two PostgreSQL database instances and emits
 * a Markdown report describing every difference found across tables, columns,
 * indexes and constraints.
 *
 * <p>The comparison covers one or more schemas (default: {@code public}).
 * For each schema the tool queries {@code information_schema} and {@code pg_indexes}
 * and produces sections for:
 * <ul>
 *   <li>Tables present only in source or only in target</li>
 *   <li>Per-table column differences (missing columns, type/nullability/default changes)</li>
 *   <li>Index definition differences</li>
 *   <li>Constraint differences (PK, UNIQUE, FK, CHECK)</li>
 * </ul>
 *
 * @author Paolo Brunasti
 */
public class CompareDbStructure extends BaseExecutor {

    static Logger log = LogManager.getLogger(CompareDbStructure.class);

    // -------------------------------------------------------------------------
    // Internal schema representation
    // -------------------------------------------------------------------------

    record ColumnInfo(
            int position,
            String dataType,
            Integer characterMaxLength,
            Integer numericPrecision,
            Integer numericScale,
            String isNullable,
            String columnDefault
    ) {
        String typeDisplay() {
            if (characterMaxLength != null) return dataType + "(" + characterMaxLength + ")";
            if (numericPrecision != null && numericScale != null && numericScale > 0)
                return dataType + "(" + numericPrecision + "," + numericScale + ")";
            if (numericPrecision != null) return dataType + "(" + numericPrecision + ")";
            return dataType;
        }
    }

    record TableInfo(
            Map<String, ColumnInfo> columns,   // column_name -> info (insertion-ordered)
            Map<String, String> indexes,        // index_name  -> indexdef
            Map<String, String> constraints     // constraint_name -> "TYPE(cols)"
    ) {}

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public CompareDbStructure() {
        super();
    }

    public CompareDbStructure(PrintStream out) {
        super(out);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs the comparison described by {@code configFile} and writes the Markdown
     * report to this instance's output stream.
     *
     * @param configFile path to the JSON comparison configuration file
     * @return {@code true} if the comparison completed without errors
     */
    public boolean compare(String configFile) {
        log.info("compare : configFile=[{}]", configFile);

        JsonCompareConfig config;
        try {
            config = DbConnectionUtils.loadCompareConfig(configFile);
        } catch (IOException e) {
            log.error("Failed to load config [{}]: {}", configFile, e.getMessage());
            return false;
        }

        List<String> schemas = (config.schemas != null && !config.schemas.isEmpty())
                ? config.schemas
                : List.of("public");

        log.info("Schemas to compare: {}", schemas);

        try (Connection src = DbConnectionUtils.getConnection(config.source);
             Connection tgt = DbConnectionUtils.getConnection(config.target)) {

            if (src == null || tgt == null) {
                log.error("Failed to establish one or both database connections");
                return false;
            }

            Map<String, TableInfo> srcSchema = loadSchema(src, schemas);
            Map<String, TableInfo> tgtSchema = loadSchema(tgt, schemas);

            renderMarkdown(config, schemas, srcSchema, tgtSchema);
            return true;

        } catch (SQLException e) {
            log.error("Database error during comparison: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Schema loading
    // -------------------------------------------------------------------------

    private Map<String, TableInfo> loadSchema(Connection conn, List<String> schemas) throws SQLException {
        Map<String, TableInfo> result = new TreeMap<>();
        String inClause = schemaInClause(schemas);

        // -- tables --
        String tablesSql =
                "SELECT table_schema, table_name FROM information_schema.tables " +
                "WHERE table_schema IN (" + inClause + ") AND table_type = 'BASE TABLE' " +
                "ORDER BY table_schema, table_name";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(tablesSql)) {
            while (rs.next()) {
                String key = tableKey(rs.getString("table_schema"), rs.getString("table_name"), schemas);
                result.put(key, new TableInfo(new LinkedHashMap<>(), new TreeMap<>(), new TreeMap<>()));
            }
        }

        // -- columns --
        String colSql =
                "SELECT table_schema, table_name, column_name, ordinal_position, " +
                "       data_type, character_maximum_length, numeric_precision, numeric_scale, " +
                "       is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_schema IN (" + inClause + ") " +
                "ORDER BY table_schema, table_name, ordinal_position";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(colSql)) {
            while (rs.next()) {
                String key = tableKey(rs.getString("table_schema"), rs.getString("table_name"), schemas);
                TableInfo ti = result.get(key);
                if (ti == null) continue;
                ti.columns().put(rs.getString("column_name"), new ColumnInfo(
                        rs.getInt("ordinal_position"),
                        rs.getString("data_type"),
                        intOrNull(rs, "character_maximum_length"),
                        intOrNull(rs, "numeric_precision"),
                        intOrNull(rs, "numeric_scale"),
                        rs.getString("is_nullable"),
                        rs.getString("column_default")
                ));
            }
        }

        // -- indexes (PostgreSQL-specific pg_indexes view) --
        String idxSql =
                "SELECT schemaname, tablename, indexname, indexdef " +
                "FROM pg_indexes WHERE schemaname IN (" + inClause + ") " +
                "ORDER BY tablename, indexname";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(idxSql)) {
            while (rs.next()) {
                String key = tableKey(rs.getString("schemaname"), rs.getString("tablename"), schemas);
                TableInfo ti = result.get(key);
                if (ti != null) {
                    ti.indexes().put(rs.getString("indexname"), rs.getString("indexdef"));
                }
            }
        }

        // -- constraints: use pg_constraint + pg_get_constraintdef for full SQL definitions
        // (information_schema loses the CHECK expression and FK target details)
        String conSql =
                "SELECT n.nspname AS table_schema, t.relname AS table_name, " +
                "       c.conname AS constraint_name, " +
                "       pg_get_constraintdef(c.oid) AS definition " +
                "FROM pg_constraint c " +
                "JOIN pg_class t     ON t.oid = c.conrelid " +
                "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                "WHERE n.nspname IN (" + inClause + ") " +
                "  AND c.contype IN ('p','u','f','c') " +
                "ORDER BY t.relname, c.contype, c.conname";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(conSql)) {
            while (rs.next()) {
                String key = tableKey(rs.getString("table_schema"), rs.getString("table_name"), schemas);
                TableInfo ti = result.get(key);
                if (ti != null) {
                    ti.constraints().put(rs.getString("constraint_name"), rs.getString("definition"));
                }
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Markdown rendering
    // -------------------------------------------------------------------------

    private void renderMarkdown(JsonCompareConfig config, List<String> schemas,
                                Map<String, TableInfo> src, Map<String, TableInfo> tgt) {
        PrintStream out = this.output;

        out.println("# DB Structure Comparison");
        out.println();
        out.println("| | |");
        out.println("|---|---|");
        out.println("| **Source** | `" + config.source.db_name + "` on `"
                + config.source.server + ":" + config.source.port + "` |");
        out.println("| **Target** | `" + config.target.db_name + "` on `"
                + config.target.server + ":" + config.target.port + "` |");
        out.println("| **Schemas** | `" + String.join("`, `", schemas) + "` |");
        out.println("| **Date** | " + LocalDate.now() + " |");
        out.println();
        out.println("---");
        out.println();

        Set<String> srcOnly = new TreeSet<>(src.keySet());
        srcOnly.removeAll(tgt.keySet());

        Set<String> tgtOnly = new TreeSet<>(tgt.keySet());
        tgtOnly.removeAll(src.keySet());

        Set<String> common = new TreeSet<>(src.keySet());
        common.retainAll(tgt.keySet());

        List<String> tablesWithDiffs = new ArrayList<>();
        List<String> identicalTables = new ArrayList<>();
        for (String table : common) {
            if (hasDifferences(src.get(table), tgt.get(table))) {
                tablesWithDiffs.add(table);
            } else {
                identicalTables.add(table);
            }
        }

        // Summary table
        out.println("## Summary");
        out.println();
        out.println("| Category | Count |");
        out.println("|---|---|");
        out.println("| Tables only in SOURCE | " + srcOnly.size() + " |");
        out.println("| Tables only in TARGET | " + tgtOnly.size() + " |");
        out.println("| Tables with differences | " + tablesWithDiffs.size() + " |");
        out.println("| Identical tables | " + identicalTables.size() + " |");
        out.println();
        out.println("---");
        out.println();

        if (!srcOnly.isEmpty()) {
            out.println("## Tables only in SOURCE");
            out.println();
            srcOnly.forEach(t -> out.println("- `" + t + "`"));
            out.println();
        }

        if (!tgtOnly.isEmpty()) {
            out.println("## Tables only in TARGET");
            out.println();
            tgtOnly.forEach(t -> out.println("- `" + t + "`"));
            out.println();
        }

        if (!tablesWithDiffs.isEmpty()) {
            out.println("## Table Differences");
            out.println();
            for (String table : tablesWithDiffs) {
                renderTableDiff(out, table, src.get(table), tgt.get(table));
            }
        }

        if (!identicalTables.isEmpty()) {
            out.println("## Identical Tables");
            out.println();
            identicalTables.forEach(t -> out.println("- `" + t + "`"));
            out.println();
        }
    }

    private void renderTableDiff(PrintStream out, String table, TableInfo src, TableInfo tgt) {
        out.println("### `" + table + "`");
        out.println();

        Set<String> srcOnlyCols = new TreeSet<>(src.columns().keySet());
        srcOnlyCols.removeAll(tgt.columns().keySet());

        Set<String> tgtOnlyCols = new TreeSet<>(tgt.columns().keySet());
        tgtOnlyCols.removeAll(src.columns().keySet());

        Set<String> commonCols = new TreeSet<>(src.columns().keySet());
        commonCols.retainAll(tgt.columns().keySet());

        if (!srcOnlyCols.isEmpty()) {
            out.println("#### Columns only in SOURCE");
            out.println();
            out.println("| Column | Type | Nullable | Default |");
            out.println("|---|---|---|---|");
            for (String col : srcOnlyCols) {
                ColumnInfo ci = src.columns().get(col);
                out.println("| `" + col + "` | " + ci.typeDisplay() + " | "
                        + ci.isNullable() + " | " + str(ci.columnDefault()) + " |");
            }
            out.println();
        }

        if (!tgtOnlyCols.isEmpty()) {
            out.println("#### Columns only in TARGET");
            out.println();
            out.println("| Column | Type | Nullable | Default |");
            out.println("|---|---|---|---|");
            for (String col : tgtOnlyCols) {
                ColumnInfo ci = tgt.columns().get(col);
                out.println("| `" + col + "` | " + ci.typeDisplay() + " | "
                        + ci.isNullable() + " | " + str(ci.columnDefault()) + " |");
            }
            out.println();
        }

        List<String[]> propDiffs = new ArrayList<>();
        for (String col : commonCols) {
            ColumnInfo s = src.columns().get(col);
            ColumnInfo t = tgt.columns().get(col);
            if (s.position() != t.position())
                propDiffs.add(row(col, "position", s.position(), t.position()));
            if (!Objects.equals(s.dataType(), t.dataType()))
                propDiffs.add(row(col, "data_type", s.dataType(), t.dataType()));
            if (!Objects.equals(s.characterMaxLength(), t.characterMaxLength()))
                propDiffs.add(row(col, "character_max_length", s.characterMaxLength(), t.characterMaxLength()));
            if (!Objects.equals(s.numericPrecision(), t.numericPrecision()))
                propDiffs.add(row(col, "numeric_precision", s.numericPrecision(), t.numericPrecision()));
            if (!Objects.equals(s.numericScale(), t.numericScale()))
                propDiffs.add(row(col, "numeric_scale", s.numericScale(), t.numericScale()));
            if (!Objects.equals(s.isNullable(), t.isNullable()))
                propDiffs.add(row(col, "is_nullable", s.isNullable(), t.isNullable()));
            if (!Objects.equals(s.columnDefault(), t.columnDefault()))
                propDiffs.add(row(col, "column_default", s.columnDefault(), t.columnDefault()));
        }

        if (!propDiffs.isEmpty()) {
            out.println("#### Column property differences");
            out.println();
            out.println("| Column | Property | SOURCE | TARGET |");
            out.println("|---|---|---|---|");
            propDiffs.forEach(d -> out.println("| `" + d[0] + "` | " + d[1] + " | " + d[2] + " | " + d[3] + " |"));
            out.println();
        }

        renderMapDiff(out, "Index", src.indexes(), tgt.indexes());
        renderMapDiff(out, "Constraint", src.constraints(), tgt.constraints());
    }

    private void renderMapDiff(PrintStream out, String label, Map<String, String> src, Map<String, String> tgt) {
        Set<String> srcOnlyKeys = new TreeSet<>(src.keySet());
        srcOnlyKeys.removeAll(tgt.keySet());

        Set<String> tgtOnlyKeys = new TreeSet<>(tgt.keySet());
        tgtOnlyKeys.removeAll(src.keySet());

        List<String> changed = src.keySet().stream()
                .filter(tgt::containsKey)
                .filter(k -> !src.get(k).equals(tgt.get(k)))
                .sorted()
                .collect(Collectors.toList());

        if (srcOnlyKeys.isEmpty() && tgtOnlyKeys.isEmpty() && changed.isEmpty()) return;

        out.println("#### " + label + " differences");
        out.println();

        // Source-only and target-only sections: sort rows by definition so that
        // constraints with different names but identical definitions appear adjacent,
        // making visual matching straightforward.
        if (!srcOnlyKeys.isEmpty() || !tgtOnlyKeys.isEmpty()) {
            List<String[]> srcRows = srcOnlyKeys.stream()
                    .map(k -> new String[]{k, src.get(k)})
                    .sorted(Comparator.comparing(r -> r[1]))
                    .collect(Collectors.toList());
            List<String[]> tgtRows = tgtOnlyKeys.stream()
                    .map(k -> new String[]{k, tgt.get(k)})
                    .sorted(Comparator.comparing(r -> r[1]))
                    .collect(Collectors.toList());

            out.println("| " + label + " | Side | Definition |");
            out.println("|---|---|---|");
            srcRows.forEach(r -> out.println("| `" + r[0] + "` | SOURCE | `" + r[1] + "` |"));
            tgtRows.forEach(r -> out.println("| `" + r[0] + "` | TARGET | `" + r[1] + "` |"));
            out.println();
        }

        if (!changed.isEmpty()) {
            out.println("| " + label + " | Side | Definition |");
            out.println("|---|---|---|");
            changed.forEach(k -> {
                out.println("| `" + k + "` | SOURCE (changed) | `" + src.get(k) + "` |");
                out.println("| `" + k + "` | TARGET (changed) | `" + tgt.get(k) + "` |");
            });
            out.println();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean hasDifferences(TableInfo src, TableInfo tgt) {
        if (!src.columns().keySet().equals(tgt.columns().keySet())) return true;
        for (String col : src.columns().keySet()) {
            if (!columnsEqual(src.columns().get(col), tgt.columns().get(col))) return true;
        }
        if (!src.indexes().equals(tgt.indexes())) return true;
        if (!src.constraints().equals(tgt.constraints())) return true;
        return false;
    }

    private boolean columnsEqual(ColumnInfo a, ColumnInfo b) {
        return b != null
                && a.position() == b.position()
                && Objects.equals(a.dataType(), b.dataType())
                && Objects.equals(a.characterMaxLength(), b.characterMaxLength())
                && Objects.equals(a.numericPrecision(), b.numericPrecision())
                && Objects.equals(a.numericScale(), b.numericScale())
                && Objects.equals(a.isNullable(), b.isNullable())
                && Objects.equals(a.columnDefault(), b.columnDefault());
    }

    private String schemaInClause(List<String> schemas) {
        return schemas.stream()
                .map(s -> "'" + s.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
    }

    private String tableKey(String schema, String table, List<String> schemas) {
        return schemas.size() > 1 ? schema + "." + table : table;
    }

    private Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int val = rs.getInt(col);
        return rs.wasNull() ? null : val;
    }

    private String str(Object val) {
        return val == null ? "_null_" : String.valueOf(val);
    }

    private String[] row(String col, String prop, Object src, Object tgt) {
        return new String[]{col, prop, str(src), str(tgt)};
    }
}
