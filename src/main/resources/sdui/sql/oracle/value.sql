-- foundation-sdui — page tables for Oracle (23ai: BOOLEAN results in queries). The same final
-- state as the H2 Flyway scripts in sdui/db/value (V1..V3), without the demo spaces. Run once in
-- the pages' schema; grant SELECT on the *_read views to delivery's read-only user.
-- NOT RUN against Oracle yet (sdui-r tests use H2).

CREATE TABLE sdui_merchant (
    merchant_id        VARCHAR2(128) PRIMARY KEY,
    name               VARCHAR2(256) NOT NULL,
    status             VARCHAR2(16)  DEFAULT 'ACTIVE' NOT NULL,
    manifest_sequence  NUMBER(19)    DEFAULT 0 NOT NULL     -- locked (FOR UPDATE) while publishing
);

CREATE TABLE sdui_screen (
    merchant_id       VARCHAR2(128) NOT NULL REFERENCES sdui_merchant(merchant_id),
    screen_key        VARCHAR2(128) NOT NULL,
    template_key      VARCHAR2(128) NOT NULL,
    template_version  NUMBER(10)    NOT NULL,
    status            VARCHAR2(16)  DEFAULT 'DRAFT' NOT NULL,
    head_revision_id  NUMBER(19),
    active_sequence   NUMBER(10),
    lock_version      NUMBER(10)    DEFAULT 0 NOT NULL,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_sdui_screen PRIMARY KEY (merchant_id, screen_key)
);

CREATE TABLE sdui_value_revision (
    revision_id   NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id   VARCHAR2(128) NOT NULL,
    screen_key    VARCHAR2(128) NOT NULL,
    revision_no   NUMBER(10)    NOT NULL,
    payload       CLOB          NOT NULL CONSTRAINT ck_sdui_value_revision_json CHECK (payload IS JSON),
    content_hash  VARCHAR2(64)  NOT NULL,
    created_by    VARCHAR2(256) NOT NULL,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_sdui_value_revision_screen FOREIGN KEY (merchant_id, screen_key) REFERENCES sdui_screen(merchant_id, screen_key),
    CONSTRAINT uq_sdui_value_revision UNIQUE (merchant_id, screen_key, revision_no)
);

CREATE TABLE sdui_value_release (
    merchant_id       VARCHAR2(128) NOT NULL,
    screen_key        VARCHAR2(128) NOT NULL,
    sequence          NUMBER(10)    NOT NULL,
    revision_id       NUMBER(19)    NOT NULL REFERENCES sdui_value_revision(revision_id),
    template_key      VARCHAR2(128) NOT NULL,
    template_version  NUMBER(10)    NOT NULL,
    payload           CLOB          NOT NULL CONSTRAINT ck_sdui_value_release_json CHECK (payload IS JSON),
    envelope          CLOB          NOT NULL,
    content_hash      VARCHAR2(64)  NOT NULL,
    key_id            VARCHAR2(128) NOT NULL,
    published_by      VARCHAR2(256) NOT NULL,
    released_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    language          VARCHAR2(16),
    CONSTRAINT pk_sdui_value_release PRIMARY KEY (merchant_id, screen_key, sequence)
);

-- spec 2.8: the release in each further language it has texts for (the release row is the default)
CREATE TABLE sdui_value_release_locale (
    merchant_id   VARCHAR2(128) NOT NULL,
    screen_key    VARCHAR2(128) NOT NULL,
    sequence      NUMBER(10)    NOT NULL,
    language      VARCHAR2(16)  NOT NULL,
    envelope      CLOB          NOT NULL,
    content_hash  VARCHAR2(64)  NOT NULL,
    key_id        VARCHAR2(128) NOT NULL,
    released_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_sdui_value_release_locale PRIMARY KEY (merchant_id, screen_key, sequence, language),
    CONSTRAINT fk_sdui_value_release_locale FOREIGN KEY (merchant_id, screen_key, sequence) REFERENCES sdui_value_release(merchant_id, screen_key, sequence)
);

CREATE TABLE sdui_manifest (
    merchant_id  VARCHAR2(128) NOT NULL REFERENCES sdui_merchant(merchant_id),
    sequence     NUMBER(19)    NOT NULL,
    body         CLOB          NOT NULL,
    envelope     CLOB          NOT NULL,
    etag         VARCHAR2(64)  NOT NULL,
    key_id       VARCHAR2(128) NOT NULL,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_sdui_manifest PRIMARY KEY (merchant_id, sequence)
);

CREATE TABLE sdui_audit_log (
    audit_id      NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id   VARCHAR2(128),
    actor_sub     VARCHAR2(256) NOT NULL,
    event         VARCHAR2(64)  NOT NULL,
    subject_type  VARCHAR2(64)  NOT NULL,
    subject_id    VARCHAR2(256) NOT NULL,
    trace_id      VARCHAR2(64),
    detail        CLOB,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE sdui_asset (
    asset_id       VARCHAR2(64)   PRIMARY KEY,
    merchant_id    VARCHAR2(128)  NOT NULL REFERENCES sdui_merchant(merchant_id),
    content_type   VARCHAR2(64)   NOT NULL,
    byte_size      NUMBER(10)     NOT NULL,
    content        BLOB           NOT NULL,
    content_hash   VARCHAR2(64)   NOT NULL,
    source         VARCHAR2(16)   NOT NULL,
    source_url     VARCHAR2(2048),
    original_name  VARCHAR2(256),
    created_by     VARCHAR2(256)  NOT NULL,
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at   TIMESTAMP,
    CONSTRAINT uq_sdui_asset_content UNIQUE (merchant_id, content_hash),
    CONSTRAINT ck_sdui_asset_source CHECK (source IN ('UPLOAD', 'URL')),
    CONSTRAINT ck_sdui_asset_type CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/gif'))
);
CREATE INDEX ix_sdui_asset_merchant ON sdui_asset (merchant_id, created_at);

-- revisions, releases, manifests and the audit are append-only
CREATE OR REPLACE TRIGGER trg_sdui_value_revision_immutable
    BEFORE UPDATE OR DELETE ON sdui_value_revision
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_value_revision is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_value_release_immutable
    BEFORE UPDATE OR DELETE ON sdui_value_release
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_value_release is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_value_release_locale_immutable
    BEFORE UPDATE OR DELETE ON sdui_value_release_locale
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_value_release_locale is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_manifest_immutable
    BEFORE UPDATE OR DELETE ON sdui_manifest
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_manifest is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_value_audit_immutable
    BEFORE UPDATE OR DELETE ON sdui_audit_log
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_audit_log is append-only');
END;
/

CREATE OR REPLACE VIEW sdui_merchant_read AS
    SELECT merchant_id, name, status FROM sdui_merchant;

CREATE OR REPLACE VIEW sdui_screen_read AS
    SELECT merchant_id, screen_key, template_key, active_sequence FROM sdui_screen;

CREATE OR REPLACE VIEW sdui_value_release_read AS
    SELECT merchant_id, screen_key, sequence, template_key, template_version, language, envelope FROM sdui_value_release;

CREATE OR REPLACE VIEW sdui_value_release_locale_read AS
    SELECT merchant_id, screen_key, sequence, language, envelope FROM sdui_value_release_locale;

CREATE OR REPLACE VIEW sdui_manifest_read AS
    SELECT merchant_id, sequence, envelope FROM sdui_manifest;

CREATE OR REPLACE VIEW sdui_asset_read AS
    SELECT asset_id, content_type, content_hash, content FROM sdui_asset WHERE published_at IS NOT NULL;
