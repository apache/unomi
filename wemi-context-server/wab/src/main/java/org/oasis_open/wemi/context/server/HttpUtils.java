package org.oasis_open.wemi.context.server;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Created by loom on 10.06.14.
 */
public class HttpUtils {

    public static void setupCORSHeaders(HttpServletRequest httpServletRequest, ServletResponse response) throws IOException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            if (httpServletRequest != null) {
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
        System.out.println("===================================================================================");
        String sessionId = null;
        if (httpServletRequest.getSession(false) != null) {
            sessionId = httpServletRequest.getSession(false).getId();
        }
        System.out.println(httpServletRequest.getMethod() + " " + httpServletRequest.getRequestURI() +
                "?" + httpServletRequest.getQueryString() +
                " sessionId=" + sessionId +
                " serverName=" + httpServletRequest.getServerName() +
                " serverPort=" + httpServletRequest.getServerPort() +
                " remoteAddr=" + httpServletRequest.getRemoteAddr() +
                " remotePort=" + httpServletRequest.getRemotePort());
    }


    public static void dumpRequestCookies(Cookie[] cookies) {
        System.out.println("Cookies:");
        System.out.println("--------");
        for (Cookie cookie : cookies) {
            System.out.println("  name=" + cookie.getName() +
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
        System.out.println("Headers:");
        System.out.println("--------");
        Enumeration<String> headerNameEnum = httpServletRequest.getHeaderNames();
        while (headerNameEnum.hasMoreElements()) {
            String headerName = headerNameEnum.nextElement();
            System.out.println(headerName + ": " + httpServletRequest.getHeader(headerName));
        }
    }

}
