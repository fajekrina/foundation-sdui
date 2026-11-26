-- Template deprecation: catalog state of a template KEY (every version), not part of the
-- signed template document. A deprecated template is offered to no new page; pages that
-- already use it keep working (their releases are immutable) and may still take its newer
-- releases. Reversible (restore clears the columns); every change is audited.
ALTER TABLE template ADD COLUMN deprecated_at      TIMESTAMP;
ALTER TABLE template ADD COLUMN deprecated_by      VARCHAR(256);
ALTER TABLE template ADD COLUMN deprecation_reason VARCHAR(4096);
-- the template key of the same owner to use instead (optional; released, not deprecated)
ALTER TABLE template ADD COLUMN replaced_by        VARCHAR(128);
ALTER TABLE template ADD CONSTRAINT ck_template_deprecation CHECK (
    (deprecated_at IS NULL AND deprecated_by IS NULL AND deprecation_reason IS NULL AND replaced_by IS NULL)
    OR (deprecated_at IS NOT NULL AND deprecated_by IS NOT NULL AND deprecation_reason IS NOT NULL));
ALTER TABLE template ADD CONSTRAINT ck_template_replaced_by CHECK (replaced_by IS NULL OR replaced_by <> template_key);
