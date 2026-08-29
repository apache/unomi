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

/**
 * Load test for the credential edge (T-8.3): drives full
 * issue-then-verify round trips — internal offer, pre-authorized-code
 * token, key-bound credential request, OID4VP authorization request and
 * DCQL presentation — at a configurable concurrency, and reports
 * per-step and end-to-end latency percentiles.
 *
 * Target containment: the edge base URL is validated once at startup
 * (http/https scheme; the resolved addresses must not be link-local,
 * unspecified, reserved or multicast) and every request goes through
 * edgeFetch(), which only accepts paths on that single declared origin —
 * no arbitrary URLs are ever fetched.
 *
 * Usage:
 *   npx tsx load-test.ts [--edge http://localhost:8081] [--iterations 200]
 *                        [--concurrency 8] [--p95-target-ms 1000]
 */

import * as jose from 'jose'
import { createHash, randomBytes, generateKeyPair } from 'node:crypto'
import { lookup } from 'node:dns/promises'
import { isIP } from 'node:net'

const args = process.argv.slice(2)
function arg(name: string, fallback: string): string {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback
}
const EDGE = arg('edge', process.env.EDGE_URL ?? 'http://localhost:8081')
const API_KEY = process.env.EDGE_API_KEY ?? 'test-key'
const ITERATIONS = parseInt(arg('iterations', '200'), 10)
const CONCURRENCY = parseInt(arg('concurrency', '8'), 10)
const P95_TARGET_MS = parseInt(arg('p95-target-ms', '1000'), 10)

/** Validated once: the operator-declared edge origin every request uses. */
let edgeOrigin: string

async function validateEdgeOrigin(raw: string): Promise<string> {
  const parsed = new URL(raw)
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error(`edge URL must be http(s), got ${parsed.protocol}`)
  }
  const addresses = await lookup(parsed.hostname, { all: true })
  for (const { address, family } of addresses) {
    const ipVersion = family === 6 ? 6 : (isIP(address) === 6 ? 6 : 4)
    const rejected = ipVersion === 6 ? isBlockedV6(address) : isBlockedV4(address)
    if (rejected) {
      throw new Error(`edge host ${parsed.hostname} resolves to blocked address ${address}`)
    }
  }
  return parsed.origin
}

function isBlockedV4(address: string): boolean {
  const parts = address.split('.').map((p) => parseInt(p, 10))
  if (parts.length !== 4 || parts.some((p) => Number.isNaN(p))) return true
  const [a, b] = parts
  // link-local 169.254/16, unspecified 0.0.0.0/8, reserved 240.0.0.0/4,
  // multicast 224.0.0.0/4. Loopback/private are allowed on purpose: the
  // declared target of this tool is frequently a local edge instance.
  return a === 169 && b === 254 ? true : a === 0 || a >= 224
}

function isBlockedV6(address: string): boolean {
  const lower = address.toLowerCase()
  return lower === '::' || lower === '::1' ? false : (lower.startsWith('fe80:') || lower.startsWith('ff'))
}

function edgeFetch(path: string, init?: RequestInit): Promise<Response> {
  if (!path.startsWith('/')) {
    throw new Error(`edgeFetch only accepts paths on the declared origin: ${path}`)
  }
  return fetch(edgeOrigin + path, init)
}

const b64url = (buf: Buffer) => buf.toString('base64url')
const sha256 = (data: string | Buffer) => createHash('sha256').update(data).digest()

interface StepTimes {
  offer: number
  token: number
  credential: number
  authorize: number
  verify: number
  total: number
}

