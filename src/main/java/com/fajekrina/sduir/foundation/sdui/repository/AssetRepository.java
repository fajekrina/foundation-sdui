package com.fajekrina.sduir.foundation.sdui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/// The pictures (`sdui_asset`): reads and writes only, no decision — what may be stored is
/// `AssetService`'s.
@Repository
public class AssetRepository {
    private static final String SELECT_META = """
        SELECT asset_id AS "name", content_type AS "contentType", byte_size AS "size", source AS "source", source_url AS "sourceUrl",
               original_name AS "originalName", created_by AS "createdBy", created_at AS "createdAt", published_at AS "publishedAt"
        FROM sdui_asset""";

    private final JdbcTemplate jdbc;

    public AssetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean exists(String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_asset WHERE asset_id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /// whether picture `id` belongs to one of the two spaces
    public boolean existsIn(String mid, String other, String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_asset WHERE merchant_id IN (?, ?) AND asset_id = ?", Integer.class, mid, other, id);
        return n != null && n > 0;
    }

    public void insert(String id, String mid, String contentType, int size, byte[] content, String contentHash, String source,
                       String sourceUrl, String originalName, String createdBy) {
        jdbc.update("""
            INSERT INTO sdui_asset (asset_id, merchant_id, content_type, byte_size, content, content_hash, source, source_url, original_name, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            id, mid, contentType, size, content, contentHash, source, sourceUrl, originalName, createdBy);
    }

    /// a space's pictures, newest first (no bytes)
    public List<Map<String, Object>> list(String mid) {
        return jdbc.queryForList(SELECT_META + " WHERE merchant_id = ? ORDER BY created_at DESC, asset_id", mid);
    }

    /// one picture's metadata
    public Map<String, Object> meta(String mid, String id) {
        return jdbc.queryForMap(SELECT_META + " WHERE merchant_id = ? AND asset_id = ?", mid, id);
    }

    /// `{ CONTENT_TYPE, CONTENT }` of picture `id` in one of the two spaces, or null
    public Map<String, Object> content(String mid, String other, String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_type, content FROM sdui_asset WHERE merchant_id IN (?, ?) AND asset_id = ?", mid, other, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// mark picture `id` (of one of the two spaces) public, once
    public void publish(String mid, String other, String id) {
        jdbc.update("UPDATE sdui_asset SET published_at = CURRENT_TIMESTAMP WHERE merchant_id IN (?, ?) AND asset_id = ? AND published_at IS NULL", mid, other, id);
    }
}
