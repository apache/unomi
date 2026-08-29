/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.didvc.services.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PKCS#11 key-material provider (FR-G2): signing happens inside an
 * HSM/token through the JDK's SunPKCS11 provider, so private keys never
 * enter application memory. Keys are located on the token by alias
 * (the kid); only public material (from the token certificate or the
 * persisted descriptor) is exposed outside.
 *
 * <p>Configuration is file-based, as required by SunPKCS11: the
 * operator writes a PKCS#11 config file (library, slot/pin options)
 * and points {@code didvc.keyservice.pkcs11.config} at it; the token
 * PIN comes from the {@code DIDVC_PKCS11_PIN} environment variable —
 * never from configuration files or source. Key pairs are created on
 * the token with external tooling (e.g. {@code pkcs11-tool
 * --keypairgen}); this provider only looks them up by alias.</p>
 */
public class Pkcs11KeyMaterialProvider implements KeyMaterialProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(Pkcs11KeyMaterialProvider.class);

    /** Environment variable carrying the token PIN. */
    public static final String PIN_ENV_VARIABLE = "DIDVC_PKCS11_PIN";

    private final KeyStore keyStore;
    private final char[] pin;
    private final Map<String, JWSAlgorithm> algorithms = new ConcurrentHashMap<>();

    /**
     * Creates the provider from a SunPKCS11 configuration file.
     *
     * @param pkcs11ConfigPath path to the PKCS#11 provider config file
     * @param pin              the token PIN (from the environment)
     * @throws IllegalStateException when the provider or token cannot be initialized
     */
    public Pkcs11KeyMaterialProvider(String pkcs11ConfigPath, char[] pin) {
        this.pin = pin == null ? new char[0] : pin.clone();
        try {
            Provider sunPkcs11 = Security.getProvider("SunPKCS11");
            if (sunPkcs11 == null) {
                throw new IllegalStateException("SunPKCS11 provider is not available in this JVM");
            }
            Provider configured = sunPkcs11.configure(pkcs11ConfigPath);
            if (Security.getProvider(configured.getName()) == null) {
                Security.addProvider(configured);
            }
            this.keyStore = KeyStore.getInstance("PKCS11", configured);
            this.keyStore.load(null, this.pin);
            LOGGER.info("Initialized PKCS#11 key-material provider from {} (provider {})",
                    pkcs11ConfigPath, configured.getName());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize PKCS#11 provider from "
                    + pkcs11ConfigPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * The token PIN read from {@value #PIN_ENV_VARIABLE}; null when unset.
     *
     * @return the PIN, or null
     */
    public static char[] pinFromEnvironment() {
        String pin = System.getenv(PIN_ENV_VARIABLE);
        return pin == null || pin.isEmpty() ? null : pin.toCharArray();
    }

    @Override
    public void register(String kid, JWK jwk, JWSAlgorithm algorithm) {
        // In-process generation is not applicable to token-held keys:
        // pairs are created on the token with external tooling and only
        // looked up by alias here. Remembering the algorithm is enough.
        algorithms.put(kid, algorithm);
    }

    @Override
    public String sign(String kid, String payloadJson, String typ) {
        try {
            PrivateKey privateKey = privateKeyByAlias(kid);
            JWSAlgorithm algorithm = algorithms.get(kid);
            if (algorithm == null) {
                algorithm = algorithmOf(privateKey);
            }
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(algorithm).keyID(kid);
            if (typ != null) {
                headerBuilder.type(new com.nimbusds.jose.JOSEObjectType(typ));
            }
            JWSObject jwsObject = new JWSObject(headerBuilder.build(), new Payload(payloadJson));
            jwsObject.sign(signerFor(privateKey, algorithm));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("HSM signing failed for kid " + kid + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void remove(String kid) {
        // Token keys outlive this process; removal is a token-administration
        // operation, not an application one
        algorithms.remove(kid);
    }

    private PrivateKey privateKeyByAlias(String kid) {
        try {
            if (!keyStore.isKeyEntry(kid)) {
                throw new IllegalStateException("No key entry '" + kid + "' on the PKCS#11 token");
            }
            PrivateKey key = (PrivateKey) keyStore.getKey(kid, pin);
            if (key == null) {
                throw new IllegalStateException("Key entry '" + kid + "' has no private key on the token");
            }
            return key;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load key '" + kid + "' from the PKCS#11 token: "
                    + e.getMessage(), e);
        }
    }

    /**
     * The token key store (aliases, public certificates). Exposure is
     * read-only by nature: KeyStore cannot export token-held private
     * keys as key material without the token's own extraction support.
     *
     * @return the initialized PKCS#11 key store
     */
    public KeyStore tokenStore() {
        return keyStore;
    }

    /**
     * The public key the token's certificate exposes for a key alias.
     *
     * @param kid the key alias
     * @return the public key, or null when the alias is unknown
     */
    public PublicKey publicKey(String kid) {
        try {
            Certificate certificate = keyStore.getCertificate(kid);
            return certificate == null ? null : certificate.getPublicKey();
        } catch (KeyStoreException e) {
            return null;
        }
    }

    /**
     * Lists the key aliases present on the token (for smoke checks).
     *
     * @return the aliases
     */
    public Enumeration<String> aliases() {
        try {
            return keyStore.aliases();
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Failed to enumerate PKCS#11 token aliases", e);
        }
    }

    private static JWSAlgorithm algorithmOf(PrivateKey privateKey) {
        return privateKey instanceof ECPrivateKey ? JWSAlgorithm.ES256 : JWSAlgorithm.EdDSA;
    }

    private static JWSSigner signerFor(PrivateKey privateKey, JWSAlgorithm algorithm) throws JOSEException {
        if (JWSAlgorithm.ES256.equals(algorithm)) {
            return new ECDSASigner(privateKey, Curve.P_256);
        }
        // Ed25519 JWS signing over PKCS#11 requires the key to expose
        // its raw value (OctetKeyPair); token-held Ed25519 keys that do
        // not surface it fail closed here
        throw new JOSEException("Ed25519 token signing is not supported by this provider"
                + " — issue ES256 keys on the token (EC P-256)");
    }
}
