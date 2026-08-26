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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issuer key lifecycle and JWS signing/verification.
 *
 * <p>Public key material is persisted as {@link KeyDescriptor} items. Private
 * key material is held only by the in-process key-material provider (the
 * HSM/KMS-backed replacement point) and is never persisted or logged.</p>
 */
@Component(service = IssuerKeyService.class, immediate = true)
public class IssuerKeyServiceImpl implements IssuerKeyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssuerKeyServiceImpl.class);

    /**
     * Default key-rotation window: 180 days.
     */
    static final long DEFAULT_ROTATION_MILLIS = 180L * 24 * 60 * 60 * 1000;

    private final Map<String, KeyMaterial> keyMaterial = new ConcurrentHashMap<>();

    @Reference
    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public KeyDescriptor generateKey(String tenantId, String issuerDid, String algorithm) {
        if (!"EdDSA".equals(algorithm) && !"ES256".equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
        JWK jwk;
        String kid;
        try {
            if ("EdDSA".equals(algorithm)) {
                jwk = new OctetKeyPairGenerator(Curve.Ed25519).generate();
            } else {
                jwk = new ECKeyGenerator(Curve.P_256).generate();
            }
            kid = jwk.computeThumbprint().toString();
        } catch (JOSEException e) {
            throw new IllegalStateException("Key generation failed for algorithm " + algorithm, e);
        }
        KeyDescriptor descriptor = new KeyDescriptor(kid);
        descriptor.setAlg(algorithm);
        descriptor.setKeyType(jwk.getKeyType().getValue());
        descriptor.setIssuerDid(issuerDid);
        descriptor.setPublicJwk(jwk.toPublicJWK().toJSONString());
        Date now = new Date();
        descriptor.setValidFrom(now);
        descriptor.setRotationDueDate(new Date(now.getTime() + DEFAULT_ROTATION_MILLIS));
        descriptor.setScope("didvc");
        descriptor.setTenantId(tenantId);
        persistenceService.save(descriptor);
        keyMaterial.put(kid, new KeyMaterial(jwk, JWSAlgorithm.parse(algorithm)));
        LOGGER.info("Generated issuer key {} ({}) for {}; public material only persisted", kid, algorithm, issuerDid);
        return descriptor;
    }

    @Override
    public KeyDescriptor getKey(String kid) {
        return persistenceService.load(kid, KeyDescriptor.class);
    }

    @Override
    public List<KeyDescriptor> getKeys(String tenantId) {
        List<KeyDescriptor> result = new ArrayList<>();
        for (KeyDescriptor descriptor : persistenceService.getAllItems(KeyDescriptor.class)) {
            if (tenantId == null || tenantId.equals(descriptor.getTenantId())) {
                result.add(descriptor);
            }
        }
        return result;
    }

    @Override
    public void deleteKey(String kid) {
        persistenceService.remove(kid, KeyDescriptor.class);
        keyMaterial.remove(kid);
    }

    @Override
    public String sign(String kid, String payloadJson) {
        KeyMaterial material = keyMaterial.get(kid);
        if (material == null) {
            throw new IllegalStateException("Private key material not available for kid " + kid
                    + ": after a restart, keys must be re-loaded from the HSM/KMS provider");
        }
        try {
            JWSObject jwsObject = new JWSObject(
                    new JWSHeader.Builder(material.algorithm).keyID(kid).build(),
                    new Payload(payloadJson));
            jwsObject.sign(signerFor(material.jwk));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Signing failed for kid " + kid, e);
        }
    }

    @Override
    public boolean verify(String kid, String jwsCompact) {
        KeyDescriptor descriptor = getKey(kid);
        if (descriptor == null) {
            return false;
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(jwsCompact);
            JWK publicJwk = JWK.parse(descriptor.getPublicJwk());
            return signedJWT.verify(verifierFor(publicJwk));
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    private static JWSSigner signerFor(JWK jwk) throws JOSEException {
        if (jwk instanceof OctetKeyPair) {
            return new Ed25519Signer((OctetKeyPair) jwk);
        }
        if (jwk instanceof ECKey) {
            return new ECDSASigner((ECKey) jwk);
        }
        throw new JOSEException("Unsupported key type: " + jwk.getKeyType());
    }

    private static JWSVerifier verifierFor(JWK jwk) throws JOSEException {
        if (jwk instanceof OctetKeyPair) {
            return new Ed25519Verifier((OctetKeyPair) jwk);
        }
        if (jwk instanceof ECKey) {
            return new ECDSAVerifier((ECKey) jwk);
        }
        throw new JOSEException("Unsupported key type: " + jwk.getKeyType());
    }

    private static final class KeyMaterial {
        private final JWK jwk;
        private final JWSAlgorithm algorithm;

        private KeyMaterial(JWK jwk, JWSAlgorithm algorithm) {
            this.jwk = jwk;
            this.algorithm = algorithm;
        }
    }
}
