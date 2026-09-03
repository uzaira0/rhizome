/*
 * Copyright (C) 2017. OpenLattice, Inc
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
 *
 * You can contact the owner of the copyright at support@openlattice.com
 *
 */

package com.geekbeast.auth0;

import com.geekbeast.authentication.Auth0AuthenticationConfiguration;
import com.geekbeast.authentication.Auth0Configuration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import jakarta.inject.Inject;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT security pod using Spring Security 6's OAuth2 Resource Server support.
 * Supports HS256 symmetric key validation for self-hosted JWT tokens.
 * No Auth0 cloud SDK dependencies required -- uses only Spring Security + javax.crypto.
 *
 * @author Matthew Tamayo-Rios &lt;matthew@kryptnostic.com&gt;
 */
@EnableMethodSecurity(proxyTargetClass = true)
@EnableWebSecurity(
        debug = false )
public class Auth0SecurityPod {
    @Inject
    private Auth0Configuration configuration;

    @Bean
    public SecurityFilterChain securityFilterChain( HttpSecurity http ) throws Exception {
        http
            .csrf( AbstractHttpConfigurer::disable )
            .oauth2ResourceServer( oauth2 -> oauth2
                .jwt( jwt -> jwt
                    .decoder( jwtDecoder() )
                )
                .bearerTokenResolver( cookieOrBearerTokenResolver() )
            )
            .sessionManagement( session -> session
                .sessionCreationPolicy( SessionCreationPolicy.STATELESS )
            )
            .securityContext( sc -> sc
                .requireExplicitSave( true )
            );

        // Ignore OPTIONS requests for CORS preflight
        http.authorizeHttpRequests( auth -> auth
            .requestMatchers( HttpMethod.OPTIONS, "/**" ).permitAll()
        );

        // Apply subclass authorization rules
        authorizeRequests( http );

        return http.build();
    }

    @Bean
    public CookieOrBearerTokenResolver cookieOrBearerTokenResolver() {
        return new CookieOrBearerTokenResolver();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Use the first configuration entry (primary auth config)
        Auth0AuthenticationConfiguration config = configuration.getClients().iterator().next();
        return createJwtDecoder( config );
    }

    /**
     * Override this method in subclass to apply custom authorization strategies to your application endpoints.
     */
    protected void authorizeRequests( HttpSecurity http ) throws Exception {
        // Default: no additional authorization rules
    }

    private JwtDecoder createJwtDecoder( Auth0AuthenticationConfiguration authConfig ) {
        if ( !"HS256".equals( authConfig.getSigningAlgorithm() ) ) {
            throw new IllegalArgumentException(
                    authConfig.getSigningAlgorithm()
                            + " is not supported. Only HS256 is supported for self-hosted JWT." );
        }

        final byte[] secret;
        if ( authConfig.isBase64EncodedSecret() ) {
            secret = Base64.getUrlDecoder().decode( authConfig.getSecret() );
        } else {
            secret = authConfig.getSecret().getBytes( StandardCharsets.UTF_8 );
        }
        SecretKey key = new SecretKeySpec( secret, "HmacSHA256" );
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey( key ).build();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator( authConfig.getIssuer() ),
                new JwtClaimValidator<>( JwtClaimNames.AUD, aud -> {
                    if ( aud instanceof String ) {
                        return authConfig.getAudience().equals( aud );
                    } else if ( aud instanceof List ) {
                        return ( (List<?>) aud ).contains( authConfig.getAudience() );
                    }
                    return false;
                })
        );
        decoder.setJwtValidator( validators );
        return decoder;
    }
}
