/*
 * Data Tools - MigrateData: copies tables from a source DB to a target DB.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import it.brunasti.dataTools.utils.DbConnectionUtils;
import it.brunasti.dataTools.utils.JsonMigrateConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrates a set of tables from a source database to a target database.
 *
 * <p>Tables are migrated in the order defined in the JSON configuration file,
 * ensuring that referenced tables are populated before tables that depend on them.
 * Optionally, target tables are truncated before inserting data.
 *
 * @author Paolo Brunasti
 */
public class MigrateData extends BaseExecutor {

    static Logger log = LogManager.getLogger(MigrateData.class);

    private static final int BATCH_SIZE = 500;

    public MigrateData() {
        super();
    }

    public MigrateData(PrintStream output) {
        super(output);
    }

    /**
     * Runs the migration defined by the given configuration file.
     *
     * @param configFile path to the JSON migration configuration file
     * @param cleanFirst when true, target tables are truncated before inserting
     * @return true if migration completed without errors
     */
    public boolean migrate(String configFile, boolean cleanFirst) {
        log.info("migrate : config=[{}] cleanFirst=[{}]", configFile, cleanFirst);

        JsonMigrateConfig config;
        try {
            config = DbConnectionUtils.loadMigrateConfig(configFile);
        } catch (IOException e) {
            log.error("Failed to load configuration file [{}]: {}", configFile, e.getMessage());
            output.println("ERROR: Cannot load configuration file: " + configFile);
            return false;
        }

        if (config.getSource() == null || config.getTarget() == null) {
            log.error("Configuration must define both 'source' and 'target' DB entries");
            output.println("ERROR: Configuration must define both 'source' and 'target'.");
            return false;
        }

        if (config.getTables() == null || config.getTables().isEmpty()) {
            log.warn("No tables defined in configuration — nothing to migrate");
            output.println("WARNING: No tables listed in configuration.");
            return true;
        }

        output.println("=== MigrateData ===");
        output.println("Source : " + config.getSource().getName() + " / " + config.getSource().getDb_name());
        output.println("Target : " + config.getTarget().getName() + " / " + config.getTarget().getDb_name());
        output.println("Tables : " + config.getTables().size());
        output.println("Clean  : " + cleanFirst);
        output.println("-------------------");

        try (Connection sourceConn = DbConnectionUtils.getConnection(config.getSource());
             Connection targetConn = DbConnectionUtils.getConnection(config.getTarget())) {

            if (sourceConn == null) {
                log.error("Could not establish connection to source database");
                output.println("ERROR: Cannot connect to source database.");
                return false;
            }
            if (targetConn == null) {
                log.error("Could not establish connection to target database");
                output.println("ERROR: Cannot connect to target database.");
                return false;
            }

            // Disable auto-commit on target for batch performance
            targetConn.setAutoCommit(false);

            boolean allOk = true;
            for (String table : config.getTables()) {
                boolean ok = migrateTable(sourceConn, targetConn, table, cleanFirst);
                if (!ok) {
                    allOk = false;
                    log.error("Migration failed for table [{}]", table);
                    output.println("ERROR: Migration failed for table: " + table);
                }
            }

            targetConn.commit();
            output.println("-------------------");
            output.println("Migration " + (allOk ? "COMPLETED successfully." : "COMPLETED WITH ERRORS."));
            return allOk;

        } catch (SQLException e) {
            log.error("Database error during migration: {}", e.getMessage());
            output.println("ERROR: " + e.getMessage());
            return false;
        }
    }

    private boolean migrateTable(Connection source, Connection target,
                                 String table, boolean cleanFirst) {
        log.info("migrateTable : [{}]", table);
        output.print("  Table [" + table + "] ... ");

        try {
            if (cleanFirst) {
                cleanTable(target, table);
            }

            long rows = copyTable(source, target, table);
            output.println(rows + " rows migrated.");
            log.info("migrateTable [{}] : {} rows migrated", table, rows);
            return true;

        } catch (SQLException e) {
            output.println("FAILED: " + e.getMessage());
            log.error("migrateTable [{}] failed: {}", table, e.getMessage());
            return false;
        }
    }

    private void cleanTable(Connection target, String table) throws SQLException {
        log.info("cleanTable : [{}]", table);
        String sql = "DELETE FROM " + table;
        try (Statement stmt = target.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            log.info("cleanTable [{}] : {} rows deleted", table, deleted);
        }
    }

    private long copyTable(Connection source, Connection target, String table) throws SQLException {
        String selectSql = "SELECT * FROM " + table;

        try (Statement srcStmt = source.createStatement();
             ResultSet rs = srcStmt.executeQuery(selectSql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            List<String> columns = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnName(i));
            }

            String insertSql = buildInsertSql(table, columns);
            log.debug("copyTable [{}] INSERT sql: {}", table, insertSql);

            long rowCount = 0;
            try (PreparedStatement insertStmt = target.prepareStatement(insertSql)) {
                while (rs.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        // setObject handles all JDBC-compatible types transparently
                        insertStmt.setObject(i, rs.getObject(i));
                    }
                    insertStmt.addBatch();
                    rowCount++;

                    if (rowCount % BATCH_SIZE == 0) {
                        insertStmt.executeBatch();
                        log.debug("copyTable [{}] : flushed batch at row {}", table, rowCount);
                    }
                }
                // flush remaining rows
                if (rowCount % BATCH_SIZE != 0) {
                    insertStmt.executeBatch();
                }
            }
            return rowCount;
        }
    }

    private String buildInsertSql(String table, List<String> columns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(table).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(columns.get(i));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }
}
