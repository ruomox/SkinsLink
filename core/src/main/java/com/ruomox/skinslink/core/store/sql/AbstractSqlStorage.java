package com.ruomox.skinslink.core.store.sql;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.store.SkinStorage;
import static com.ruomox.skinslink.core.util.LogUtil.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQL 数据库通用基类
 * 负责异步初始化数据库、保存与读取 SkinRecord，
 * 并统一处理 JDBC 连接、预编译语句与日志输出。
 */
public abstract class AbstractSqlStorage implements SkinStorage {

    protected final Logger logger;
    protected final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    protected AbstractSqlStorage(Logger logger) {
        this.logger = logger;
    }

    // --- 抽象层 ---
    protected abstract Connection getConnection() throws SQLException;
    protected abstract String getCreateTableSql();
    protected abstract String getSaveSql();
    protected abstract String getSelectSql();
    protected abstract void bindSaveStatement(PreparedStatement ps, SkinRecord r) throws SQLException;

    // --- 核心逻辑 ---

    @Override
    public void init() {
        ioExecutor.submit(() -> {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(getCreateTableSql());
                info(logger, "[DB] Initialized successfully.");
            } catch (SQLException e) {
                error(logger, "[DB] Initialization failed!", e);
            }
        });
    }

    @Override
    public void shutdown() {
        ioExecutor.shutdown();
    }

    @Override
    public CompletableFuture<Void> save(SkinRecord r) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(getSaveSql())) {

                bindSaveStatement(ps, r);
                ps.executeUpdate();

                // 可选：开启 debug 时打印保存成功
                debug(logger, "[DB] Saved record for: " + r.userID());

            } catch (SQLException e) {
                error(logger, "[DB] Save failed for: " + r.userID(), e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Optional<SkinRecord>> load(UUID userID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(getSelectSql())) {

                ps.setString(1, userID.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new SkinRecord(
                                UUID.fromString(rs.getString("user_uuid")),
                                rs.getString("skin_id"),
                                rs.getString("skin_url"),
                                rs.getString("skin_value"),
                                rs.getString("skin_key"),
                                rs.getString("skin_auth"),
                                rs.getString("url_hash"),
                                rs.getString("skin_hash"),
                                rs.getString("skin_time")
                        ));
                    }
                }
            } catch (SQLException e) {
                error(logger, "[DB] Load failed for: " + userID, e);
            }
            return Optional.empty();
        }, ioExecutor);
    }
}