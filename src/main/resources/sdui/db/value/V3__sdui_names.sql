-- foundation-sdui: every table is named sdui_*; delivery reads the *_read views only (a
-- read-only user in production).
ALTER TABLE merchant RENAME TO sdui_merchant;
ALTER TABLE screen RENAME TO sdui_screen;
ALTER TABLE value_revision RENAME TO sdui_value_revision;
ALTER TABLE value_release RENAME TO sdui_value_release;
ALTER TABLE manifest RENAME TO sdui_manifest;
ALTER TABLE audit_log RENAME TO sdui_audit_log;
ALTER TABLE asset RENAME TO sdui_asset;
-- the CHECK constraints are named after their table too
ALTER TABLE sdui_asset DROP CONSTRAINT ck_asset_source;
ALTER TABLE sdui_asset DROP CONSTRAINT ck_asset_type;
ALTER TABLE sdui_asset ADD CONSTRAINT ck_sdui_asset_source CHECK (source IN ('UPLOAD', 'URL'));
ALTER TABLE sdui_asset ADD CONSTRAINT ck_sdui_asset_type CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/gif'));

CREATE VIEW sdui_merchant_read AS
    SELECT merchant_id, name, status FROM sdui_merchant;

CREATE VIEW sdui_screen_read AS
    SELECT merchant_id, screen_key, template_key, active_sequence FROM sdui_screen;

CREATE VIEW sdui_value_release_read AS
    SELECT merchant_id, screen_key, sequence, template_key, template_version, envelope FROM sdui_value_release;

CREATE VIEW sdui_manifest_read AS
    SELECT merchant_id, sequence, envelope FROM sdui_manifest;

-- only what delivery may serve: published pictures
CREATE VIEW sdui_asset_read AS
    SELECT asset_id, content_type, content_hash, content FROM sdui_asset WHERE published_at IS NOT NULL;
