package com.fajekrina.sduir.foundation.sdui.service.page;

import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import com.fajekrina.sduir.foundation.sdui.service.audit.SduiAudit;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SpecContract;
import com.fajekrina.sduir.foundation.sdui.repository.MerchantRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/// The kanal scope of every value-service call (screens, value sets, assets). A kanal groups
/// pages (`/s/<kanal>/<page>`); the database and the API still call it a merchant. Who may
/// reach or make one is decided here; the table is {@link MerchantRepository}'s.
@Component
public class Merchants {
    /// the one page space of the Studio (spec 2.6): every new page, and the shared templates' pictures
    public static final String SHARED = "sdui";

    private final MerchantRepository merchants;
    private final SduiAudit audit;

    public Merchants(MerchantRepository merchants, SduiAudit audit) {
        this.merchants = merchants;
        this.audit = audit;
    }

    /// A page space (the API calls it a merchant) that exists and is active. Pages belong to no
    /// user: every page made in the Studio lives in space `sdui` (made on first use by any Studio
    /// user), with the shared templates' pictures; older spaces (`merchant_demo` …) keep their
    /// pages. An older account scoped to one space still gets it created on first use. Services
    /// only reach spaces that exist.
    public void require(String mid, SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        boolean shared = mid.equals(SHARED) && actor.hasRole(SduiActor.TEMPLATE_ADMIN);
        if (!shared) actor.requireMerchant(mid);
        boolean exists = merchants.exists(mid);
        boolean own = actor.merchants().equals(Set.of(mid)) || shared;
        if (!exists && actor.hasRole(SduiActor.TEMPLATE_ADMIN) && own && SpecContract.isIdentifier(mid)) {
            merchants.insertIfMissing(mid, shared ? "Studio" : actor.name());
            audit.record(actor, mid, "MERCHANT_CREATED", "merchant", mid, Map.of("name", actor.name()));
            exists = true;
        }
        if (!exists || !merchants.isActive(mid)) throw SduiException.notFound("kanal " + mid + " not found");
    }

    /// Make a kanal (a Studio user): `id` an identifier, `name` what people see. Answers whether it is new.
    public boolean create(String id, String name, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN).requireMerchant(id);
        if (!SpecContract.isIdentifier(id)) throw SduiException.badRequest("a kanal id is an identifier: letters, digits and _ (e.g. wondr)");
        if (id.equalsIgnoreCase(SHARED)) throw SduiException.conflict("kanal " + SHARED + " is reserved for the shared templates' pictures");
        String label = name == null || name.isBlank() ? id : name.trim();
        if (label.length() > 256) throw SduiException.invalid("name: at most 256 characters");
        java.util.List<String> clash = merchants.idsIgnoringCase(id);
        if (!clash.isEmpty()) {
            if (clash.get(0).equals(id)) return false;
            throw SduiException.conflict("kanal " + clash.get(0) + " already exists; an id may not differ from it only by letter case");
        }
        merchants.insert(id, label);
        audit.record(actor, id, "MERCHANT_CREATED", "merchant", id, Map.of("name", label));
        return true;
    }
}
