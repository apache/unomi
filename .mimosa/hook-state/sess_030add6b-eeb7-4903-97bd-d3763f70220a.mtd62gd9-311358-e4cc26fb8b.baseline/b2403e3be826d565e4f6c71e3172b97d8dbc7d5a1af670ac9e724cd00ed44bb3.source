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

package org.apache.unomi.didvc.sdjwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official test vectors from RFC 9901 (Selective Disclosure for JWTs):
 * §5.1 issuance digests and disclosures, §5.2 presentation with key
 * binding and sd_hash, and Appendix A.1 nested/decoy structures.
 *
 * <p>Vector strings were extracted from the RFC text (line-wrapped
 * base64url joined without whitespace) and cross-verified against the
 * hashes printed in the RFC.</p>
 */
class Rfc9901VectorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // §5.1: full issuance serialization (Issuer-signed JWT + 10 disclosures + trailing tilde)
    private static final String RFC_5_1_ISSUANCE =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBbIkNyUWU3UzVrcUJBSHQtbk1ZWGdj"
             + "NmJkdDJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQWWxTRSIs"
             + "ICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlV"
             + "ZEdFMHc1cnREc3JaemZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tBTmtxVlI2eVoyVmE1TnJQSXZQWWJ5TXZSS0JNTSIsICJY"
             + "ekZyendzY002R242Q0pEYzZ2Vks4QmtNbmZHOHZPU0tmcFBJWmRBZmRFIiwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFrb2I5"
             + "aFYxY1JEMEFUTjNvUUw5Sk0iLCAianN1OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpnbGhRRzBEcGZheVF3TFVLNCJdLCAiaXNz"
             + "IjogImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAwMDAwLCAi"
             + "c3ViIjogInVzZXJfNDIiLCAibmF0aW9uYWxpdGllcyI6IFt7Ii4uLiI6ICJwRm5kamtaX1ZDem15VGE2VWpsWm8zZGgta284"
             + "YUlLUWM5RGxHemhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlclAwVmtCSnJXWjAi"
             + "fV0sICJfc2RfYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAi"
             + "eCI6ICJUQ0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIVldL"
             + "VlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.MczwjBFGtzf-6WMT-hIvYbkb11NrV1WMO-jTijpMPNbswNzZ87wY"
             + "2uHz-CXo6R04b7jYrpj9mNRAvVssXou1iw~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9ob"
             + "iJd~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd~WyI2SWo3dE0tYTVpVlBHYm9TNXR"
             + "tdlZBIiwgImVtYWlsIiwgImpvaG5kb2VAZXhhbXBsZS5jb20iXQ~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25l"
             + "X251bWJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgInBob25lX251bWJlcl92Z"
             + "XJpZmllZCIsIHRydWVd~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjog"
             + "IjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAi"
             + "VVMifV0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0~WyJHMDJOU3JRZmpG"
             + "WFE3SW8wOXN5YWpBIiwgInVwZGF0ZWRfYXQiLCAxNTcwMDAwMDAwXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTI"
             + "l0~WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0~";

    // §5.2: SD-JWT+KB presentation (JWT + 4 disclosures + KB-JWT = 6 parts)
    private static final String RFC_5_2_PRESENTATION =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBbIkNyUWU3UzVrcUJBSHQtbk1ZWGdj"
             + "NmJkdDJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQWWxTRSIs"
             + "ICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlV"
             + "ZEdFMHc1cnREc3JaemZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tBTmtxVlI2eVoyVmE1TnJQSXZQWWJ5TXZSS0JNTSIsICJY"
             + "ekZyendzY002R242Q0pEYzZ2Vks4QmtNbmZHOHZPU0tmcFBJWmRBZmRFIiwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFrb2I5"
             + "aFYxY1JEMEFUTjNvUUw5Sk0iLCAianN1OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpnbGhRRzBEcGZheVF3TFVLNCJdLCAiaXNz"
             + "IjogImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAwMDAwLCAi"
             + "c3ViIjogInVzZXJfNDIiLCAibmF0aW9uYWxpdGllcyI6IFt7Ii4uLiI6ICJwRm5kamtaX1ZDem15VGE2VWpsWm8zZGgta284"
             + "YUlLUWM5RGxHemhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlclAwVmtCSnJXWjAi"
             + "fV0sICJfc2RfYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAi"
             + "eCI6ICJUQ0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIVldL"
             + "VlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.MczwjBFGtzf-6WMT-hIvYbkb11NrV1WMO-jTijpMPNbswNzZ87wY"
             + "2uHz-CXo6R04b7jYrpj9mNRAvVssXou1iw~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZ"
             + "SJd~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0"
             + "IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0~WyIyR0xD"
             + "NDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlV"
             + "TIl0~eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImtiK2p3dCJ9.eyJub25jZSI6ICIxMjM0NTY3ODkwIiwgImF1ZCI6ICJodH"
             + "RwczovL3ZlcmlmaWVyLmV4YW1wbGUub3JnIiwgImlhdCI6IDE3NDg1MzcyNDQsICJzZF9oYXNoIjogIjBfQWYtMkItRWhMV1"
             + "g1eWRoX3cyeHp3bU82aU02NkJfMlFDRWFuSTRmVVkifQ.T3SIus2OidNl41nmVkTZVCKKhOAX97aOldMyHFiYjHm261eLiJ1"
             + "YiuONFiMN8QlCmYzDlBLAdPvrXh52KaLgUQ";

    // Appendix A.1: presentation disclosing only region and country of the address
    private static final String RFC_A1_PRESENTATION =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBbIkM5aW5wNllvUmFFWFI0Mjd6WUpQ"
             + "N1FyazFXSF84YmR3T0FfWVVyVW5HUVUiLCAiS3VldDF5QWEwSElRdlluT1ZkNTloY1ZpTzlVZzZKMmtTZnFZUkJlb3d2RSIs"
             + "ICJNTWxkT0ZGekIyZDB1bWxtcFRJYUdlcmhXZFVfUHBZZkx2S2hoX2ZfOWFZIiwgIlg2WkFZT0lJMnZQTjQwVjd4RXhad1Z3"
             + "ejd5Um1MTmNWd3Q1REw4Ukx2NGciLCAiWTM0em1JbzBRTExPdGRNcFhHd2pCZ0x2cjE3eUVoaFlUMEZHb2ZSLWFJRSIsICJm"
             + "eUdwMFdUd3dQdjJKRFFsbjFsU2lhZW9iWnNNV0ExMGJRNTk4OS05RFRzIiwgIm9tbUZBaWNWVDhMR0hDQjB1eXd4N2ZZdW8z"
             + "TUhZS08xNWN6LVJaRVlNNVEiLCAiczBCS1lzTFd4UVFlVTh0VmxsdE03TUtzSVJUckVJYTFQa0ptcXhCQmY1VSJdLCAiaXNz"
             + "IjogImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAwMDAwLCAi"
             + "YWRkcmVzcyI6IHsiX3NkIjogWyI2YVVoelloWjdTSjFrVm1hZ1FBTzN1MkVUTjJDQzFhSGhlWnBLbmFGMF9FIiwgIkF6TGxG"
             + "b2JrSjJ4aWF1cFJFUHlvSnotOS1OU2xkQjZDZ2pyN2ZVeW9IemciLCAiUHp6Y1Z1MHFiTXVCR1NqdWxmZXd6a2VzRDl6dXRP"
             + "RXhuNUVXTndrclEtayIsICJiMkRrdzBqY0lGOXJHZzhfUEY4WmN2bmNXN3p3Wmo1cnlCV3ZYZnJwemVrIiwgImNQWUpISVo4"
             + "VnUtZjlDQ3lWdWIyVWZnRWs4anZ2WGV6d0sxcF9KbmVlWFEiLCAiZ2xUM2hyU1U3ZlNXZ3dGNVVEWm1Xd0JUdzMyZ25VbGRJ"
             + "aGk4aEdWQ2FWNCIsICJydkpkNmlxNlQ1ZWptc0JNb0d3dU5YaDlxQUFGQVRBY2k0MG9pZEVlVnNBIiwgInVOSG9XWWhYc1po"
             + "VkpDTkUyRHF5LXpxdDd0NjlnSkt5NVFhRnY3R3JNWDQiXX0sICJfc2RfYWxnIjogInNoYS0yNTYifQ.EOZa2YqK8j4i7cqBD"
             + "kfPcTMaFsgPwcx3aYJkFoMfvV46LxL-PPqrWsIyNukB4x8Y2LT31eIHDc4Wg4XNzaqu4w~WyJHMDJOU3JRZmpGWFE3SW8wOX"
             + "N5YWpBIiwgInJlZ2lvbiIsICJcdTZlMmZcdTUzM2EiXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgImNvdW50cnkiLCA"
             + "iSlAiXQ~";

    // Appendix A.1: the RFC presentation's JWT combined with ALL disclosures
    // listed in the appendix (nested address members + top-level claims)
    private static final String RFC_A1_ISSUANCE_ALL_DISCLOSURES =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0.eyJfc2QiOiBbIkM5aW5wNllvUmFFWFI0Mjd6WUpQ"
             + "N1FyazFXSF84YmR3T0FfWVVyVW5HUVUiLCAiS3VldDF5QWEwSElRdlluT1ZkNTloY1ZpTzlVZzZKMmtTZnFZUkJlb3d2RSIs"
             + "ICJNTWxkT0ZGekIyZDB1bWxtcFRJYUdlcmhXZFVfUHBZZkx2S2hoX2ZfOWFZIiwgIlg2WkFZT0lJMnZQTjQwVjd4RXhad1Z3"
             + "ejd5Um1MTmNWd3Q1REw4Ukx2NGciLCAiWTM0em1JbzBRTExPdGRNcFhHd2pCZ0x2cjE3eUVoaFlUMEZHb2ZSLWFJRSIsICJm"
             + "eUdwMFdUd3dQdjJKRFFsbjFsU2lhZW9iWnNNV0ExMGJRNTk4OS05RFRzIiwgIm9tbUZBaWNWVDhMR0hDQjB1eXd4N2ZZdW8z"
             + "TUhZS08xNWN6LVJaRVlNNVEiLCAiczBCS1lzTFd4UVFlVTh0VmxsdE03TUtzSVJUckVJYTFQa0ptcXhCQmY1VSJdLCAiaXNz"
             + "IjogImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAwMDAwLCAi"
             + "YWRkcmVzcyI6IHsiX3NkIjogWyI2YVVoelloWjdTSjFrVm1hZ1FBTzN1MkVUTjJDQzFhSGhlWnBLbmFGMF9FIiwgIkF6TGxG"
             + "b2JrSjJ4aWF1cFJFUHlvSnotOS1OU2xkQjZDZ2pyN2ZVeW9IemciLCAiUHp6Y1Z1MHFiTXVCR1NqdWxmZXd6a2VzRDl6dXRP"
             + "RXhuNUVXTndrclEtayIsICJiMkRrdzBqY0lGOXJHZzhfUEY4WmN2bmNXN3p3Wmo1cnlCV3ZYZnJwemVrIiwgImNQWUpISVo4"
             + "VnUtZjlDQ3lWdWIyVWZnRWs4anZ2WGV6d0sxcF9KbmVlWFEiLCAiZ2xUM2hyU1U3ZlNXZ3dGNVVEWm1Xd0JUdzMyZ25VbGRJ"
             + "aGk4aEdWQ2FWNCIsICJydkpkNmlxNlQ1ZWptc0JNb0d3dU5YaDlxQUFGQVRBY2k0MG9pZEVlVnNBIiwgInVOSG9XWWhYc1po"
             + "VkpDTkUyRHF5LXpxdDd0NjlnSkt5NVFhRnY3R3JNWDQiXX0sICJfc2RfYWxnIjogInNoYS0yNTYifQ.EOZa2YqK8j4i7cqBD"
             + "kfPcTMaFsgPwcx3aYJkFoMfvV46LxL-PPqrWsIyNukB4x8Y2LT31eIHDc4Wg4XNzaqu4w~WyIyR0xDNDJzS1F2ZUNmR2ZyeU"
             + "5STjl3IiwgInN1YiIsICI2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiXQ~WyJlbHVWNU9nM2dTTklJOEV"
             + "ZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiXHU1OTJhXHU5MGNlIl0~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWl"
             + "seV9uYW1lIiwgIlx1NWM3MVx1NzUzMCJd~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgImVtYWlsIiwgIlwidW51c3VhbC"
             + "BlbWFpbCBhZGRyZXNzXCJAZXhhbXBsZS5qcCJd~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgInBob25lX251bWJlciIsI"
             + "CIrODEtODAtMTIzNC01Njc4Il0~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgInN0cmVldF9hZGRyZXNzIiwgIlx1Njc3M"
             + "Vx1NGVhY1x1OTBmZFx1NmUyZlx1NTMzYVx1ODI5ZFx1NTE2Y1x1NTcxMlx1ZmYxNFx1NGUwMVx1NzZlZVx1ZmYxMlx1MjIxM"
             + "lx1ZmYxOCJd~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImxvY2FsaXR5IiwgIlx1Njc3MVx1NGVhY1x1OTBmZCJd~WyJ"
             + "HMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgInJlZ2lvbiIsICJcdTZlMmZcdTUzM2EiXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5J"
             + "dkNBIiwgImNvdW50cnkiLCAiSlAiXQ~WyJ5eXRWYmRBUEdjZ2wyckk0QzlHU29nIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxL"
             + "TAxIl0~";

    // ---- §5.1 disclosures, in issuance order ----

    private static final String D_1 = "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd";
    private static final String D_2 = "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd";
    private static final String D_3 = "WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWlsIiwgImpvaG5kb2VAZXhhbXBsZS5jb20iXQ";
    private static final String D_4 = "WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25lX251bWJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ";
    private static final String D_5 = "WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgInBob25lX251bWJlcl92ZXJpZmllZCIsIHRydWVd";
    private static final String D_6 = "WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOiAiVVMifV0";
    private static final String D_7 = "WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0";
    private static final String D_8 = "WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgInVwZGF0ZWRfYXQiLCAxNTcwMDAwMDAwXQ";
    private static final String D_9 = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0";
    private static final String D_10 = "WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0";

    // ---- Appendix A.1 disclosures ----

    private static final String A1_D_GIVEN_NAME = "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiXHU1OTJhXHU5MGNlIl0";

    /** RFC §5.1 disclosure digest pairs, including the two array-entry disclosures. */
    @Test
    void section51DisclosureDigestPairs() {
        assertEquals("jsu9yVulwQQlhFlM_3JlzMaSFzglhQG0DpfayQwLUK4", SdJwtDigest.digestOf(D_1));
        assertEquals("TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo", SdJwtDigest.digestOf(D_2));
        assertEquals("JzYjH4svliH0R3PyEMfeZu6Jt69u5qehZo7F7EPYlSE", SdJwtDigest.digestOf(D_3));
        assertEquals("PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI", SdJwtDigest.digestOf(D_4));
        assertEquals("XQ_3kPKt1XyX7KANkqVR6yZ2Va5NrPIvPYbyMvRKBMM", SdJwtDigest.digestOf(D_5));
        assertEquals("XzFrzwscM6Gn6CJDc6vVK8BkMnfG8vOSKfpPIZdAfdE", SdJwtDigest.digestOf(D_6));
        assertEquals("gbOsI4Edq2x2Kw-w5wPEzakob9hV1cRD0ATN3oQL9JM", SdJwtDigest.digestOf(D_7));
        assertEquals("CrQe7S5kqBAHt-nMYXgc6bdt2SH5aTY1sU_M-PgkjPI", SdJwtDigest.digestOf(D_8));
        assertEquals("pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo", SdJwtDigest.digestOf(D_9));
        assertEquals("7Cf6JkPudry3lcbwHgeZ8khAv1U1OSlerP0VkBJrWZ0", SdJwtDigest.digestOf(D_10));
    }

    /** RFC §5.1 disclosure contents (decodeDisclosure), incl. array-entry and object values. */
    @Test
    void section51DisclosureContents() {
        List<Object> givenName = SdJwtDigest.decodeDisclosure(D_1);
        assertEquals(3, givenName.size());
        assertEquals("2GLC42sKQveCfGfryNRN9w", givenName.get(0));
        assertEquals("given_name", givenName.get(1));
        assertEquals("John", givenName.get(2));

        List<Object> address = SdJwtDigest.decodeDisclosure(D_6);
        assertEquals("address", address.get(1));
        assertEquals("Anytown", ((Map<?, ?>) address.get(2)).get("locality"));

        List<Object> updatedAt = SdJwtDigest.decodeDisclosure(D_8);
        assertEquals(1570000000L, ((Number) updatedAt.get(2)).longValue());

        // array-entry disclosures are two-element [salt, value] arrays
        List<Object> us = SdJwtDigest.decodeDisclosure(D_9);
        assertEquals(2, us.size());
        assertEquals("lklxF5jMYlGTPUovMNIvCA", us.get(0));
        assertEquals("US", us.get(1));
        assertEquals("DE", SdJwtDigest.decodeDisclosure(D_10).get(1));
    }

    /** Appendix A.1 digest pairs; the escaped unicode decodes to real characters. */
    @Test
    void appendixA1DisclosureDigestPairsAndUnicodeContents() {
        assertEquals("ommFAicVT8LGHCB0uywx7fYuo3MHYKO15cz-RZEYM5Q", SdJwtDigest.digestOf(A1_D_GIVEN_NAME));
        List<Object> givenName = SdJwtDigest.decodeDisclosure(A1_D_GIVEN_NAME);
        assertEquals("given_name", givenName.get(1));
        assertEquals("\u592a\u90ce", givenName.get(2));
    }

    /**
     * §5.1 end to end: parsing the full issuance serialization with all
     * disclosures yields the RFC's Processed SD-JWT Payload; the
     * nationalities array is assembled from array-entry disclosures.
     */
    @Test
    void section51IssuanceParsesToProcessedPayload() throws Exception {
        SdJwtPresentation presentation = new SdJwtParser().parse(RFC_5_1_ISSUANCE);
        assertEquals("https://issuer.example.com", presentation.getClaims().get("iss"));
        assertEquals(10, presentation.getDisclosures().size());

        Map<String, Object> expected = readJson("{" +
                "\"iss\": \"https://issuer.example.com\"," +
                "\"iat\": 1683000000," +
                "\"exp\": 1883000000," +
                "\"sub\": \"user_42\"," +
                "\"nationalities\": [\"US\", \"DE\"]," +
                "\"cnf\": {\"jwk\": {\"kty\": \"EC\", \"crv\": \"P-256\"," +
                " \"x\": \"TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc\"," +
                " \"y\": \"ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ\"}}," +
                "\"given_name\": \"John\"," +
                "\"family_name\": \"Doe\"," +
                "\"email\": \"johndoe@example.com\"," +
                "\"phone_number\": \"+1-202-555-0101\"," +
                "\"phone_number_verified\": true," +
                "\"address\": {\"street_address\": \"123 Main St\", \"locality\": \"Anytown\"," +
                " \"region\": \"Anystate\", \"country\": \"US\"}," +
                "\"birthdate\": \"1940-01-01\"," +
                "\"updated_at\": 1570000000" +
                "}");
        assertEquals(normalized(expected), normalized(presentation.getDisclosedClaims()));

        @SuppressWarnings("unchecked")
        List<Object> nationalities = (List<Object>) presentation.getDisclosedClaims().get("nationalities");
        assertEquals(2, nationalities.size());
    }

    /** §5.2: the 6-part presentation's key binding verifies against cnf.jwk. */
    @Test
    void section52PresentationKeyBindingVerifies() throws Exception {
        SdJwtPresentation presentation = new SdJwtParser().parse(RFC_5_2_PRESENTATION);
        assertEquals(6, RFC_5_2_PRESENTATION.split("~").length);
        assertEquals(4, presentation.getDisclosures().size());
        assertEquals("1234567890", presentation.getKeyBindingClaims().get("nonce"));
        assertEquals("0_Af-2B-EhLWX5ydh_w2xzwmO6iM66B_2QCEanI4fUY",
                presentation.getKeyBindingClaims().get("sd_hash"));

        presentation.verifyKeyBinding("1234567890", "https://verifier.example.org", 1748537244L + 60);
        assertThrows(SecurityException.class,
                () -> presentation.verifyKeyBinding("wrong-nonce", "https://verifier.example.org", 1748537244L + 60));

        // Only family_name, address, given_name and the US nationality were presented
        Map<String, Object> disclosed = presentation.getDisclosedClaims();
        assertEquals("Doe", disclosed.get("family_name"));
        assertEquals("John", disclosed.get("given_name"));
        assertEquals("Anytown", ((Map<?, ?>) disclosed.get("address")).get("locality"));
        assertFalse(disclosed.containsKey("email"));
        @SuppressWarnings("unchecked")
        List<Object> nationalities = (List<Object>) disclosed.get("nationalities");
        assertEquals(1, nationalities.size());
        assertEquals("US", nationalities.get(0));
    }

    /**
     * §5.2 sd_hash: hashing the presentation minus the KB part (JWT plus
     * each disclosure followed by a tilde) reproduces the RFC's value.
     */
    @Test
    void sdHashMatchesSection52Vector() {
        String[] parts = RFC_5_2_PRESENTATION.split("~");
        String preKeyBinding = parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + parts[3] + "~" + parts[4] + "~";
        assertEquals("0_Af-2B-EhLWX5ydh_w2xzwmO6iM66B_2QCEanI4fUY",
                SdJwtDigest.hashOfSdJwt(preKeyBinding));
    }

    /**
     * Appendix A.1: nested _sd inside the address object is honoured and
     * decoy digests are ignored; the RFC's own region+country presentation
     * yields exactly the appendix's Processed SD-JWT Payload.
     */
    @Test
    void appendixA1PresentationProcessedPayload() throws Exception {
        SdJwtPresentation presentation = new SdJwtParser().parse(RFC_A1_PRESENTATION);
        Map<String, Object> expected = readJson("{" +
                "\"iss\": \"https://issuer.example.com\"," +
                "\"iat\": 1683000000," +
                "\"exp\": 1883000000," +
                "\"address\": {\"region\": \"\u6e2f\u533a\", \"country\": \"JP\"}" +
                "}");
        assertEquals(normalized(expected), normalized(presentation.getDisclosedClaims()));
    }

    /**
     * Appendix A.1 with all its disclosures: the nested address members are
     * assembled from the address object's _sd array while the decoy digests
     * in both _sd arrays match no disclosure and are silently ignored.
     */
    @Test
    void appendixA1AllDisclosuresAssembleNestedAddress() throws Exception {
        SdJwtPresentation presentation = new SdJwtParser().parse(RFC_A1_ISSUANCE_ALL_DISCLOSURES);
        Map<String, Object> disclosed = presentation.getDisclosedClaims();

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) disclosed.get("address");
        assertEquals("\u6771\u4eac\u90fd\u6e2f\u533a\u829d\u516c\u5712\uff14\u4e01\u76ee\uff12\u2212\uff18",
                address.get("street_address"));
        assertEquals("\u6771\u4eac\u90fd", address.get("locality"));
        assertEquals("\u6e2f\u533a", address.get("region"));
        assertEquals("JP", address.get("country"));
        assertEquals(4, address.size());

        assertEquals("6c5c0a49-b589-431d-bae7-219122a9ec2c", disclosed.get("sub"));
        assertEquals("\u592a\u90ce", disclosed.get("given_name"));
        assertEquals("\u5c71\u7530", disclosed.get("family_name"));
        assertEquals("\"unusual email address\"@example.jp", disclosed.get("email"));
        assertEquals("+81-80-1234-5678", disclosed.get("phone_number"));
        assertEquals("1940-01-01", disclosed.get("birthdate"));
        assertFalse(disclosed.containsKey("_sd"));
    }

    /** RFC 9901 §4.1.1: a non-sha-256 _sd_alg must be rejected. */
    @Test
    void unsupportedSdAlgRejected() throws Exception {
        String disclosure = SdJwtDigest.buildDisclosure("given_name", "John");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("_sd", List.of(SdJwtDigest.digestOf(disclosure)));
        payload.put("_sd_alg", "sha-384");
        String sdJwt = signLocally(payload) + "~" + disclosure + "~";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new SdJwtParser().parse(sdJwt));
        assertTrue(e.getMessage().contains("_sd_alg"));
    }

    /** RFC 9901 §4.1.1: a missing _sd_alg defaults to sha-256. */
    @Test
    void missingSdAlgDefaultsToSha256() throws Exception {
        String disclosure = SdJwtDigest.buildDisclosure("given_name", "John");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("_sd", List.of(SdJwtDigest.digestOf(disclosure)));
        payload.put("iss", "https://issuer.example.com");
        String sdJwt = signLocally(payload) + "~" + disclosure + "~";
        SdJwtPresentation presentation = new SdJwtParser().parse(sdJwt);
        assertEquals("John", presentation.getDisclosedClaims().get("given_name"));
    }

    private String signLocally(Map<String, Object> payload) throws Exception {
        ECKey issuerKey = new ECKeyGenerator(Curve.P_256).generate();
        JWSObject jwsObject = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).build(),
                new Payload(SdJwtDigest.toJsonBytes(payload)));
        jwsObject.sign(new ECDSASigner(issuerKey));
        return jwsObject.serialize();
    }

    private static Map<String, Object> readJson(String json) throws Exception {
        return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * Normalizes numbers to Long so maps produced by the Nimbus JSON parser
     * (Longs) compare equal to Jackson-parsed expectations (Integers).
     */
    private static Map<String, Object> normalized(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), normalizedValue(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizedValue(Object value) {
        if (value instanceof Map) {
            return normalized((Map<String, Object>) value);
        }
        if (value instanceof List) {
            return ((List<?>) value).stream().map(Rfc9901VectorTest::normalizedValue)
                    .collect(java.util.stream.Collectors.toList());
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value;
    }
}
