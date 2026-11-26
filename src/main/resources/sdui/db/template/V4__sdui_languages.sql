-- spec 2.8: a template release in each language it has texts for besides its default. The
-- release row itself holds the default language (its envelope signs the template without
-- `locales`); each row here signs the template in one more language. Append-only, like the release.
ALTER TABLE sdui_template_release ADD COLUMN language VARCHAR(16);

CREATE TABLE sdui_template_release_locale (
    owner         VARCHAR(128) NOT NULL,
    template_key  VARCHAR(128) NOT NULL,
    version       INT          NOT NULL,
    language      VARCHAR(16)  NOT NULL,
    envelope      CLOB         NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,
    key_id        VARCHAR(128) NOT NULL,
    released_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner, template_key, version, language),
    CONSTRAINT fk_sdui_template_release_locale FOREIGN KEY (owner, template_key, version) REFERENCES sdui_template_release(owner, template_key, version)
);

DROP VIEW sdui_template_release_read;
CREATE VIEW sdui_template_release_read AS
    SELECT owner, template_key, version, language, envelope FROM sdui_template_release;

CREATE VIEW sdui_template_release_locale_read AS
    SELECT owner, template_key, version, language, envelope FROM sdui_template_release_locale;
