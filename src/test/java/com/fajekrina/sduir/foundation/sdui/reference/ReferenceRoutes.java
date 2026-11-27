package com.fajekrina.sduir.foundation.sdui.reference;

import com.fajekrina.sduir.foundation.sdui.annotation.SduiEndpoint;
import com.fajekrina.sduir.foundation.sdui.contract.model.RouteMap;
import com.fajekrina.sduir.foundation.sdui.service.action.SduiActionDispatcher;
import com.fajekrina.sduir.foundation.sdui.service.delivery.SduiDelivery;
import com.fajekrina.sduir.foundation.sdui.service.page.SduiAssetAuthoring;
import com.fajekrina.sduir.foundation.sdui.service.page.SduiPageAuthoring;
import com.fajekrina.sduir.foundation.sdui.service.template.SduiTemplateAuthoring;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/// The reference service's routes: every operation, at paths of its own (the contract names
/// none). {@link #MAP} is the same map, as the kit and an app would get it.
@SduiEndpoint
@RestController
public class ReferenceRoutes {
    static final RouteMap MAP = RouteMap.of(Map.ofEntries(
        Map.entry("screen", "/ref/read/pages/{space}/{key}"),
        Map.entry("screenRelease", "/ref/read/pages/{space}/{key}/releases/{sequence}"),
        Map.entry("template", "/ref/read/templates/{space}/{key}/{version}"),
        Map.entry("manifest", "/ref/read/manifests/{space}"),
        Map.entry("spaces", "/ref/read/spaces"),
        Map.entry("asset", "/ref/read/pictures/{id}"),
        Map.entry("signingKeys", "/ref/read/keys"),
        Map.entry("templateList", "/ref/studio/templates"),
        Map.entry("templateGet", "/ref/studio/templates/{key}"),
        Map.entry("templateDraft", "/ref/studio/templates/{key}/draft"),
        Map.entry("templatePublish", "/ref/studio/templates/{key}/releases"),
        Map.entry("templateDeprecate", "/ref/studio/templates/{key}/deprecation"),
        Map.entry("templateRestore", "/ref/studio/templates/{key}/deprecation"),
        Map.entry("templateSupport", "/ref/studio/templates/{key}/releases/{version}/apps"),
        Map.entry("templateSupportSet", "/ref/studio/templates/{key}/releases/{version}/apps"),
        Map.entry("templateCatalog", "/ref/studio/spaces/{space}/templates"),
        Map.entry("templateStatus", "/ref/studio/spaces/{space}/templates/{key}"),
        Map.entry("templateReleased", "/ref/studio/spaces/{space}/templates/{key}/{version}"),
        Map.entry("templateDraftOf", "/ref/studio/spaces/{space}/templates/{key}/draft"),
        Map.entry("spaceList", "/ref/studio/spaces"),
        Map.entry("spaceCreate", "/ref/studio/spaces"),
        Map.entry("pageList", "/ref/studio/pages"),
        Map.entry("screenList", "/ref/studio/spaces/{space}/pages"),
        Map.entry("screenGet", "/ref/studio/spaces/{space}/pages/{key}"),
        Map.entry("screenDraft", "/ref/studio/spaces/{space}/pages/{key}/draft"),
        Map.entry("screenPublish", "/ref/studio/spaces/{space}/pages/{key}/releases"),
        Map.entry("manifestGet", "/ref/studio/spaces/{space}/manifest"),
        Map.entry("assetCreate", "/ref/studio/spaces/{space}/pictures"),
        Map.entry("assetList", "/ref/studio/spaces/{space}/pictures"),
        Map.entry("assetContent", "/ref/studio/spaces/{space}/pictures/{id}"),
        Map.entry("actionNames", "/ref/calls"),
        Map.entry("action", "/ref/calls/{name}")));

    private final SduiDelivery delivery;
    private final SduiTemplateAuthoring templates;
    private final SduiPageAuthoring pages;
    private final SduiAssetAuthoring assets;
    private final SduiActionDispatcher actions;

    public ReferenceRoutes(SduiDelivery delivery, SduiTemplateAuthoring templates, SduiPageAuthoring pages, SduiAssetAuthoring assets, SduiActionDispatcher actions) {
        this.delivery = delivery;
        this.templates = templates;
        this.pages = pages;
        this.assets = assets;
        this.actions = actions;
    }

    // delivery
    @GetMapping("/ref/read/pages/{space}/{key}")
    public ResponseEntity<byte[]> screen(@PathVariable String space, @PathVariable String key, HttpServletRequest r) { return delivery.screen(space, key, r).toEntity(); }

    @GetMapping("/ref/read/pages/{space}/{key}/releases/{sequence}")
    public ResponseEntity<byte[]> screenRelease(@PathVariable String space, @PathVariable String key, @PathVariable int sequence, HttpServletRequest r) {
        return delivery.screenRelease(space, key, sequence, r).toEntity();
    }

    @GetMapping("/ref/read/templates/{space}/{key}/{version}")
    public ResponseEntity<byte[]> template(@PathVariable String space, @PathVariable String key, @PathVariable int version, HttpServletRequest r) {
        return delivery.template(space, key, version, r).toEntity();
    }

    @GetMapping("/ref/read/manifests/{space}")
    public ResponseEntity<byte[]> manifest(@PathVariable String space, HttpServletRequest r) { return delivery.manifest(space, r).toEntity(); }

    @GetMapping("/ref/read/spaces")
    public ResponseEntity<byte[]> spaces(HttpServletRequest r) { return delivery.spaces(r).toEntity(); }

