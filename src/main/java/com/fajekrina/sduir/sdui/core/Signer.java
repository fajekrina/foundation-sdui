package com.fajekrina.sduir.sdui.core;

import com.fajekrina.sduir.sdui.contract.SduiJson;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Base64;

/// Signs releases and manifests (Ed25519). Port: the default is {@link Ed25519Signer} (a key
/// file or a PKCS#8 key from configuration); a service with an HSM registers its own bean.
public interface Signer {
    /// the key id written into every envelope (the app pins the key under this id)
    String keyId();

    /// the Ed25519 signature of `data`
    byte[] sign(byte[] data);

    /// the raw 32-byte public key (what the app pins; WebCrypto `importKey('raw', …)`)
    byte[] publicKeyRaw();

    /// `{ schemaVersion, alg, keyId, payload, signature }` over the exact payload bytes —
    /// canonical bytes of the envelope, stored and served as-is.
    default byte[] envelope(byte[] payload, String schemaVersion) {
        ObjectNode env = SduiJson.object();
        env.put("schemaVersion", schemaVersion);
        env.put("alg", "ed25519");
        env.put("keyId", keyId());
        env.put("payload", Base64.getEncoder().encodeToString(payload));
        env.put("signature", Base64.getEncoder().encodeToString(sign(payload)));
        return SduiJson.canonical(env);
    }
}
