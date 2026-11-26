-- spec 2.8: a page release in each language it has texts for besides its default. The release
-- row itself holds the default language (its envelope signs the value set without `locales`);
-- each row here signs the value set in one more language. Append-only, like the release.
ALTER TABLE sdui_value_release ADD COLUMN language VARCHAR(16);

CREATE TABLE sdui_value_release_locale (
    merchant_id   VARCHAR(128) NOT NULL,
    screen_key    VARCHAR(128) NOT NULL,
    sequence      INT          NOT NULL,
    language      VARCHAR(16)  NOT NULL,
    envelope      CLOB         NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,
    key_id        VARCHAR(128) NOT NULL,
    released_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id, screen_key, sequence, language),
    CONSTRAINT fk_sdui_value_release_locale FOREIGN KEY (merchant_id, screen_key, sequence) REFERENCES sdui_value_release(merchant_id, screen_key, sequence)
);

DROP VIEW sdui_value_release_read;
CREATE VIEW sdui_value_release_read AS
    SELECT merchant_id, screen_key, sequence, template_key, template_version, language, envelope FROM sdui_value_release;

CREATE VIEW sdui_value_release_locale_read AS
    SELECT merchant_id, screen_key, sequence, language, envelope FROM sdui_value_release_locale;