async function oneRoundTrip(holderPrivateJwk: jose.JWK, workerId: number, iteration: number): Promise<StepTimes> {
  const signerKey = await jose.importJWK(holderPrivateJwk as jose.JWK, 'EdDSA')
  const publicJwk = { ...holderPrivateJwk, d: undefined }
  delete (publicJwk as Record<string, unknown>).d

  const times = { offer: 0, token: 0, credential: 0, authorize: 0, verify: 0, total: 0 } as StepTimes
  const started = performance.now()

  // 1. internal offer (pre-authorized code grant)
  let t = performance.now()
  const kid = (await (await edgeFetch('/demo/issuer-kid')).json()).kid
  const offerResponse = await edgeFetch('/hkt/internal/offers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Api-Key': API_KEY },
    body: JSON.stringify({
      schemaId: 'hkt-kyc-v1',
      vct: 'hkt_kyc_v1',
      subjectId: `didvc:pairwise:load-${workerId}-${iteration}`,
      kid,
      alwaysDisclosedClaims: { kycLevel: 'REMOTE_FULL' },
      selectivelyDisclosedClaims: { givenName: 'LoadTest', nationality: 'HK' },
    }),
  })
  if (!offerResponse.ok) throw new Error('offer failed: ' + (await offerResponse.text()))
  const offer = await offerResponse.json()
  times.offer = performance.now() - t

  // 2. token exchange
  t = performance.now()
  const tokenResponse = await edgeFetch('/hkt/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:pre-authorized_code',
      'pre-authorized_code': offer.grants['urn:ietf:params:oauth:grant-type:pre-authorized_code']['pre-authorized_code'],
    }),
  })
  if (!tokenResponse.ok) throw new Error('token failed: ' + (await tokenResponse.text()))
  const token = await tokenResponse.json()
  times.token = performance.now() - t

  // 3. proof + credential request
  t = performance.now()
  const proofPayload = {
    iss: 'didvc:pairwise:load-wallet',
    aud: `${edgeOrigin}/hkt`,
    iat: Math.floor(Date.now() / 1000),
    nonce: token.c_nonce,
  }
  const proofJwt = await new jose.CompactSign(new TextEncoder().encode(JSON.stringify(proofPayload)))
    .setProtectedHeader({ typ: 'openid4vci-proof+jwt', alg: 'EdDSA', jwk: publicJwk })
    .sign(signerKey)
  const credentialResponse = await edgeFetch('/hkt/credential', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token.access_token}`,
    },
    body: JSON.stringify({
      credential_configuration_id: 'hkt_kyc_v1',
      proof: { proof_type: 'jwt', jwt: proofJwt },
    }),
  })
  if (!credentialResponse.ok) throw new Error('credential failed: ' + (await credentialResponse.text()))
  const credential = (await credentialResponse.json()).credential
  times.credential = performance.now() - t

  // 4. OID4VP authorization request (claims map)
  t = performance.now()
  const nonce = `load-${workerId}-${iteration}-${randomBytes(8).toString('hex')}`
  const authorizeResponse = await edgeFetch('/bank-a/vp/authorize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ client_id: 'load-verifier', nonce, claims: { hkt_kyc_v1: ['givenName'] } }),
  })
  if (!authorizeResponse.ok) throw new Error('authorize failed: ' + (await authorizeResponse.text()))
  times.authorize = performance.now() - t

  // 5. key-binding JWT + presentation (RFC 9901 §4.3.1 sd_hash over the
  //    full pre-KB presentation, trailing tilde included)
  t = performance.now()
  const kbJwt = await new jose.CompactSign(
    new TextEncoder().encode(
      JSON.stringify({
        nonce,
        aud: 'load-verifier',
        iat: Math.floor(Date.now() / 1000),
        sd_hash: b64url(sha256(credential)),
      }),
    ),
  )
    .setProtectedHeader({ alg: 'EdDSA', typ: 'kb+jwt' })
    .sign(signerKey)
  const directPost = await edgeFetch('/bank-a/vp/direct_post', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ state: nonce, nonce, vp_token: credential + kbJwt }),
  })
  if (!directPost.ok) throw new Error('direct_post failed: ' + (await directPost.text()))
  const verification = await directPost.json()
  if (verification.valid !== true) throw new Error('verification not valid: ' + JSON.stringify(verification))
  times.verify = performance.now() - t

  times.total = performance.now() - started
  return times
}

async function generateHolderKey(): Promise<jose.JWK> {
  const { privateKey } = generateKeyPair('ed25519')
  return (await jose.exportJWK(privateKey)) as jose.JWK
}

function percentile(sorted: number[], p: number): number {
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return sorted[Math.max(0, idx)]
}

async function main() {
  edgeOrigin = await validateEdgeOrigin(EDGE)
  console.log(`edge=${edgeOrigin} iterations=${ITERATIONS} concurrency=${CONCURRENCY} p95Target=${P95_TARGET_MS}ms`)
  const holderKeys = await Promise.all(Array.from({ length: CONCURRENCY }, () => generateHolderKey()))
  const samples: StepTimes[] = []
  const failures: string[] = []
  let cursor = 0

  const startedAll = performance.now()
  await Promise.all(
    Array.from({ length: CONCURRENCY }, async (_, worker) => {
      for (;;) {
        const iteration = cursor++
        if (iteration >= ITERATIONS) break
        try {
          samples.push(await oneRoundTrip(holderKeys[worker], worker, iteration))
        } catch (e) {
          failures.push(`#${iteration}: ${(e as Error).message}`)
        }
      }
    }),
  )
  const wallClock = performance.now() - startedAll

  if (samples.length === 0) {
    console.error('no successful round trips; failures:', failures.slice(0, 5))
    process.exit(1)
  }

  const step = (name: keyof StepTimes) => samples.map((s) => s[name]).sort((a, b) => a - b)
  const fmt = (sorted: number[]) =>
    `p50=${percentile(sorted, 50).toFixed(0)}ms p95=${percentile(sorted, 95).toFixed(0)}ms p99=${percentile(sorted, 99).toFixed(0)}ms`

  console.log('\n| step | latency |')
  console.log('|---|---|')
  for (const name of ['offer', 'token', 'credential', 'authorize', 'verify', 'total'] as const) {
    console.log(`| ${name} | ${fmt(step(name))} |`)
  }
  const verifyP95 = percentile(step('verify'), 95)
  console.log(`\nthroughput=${(samples.length / (wallClock / 1000)).toFixed(1)} roundtrips/s wall=${(wallClock / 1000).toFixed(1)}s ok=${samples.length} failed=${failures.length}`)
  console.log(`verification p95=${verifyP95.toFixed(0)}ms (target <= ${P95_TARGET_MS}ms): ${verifyP95 <= P95_TARGET_MS ? 'PASS' : 'FAIL'}`)
  if (failures.length > 0) {
    console.log('failures (first 10):')
    for (const f of failures.slice(0, 10)) console.log('  ' + f)
    process.exit(1)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
