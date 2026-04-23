/*
 * Data Tools - Migration configuration model.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@ToString
@Getter
@Setter
public class JsonMigrateConfig {
    public JsonDbRecord source;
    public JsonDbRecord target;
    public List<String> tables;
}
