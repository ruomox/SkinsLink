package com.ruomox.skinslink.core.api;

import org.jetbrains.annotations.Nullable;

public record SkinProfile(String value, @Nullable String signature) {

    public boolean isSigned() {
        return signature != null && !signature.isEmpty();
    }

    public static SkinProfile unsigned(String value) {
        return new SkinProfile(value, null);
    }
}
