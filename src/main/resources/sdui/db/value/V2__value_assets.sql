-- Pictures makers put into image components (uploaded files or links copied once). A value
-- set only holds the asset id (`image.name`); the bytes live here. Immutable: an asset is
-- never changed (same bytes for the same merchant = same id), only marked published when
-- the first value release that uses it is approved — delivery serves published assets only.
CREATE TABLE asset (
    asset_id       VARCHAR(64)   PRIMARY KEY,                  -- img_<32 hex>, from merchant + content hash
    merchant_id    VARCHAR(128)  NOT NULL REFERENCES merchant(merchant_id),
    content_type   VARCHAR(64)   NOT NULL,                     -- image/png | image/jpeg | image/webp | image/gif
    byte_size      INT           NOT NULL,
    content        BLOB          NOT NULL,
    content_hash   VARCHAR(64)   NOT NULL,                     -- SHA-256 of content
    source         VARCHAR(16)   NOT NULL,                     -- UPLOAD | URL
    source_url     VARCHAR(2048),
    original_name  VARCHAR(256),
    created_by     VARCHAR(256)  NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP,
    CONSTRAINT uq_asset_content UNIQUE (merchant_id, content_hash),
    CONSTRAINT ck_asset_source CHECK (source IN ('UPLOAD', 'URL')),
    CONSTRAINT ck_asset_type CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/gif'))
);
CREATE INDEX ix_asset_merchant ON asset (merchant_id, created_at);
