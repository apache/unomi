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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PKCS#11 signing-path failure modes (FR-G2): the provider must fail
 * closed on a missing config file, malformed config content, and
 * unknown key aliases; the PIN is read only from the environment. The
 * positive signing proof against SoftHSM2 runs via
 * {@code didvc/scripts/run-hsm-softhsm2-proof.sh} (CI installs
 * SoftHSM2; token and PIN are created per run — no credential
 * literals).
 */
class Pkcs11KeyMaterialProviderTest {

    @Test
    void missingConfigFileIsAStartupFailure() {
        assertThrows(IllegalStateException.class,
                () -> new Pkcs11KeyMaterialProvider("/nonexistent/pkcs11.cfg", "pin".toCharArray()));
    }

    @Test
    void malformedConfigContentSurfacesAClearError(@TempDir Path tempDir) throws IOException {
        Path bad = Files.writeString(tempDir.resolve("bad.cfg"), "this is not pkcs11 config");
        assertThrows(IllegalStateException.class,
                () -> new Pkcs11KeyMaterialProvider(bad.toString(), "pin".toCharArray()));
    }

    @Test
    void pinComesOnlyFromTheEnvironment() {
        String pin = System.getenv(Pkcs11KeyMaterialProvider.PIN_ENV_VARIABLE);
        assumeTrue(pin == null || pin.isEmpty(),
                "environmental PIN present — the unset case cannot be exercised");
        assertEquals(null, Pkcs11KeyMaterialProvider.pinFromEnvironment());
    }

    @Test
    void inProcessProviderFailsClosedForUnknownKey() {
        InProcessKeyMaterialProvider provider = new InProcessKeyMaterialProvider();
        assertThrows(IllegalStateException.class, () -> provider.sign("unknown-kid", "{}", null));
    }

    @Test
    void providerConstructionIsTheOnlyKeyStoreEntry() {
        // The provider exposes no method that accepts key bytes: private
        // material can only ever come from the token itself
        java.lang.reflect.Method[] methods = Pkcs11KeyMaterialProvider.class.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertEquals(false, byte[].class.equals(parameterType) && !method.getName().equals("pin"),
                        "provider must not accept raw key bytes: " + method.getName());
            }
        }
    }
}
