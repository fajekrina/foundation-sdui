package com.fajekrina.sduir.foundation.sdui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/// The page spaces (`sdui_merchant`; a kanal, the API still calls it a merchant): reads and
/// writes only, no decision — who may reach or make one is `Merchants`'.
@Repository
public class MerchantRepository {
    private final JdbcTemplate jdbc;

    public MerchantRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /// every space but `except`: `{ merchantId, name, manifestSequence }`, by id
    public List<Map<String, Object>> listExcept(String except) {
        return jdbc.queryForList("SELECT merchant_id AS \"merchantId\", name AS \"name\", manifest_sequence AS \"manifestSequence\" FROM sdui_merchant WHERE merchant_id <> ? ORDER BY merchant_id", except);
    }

    public boolean exists(String mid) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_merchant WHERE merchant_id = ?", Integer.class, mid);
        return n != null && n > 0;
    }

    public boolean isActive(String mid) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_merchant WHERE merchant_id = ? AND status = 'ACTIVE'", Integer.class, mid);
        return n != null && n > 0;
    }

    /// ids equal to `mid` but for letter case
    public List<String> idsIgnoringCase(String mid) {
        return jdbc.queryForList("SELECT merchant_id FROM sdui_merchant WHERE LOWER(merchant_id) = LOWER(?)", String.class, mid);
    }

    public void insert(String mid, String name) {
        jdbc.update("INSERT INTO sdui_merchant (merchant_id, name) VALUES (?, ?)", mid, name);
    }

    /// insert the space unless another request made it first
    public void insertIfMissing(String mid, String name) {
        jdbc.update("MERGE INTO sdui_merchant m USING (SELECT CAST(? AS VARCHAR(128)) AS merchant_id, CAST(? AS VARCHAR(256)) AS name FROM DUAL) s ON (m.merchant_id = s.merchant_id) "
            + "WHEN NOT MATCHED THEN INSERT (merchant_id, name) VALUES (s.merchant_id, s.name)", mid, name);
    }

    /// Take the space's next manifest sequence under its row lock (monotonic per space).
    public long nextManifestSequence(String mid) {
        long next = jdbc.queryForObject("SELECT manifest_sequence FROM sdui_merchant WHERE merchant_id = ? FOR UPDATE", Long.class, mid) + 1;
        jdbc.update("UPDATE sdui_merchant SET manifest_sequence = ? WHERE merchant_id = ?", next, mid);
        return next;
    }
}
