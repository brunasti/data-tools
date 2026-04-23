/*
 * Data Tools - DB connection record.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class JsonDbRecord {
    public String name;
    public String server;
    public String port;
    public String db_name;
    public String login;
    public String password;
}
