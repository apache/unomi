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
package org.apache.unomi.samples.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demo "authentication server" for the login sample.
 * <p>
 * The browser posts the form here. This servlet checks a hardcoded demo password,
 * then calls Unomi {@code /cxs/context.json} with <strong>trusted</strong> Basic
 * credentials (a tenant private key) so {@code mergeProfilesOnPropertyAction}
 * is allowed. The browser must not call Unomi for login events itself.
 */
@Component(
        service = Servlet.class,
        immediate = true,
        configurationPid = "org.apache.unomi.samples.login",
        property = {
                "osgi.http.whiteboard.servlet.name=LoginSampleServlet",
                "osgi.http.whiteboard.servlet.pattern=/login/authenticate"
        }
)
@Designate(ocd = LoginServlet.Config.class)
public class LoginServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginServlet.class);
    /** Must match the {@code configurationPid} above; quoted in the hint printed when config is missing. */
    private static final String CONFIGURATION_PID = "org.apache.unomi.samples.login";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Attribute holding the Unomi session id we generated for this browser's container session. */
    private static final String UNOMI_SESSION_ID_ATTRIBUTE = "org.apache.unomi.samples.login.unomiSessionId";
    /**
     * Idle timeout applied to the container sessions this servlet creates. A login round trip takes
     * seconds, so a few minutes is generous; see {@link #resolveSessionId} for why it is capped.
     */
    private static final int SESSION_MAX_INACTIVE_SECONDS = 300;

    private String unomiBaseUrl = "http://localhost:8181";
    private String tenantId = "default";
    private String scope = "default";
    private String privateKey = "";
    private String demoPassword = "";

    @ObjectClassDefinition(
            name = "Unomi login sample",
            description = "Trusted credentials used by /login/authenticate to call Unomi (UNOMI-972)"
    )
    public @interface Config {

        @AttributeDefinition(name = "Unomi base URL", description = "Base URL of this Unomi instance")
        String unomiBaseUrl() default "http://localhost:8181";

        @AttributeDefinition(
                name = "Tenant ID",
                description = "Tenant the login events belong to. Sent as the Basic auth user name alongside privateKey."
        )
        String tenantId() default "default";

        @AttributeDefinition(
                name = "Scope",
                description = "Event/source scope (must already exist for the tenant; systemscope is not a valid event scope)"
        )
        String scope() default "default";

        @AttributeDefinition(
                name = "Tenant private key",
                description = "Required. Plain-text tenant private API key; authenticates as tenant "
                        + "administrator, which is what allows the profile merge."
        )
        String privateKey() default "";

        @AttributeDefinition(
                name = "Demo login password",
                description = "Password the sample login form accepts. Required; no default is shipped, "
                        + "so choose one when configuring the sample. Stands in for the user directory "
                        + "a real integration would authenticate against."
        )
        String demoPassword() default "";
    }

    @Activate
    @Modified
    public void activate(Config config) {
        this.unomiBaseUrl = config.unomiBaseUrl();
        this.tenantId = config.tenantId();
        this.scope = config.scope() != null && !config.scope().isBlank() ? config.scope().trim() : "default";
        this.privateKey = config.privateKey() != null ? config.privateKey().trim() : "";
        this.demoPassword = config.demoPassword() != null ? config.demoPassword().trim() : "";
        logConfigurationStatus();
    }

    /**
     * Reports whether the sample is usable, so that starting the bundle after configuring it is a
     * self-checking step. Re-runs on every configuration update because {@link Modified} is applied
     * to {@link #activate}, so correcting a value and running {@code config:update} reprints this.
     * <p>
     * Never logs a credential, only whether one is present.
     */
    private void logConfigurationStatus() {
        List<String> missing = new ArrayList<>();
        if (demoPassword.isEmpty()) {
            missing.add("demoPassword (the password the login form accepts)");
        }
        if (privateKey.isEmpty()) {
            missing.add("privateKey (a tenant private API key)");
        }

        if (missing.isEmpty()) {
            LOGGER.info("Login sample ready - open {}/login/index.html (tenantId={}, scope={})",
                    unomiBaseUrl, tenantId, scope);
            return;
        }

        LOGGER.warn("Login sample is NOT usable yet, missing configuration: {}.\n"
                        + "Set it from the Karaf console, then start the bundle again:\n"
                        + "  config:edit {}\n"
                        + "  config:property-set demoPassword <choose-a-password>\n"
                        + "  config:property-set privateKey <tenant-private-key>\n"
                        + "  config:update",
                String.join(", ", missing), CONFIGURATION_PID);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // This endpoint is an unauthenticated state-changing POST that then calls Unomi with trusted
        // credentials, so a hostile page could otherwise drive it from a victim's browser. A real
        // integration must use a proper per-session CSRF token; this same-origin check is only the
        // lightweight equivalent that fits a sample.
        if (!isSameOrigin(req)) {
            writeError(resp, HttpServletResponse.SC_FORBIDDEN, "Cross-origin request rejected");
            return;
        }

        String email = trim(req.getParameter("email"));
        String firstName = trim(req.getParameter("firstName"));
        String lastName = trim(req.getParameter("lastName"));
        String password = trim(req.getParameter("password"));

        // No demo password ships with the sample, for the same reason Unomi itself no longer ships a
        // default admin password: a credential baked into published source is a credential everyone has.
        if (demoPassword.isEmpty()) {
            writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "demoPassword is not configured: run 'config:edit " + CONFIGURATION_PID + "', "
                            + "'config:property-set demoPassword <password>', 'config:update'");
            return;
        }
        if (!demoPassword.equals(password)) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
            return;
        }
        if (email.isEmpty()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "email is required");
            return;
        }

        // The session id must be derived from state this servlet controls, never from the request
        // parameters. We call Unomi with trusted credentials, and a trusted caller is allowed to
        // adopt whatever profile owns the session id it passes: forwarding a client-supplied id
        // would launder untrusted client input across the trust boundary and let anyone who guesses
        // another visitor's session id rebind or merge that victim's profile. Storing a generated
        // id on the container's own HttpSession keeps it attacker-unreachable while staying stable
        // across requests from the same browser, which is what lets Unomi recover the visitor's
        // pre-login anonymous profile.
        String sessionId = resolveSessionId(req);

        // Only a tenant private key. A system administrator credential would also satisfy the merge
        // gate, but it grants far more than this sample needs and is scoped to the whole instance
        // rather than one tenant, so it is deliberately not accepted here.
        if (privateKey.isEmpty()) {
            writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "privateKey is not configured: run 'config:edit " + CONFIGURATION_PID + "', "
                            + "'config:property-set privateKey <tenant-private-key>', 'config:update'");
            return;
        }

        ObjectNode contextRequest = MAPPER.createObjectNode();
        ObjectNode source = contextRequest.putObject("source");
        source.put("itemId", "/login");
        source.put("itemType", "page");
        source.put("scope", scope);

        ArrayNode events = contextRequest.putArray("events");
        ObjectNode loginEvent = events.addObject();
        loginEvent.put("eventType", "login");
        loginEvent.put("scope", scope);
        ObjectNode target = loginEvent.putObject("target");
        target.put("itemId", email);
        target.put("itemType", "exampleUser");
        ObjectNode targetProps = target.putObject("properties");
        targetProps.put("email", email);
        targetProps.put("firstName", firstName);
        targetProps.put("lastName", lastName);

        contextRequest.set("requiredProfileProperties", MAPPER.valueToTree(List.of("*")));
        contextRequest.set("requiredSessionProperties", MAPPER.valueToTree(List.of("*")));

        byte[] body = MAPPER.writeValueAsBytes(contextRequest);

        int status;
        JsonNode responseJson;
        List<String> setCookieValues;
        try {
            URL url = new URL(unomiBaseUrl.replaceAll("/$", "") + "/cxs/context.json?sessionId="
                    + URLEncoder.encode(sessionId, StandardCharsets.UTF_8));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            // The tenant is the Basic auth user name: Unomi derives the tenant context from the key
            // itself, so no X-Unomi-Tenant-Id header is needed.
            String token = Base64.getEncoder()
                    .encodeToString((tenantId + ":" + privateKey).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + token);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream != null) {
                responseJson = MAPPER.readTree(stream);
            } else {
                responseJson = MAPPER.createObjectNode();
            }
            // getHeaderField() only returns the first value: Unomi can set several cookies
            // (profile id and session id), so every value has to be forwarded.
            setCookieValues = headerValues(conn, "Set-Cookie");
        } catch (IOException e) {
            // Never let the container render a stack trace: it would disclose the Unomi endpoint
            // and internal class names to an unauthenticated caller.
            LOGGER.warn("Login sample could not complete the call to Unomi", e);
            writeError(resp, HttpServletResponse.SC_BAD_GATEWAY, "Profile service unavailable, please try again later");
            return;
        }

        if (setCookieValues != null) {
            for (String setCookie : setCookieValues) {
                if (setCookie != null) {
                    resp.addHeader("Set-Cookie", setCookie);
                }
            }
        }
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        MAPPER.writeValue(resp.getOutputStream(), responseJson);
    }

    /**
     * Returns <em>all</em> values of a response header. Unlike {@code getHeaderFields().get(name)},
     * this keeps the case-insensitive matching that {@code getHeaderField(name)} provided.
     * <p>
     * Package-private rather than private so {@code LoginServletTest} can cover it without
     * reflection.
     */
    static List<String> headerValues(HttpURLConnection conn, String name) {
        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the Unomi session id bound to this browser's container session, generating one on
     * first use. Deliberately not read from any request parameter or header — see the call site.
     * <p>
     * Package-private rather than private so {@code LoginServletTest} can exercise the trust
     * boundary directly instead of going through reflection.
     */
    static String resolveSessionId(HttpServletRequest req) {
        HttpSession httpSession = req.getSession(true);
        // Guard against two concurrent first requests from the same browser generating two different
        // ids. The session object is the conventional mutex here; do not lock on an interned session
        // id, which shares a JVM-wide monitor with any other code that interns the same value.
        synchronized (httpSession) {
            Object existing = httpSession.getAttribute(UNOMI_SESSION_ID_ATTRIBUTE);
            if (existing instanceof String && !((String) existing).isEmpty()) {
                return (String) existing;
            }
            String generated = UUID.randomUUID().toString();
            httpSession.setAttribute(UNOMI_SESSION_ID_ATTRIBUTE, generated);
            // Bound the lifetime of the sessions this servlet creates. The demo password gate above
            // is NOT authentication: it is a single shared demo password, so
            // anyone can pass it repeatedly while discarding the session cookie each time. Every such
            // POST would otherwise pin a container session in memory for the container's default
            // timeout (commonly 30 minutes), which is a cheap memory-exhaustion path. Expiring these
            // sessions after a few minutes keeps the id stable for a real browser's login round trip
            // while letting the container reclaim the throwaway ones almost immediately.
            httpSession.setMaxInactiveInterval(SESSION_MAX_INACTIVE_SECONDS);
            return generated;
        }
    }

    /**
     * Lightweight CSRF defence: when the browser sends an {@code Origin} header it must match the
     * origin this request was addressed to. A missing header (same-origin form posts on older
     * browsers, curl) is tolerated; an unparsable or mismatching one is rejected.
     * <p>
     * Package-private rather than private so {@code LoginServletTest} can cover it without
     * reflection.
     */
    static boolean isSameOrigin(HttpServletRequest req) {
        String origin = trim(req.getHeader("Origin"));
        if (origin.isEmpty()) {
            return true;
        }
        URI originUri;
        try {
            originUri = new URI(origin);
        } catch (URISyntaxException e) {
            LOGGER.debug("Rejecting login request with unparsable Origin header", e);
            return false;
        }
        String originScheme = originUri.getScheme();
        String originHost = originUri.getHost();
        if (originScheme == null || originHost == null) {
            // Includes the opaque "null" origin sent by sandboxed frames.
            return false;
        }
        return originScheme.equalsIgnoreCase(req.getScheme())
                && originHost.equalsIgnoreCase(req.getServerName())
                && defaultedPort(originScheme, originUri.getPort()) == req.getServerPort();
    }

    private static int defaultedPort(String scheme, int port) {
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", message);
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        MAPPER.writeValue(resp.getOutputStream(), error);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
