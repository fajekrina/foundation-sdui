-- value-service owns schema VAL: one value set per merchant screen, pinned to a release of
-- one of THAT merchant's templates. Revisions, releases and manifests are append-only.
-- Publishing is direct (no review step): a release records who published it.
CREATE TABLE merchant (
    merchant_id        VARCHAR(128) PRIMARY KEY,
    name               VARCHAR(256) NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    manifest_sequence  BIGINT       NOT NULL DEFAULT 0     -- locked (FOR UPDATE) while publishing
);

CREATE TABLE screen (
    merchant_id         VARCHAR(128) NOT NULL REFERENCES merchant(merchant_id),
    screen_key          VARCHAR(128) NOT NULL,
    template_key        VARCHAR(128) NOT NULL,
    template_version    INT          NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    head_revision_id    BIGINT,
    active_sequence     INT,
    lock_version        INT          NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id, screen_key)
);

CREATE TABLE value_revision (
    revision_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id   VARCHAR(128) NOT NULL,
    screen_key    VARCHAR(128) NOT NULL,
    revision_no   INT          NOT NULL,
    payload       CLOB         NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,
    created_by    VARCHAR(256) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_value_revision_screen FOREIGN KEY (merchant_id, screen_key) REFERENCES screen(merchant_id, screen_key),
    CONSTRAINT uq_value_revision UNIQUE (merchant_id, screen_key, revision_no)
);

CREATE TABLE value_release (
    merchant_id       VARCHAR(128) NOT NULL,
    screen_key        VARCHAR(128) NOT NULL,
    sequence          INT          NOT NULL,
    revision_id       BIGINT       NOT NULL REFERENCES value_revision(revision_id),
    template_key      VARCHAR(128) NOT NULL,
    template_version  INT          NOT NULL,
    payload           CLOB         NOT NULL,
    envelope          CLOB         NOT NULL,
    content_hash      VARCHAR(64)  NOT NULL,
    key_id            VARCHAR(128) NOT NULL,
    published_by      VARCHAR(256) NOT NULL,
    released_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id, screen_key, sequence)
);

CREATE TABLE manifest (
    merchant_id  VARCHAR(128) NOT NULL REFERENCES merchant(merchant_id),
    sequence     BIGINT       NOT NULL,
    body         CLOB         NOT NULL,
    envelope     CLOB         NOT NULL,
    etag         VARCHAR(64)  NOT NULL,
    key_id       VARCHAR(128) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id, sequence)
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

INSERT INTO merchant (merchant_id, name) VALUES ('merchant_demo', 'Merchant Demo'), ('merchant_promo', 'Merchant Promo');
