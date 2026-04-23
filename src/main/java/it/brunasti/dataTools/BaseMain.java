/*
 * Data Tools - Generic base main.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;

public abstract class BaseMain {

    static Logger log = LogManager.getLogger(BaseMain.class);
    protected static Options options;

    protected static void reset() {
        options = null;
    }

    protected static void printHelp(Options opts, String className) {
        if (null == opts) {
            System.err.println("ERROR: No options provided to printHelp");
            return;
        }
        HelpFormatter helper = new HelpFormatter();
        PrintWriter outError = new PrintWriter(System.err);
        helper.printHelp(outError, 100, "java " + className, "", opts, 4, 4, "");
        outError.flush();
    }

    protected static void printUsage(Options opts, String className) {
        if (null == opts) {
            System.err.println("ERROR: No options provided to printUsage");
            return;
        }
        HelpFormatter helper = new HelpFormatter();
        PrintWriter outError = new PrintWriter(System.err);
        helper.printUsage(outError, 100, "java " + className, opts);
        outError.flush();
    }
}
