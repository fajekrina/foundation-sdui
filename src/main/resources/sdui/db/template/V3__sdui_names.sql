-- foundation-sdui: every table is named sdui_*; delivery reads the *_read views only (a
-- read-only user in production). Adds which app versions carry a template release.
ALTER TABLE template RENAME TO sdui_template;
ALTER TABLE template_revision RENAME TO sdui_template_revision;
ALTER TABLE template_release RENAME TO sdui_template_release;
ALTER TABLE audit_log RENAME TO sdui_audit_log;
-- the CHECK constraints are named after their table too
ALTER TABLE sdui_template DROP CONSTRAINT ck_template_deprecation;
ALTER TABLE sdui_template DROP CONSTRAINT ck_template_replaced_by;
ALTER TABLE sdui_template ADD CONSTRAINT ck_sdui_template_deprecation CHECK (
    (deprecated_at IS NULL AND deprecated_by IS NULL AND deprecation_reason IS NULL AND replaced_by IS NULL)
    OR (deprecated_at IS NOT NULL AND deprecated_by IS NOT NULL AND deprecation_reason IS NOT NULL));
ALTER TABLE sdui_template ADD CONSTRAINT ck_sdui_template_replaced_by CHECK (replaced_by IS NULL OR replaced_by <> template_key);

-- Native apps (android, ios, mpaas) build every template themselves: a page reaches such an app
-- only when the app's version carries the page's template release. No row for a platform = not
-- carried by any version of it. The web needs no row (it renders any release).
CREATE TABLE sdui_template_support (
    owner                   VARCHAR(128) NOT NULL,
    template_key            VARCHAR(128) NOT NULL,
    version                 INT          NOT NULL,
    platform                VARCHAR(16)  NOT NULL,
    minimum_client_version  VARCHAR(32)  NOT NULL,     -- numeric dotted, e.g. 5.4.0
    recorded_by             VARCHAR(256) NOT NULL,
    recorded_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner, template_key, version, platform),
    CONSTRAINT fk_sdui_template_support FOREIGN KEY (owner, template_key, version) REFERENCES sdui_template_release(owner, template_key, version),
    CONSTRAINT ck_sdui_template_support_platform CHECK (platform IN ('android', 'ios', 'mpaas'))
);

CREATE VIEW sdui_template_release_read AS
    SELECT owner, template_key, version, envelope FROM sdui_template_release;

CREATE VIEW sdui_template_support_read AS
    SELECT owner, template_key, version, platform, minimum_client_version FROM sdui_template_support;
