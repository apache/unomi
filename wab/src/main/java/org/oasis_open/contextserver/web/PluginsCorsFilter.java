package org.oasis_open.contextserver.web;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A simple filter to setup CORS headers in front of plugin web resources.
 */
public class PluginsCorsFilter implements Filter {

    FilterConfig filterConfig;


    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String httpMethod = httpServletRequest.getMethod();
//        HttpUtils.dumpBasicRequestInfo(httpServletRequest);
//        HttpUtils.dumpRequestHeaders(httpServletRequest);

        if ("options".equals(httpMethod.toLowerCase())) {
            HttpUtils.setupCORSHeaders(httpServletRequest, httpServletResponse);
            httpServletResponse.flushBuffer();
            return;
        }

        HttpUtils.setupCORSHeaders(httpServletRequest, httpServletResponse);

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public void destroy() {
    }


}
