package com.fajekrina.sduir.sdui.core;

import com.fajekrina.sduir.sdui.contract.SduiJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreTest {
    @Test
    void envelopesAreCanonicalAndVerify() throws Exception {
        Path file = Files.createTempDirectory("keys").resolve("k.json");
        Ed25519Signer signer = Ed25519Signer.fromFile(file, true);
        Ed25519Signer again = Ed25519Signer.fromFile(file, false);
        assertThat(again.keyId()).isEqualTo(signer.keyId());
        assertThat(signer.publicKeyRaw()).hasSize(32);
        byte[] payload = SduiJson.canonical(SduiJson.parse("{\"b\":1,\"a\":{\"d\":2,\"c\":3}}"));
        assertThat(new String(payload)).isEqualTo("{\"a\":{\"c\":3,\"d\":2},\"b\":1}");
        JsonNode env = SduiJson.parse(signer.envelope(payload, "2.0"));
        assertThat(env.path("alg").asText()).isEqualTo("ed25519");
        byte[] signed = Base64.getDecoder().decode(env.path("payload").asText());
        assertThat(again.verify(signed, Base64.getDecoder().decode(env.path("signature").asText()))).isTrue();
        assertThat(again.verify("{}".getBytes(), Base64.getDecoder().decode(env.path("signature").asText()))).isFalse();
    }

    @Test
    void aPkcs8KeyFromConfigurationSignsLikeTheKeyFile() throws Exception {
        Path file = Files.createTempDirectory("keys").resolve("k.json");
        Ed25519Signer fromFile = Ed25519Signer.fromFile(file, true);
        String pkcs8 = SduiJson.parse(Files.readString(file)).path("privateKey").asText();
        Ed25519Signer fromConfig = Ed25519Signer.fromPkcs8("cfg-1", pkcs8);
        assertThat(fromConfig.keyId()).isEqualTo("cfg-1");
        assertThat(fromConfig.publicKeyRaw()).isEqualTo(fromFile.publicKeyRaw());
        byte[] data = "halo".getBytes();
        assertThat(fromFile.verify(data, fromConfig.sign(data))).isTrue();
    }

    @Test
    void clientVersionsCompareLikeSemver() {
        ClientInfo android = ClientInfo.parse("android/5.4.0; spec=2.7");
        assertThat(android.platform()).isEqualTo("android");
        assertThat(android.appVersion()).isEqualTo("5.4.0");
        assertThat(android.spec()).isEqualTo("2.7");
        assertThat(android.meets("5.4.0")).isTrue();
        assertThat(android.meets("5.3.9")).isTrue();
        assertThat(android.meets("5.10")).isFalse();
        assertThat(ClientInfo.parse("ios/6").meets("5.9.9")).isTrue();
        assertThat(ClientInfo.parse(null).isNative()).isFalse();
        assertThat(ClientInfo.parse("web/1.0").isNative()).isFalse();
        assertThat(ClientInfo.parse("garbage").isNative()).isFalse();
        assertThat(ClientInfo.compare("1.2.10", "1.2.9")).isPositive();
        assertThat(ClientInfo.compare("1.2", "1.2.0")).isZero();
    }
}