    @GetMapping("/ref/read/pictures/{id}")
    public ResponseEntity<byte[]> asset(@PathVariable String id, HttpServletRequest r) { return delivery.asset(id, r).toEntity(); }

    @GetMapping("/ref/read/keys")
    public ResponseEntity<byte[]> keys(HttpServletRequest r) { return delivery.signingKeys(r).toEntity(); }

    // template authoring
    @GetMapping("/ref/studio/templates")
    public ResponseEntity<byte[]> templateList(HttpServletRequest r) { return templates.list(r).toEntity(); }

    @GetMapping("/ref/studio/templates/{key}")
    public ResponseEntity<byte[]> templateGet(@PathVariable String key, HttpServletRequest r) { return templates.get(key, r).toEntity(); }

    @PutMapping("/ref/studio/templates/{key}/draft")
    public ResponseEntity<byte[]> templateDraft(@PathVariable String key, HttpServletRequest r) { return templates.saveDraft(key, r).toEntity(); }

    @PostMapping("/ref/studio/templates/{key}/releases")
    public ResponseEntity<byte[]> templatePublish(@PathVariable String key, HttpServletRequest r) { return templates.publish(key, r).toEntity(); }

    @PutMapping("/ref/studio/templates/{key}/deprecation")
    public ResponseEntity<byte[]> templateDeprecate(@PathVariable String key, HttpServletRequest r) { return templates.deprecate(key, r).toEntity(); }

    @DeleteMapping("/ref/studio/templates/{key}/deprecation")
    public ResponseEntity<byte[]> templateRestore(@PathVariable String key, HttpServletRequest r) { return templates.restore(key, r).toEntity(); }

    @GetMapping("/ref/studio/templates/{key}/releases/{version}/apps")
    public ResponseEntity<byte[]> templateSupport(@PathVariable String key, @PathVariable int version, HttpServletRequest r) { return templates.support(key, version, r).toEntity(); }

    @PutMapping("/ref/studio/templates/{key}/releases/{version}/apps")
    public ResponseEntity<byte[]> templateSupportSet(@PathVariable String key, @PathVariable int version, HttpServletRequest r) { return templates.setSupport(key, version, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/templates")
    public ResponseEntity<byte[]> templateCatalog(@PathVariable String space, HttpServletRequest r) { return templates.catalog(space, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/templates/{key}")
    public ResponseEntity<byte[]> templateStatus(@PathVariable String space, @PathVariable String key, HttpServletRequest r) { return templates.status(space, key, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/templates/{key}/{version}")
    public ResponseEntity<byte[]> templateReleased(@PathVariable String space, @PathVariable String key, @PathVariable int version, HttpServletRequest r) {
        return templates.released(space, key, version, r).toEntity();
    }

    @GetMapping("/ref/studio/spaces/{space}/templates/{key}/draft")
    public ResponseEntity<byte[]> templateDraftOf(@PathVariable String space, @PathVariable String key, HttpServletRequest r) { return templates.draft(space, key, r).toEntity(); }

    // page authoring
    @GetMapping("/ref/studio/spaces")
    public ResponseEntity<byte[]> spaceList(HttpServletRequest r) { return pages.spaces(r).toEntity(); }

    @PostMapping("/ref/studio/spaces")
    public ResponseEntity<byte[]> spaceCreate(HttpServletRequest r) { return pages.createSpace(r).toEntity(); }

    @GetMapping("/ref/studio/pages")
    public ResponseEntity<byte[]> pageList(HttpServletRequest r) { return pages.pages(r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/pages")
    public ResponseEntity<byte[]> screenList(@PathVariable String space, HttpServletRequest r) { return pages.screens(space, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/pages/{key}")
    public ResponseEntity<byte[]> screenGet(@PathVariable String space, @PathVariable String key, HttpServletRequest r) { return pages.screen(space, key, r).toEntity(); }

    @PutMapping("/ref/studio/spaces/{space}/pages/{key}/draft")
    public ResponseEntity<byte[]> screenDraft(@PathVariable String space, @PathVariable String key, HttpServletRequest r) { return pages.saveDraft(space, key, r).toEntity(); }

    @PostMapping("/ref/studio/spaces/{space}/pages/{key}/releases")
    public ResponseEntity<byte[]> screenPublish(@PathVariable String space, @PathVariable String key, HttpServletRequest r) { return pages.publish(space, key, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/manifest")
    public ResponseEntity<byte[]> manifestGet(@PathVariable String space, HttpServletRequest r) { return pages.manifest(space, r).toEntity(); }

    @PostMapping("/ref/studio/spaces/{space}/pictures")
    public ResponseEntity<byte[]> assetCreate(@PathVariable String space, HttpServletRequest r) { return assets.create(space, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/pictures")
    public ResponseEntity<byte[]> assetList(@PathVariable String space, HttpServletRequest r) { return assets.list(space, r).toEntity(); }

    @GetMapping("/ref/studio/spaces/{space}/pictures/{id}")
    public ResponseEntity<byte[]> assetContent(@PathVariable String space, @PathVariable String id, HttpServletRequest r) { return assets.content(space, id, r).toEntity(); }

    // actions
    @GetMapping("/ref/calls")
    public ResponseEntity<byte[]> actionNames() { return actions.names().toEntity(); }

    @RequestMapping(value = "/ref/calls/{name}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<byte[]> action(@PathVariable String name, HttpServletRequest r) { return actions.dispatch(name, r, null).toEntity(); }
}
