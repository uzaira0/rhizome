package com.openlattice.authentication;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.geekbeast.authentication.Auth0AuthenticationConfiguration;
import com.geekbeast.authentication.Auth0Configuration;
import com.google.common.collect.ImmutableSet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Self-hosted HS256 JWT authentication test. Generates and validates JWT tokens
 * locally without any Auth0 cloud service dependency.
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
public class AuthenticationTest {
    private static final Logger logger           = LoggerFactory.getLogger( AuthenticationTest.class );
    private static final String issuer           = "https://localhost/";
    private static final String audience         = "test-client-id";
    private static final String secret           = "test-jwt-secret-for-rhizome-tests";
    private static final String signingAlgorithm = "HS256";

    public static final Auth0AuthenticationConfiguration authConfiguration = new Auth0AuthenticationConfiguration(
            issuer, audience, secret, Optional.of( false ), signingAlgorithm
    );
    public static final Auth0Configuration configuration = new Auth0Configuration(
            "localhost",
            "test-client-id",
            secret,
            ImmutableSet.of( authConfiguration ),
            Optional.empty(),
            "localhost" );

    private static final Algorithm algorithm = Algorithm.HMAC256( secret );
    private static final JwtDecoder jwtDecoder;

    static {
        SecretKey key = new SecretKeySpec(
                secret.getBytes( StandardCharsets.UTF_8 ), "HmacSHA256" );
        jwtDecoder = NimbusJwtDecoder.withSecretKey( key ).build();
    }

    /**
     * Creates a self-hosted HS256 JWT token for testing.
     */
    public static String createTestToken( String subject ) {
        return JWT.create()
                .withSubject( subject )
                .withIssuer( issuer )
                .withAudience( audience )
                .withClaim( "email", subject + "@test.local" )
                .withClaim( "email_verified", true )
                .withClaim( "scope", "openid email" )
                .withIssuedAt( Date.from( Instant.now() ) )
                .withExpiresAt( Date.from( Instant.now().plus( 1, ChronoUnit.HOURS ) ) )
                .sign( algorithm );
    }

    /**
     * Authenticates using a self-hosted JWT token. Returns a Spring Security Authentication
     * with the JWT token as credentials.
     */
    public static Authentication authenticate() {
        String token = createTestToken( "test-user" );
        Jwt jwt = jwtDecoder.decode( token );
        return new JwtAuthenticationToken( jwt );
    }

    @Test
    public void testSelfHostedJwtCreationAndValidation() {
        String token = createTestToken( "test-user" );
        Assert.assertNotNull( token );

        JWTVerifier verifier = JWT.require( algorithm )
                .withIssuer( issuer )
                .withAudience( audience )
                .build();
        var decoded = verifier.verify( token );
        Assert.assertEquals( "test-user", decoded.getSubject() );
        Assert.assertEquals( issuer, decoded.getIssuer() );
        Assert.assertTrue( decoded.getAudience().contains( audience ) );
    }

    @Test
    public void testSpringSecurityJwtDecoder() {
        String token = createTestToken( "spring-test-user" );
        Jwt jwt = jwtDecoder.decode( token );
        Assert.assertEquals( "spring-test-user", jwt.getSubject() );
        Assert.assertEquals( issuer, jwt.getClaimAsString( "iss" ) );
    }
}
