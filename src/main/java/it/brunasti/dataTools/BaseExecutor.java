/*
 * Data Tools - Generic base executor.
 *
 * Copyright (c) 2026.
 * Paolo Brunasti
 */
package it.brunasti.dataTools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;

public abstract class BaseExecutor {

    protected PrintStream output;

    static Logger log = LogManager.getLogger(BaseExecutor.class);

    protected BaseExecutor() {
        this.output = System.out;
    }

    protected BaseExecutor(PrintStream output) {
        this.output = output;
    }
}
