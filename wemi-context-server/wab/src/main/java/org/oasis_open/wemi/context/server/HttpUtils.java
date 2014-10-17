package org.oasis_open.wemi.context.server;

import org.oasis_open.wemi.context.server.api.Persona;
import org.oasis_open.wemi.context.server.api.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by loom on 10.06.14.
 */
public class HttpUtils {

    public static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

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
            httpServletResponse.flushBuffer();
        }
    }

    public static void dumpBasicRequestInfo(HttpServletRequest httpServletRequest) {
        logger.info("=== ");
        String sessionId = null;
        if (httpServletRequest.getSession(false) != null) {
            sessionId = httpServletRequest.getSession(false).getId();
        }
        logger.info(httpServletRequest.getMethod() + " " + httpServletRequest.getRequestURI());
        if (httpServletRequest.getQueryString() != null) {
            logger.info("?" + httpServletRequest.getQueryString());
        }
        System.out.println(
                " sessionId=" + sessionId +
                        " serverName=" + httpServletRequest.getServerName() +
                        " serverPort=" + httpServletRequest.getServerPort() +
                        " remoteAddr=" + httpServletRequest.getRemoteAddr() +
                        " remotePort=" + httpServletRequest.getRemotePort());
    }


    public static void dumpRequestCookies(Cookie[] cookies) {
        logger.info("Cookies:");
        logger.info("--------");
        for (Cookie cookie : cookies) {
            logger.info("  name=" + cookie.getName() +
                    " value=" + cookie.getValue() +
                    " domain=" + cookie.getDomain() +
                    " path=" + cookie.getPath() +
                    " maxAge=" + cookie.getMaxAge() +
                    " httpOnly=" + cookie.isHttpOnly() +
                    " secure=" + cookie.getSecure() +
                    " version=" + cookie.getVersion() +
                    " comment=" + cookie.getComment());
        }
    }

    public static void dumpRequestHeaders(HttpServletRequest httpServletRequest) {
        logger.info("Headers:");
        logger.info("--------");
        Enumeration<String> headerNameEnum = httpServletRequest.getHeaderNames();
        while (headerNameEnum.hasMoreElements()) {
            String headerName = headerNameEnum.nextElement();
            logger.info(headerName + ": " + httpServletRequest.getHeader(headerName));
        }
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

}
