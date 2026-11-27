package com.fajekrina.sduir.foundation.sdui.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/// Every `sdui.*` property of the foundation in one place (the parts are turned on by the
/// `enabled` flags; each config class reads only its own section).
@ConfigurationProperties(prefix = "sdui")
public class SduiProperties {
    private final Authoring authoring = new Authoring();
    private final Delivery delivery = new Delivery();
    private final Signing signing = new Signing();
    private final Actor actor = new Actor();
    private final Idempotency idempotency = new Idempotency();
    private final Audit audit = new Audit();
    private final Assets assets = new Assets();
    private final Language language = new Language();

    public Authoring getAuthoring() { return authoring; }
    public Delivery getDelivery() { return delivery; }
    public Signing getSigning() { return signing; }
    public Actor getActor() { return actor; }
    public Idempotency getIdempotency() { return idempotency; }
    public Audit getAudit() { return audit; }
    public Assets getAssets() { return assets; }
    public Language getLanguage() { return language; }

    /// `sdui.authoring.templates.enabled`, `sdui.authoring.pages.enabled` (default false)
    public static class Authoring {
        private final Toggle templates = new Toggle();
        private final Toggle pages = new Toggle();
        public Toggle getTemplates() { return templates; }
        public Toggle getPages() { return pages; }
    }

    public static class Toggle {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /// `sdui.delivery.*`
    public static class Delivery {
        private boolean enabled;
        /// where the template `*_read` views are ("" = the connection's schema)
        private String templateSchema = "";
        /// where the page `*_read` views are ("" = the connection's schema)
        private String pageSchema = "";
        /// `no-content` (204) | `previous-release`: for a native app whose version does not carry the page's template release
        private String fallback = "no-content";
        /// the published key, when the service does not sign
        private String publicKeyFile = "";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTemplateSchema() { return templateSchema; }
        public void setTemplateSchema(String templateSchema) { this.templateSchema = templateSchema; }
        public String getPageSchema() { return pageSchema; }
        public void setPageSchema(String pageSchema) { this.pageSchema = pageSchema; }
        public String getFallback() { return fallback; }
        public void setFallback(String fallback) { this.fallback = fallback; }
        public String getPublicKeyFile() { return publicKeyFile; }
        public void setPublicKeyFile(String publicKeyFile) { this.publicKeyFile = publicKeyFile; }
    }

    /// `sdui.signing.*`: `private-key` (PKCS#8 base64) + `key-id`, or `key-file` (dev)
    public static class Signing {
        private String keyId = "";
        private String privateKey = "";
        private String keyFile = "";
        private boolean generateIfMissing = true;
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public String getKeyFile() { return keyFile; }
        public void setKeyFile(String keyFile) { this.keyFile = keyFile; }
        public boolean isGenerateIfMissing() { return generateIfMissing; }
        public void setGenerateIfMissing(boolean generateIfMissing) { this.generateIfMissing = generateIfMissing; }
    }

    /// `sdui.actor.trust-header`: take the Studio user from `X-Sdui-Actor` (behind a BFF only)
    public static class Actor {
        private boolean trustHeader;
        public boolean isTrustHeader() { return trustHeader; }
        public void setTrustHeader(boolean trustHeader) { this.trustHeader = trustHeader; }
    }

    /// `sdui.idempotency.ttl` (default 24h)
    public static class Idempotency {
        private Duration ttl = Duration.ofHours(24);
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }

    /// `sdui.audit.table`, e.g. `TPL.sdui_audit_log`
    public static class Audit {
        private String table = "";
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
    }

    /// `sdui.assets.max-bytes` (default 2 MiB), `sdui.assets.url-import.allow-private-hosts` (default false)
    public static class Assets {
        private int maxBytes = 2 * 1024 * 1024;
        private final UrlImport urlImport = new UrlImport();
        public int getMaxBytes() { return maxBytes; }
        public void setMaxBytes(int maxBytes) { this.maxBytes = maxBytes; }
        public UrlImport getUrlImport() { return urlImport; }
    }

    public static class UrlImport {
        private boolean allowPrivateHosts;
        public boolean isAllowPrivateHosts() { return allowPrivateHosts; }
        public void setAllowPrivateHosts(boolean allowPrivateHosts) { this.allowPrivateHosts = allowPrivateHosts; }
    }

    /// `sdui.language.default`: the language of a document that names none (spec 2.8 `language`), default `id`
    public static class Language {
        private String defaultLanguage = "id";
        public String getDefault() { return defaultLanguage; }
        public void setDefault(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    }
}
