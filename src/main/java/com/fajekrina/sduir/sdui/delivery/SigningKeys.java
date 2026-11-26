package com.fajekrina.sduir.sdui.delivery;

import com.fajekrina.sduir.sdui.contract.SduiJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/// The verification keys delivery publishes (`/client/signing-keys`). Port: the default reads
/// the public half of the key file (`sdui.delivery.public-key-file`), or the service's
/// {@link com.fajekrina.sduir.sdui.core.Signer} when it has one; a service with several keys
/// (rotation, revocation) registers its own bean.
@FunctionalInterface
public interface SigningKeys {
    /// `publicKey` raw 32-byte Ed25519, base64; `status` ACTIVE | NEXT | REVOKED
    record Key(String keyId, String publicKey, String status) {}

    List<Key> keys();

    /// the public key of a key file `{ keyId, publicKey (X.509, base64) }`; none while it is missing
    static SigningKeys fromFile(Path file) {
        return () -> {
            if (!Files.exists(file)) return List.of();
            try {
                JsonNode json = SduiJson.parse(Files.readString(file));
                byte[] x509 = Base64.getDecoder().decode(json.path("publicKey").asText());
                String raw = Base64.getEncoder().encodeToString(Arrays.copyOfRange(x509, x509.length - 32, x509.length));
                return List.of(new Key(json.path("keyId").asText(), raw, "ACTIVE"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
