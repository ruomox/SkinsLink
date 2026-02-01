package com.ruomox.skinslink.core.store.sql;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.model.SkinRecord;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SQLiteStorage extends AbstractSqlStorage {

    private final String jdbcUrl;

    public SQLiteStorage(Logger logger, Path dataFolder) {
        super(logger);
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found!", e);
        }
        // 使用 resolve 组合路径，更安全
        this.jdbcUrl = "jdbc:sqlite:" + dataFolder.resolve("database.db").toAbsolutePath();
    }

    @Override
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    @Override
    protected String getCreateTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS skins_data (
                user_uuid  VARCHAR(36) PRIMARY KEY,
                skin_id    VARCHAR(36) NOT NULL,
                skin_url   TEXT,
                skin_value TEXT,
                skin_key   TEXT,
                skin_auth  VARCHAR(20) NOT NULL,
                url_hash   CHAR(64),
                skin_hash  CHAR(64),
                skin_time  VARCHAR(20) NOT NULL
            );
        """;
    }

    @Override
    protected String getSaveSql() {
        return """
            INSERT INTO skins_data 
            (user_uuid, skin_id, skin_url, skin_value, skin_key, skin_auth, url_hash, skin_hash, skin_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_uuid) DO UPDATE SET
                skin_id    = excluded.skin_id,
                skin_url   = excluded.skin_url,
                skin_value = excluded.skin_value,
                skin_key   = excluded.skin_key,
                skin_auth  = excluded.skin_auth,
                url_hash   = excluded.url_hash,
                skin_hash  = excluded.skin_hash,
                skin_time  = excluded.skin_time;
        """;
    }

    @Override
    protected String getSelectSql() {
        return "SELECT * FROM skins_data WHERE user_uuid = ?";
    }

    @Override
    protected void bindSaveStatement(PreparedStatement ps, SkinRecord r) throws SQLException {
        ps.setString(1, r.userID().toString());
        ps.setString(2, r.skinID());
        ps.setString(3, r.skinURL());
        ps.setString(4, r.skinValue());
        ps.setString(5, r.skinKey());
        ps.setString(6, r.skinAuth());
        ps.setString(7, r.urlHash());
        ps.setString(8, r.skinHash());
        ps.setString(9, r.skinTime());
    }
}