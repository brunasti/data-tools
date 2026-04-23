/*
 * Data Tools - MigrateData CLI entry point.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CLI entry point for the MigrateData tool.
 *
 * <p>Reads a JSON configuration file that defines source DB, target DB and the
 * ordered list of tables to migrate. Optionally cleans the target tables before
 * inserting data from the source.
 *
 * <p>Usage:
 * <pre>
 *   java it.brunasti.dataTools.MigrateDataMain -c migrate-config.json [-x]
 * </pre>
 *
 * <p>Uses <a href="https://commons.apache.org/proper/commons-cli/usage.html">commons-cli</a>
 * for command-line parsing.
 *
 * @author Paolo Brunasti
 */
public class MigrateDataMain extends BaseMain {

    static Logger log = LogManager.getLogger(MigrateDataMain.class);

    private static CommandLine commandLine;
    private static String configurationFile = "";
    private static boolean cleanFirst = false;

    protected static void reset() {
        configurationFile = "";
        cleanFirst = false;
        options = null;
    }

    protected static boolean processCommandLine(String[] args) {
        reset();

        options = new Options();

        Option optionHelp        = new Option("h", "help",    false, "Print this help message");
        Option optionShortUsage  = new Option("?", false,            "Quick reference");
        Option optionConfigFile  = new Option("c", "config",  true,  "Path to JSON migration configuration file");
        Option optionClean       = new Option("x", "clean",   false, "Truncate (DELETE) target tables before migrating");

        options.addOption(optionHelp);
        options.addOption(optionShortUsage);
        options.addOption(optionConfigFile);
        options.addOption(optionClean);

        try {
            CommandLineParser parser = new DefaultParser();
            commandLine = parser.parse(options, args);

            if (commandLine.hasOption("h")) {
                printHelp(options, MigrateDataMain.class.getCanonicalName());
                return false;
            }

            if (commandLine.hasOption("?")) {
                printUsage(options, MigrateDataMain.class.getCanonicalName());
                return false;
            }

            if (!commandLine.hasOption(optionConfigFile.getOpt())) {
                System.err.println("ERROR: Configuration file (-c) is required.");
                printHelp(options, MigrateDataMain.class.getCanonicalName());
                return false;
            }

            configurationFile = commandLine.getOptionValue(optionConfigFile.getOpt());
            log.debug("configurationFile set to [{}]", configurationFile);

            cleanFirst = commandLine.hasOption(optionClean.getOpt());
            log.debug("cleanFirst set to [{}]", cleanFirst);

        } catch (ParseException | NullPointerException e) {
            System.err.println(e.getMessage());
            printHelp(options, MigrateDataMain.class.getCanonicalName());
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
        log.info("cleanFirst        [{}]", cleanFirst);

        MigrateData migrateData = new MigrateData();
        boolean success = migrateData.migrate(configurationFile, cleanFirst);

        System.exit(success ? 0 : 1);
    }

    /**
     * Programmatic entry point (for use from other tools or tests).
     *
     * @param configFile path to the JSON migration configuration file
     * @param clean      when true, target tables are deleted before inserting
     * @return true if migration completed without errors
     */
    public boolean exec(String configFile, boolean clean) {
        MigrateData migrateData = new MigrateData();
        return migrateData.migrate(configFile, clean);
    }
}
