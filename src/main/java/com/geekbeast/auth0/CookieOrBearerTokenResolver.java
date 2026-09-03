/*
 * Copyright (C) 2024. Chronicle.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.geekbeast.auth0;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Resolves bearer tokens from the Authorization header, an httpOnly cookie, or a legacy
 * authorization cookie. Priority order:
 * <ol>
 *   <li>Authorization header (for API clients, mobile, API keys)</li>
 *   <li>httpOnly "chronicle_auth" cookie (F-P0-2: secure browser auth)</li>
 *   <li>Legacy "authorization" cookie with Bearer prefix</li>
 * </ol>
 *
 * Cookie-based auth requires CSRF validation via X-CSRF-Token header.
 *
 * @author uzaira0
 */
public class CookieOrBearerTokenResolver implements BearerTokenResolver {

    private static final Logger logger = LoggerFactory.getLogger( CookieOrBearerTokenResolver.class );

    private static final String AUTHORIZATION_HEADER  = "Authorization";
    private static final String AUTHORIZATION_COOKIE  = AUTHORIZATION_HEADER.toLowerCase();
    private static final String BEARER_PREFIX         = "Bearer";
    private static final String HTTPONLY_AUTH_COOKIE   = "chronicle_auth";
    private static final String CSRF_COOKIE           = "ol_csrf_token";
    private static final String CSRF_HEADER           = "X-CSRF-Token";

    @Override
    public String resolve( HttpServletRequest request ) {

        // 1. Authorization header (API clients, mobile, programmatic access)
        final String authorizationHeader = getAuthorizationTokenFromHeader( request );
        if ( authorizationHeader != null && authorizationHeader.startsWith( BEARER_PREFIX ) ) {
            final String[] parts = authorizationHeader.split( " " );
            return parts.length == 2 ? parts[ 1 ] : null;
        }

        // 2. httpOnly "chronicle_auth" cookie (F-P0-2: browser auth via httpOnly cookie)
        final String httpOnlyToken = getRequestCookie( request, HTTPONLY_AUTH_COOKIE );
        if ( httpOnlyToken != null && !httpOnlyToken.isEmpty() ) {
            if ( validateCsrf( request ) ) {
                return httpOnlyToken;
            }
            logger.debug( "CSRF validation failed for httpOnly cookie auth" );
            return null;
        }

        // 3. Legacy: "authorization" cookie with "Bearer <token>" value
        final String legacyCookie = getRequestCookie( request, AUTHORIZATION_COOKIE );
        if ( legacyCookie != null && legacyCookie.startsWith( BEARER_PREFIX ) ) {
            if ( validateCsrf( request ) ) {
                final String[] parts = legacyCookie.split( " " );
                return parts.length == 2 ? parts[ 1 ] : null;
            }
            logger.debug( "CSRF validation failed for legacy cookie auth" );
        }

        return null;
    }

    /**
     * Validates CSRF double-submit pattern via X-CSRF-Token header only.
     */
    private boolean validateCsrf( HttpServletRequest request ) {
        final String csrfFromCookie = getRequestCookie( request, CSRF_COOKIE );
        if ( csrfFromCookie == null ) {
            return false;
        }

        final String csrfFromHeader = request.getHeader( CSRF_HEADER );
        return csrfFromHeader != null && csrfFromCookie.equals( csrfFromHeader );
    }

    private static String getRequestCookie( HttpServletRequest request, String targetCookie ) {
        Cookie[] cookies = request.getCookies();
        if ( cookies != null ) {
            for ( Cookie cookie : cookies ) {
                if ( targetCookie.equals( cookie.getName() ) ) {
                    try {
                        return URLDecoder.decode( cookie.getValue(), StandardCharsets.UTF_8 );
                    } catch ( Exception e ) {
                        logger.error( "Unable to decode {} cookie.", targetCookie );
                    }
                }
            }
        }
        return null;
    }

    private static String getAuthorizationTokenFromHeader( HttpServletRequest request ) {
        return request.getHeader( AUTHORIZATION_HEADER );
    }
}
