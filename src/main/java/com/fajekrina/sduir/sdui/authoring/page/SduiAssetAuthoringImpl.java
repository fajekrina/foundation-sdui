package com.fajekrina.sduir.sdui.authoring.page;

import com.fajekrina.sduir.sdui.web.ActorResolver;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiIdempotency;
import com.fajekrina.sduir.sdui.web.SduiRequests;
import com.fajekrina.sduir.sdui.web.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

/// The built-in {@link SduiAssetAuthoring} (see there for what each handler answers).
public class SduiAssetAuthoringImpl implements SduiAssetAuthoring {
    private final AssetService assets;
    private final ActorResolver actors;
    private final SduiErrors errors;
    private final SduiIdempotency idempotency;

    public SduiAssetAuthoringImpl(AssetService assets, ActorResolver actors, SduiErrors errors, SduiIdempotency idempotency) {
        this.assets = assets;
        this.actors = actors;
        this.errors = errors;
        this.idempotency = idempotency;
    }

    @Override
    public SduiResponse create(String space, HttpServletRequest request) {
        return errors.guard(() -> idempotency.run(request,
            () -> SduiResponse.json(201, assets.create(space, SduiRequests.json(request, false), actors.current()))));
    }

    @Override
    public SduiResponse list(String space, HttpServletRequest request) {
        return errors.guard(() -> SduiResponse.ok(assets.list(space, actors.current())));
    }

    @Override
    public SduiResponse content(String space, String id, HttpServletRequest request) {
        return errors.guard(() -> {
            AssetService.Content c = assets.content(space, id, actors.current());
            return SduiResponse.bytes(200, MediaType.parseMediaType(c.type()), c.bytes())
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "private, max-age=300");
        });
    }
}
