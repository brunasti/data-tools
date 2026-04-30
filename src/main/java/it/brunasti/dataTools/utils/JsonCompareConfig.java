/*
 * Data Tools - CompareDbStructure configuration model.
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
public class JsonCompareConfig {
    public JsonDbRecord source;
    public JsonDbRecord target;
    /** Optional list of schemas to compare. Defaults to ["public"] when null or empty. */
    public List<String> schemas;
    /** Optional default output file for the Markdown report. Overridden by the -o CLI option. */
    public String output_file;
}
