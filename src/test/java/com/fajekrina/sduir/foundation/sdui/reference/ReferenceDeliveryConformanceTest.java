package com.fajekrina.sduir.foundation.sdui.reference;

import com.fajekrina.sduir.foundation.sdui.contract.conformance.DeliveryConformance;
import com.fajekrina.sduir.foundation.sdui.contract.conformance.SduiTarget;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SduiReleases;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/// foundation-sdui passes the server conformance kit: the delivery API.
@SpringBootTest(classes = ReferenceApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.flyway.enabled=false", "sdui.authoring.templates.enabled=true", "sdui.authoring.pages.enabled=true", "sdui.delivery.enabled=true",
    "sdui.delivery.template-schema=TPL", "sdui.delivery.page-schema=VAL", "sdui.actor.trust-header=true",
    "sdui.audit.table=TPL.sdui_audit_log", "sdui.signing.key-file=build/test-keys/reference.json"})
class ReferenceDeliveryConformanceTest extends DeliveryConformance {
    @LocalServerPort int port;
    @Autowired ReferenceData data;

    @Override protected SduiTarget target() { return ReferenceData.target(port); }

    @Override protected Fixture fixture() {
        String asset = data.seed();
        return new Fixture(ReferenceApp.SPACE, ReferenceData.PAGE, ReferenceData.TEMPLATE, 1, asset, "android/1.0.0", SduiReleases.Fallback.NO_CONTENT);
    }
}
