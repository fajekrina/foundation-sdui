package com.fajekrina.sduir.sdui.authoring.page;

import com.fajekrina.sduir.sdui.core.SduiActor;
import com.fajekrina.sduir.sdui.core.SduiAudit;
import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.contract.SpecContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/// The kanal scope of every value-service call (screens, value sets, assets). A kanal groups
/// pages (`/s/<kanal>/<page>`); the database and the API still call it a merchant.
@Component
public class Merchants {
    /// the one page space of the Studio (spec 2.6): every new page, and the shared templates' pictures
    public static final String SHARED = "sdui";

    private final JdbcTemplate jdbc;
    private final SduiAudit audit;

    public Merchants(JdbcTemplate jdbc, SduiAudit audit) {
        this.jdbc = jdbc;
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
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_merchant WHERE merchant_id = ?", Integer.class, mid);
        boolean own = actor.merchants().equals(Set.of(mid)) || shared;
        if ((n == null || n == 0) && actor.hasRole(SduiActor.TEMPLATE_ADMIN) && own && SpecContract.isIdentifier(mid)) {
            jdbc.update("MERGE INTO sdui_merchant m USING (SELECT CAST(? AS VARCHAR(128)) AS merchant_id, CAST(? AS VARCHAR(256)) AS name FROM DUAL) s ON (m.merchant_id = s.merchant_id) "
                + "WHEN NOT MATCHED THEN INSERT (merchant_id, name) VALUES (s.merchant_id, s.name)", mid, shared ? "Studio" : actor.name());
            audit.record(actor, mid, "MERCHANT_CREATED", "merchant", mid, Map.of("name", actor.name()));
            n = 1;
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_merchant WHERE merchant_id = ? AND status = 'ACTIVE'", Integer.class, mid);
        if (n == null || n == 0 || active == null || active == 0) throw SduiProblem.notFound("kanal " + mid + " not found");
    }

    /// Make a kanal (a Studio user): `id` an identifier, `name` what people see. Answers whether it is new.
    public boolean create(String id, String name, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN).requireMerchant(id);
        if (!SpecContract.isIdentifier(id)) throw SduiProblem.badRequest("a kanal id is an identifier: letters, digits and _ (e.g. wondr)");
        if (id.equalsIgnoreCase(SHARED)) throw SduiProblem.conflict("kanal " + SHARED + " is reserved for the shared templates' pictures");
        String label = name == null || name.isBlank() ? id : name.trim();
        if (label.length() > 256) throw SduiProblem.invalid("name: at most 256 characters");
        java.util.List<String> clash = jdbc.queryForList("SELECT merchant_id FROM sdui_merchant WHERE LOWER(merchant_id) = LOWER(?)", String.class, id);
        if (!clash.isEmpty()) {
            if (clash.get(0).equals(id)) return false;
            throw SduiProblem.conflict("kanal " + clash.get(0) + " already exists; an id may not differ from it only by letter case");
        }
        jdbc.update("INSERT INTO sdui_merchant (merchant_id, name) VALUES (?, ?)", id, label);
        audit.record(actor, id, "MERCHANT_CREATED", "merchant", id, Map.of("name", label));
        return true;
    }
}
