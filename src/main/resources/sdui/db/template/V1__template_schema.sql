-- template-service owns schema TPL. A template belongs to ONE owner — the admin's merchant
-- (an admin's account name is its merchant id) — so every merchant can have its own `home`.
-- Revisions and releases are append-only (the service never updates or deletes them).
-- Publishing is direct (no review step): a release records who published it.
CREATE TABLE template (
    owner             VARCHAR(128) NOT NULL,                       -- merchant id of the admin
    template_key      VARCHAR(128) NOT NULL,
    title             VARCHAR(4096),
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',      -- DRAFT | PUBLISHED | ARCHIVED
    head_revision_id  BIGINT,
    active_version    INT,
    lock_version      INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner, template_key)
);

CREATE TABLE template_revision (
    revision_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner         VARCHAR(128) NOT NULL,
    template_key  VARCHAR(128) NOT NULL,
    revision_no   INT          NOT NULL,
    payload       CLOB         NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,
    created_by    VARCHAR(256) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_revision FOREIGN KEY (owner, template_key) REFERENCES template(owner, template_key),
    CONSTRAINT uq_template_revision UNIQUE (owner, template_key, revision_no)
);

CREATE TABLE template_release (
    owner         VARCHAR(128) NOT NULL,
    template_key  VARCHAR(128) NOT NULL,
    version       INT          NOT NULL,
    revision_id   BIGINT       NOT NULL REFERENCES template_revision(revision_id),
    title         VARCHAR(4096),
    payload       CLOB         NOT NULL,     -- canonical template document
    envelope      CLOB         NOT NULL,     -- signed envelope, served as-is
    content_hash  VARCHAR(64)  NOT NULL,
    key_id        VARCHAR(128) NOT NULL,
    published_by  VARCHAR(256) NOT NULL,
    released_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner, template_key, version),
    CONSTRAINT fk_template_release FOREIGN KEY (owner, template_key) REFERENCES template(owner, template_key)
);

CREATE TABLE audit_log (
    audit_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id   VARCHAR(128),
    actor_sub     VARCHAR(256) NOT NULL,
    event         VARCHAR(64)  NOT NULL,
    subject_type  VARCHAR(64)  NOT NULL,
    subject_id    VARCHAR(256) NOT NULL,
    trace_id      VARCHAR(64),
    detail        CLOB,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
