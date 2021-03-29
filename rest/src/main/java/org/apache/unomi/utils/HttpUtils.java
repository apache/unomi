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

package org.apache.unomi.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ConfigSharingService;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;


/**
 * This is duplicate of the class from the wab bundle, the original file will be removed once endpoints forwarded
 */
public class HttpUtils {

    private static final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365; // 1 year

    /**
     * Utility to dump request info for a given http request.
     * @param httpServletRequest request to dump
     * @return the info as a String
     */
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
        stringBuilder.append(httpServletRequest.getMethod()).append(" ").append(httpServletRequest.getRequestURI());
        if (httpServletRequest.getQueryString() != null) {
            stringBuilder.append("?").append(httpServletRequest.getQueryString());
        }
        stringBuilder.append(" serverName=").append(httpServletRequest.getServerName()).append(" serverPort=").append(httpServletRequest.getServerPort()).append(" remoteAddr=").append(httpServletRequest.getRemoteAddr()).append(" remotePort=").append(httpServletRequest.getRemotePort()).append("\n");
        return stringBuilder.toString();
    }


    public static String dumpRequestCookies(Cookie[] cookies) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Cookies:\n");
        if (cookies == null) {
            stringBuilder.append("  none");
        } else {
            for (Cookie cookie : cookies) {
                stringBuilder.append("  ").append(cookie.getName()).append("=").append(cookie.getValue()).append(" domain=").append(cookie.getDomain()).append(" path=").append(cookie.getPath()).append(" maxAge=").append(cookie.getMaxAge()).append(" httpOnly=").append(cookie.isHttpOnly()).append(" secure=").append(cookie.getSecure()).append(" version=").append(cookie.getVersion()).append(" comment=").append(cookie.getComment()).append("\n");
            }
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

    /**
     * Return the cookie string for the given profile
     * We can't use the build in NewCookie jax-rs object as it does not support the SameSite value.
     * @param profile to parse
     * @param configSharingService shared config location.
     * @return the cookie string to set in the header.
     */
    public static String getProfileCookieString(Profile profile, ConfigSharingService configSharingService) {
        final String profileIdCookieDomain = (String) configSharingService.getProperty("profileIdCookieDomain");
        final String profileIdCookieName = (String) configSharingService.getProperty("profileIdCookieName");
        final Integer profileIdCookieMaxAgeInSeconds = (Integer) configSharingService.getProperty("profileIdCookieMaxAgeInSeconds") ;
        return profileIdCookieName + "=" + profile.getItemId() +
                "; Path=/" +
                "; Max-Age=" + profileIdCookieMaxAgeInSeconds +
                (StringUtils.isNotBlank(profileIdCookieDomain) ? ("; Domain=" + profileIdCookieDomain) : "")  +
                "; SameSite=Lax";
    }
}
