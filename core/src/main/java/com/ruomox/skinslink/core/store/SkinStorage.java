package com.ruomox.skinslink.core.store;

import com.ruomox.skinslink.core.model.SkinRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkinStorage {
    void init();
    CompletableFuture<Void> save(SkinRecord record);
    CompletableFuture<Optional<SkinRecord>> load(UUID userID);
    void shutdown();
}