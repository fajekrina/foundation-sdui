package com.fajekrina.sduir.foundation.sdui.reference;

import com.fajekrina.sduir.foundation.sdui.contract.conformance.SduiTarget;
import com.fajekrina.sduir.foundation.sdui.contract.constant.SduiHeaders;
import com.fajekrina.sduir.foundation.sdui.contract.constant.SduiOperation;
import com.fajekrina.sduir.foundation.sdui.contract.model.RouteMap;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import com.fajekrina.sduir.foundation.sdui.service.page.AssetService;
import com.fajekrina.sduir.foundation.sdui.service.page.ValueService;
import com.fajekrina.sduir.foundation.sdui.service.template.TemplateService;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.Set;

/// What the reference service has published before the kit reads it (made through its own
/// services, once per test context), and the target the kit calls.
@Component
public class ReferenceData {
    static final String TEMPLATE = "kitPage";
    static final String PAGE = "kitHome";
    static final SduiActor KIT = new SduiActor("kit", "Kit", Set.of(SduiActor.TEMPLATE_ADMIN), Set.of("*"));

    private final TemplateService templates;
    private final ValueService values;
    private final AssetService assets;
    private String assetId;

    public ReferenceData(TemplateService templates, ValueService values, AssetService assets) {
        this.templates = templates;
        this.values = values;
        this.assets = assets;
    }

    /// Publish template `kitPage@1` (a text and a picture; android from 5.4.0), a picture and
    /// page `kitHome` on it — once.
    public synchronized String seed() {
        if (assetId != null) return assetId;
        assetId = assets.create(ReferenceApp.SPACE, Json.parse("{\"filename\":\"dot.png\",\"data\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==\"}"), KIT)
            .get("name").toString();
        templates.saveDraft(TEMPLATE, Json.parse("{\"schemaVersion\":\"2.8\",\"template\":{\"key\":\"x\",\"version\":1,\"title\":\"Kit\",\"root\":{\"type\":\"vstack\",\"id\":\"root\",\"order\":0,\"children\":["
            + "{\"type\":\"text\",\"id\":\"title\",\"order\":0,\"props\":{\"value\":\"Judul\"}},"
            + "{\"type\":\"image\",\"id\":\"pic\",\"order\":1,\"props\":{\"name\":\"placeholder\"}}]}}}"), null, KIT);
        templates.publish(TEMPLATE, KIT);
        templates.setSupport(TEMPLATE, 1, Json.parse("[{\"platform\":\"android\",\"minimumClientVersion\":\"5.4.0\"}]"), KIT);
        values.saveDraft(ReferenceApp.SPACE, PAGE, Json.parse("{\"schemaVersion\":\"2.8\",\"values\":{\"screen\":\"" + PAGE + "\",\"template\":{\"key\":\"" + TEMPLATE + "\",\"version\":1},"
            + "\"components\":{\"title\":{\"props\":{\"value\":\"Halo\"}},\"pic\":{\"props\":{\"name\":\"" + assetId + "\"}}}}}"), null, KIT);
        values.publish(ReferenceApp.SPACE, PAGE, KIT);
        return assetId;
    }

    /// the reference service at `port`, a Studio user for every call (the trusted gateway header)
    public static SduiTarget target(int port) {
        return new SduiTarget() {
            @Override public String baseUrl() { return "http://localhost:" + port; }
            @Override public RouteMap routes() { return ReferenceRoutes.MAP; }
            @Override public void authorize(HttpRequest.Builder request, SduiOperation op) { request.header(SduiHeaders.ACTOR, "kit"); }
        };
    }
}
