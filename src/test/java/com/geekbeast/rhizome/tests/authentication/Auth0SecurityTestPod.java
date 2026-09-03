package com.geekbeast.rhizome.tests.authentication;

import com.geekbeast.auth0.Auth0SecurityPod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@EnableMethodSecurity
@EnableWebSecurity(
        debug = false )
public class Auth0SecurityTestPod extends Auth0SecurityPod {
    @Override protected void authorizeRequests( HttpSecurity http ) throws Exception {
        // Spring Security 6's OAuth2 resource server maps the JWT `scope` claim to
        // authorities prefixed with SCOPE_ (e.g. "openid email" -> SCOPE_openid, SCOPE_email).
        // The bare-name authorities ("openid"/"email") this was written against no longer
        // exist, so match the prefixed names that the BearerTokenAuthenticationFilter grants.
        http.authorizeHttpRequests( auth -> auth
                .requestMatchers( "/api/unsecured/**" ).authenticated()
                .requestMatchers( "/api/secured/foo" ).hasAnyAuthority( "a", "b" )
                .requestMatchers( "/api/secured/admin" ).hasAnyAuthority( "SCOPE_openid" )
                .requestMatchers( "/api/secured/user" ).hasAnyAuthority( "SCOPE_email" )
                // Spring Security 6's authorizeHttpRequests denies unmatched requests by
                // default, unlike the SS5 authorizeRequests() this was migrated from (which
                // permitted them). /api/secured/test carries a valid JWT but matches none of
                // the rules above, so without this catch-all it 403s. Restore the original
                // "specific rules enforced, everything else allowed" semantics.
                .anyRequest().permitAll()
        );
    }
}
