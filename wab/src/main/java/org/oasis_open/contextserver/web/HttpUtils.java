package org.oasis_open.contextserver.web;

import org.oasis_open.contextserver.api.Persona;
import org.oasis_open.contextserver.api.User;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by loom on 10.06.14.
 */
public class HttpUtils {

    private static final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 10; // 10-years

    private static int cookieAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;

    public static void setupCORSHeaders(HttpServletRequest httpServletRequest, ServletResponse response) throws IOException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            if (httpServletRequest != null && httpServletRequest.getHeader("Origin") != null) {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", httpServletRequest.getHeader("Origin"));
            } else {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
            }
            httpServletResponse.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
            httpServletResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpServletResponse.setHeader("Access-Control-Allow-Methods", "OPTIONS, POST, GET");
            // httpServletResponse.setHeader("Access-Control-Max-Age", "600");
            // httpServletResponse.setHeader("Access-Control-Expose-Headers","Access-Control-Allow-Origin");
            // httpServletResponse.flushBuffer();
        }
    }

    public static String dumpRequestInfo(HttpServletRequest httpServletRequest) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("======================================================================================\n");
        stringBuilder.append(dumpBasicRequestInfo(httpServletRequest));
        stringBuilder.append(dumpRequestHeaders(httpServletRequest));
        stringBuilder.append(dumpRequestCookies(httpServletRequest.getCookies()));
        stringBuilder.append("======================================================================================\n");
        return stringBuilder.toString();
    }

    public static String dumpBasicRequestInfo(HttpServletRequest httpServletRequest) {
        StringBuilder stringBuilder = new StringBuilder();
        String sessionId = null;
        if (httpServletRequest.getSession(false) != null) {
            sessionId = httpServletRequest.getSession(false).getId();
        }
        stringBuilder.append(httpServletRequest.getMethod()).append(" ").append(httpServletRequest.getRequestURI());
        if (httpServletRequest.getQueryString() != null) {
            stringBuilder.append("?").append(httpServletRequest.getQueryString());
        }
        stringBuilder.append(" sessionId=").append(sessionId).append(" serverName=").append(httpServletRequest.getServerName()).append(" serverPort=").append(httpServletRequest.getServerPort()).append(" remoteAddr=").append(httpServletRequest.getRemoteAddr()).append(" remotePort=").append(httpServletRequest.getRemotePort()).append("\n");
        return stringBuilder.toString();
    }


    public static String dumpRequestCookies(Cookie[] cookies) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Cookies:\n");
        for (Cookie cookie : cookies) {
            stringBuilder.append("  ").append(cookie.getName()).append("=").append(cookie.getValue()).append(" domain=").append(cookie.getDomain()).append(" path=").append(cookie.getPath()).append(" maxAge=").append(cookie.getMaxAge()).append(" httpOnly=").append(cookie.isHttpOnly()).append(" secure=").append(cookie.getSecure()).append(" version=").append(cookie.getVersion()).append(" comment=").append(cookie.getComment()).append("\n");
        }
        return stringBuilder.toString();
    }

    public static String dumpRequestHeaders(HttpServletRequest httpServletRequest) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Headers:\n");
        Enumeration<String> headerNameEnum = httpServletRequest.getHeaderNames();
        while (headerNameEnum.hasMoreElements()) {
            String headerName = headerNameEnum.nextElement();
            stringBuilder.append("  ").append(headerName).append(": ").append(httpServletRequest.getHeader(headerName)).append("\n");
        }
        return stringBuilder.toString();
    }

    public static String getBaseRequestURL(HttpServletRequest httpServletRequest) {
        String baseRequestURL;
        baseRequestURL = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName();
        if (("http".equals(httpServletRequest.getScheme()) && (httpServletRequest.getServerPort() == 80)) ||
                ("https".equals(httpServletRequest.getScheme()) && (httpServletRequest.getServerPort() == 443))) {
            // normal case, don't add the port
        } else {
            baseRequestURL += ":" + httpServletRequest.getServerPort();
        }
        return baseRequestURL;
    }

    public static void sendProfileCookie(User user, ServletResponse response, String profileIdCookieName, String personaIdCookieName) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            if (user instanceof Persona) {
                Cookie personaIdCookie = new Cookie(personaIdCookieName, user.getItemId());
                personaIdCookie.setPath("/");
                personaIdCookie.setMaxAge(cookieAgeInSeconds);
                httpServletResponse.addCookie(personaIdCookie);
            } else {
                Cookie visitorIdCookie = new Cookie(profileIdCookieName, user.getItemId());
                visitorIdCookie.setPath("/");
                visitorIdCookie.setMaxAge(cookieAgeInSeconds);
                httpServletResponse.addCookie(visitorIdCookie);
            }
        }
    }

    public static void clearCookie(ServletResponse response, String cookieName) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            Cookie cookie = new Cookie(cookieName, "");
            cookie.setPath("/");
            cookie.setMaxAge(0);
            httpServletResponse.addCookie(cookie);
        }
    }

    public static Map<String, Cookie> getCookieMap(Cookie[] cookieArray) {
        Map<String, Cookie> cookieMap = new LinkedHashMap<String, Cookie>();
        for (Cookie cookie : cookieArray) {
            cookieMap.put(cookie.getName(), cookie);
        }
        return cookieMap;
    }

    public static String getPayload(HttpServletRequest request) throws IOException {
        if ("post".equals(request.getMethod().toLowerCase())) {
            StringBuilder buffer = new StringBuilder();
            String line;
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            if (buffer.length() > 0) {
                return buffer.toString();
            }
        } else if ("get".equals(request.getMethod().toLowerCase()) && request.getParameter("payload") != null) {
            return request.getParameter("payload");
        }
        return null;
    }
}
