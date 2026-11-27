package com.fajekrina.sduir.foundation.sdui.reference;

import com.fajekrina.sduir.foundation.sdui.contract.conformance.PageAuthoringConformance;
import com.fajekrina.sduir.foundation.sdui.contract.conformance.SduiTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/// foundation-sdui passes the server conformance kit: the page authoring API.
@SpringBootTest(classes = ReferenceApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.flyway.enabled=false", "sdui.authoring.templates.enabled=true", "sdui.authoring.pages.enabled=true", "sdui.delivery.enabled=true",
    "sdui.delivery.template-schema=TPL", "sdui.delivery.page-schema=VAL", "sdui.actor.trust-header=true",
    "sdui.audit.table=TPL.sdui_audit_log", "sdui.signing.key-file=build/test-keys/reference.json"})
class ReferencePageAuthoringConformanceTest extends PageAuthoringConformance {
    @LocalServerPort int port;
    @Autowired ReferenceData data;

    @Override protected SduiTarget target() { return ReferenceData.target(port); }

    @Override protected Fixture fixture() {
        data.seed();
        return new Fixture(ReferenceApp.SPACE, ReferenceData.TEMPLATE, 1, "title", "value");
    }
}
