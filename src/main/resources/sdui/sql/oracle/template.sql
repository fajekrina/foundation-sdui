-- foundation-sdui — template tables for Oracle (23ai: BOOLEAN results in queries). The same
-- final state as the H2 Flyway scripts in sdui/db/template (V1..V3). Run once in the templates'
-- schema; grant SELECT on the *_read views to delivery's read-only user.
-- NOT RUN against Oracle yet (sdui-r tests use H2).

CREATE TABLE sdui_template (
    owner               VARCHAR2(128)  NOT NULL,
    template_key        VARCHAR2(128)  NOT NULL,
    title               VARCHAR2(4000),
    status              VARCHAR2(16)   DEFAULT 'DRAFT' NOT NULL,
    head_revision_id    NUMBER(19),
    active_version      NUMBER(10),
    lock_version        NUMBER(10)     DEFAULT 0 NOT NULL,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deprecated_at       TIMESTAMP,
    deprecated_by       VARCHAR2(256),
    deprecation_reason  VARCHAR2(4000),
    replaced_by         VARCHAR2(128),
    CONSTRAINT pk_sdui_template PRIMARY KEY (owner, template_key),
    CONSTRAINT ck_sdui_template_deprecation CHECK (
        (deprecated_at IS NULL AND deprecated_by IS NULL AND deprecation_reason IS NULL AND replaced_by IS NULL)
        OR (deprecated_at IS NOT NULL AND deprecated_by IS NOT NULL AND deprecation_reason IS NOT NULL)),
    CONSTRAINT ck_sdui_template_replaced_by CHECK (replaced_by IS NULL OR replaced_by <> template_key)
);

CREATE TABLE sdui_template_revision (
    revision_id   NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner         VARCHAR2(128) NOT NULL,
    template_key  VARCHAR2(128) NOT NULL,
    revision_no   NUMBER(10)    NOT NULL,
    payload       CLOB          NOT NULL CONSTRAINT ck_sdui_template_revision_json CHECK (payload IS JSON),
    content_hash  VARCHAR2(64)  NOT NULL,
    created_by    VARCHAR2(256) NOT NULL,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_sdui_template_revision FOREIGN KEY (owner, template_key) REFERENCES sdui_template(owner, template_key),
    CONSTRAINT uq_sdui_template_revision UNIQUE (owner, template_key, revision_no)
);

CREATE TABLE sdui_template_release (
    owner         VARCHAR2(128) NOT NULL,
    template_key  VARCHAR2(128) NOT NULL,
    version       NUMBER(10)    NOT NULL,
    revision_id   NUMBER(19)    NOT NULL REFERENCES sdui_template_revision(revision_id),
    title         VARCHAR2(4000),
    payload       CLOB          NOT NULL CONSTRAINT ck_sdui_template_release_json CHECK (payload IS JSON),
    envelope      CLOB          NOT NULL,
    content_hash  VARCHAR2(64)  NOT NULL,
    key_id        VARCHAR2(128) NOT NULL,
    published_by  VARCHAR2(256) NOT NULL,
    released_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    language      VARCHAR2(16),
    CONSTRAINT pk_sdui_template_release PRIMARY KEY (owner, template_key, version),
    CONSTRAINT fk_sdui_template_release FOREIGN KEY (owner, template_key) REFERENCES sdui_template(owner, template_key)
);

-- spec 2.8: the release in each further language it has texts for (the release row is the default)
CREATE TABLE sdui_template_release_locale (
    owner         VARCHAR2(128) NOT NULL,
    template_key  VARCHAR2(128) NOT NULL,
    version       NUMBER(10)    NOT NULL,
    language      VARCHAR2(16)  NOT NULL,
    envelope      CLOB          NOT NULL,
    content_hash  VARCHAR2(64)  NOT NULL,
    key_id        VARCHAR2(128) NOT NULL,
    released_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_sdui_template_release_locale PRIMARY KEY (owner, template_key, version, language),
    CONSTRAINT fk_sdui_template_release_locale FOREIGN KEY (owner, template_key, version) REFERENCES sdui_template_release(owner, template_key, version)
);

CREATE TABLE sdui_template_support (
    owner                   VARCHAR2(128) NOT NULL,
    template_key            VARCHAR2(128) NOT NULL,
    version                 NUMBER(10)    NOT NULL,
    platform                VARCHAR2(16)  NOT NULL,
    minimum_client_version  VARCHAR2(32)  NOT NULL,
    recorded_by             VARCHAR2(256) NOT NULL,
    recorded_at             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_sdui_template_support PRIMARY KEY (owner, template_key, version, platform),
    CONSTRAINT fk_sdui_template_support FOREIGN KEY (owner, template_key, version) REFERENCES sdui_template_release(owner, template_key, version),
    CONSTRAINT ck_sdui_template_support_platform CHECK (platform IN ('android', 'ios', 'mpaas'))
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

-- revisions, releases and the audit are append-only
CREATE OR REPLACE TRIGGER trg_sdui_template_revision_immutable
    BEFORE UPDATE OR DELETE ON sdui_template_revision
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_template_revision is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_template_release_immutable
    BEFORE UPDATE OR DELETE ON sdui_template_release
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_template_release is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_template_release_locale_immutable
    BEFORE UPDATE OR DELETE ON sdui_template_release_locale
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_template_release_locale is append-only');
END;
/
CREATE OR REPLACE TRIGGER trg_sdui_template_audit_immutable
    BEFORE UPDATE OR DELETE ON sdui_audit_log
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'sdui_audit_log is append-only');
END;
/

CREATE OR REPLACE VIEW sdui_template_release_read AS
    SELECT owner, template_key, version, language, envelope FROM sdui_template_release;

CREATE OR REPLACE VIEW sdui_template_release_locale_read AS
    SELECT owner, template_key, version, language, envelope FROM sdui_template_release_locale;

CREATE OR REPLACE VIEW sdui_template_support_read AS
    SELECT owner, template_key, version, platform, minimum_client_version FROM sdui_template_support;
