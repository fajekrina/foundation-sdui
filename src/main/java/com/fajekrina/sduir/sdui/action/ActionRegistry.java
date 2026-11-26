package com.fajekrina.sduir.sdui.action;

import com.fajekrina.sduir.sdui.contract.SpecContract;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Every {@link SduiHandler} bean of the service by the name pages use. Fails at start on a
/// handler without a name annotation, a bad name, or a name used twice.
public class ActionRegistry {
    /// `type` api | source
    public record Entry(String name, String type, String method, boolean idempotent, boolean customer, String title,
                        SduiHandler<Object, Object> handler, Class<?> requestType) {}

    private final Map<String, Entry> entries = new TreeMap<>();

    @SuppressWarnings("unchecked")
    public ActionRegistry(ApplicationContext context) {
        for (Map.Entry<String, SduiHandler> bean : context.getBeansOfType(SduiHandler.class).entrySet()) {
            Class<?> type = AopUtils.getTargetClass(bean.getValue());
            SduiApi api = type.getAnnotation(SduiApi.class);
            SduiSource source = type.getAnnotation(SduiSource.class);
            if ((api == null) == (source == null)) throw new IllegalStateException("handler " + bean.getKey() + " needs exactly one of @SduiApi or @SduiSource");
            String name = api != null ? api.name() : source.name();
            if (!SpecContract.isIdentifier(name)) throw new IllegalStateException("handler " + bean.getKey() + ": " + name + " is not an identifier");
            String method = api != null ? api.method().toUpperCase(java.util.Locale.ROOT) : "GET";
            if (!method.equals("GET") && !method.equals("POST")) throw new IllegalStateException("handler " + name + ": method must be GET or POST");
            Class<?> requestType = ResolvableType.forClass(SduiHandler.class, type).getGeneric(0).resolve(Object.class);
            Entry entry = new Entry(name, api != null ? "api" : "source", method, api != null && api.idempotent(),
                api != null ? api.customer() : source.customer(), api != null ? api.title() : source.title(),
                (SduiHandler<Object, Object>) bean.getValue(), requestType);
            if (entries.putIfAbsent(name, entry) != null) throw new IllegalStateException("two handlers named " + name);
        }
    }

    public Entry get(String name) { return entries.get(name); }

    public Map<String, Entry> all() { return Collections.unmodifiableMap(entries); }

    /// The names in `used` (e.g. `SduiNames.used(document)` of foundation-sdui-contract) that no
    /// handler of this service answers, sorted; empty when every name is handled.
    public java.util.SortedSet<String> missing(java.util.Collection<String> used) {
        java.util.SortedSet<String> out = new java.util.TreeSet<>();
        for (String name : used) if (!entries.containsKey(name)) out.add(name);
        return out;
    }

    /// `[{ name, type, method, title }]` — what the Studio may offer
    public List<Map<String, Object>> names() {
        List<Map<String, Object>> out = new ArrayList<>();
        entries.values().forEach((e) -> out.add(Map.of("name", e.name(), "type", e.type(), "method", e.method(), "title", e.title())));
        return out;
    }
}
