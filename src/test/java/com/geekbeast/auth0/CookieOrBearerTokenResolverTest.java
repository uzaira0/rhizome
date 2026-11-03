package com.geekbeast.auth0;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CookieOrBearerTokenResolverTest {
    @Test
    public void onlyTheCurrentCookieAndAuthorizationHeaderAuthenticate() {
        var request = mock(HttpServletRequest.class);
        var resolver = new CookieOrBearerTokenResolver();
        when(request.getHeader("X-CSRF-Token")).thenReturn("csrf");
        when(request.getCookies()).thenReturn(new Cookie[] {
            new Cookie("authorization", "Bearer token"), new Cookie("ol_csrf_token", "csrf")
        });
        assertNull(resolver.resolve(request));
        when(request.getCookies()).thenReturn(new Cookie[] {
            new Cookie("chronicle_auth", "token"), new Cookie("ol_csrf_token", "csrf")
        });
        assertEquals("token", resolver.resolve(request));
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);
        assertNull(resolver.resolve(request));
        when(request.getHeader("Authorization")).thenReturn("Bearer header-token");
        assertEquals("header-token", resolver.resolve(request));
    }
}
