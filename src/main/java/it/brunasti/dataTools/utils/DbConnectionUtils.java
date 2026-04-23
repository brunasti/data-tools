/*
 * Data Tools - DB connection utilities.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools.utils;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnectionUtils {

    static Logger log = LogManager.getLogger(DbConnectionUtils.class);

    public static JsonMigrateConfig loadMigrateConfig(String jsonFile) throws IOException {
        log.info("loadMigrateConfig : {}", jsonFile);
        Gson gson = new Gson();
        try (Reader reader = new FileReader(jsonFile)) {
            return gson.fromJson(reader, JsonMigrateConfig.class);
        }
    }

    public static Connection getConnection(JsonDbRecord dbRecord) {
        log.info("getConnection : {}", dbRecord.name);
        return getConnection(dbRecord.db_name, dbRecord.login, dbRecord.password,
                dbRecord.server, dbRecord.port);
    }

    public static Connection getConnection(String dbName, String userName, String password,
                                           String hostname, String port) {
        log.debug("getConnection : db=[{}] host=[{}] port=[{}] user=[{}]", dbName, hostname, port, userName);
        try {
            String encodedPassword;
            try {
                encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8).replace("+", "%20");
            } catch (Exception ex) {
                log.warn("Password encoding failed, using raw password: {}", ex.getMessage());
                encodedPassword = password;
            }

            Class.forName("org.postgresql.Driver");

            String jdbcUrl = "jdbc:postgresql://" + hostname + ":" + port + "/" + dbName
                    + "?user=" + userName + "&password=" + encodedPassword;
            log.info("Connecting to: jdbc:postgresql://{}:{}/{}", hostname, port, dbName);
            Connection con = DriverManager.getConnection(jdbcUrl);
            log.info("Connection successful to: {}", dbName);
            return con;
        } catch (ClassNotFoundException | SQLException e) {
            log.error("Failed to connect to database [{}]: {}", dbName, e.getMessage());
            return null;
        }
    }
}
