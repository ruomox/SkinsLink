package com.ruomox.skinslink.paper;

import com.ruomox.skinslink.core.api.Logger;
import java.util.logging.Level;

public class PaperLogger implements Logger {
    private final java.util.logging.Logger bukkitLogger;

    public PaperLogger(java.util.logging.Logger bukkitLogger) {
        this.bukkitLogger = bukkitLogger;
    }

    @Override
    public void info(String message) {
        bukkitLogger.info("[Core] " + message);
    }

    @Override
    public void warn(String message) {
        bukkitLogger.warning("[Core] " + message);
    }

    @Override
    public void error(String message) {
        bukkitLogger.severe("[Core] " + message);
    }

    @Override
    public void error(String message, Throwable t) {
        bukkitLogger.log(Level.SEVERE, "[Core] " + message, t);
    }
}