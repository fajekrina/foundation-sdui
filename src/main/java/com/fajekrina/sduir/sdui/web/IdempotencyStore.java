package com.fajekrina.sduir.sdui.web;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Where `Idempotency-Key` answers are kept. Port: the default ({@link Memory}) keeps them in
/// memory, per instance; a service with several instances registers a shared store (e.g. Redis).
public interface IdempotencyStore {
    record Stored(String bodyHash, int status, String contentType, byte[] body, Instant expires) {}

    /// the kept answer of `scope`, or null (expired answers are gone)
    Stored get(String scope, Instant now);

    void put(String scope, Stored answer);

    /// The default store: in memory, per instance.
    class Memory implements IdempotencyStore {
        private final Map<String, Stored> store = new ConcurrentHashMap<>();

        @Override
        public Stored get(String scope, Instant now) {
            store.values().removeIf((s) -> s.expires().isBefore(now));
            return store.get(scope);
        }

        @Override
        public void put(String scope, Stored answer) { store.put(scope, answer); }
    }
}
