package com.ruomox.skinslink.core.api;

public interface Logger {
    void info(String message);
    void warn(String message);
    void error(String message);
    void error(String message, Throwable t);
}
