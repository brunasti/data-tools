/*
 * Data Tools - CompareDbStructure CLI entry point.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import it.brunasti.dataTools.utils.DbConnectionUtils;
import it.brunasti.dataTools.utils.JsonCompareConfig;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * CLI entry point for the CompareDbStructure tool.
 *
 * <p>Reads a JSON configuration file that defines source DB, target DB and an
 * optional list of schemas to compare (defaults to {@code public}). Outputs a
 * Markdown report of all structural differences to stdout or to a file.
 *
 * <p>Usage:
 * <pre>
 *   java it.brunasti.dataTools.CompareDbStructureMain -c compare-config.json [-o report.md]
 * </pre>
 *
 * @author Paolo Brunasti
 */
public class CompareDbStructureMain extends BaseMain {

    static Logger log = LogManager.getLogger(CompareDbStructureMain.class);

    private static CommandLine commandLine;
    private static String configurationFile = "";
    private static String outputFile = "";

    protected static void reset() {
        configurationFile = "";
        outputFile = "";
        options = null;
    }

    protected static boolean processCommandLine(String[] args) {
        reset();

        options = new Options();

        Option optionHelp       = new Option("h", "help",   false, "Print this help message");
        Option optionShortUsage = new Option("?", false,           "Quick reference");
        Option optionConfigFile = new Option("c", "config", true,  "Path to JSON comparison configuration file");
        Option optionOutputFile = new Option("o", "output", true,  "Output file path for the Markdown report (overrides 'output_file' in config; default: stdout)");

        options.addOption(optionHelp);
        options.addOption(optionShortUsage);
        options.addOption(optionConfigFile);
        options.addOption(optionOutputFile);

        try {
            CommandLineParser parser = new DefaultParser();
            commandLine = parser.parse(options, args);

            if (commandLine.hasOption("h")) {
                printHelp(options, CompareDbStructureMain.class.getCanonicalName());
                return false;
            }

            if (commandLine.hasOption("?")) {
                printUsage(options, CompareDbStructureMain.class.getCanonicalName());
                return false;
            }

            if (!commandLine.hasOption(optionConfigFile.getOpt())) {
                System.err.println("ERROR: Configuration file (-c) is required.");
                printHelp(options, CompareDbStructureMain.class.getCanonicalName());
                return false;
            }

            configurationFile = commandLine.getOptionValue(optionConfigFile.getOpt());
            log.debug("configurationFile set to [{}]", configurationFile);

            if (commandLine.hasOption(optionOutputFile.getOpt())) {
                outputFile = commandLine.getOptionValue(optionOutputFile.getOpt());
                log.debug("outputFile set to [{}]", outputFile);
            }

        } catch (ParseException | NullPointerException e) {
            System.err.println(e.getMessage());
            printHelp(options, CompareDbStructureMain.class.getCanonicalName());
            return false;
        }
        return true;
    }

    /**
     * CLI entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        boolean cliIsCorrect = processCommandLine(args);
        log.debug("CommandLine parsed [{}]", cliIsCorrect);

        if (!cliIsCorrect) {
            return;
        }

        log.info("configurationFile [{}]", configurationFile);

        // If -o was not given on the CLI, fall back to output_file from the config
        if (outputFile.isEmpty()) {
            try {
                JsonCompareConfig config = DbConnectionUtils.loadCompareConfig(configurationFile);
                if (config.output_file != null && !config.output_file.isBlank()) {
                    outputFile = config.output_file;
                    log.info("outputFile resolved from config: [{}]", outputFile);
                }
            } catch (IOException e) {
                // Ignore — compare() will report the load failure properly
            }
        }

        log.info("outputFile        [{}]", outputFile.isEmpty() ? "(stdout)" : outputFile);

        boolean success;

        if (!outputFile.isEmpty()) {
            try (PrintStream out = new PrintStream(new FileOutputStream(outputFile))) {
                success = new CompareDbStructure(out).compare(configurationFile);
            } catch (IOException e) {
                System.err.println("ERROR: Cannot open output file [" + outputFile + "]: " + e.getMessage());
                System.exit(1);
                return;
            }
        } else {
            success = new CompareDbStructure().compare(configurationFile);
        }

        System.exit(success ? 0 : 1);
    }

    /**
     * Programmatic entry point — writes report to stdout.
     *
     * @param configFile path to the JSON comparison configuration file
     * @return true if comparison completed without errors
     */
    public boolean exec(String configFile) {
        return new CompareDbStructure().compare(configFile);
    }

    /**
     * Programmatic entry point — writes report to a file.
     *
     * @param configFile path to the JSON comparison configuration file
     * @param outFile    path to the Markdown output file
     * @return true if comparison completed without errors
     */
    public boolean exec(String configFile, String outFile) {
        try (PrintStream out = new PrintStream(new FileOutputStream(outFile))) {
            return new CompareDbStructure(out).compare(configFile);
        } catch (IOException e) {
            log.error("Cannot open output file [{}]: {}", outFile, e.getMessage());
            return false;
        }
    }
}
