package com.fajekrina.sduir.sdui.core;

import com.fajekrina.sduir.sdui.contract.SduiJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/// The default {@link Signer}: Ed25519 from the JDK. Either a key file
/// `{ keyId, privateKey (PKCS#8, base64), publicKey (X.509, base64) }` (generated on first
/// start when missing; dev), or a PKCS#8 private key from configuration ({@link #fromPkcs8}).
/// A service with an HSM registers its own Signer instead.
public final class Ed25519Signer implements Signer {
    private final String keyId;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private Ed25519Signer(String keyId, PrivateKey privateKey, PublicKey publicKey) {
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static synchronized Ed25519Signer fromFile(Path file, boolean generateIfMissing) {
        try {
            if (!Files.exists(file)) {
                if (!generateIfMissing) throw new IllegalStateException("signing key file missing: " + file);
                KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                ObjectNode json = SduiJson.object();
                json.put("keyId", "dev-" + SduiJson.sha256(pair.getPublic().getEncoded()).substring(0, 12));
                json.put("privateKey", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
                json.put("publicKey", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, SduiJson.write(json));
                try {
                    Files.move(tmp, file);
                } catch (IOException raced) {   // another service generated it first
                    Files.deleteIfExists(tmp);
                }
            }
            JsonNode json = SduiJson.parse(Files.readString(file));
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(json.path("privateKey").asText())));
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(json.path("publicKey").asText())));
            return new Ed25519Signer(json.path("keyId").asText(), priv, pub);
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("cannot load signing key " + file, e);
        }
    }

    /// A signer from a PKCS#8 Ed25519 private key (base64, as `openssl genpkey -algorithm ed25519`
    /// writes it without the PEM armour); the public key is derived from it.
    public static Ed25519Signer fromPkcs8(String keyId, String privateKeyBase64) {
        try {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            byte[] pkcs8 = Base64.getDecoder().decode(privateKeyBase64.replaceAll("-----[A-Z ]+-----|\\s", ""));
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            // an Ed25519 PKCS#8 key (RFC 8410) ends with the 32-byte seed; the public key is
            // derived from that seed
            byte[] seed = Arrays.copyOfRange(pkcs8, pkcs8.length - 32, pkcs8.length);
            PublicKey pub = publicFromSeed(seed);
            return new Ed25519Signer(keyId, priv, pub);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("cannot read the Ed25519 private key " + keyId, e);
        }
    }

    /// The public key of an Ed25519 seed: the JDK generator takes its 32 private bytes from the
    /// random source, so feeding it the seed rebuilds the same key pair (RFC 8032 §5.1.5).
    private static PublicKey publicFromSeed(byte[] seed) throws java.security.GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("Ed25519");
        g.initialize(new java.security.spec.NamedParameterSpec("Ed25519"), new java.security.SecureRandom() {
            @Override public void nextBytes(byte[] bytes) { System.arraycopy(seed, 0, bytes, 0, Math.min(seed.length, bytes.length)); }
        });
        return g.generateKeyPair().getPublic();
    }

    @Override
    public String keyId() { return keyId; }

    /// The raw 32-byte public key (what WebCrypto `importKey('raw', …)` takes).
    @Override
    public byte[] publicKeyRaw() {
        byte[] x509 = publicKey.getEncoded();
        return Arrays.copyOfRange(x509, x509.length - 32, x509.length);
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(privateKey);
            s.update(data);
            return s.sign();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verify(byte[] data, byte[] signature) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(publicKey);
            s.update(data);
            return s.verify(signature);
        } catch (java.security.GeneralSecurityException e) {
            return false;
        }
    }

    public static String utf8(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
}
