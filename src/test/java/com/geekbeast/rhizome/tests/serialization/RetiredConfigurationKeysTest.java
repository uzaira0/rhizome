package com.geekbeast.rhizome.tests.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.geekbeast.ResourceConfigurationLoader;
import com.geekbeast.authentication.Auth0Configuration;
import com.geekbeast.mappers.mappers.ObjectMappers;
import com.geekbeast.rhizome.configuration.RhizomeConfiguration;
import com.geekbeast.rhizome.configuration.graphite.GraphiteConfiguration;
import com.geekbeast.rhizome.configuration.hazelcast.HazelcastConfiguration;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class RetiredConfigurationKeysTest {
    private static final String LEGACY_CONFIGURATIONS = """
            rhizome.yaml:
              name: compatibility-test
              enable-persistence: false
              session-clustering-enabled: true
              cors-access-control-allow-origin-url: https://example.org
              spark:
                port: 7077
                master: [127.0.0.1]
                appname: old-spark
                jars: [old.jar]
                local: true
                workingDir: /tmp/spark
                provider: old-provider
                region: old-region
              hazelcast-session-filter:
                client-config-location: old-client.xml
                use-client: true
                map-name: sessions
                sticky-session: false
                cookie-name: old-session
                cookie-secure: true
                cookie-http-only: true
                debug: false
                instance-name: old-instance
                shutdown-on-destroy: true
              graphite:
                global-prefix: compatibility
                host: localhost
                port: 2003
                console-enabled: true
              hazelcast:
                group: compatibility-member
                password: {discard: [all, values]}
                replication-factor: 2
              hazelcast-clients:
                IDS:
                  group: compatibility-client
                  server: false
                  password: old-client-password
                  replication-factor: 3
            auth0.yaml:
              configurations: []
              users: [{email: ignored@example.org}]
            mail.yaml:
              smtp-auth: true
              ttls-enable: true
              smtp-host: localhost
              smtp-port: 25
              username: retired-user
              password: retired-mail-password
              smtpHost: localhost
              smtpPort: 25
              defaultFromEmail: ignored@example.org
              domainBlacklist: [example.org]
              enabled: true
              useSsl: false
            """;
    private static final Map<Class<?>, String> CURRENT_CONFIGURATIONS = Map.of(
            RhizomeConfiguration.class, """
                    name: compatibility-test
                    enable-persistence: false
                    graphite:
                      global-prefix: compatibility
                    hazelcast:
                      group: compatibility-member
                    hazelcast-clients:
                      IDS:
                        group: compatibility-client
                        server: false
                    """,
            Auth0Configuration.class, "configurations: []" );

    @Rule
    public TemporaryFolder configurations = new TemporaryFolder();

    @Test
    public void retiredKeysAreAcceptedAndDiscarded() throws Exception {
        ObjectMapper yaml = ResourceConfigurationLoader.getYamlMapper();
        JsonNode legacy = yaml.readTree( LEGACY_CONFIGURATIONS );
        var files = legacy.fields();
        while ( files.hasNext() ) {
            var entry = files.next();
            yaml.writeValue( new File( configurations.getRoot(), entry.getKey() ), entry.getValue() );
        }
        for ( var entry : CURRENT_CONFIGURATIONS.entrySet() ) {
            Class<?> type = entry.getKey();
            Object expected = yaml.readValue( entry.getValue(), type );
            Object loaded = ResourceConfigurationLoader.loadConfigurationFromFile(
                    configurations.getRoot().getPath(), type );
            assertNotNull( loaded );
            assertEquals( yaml.valueToTree( expected ), yaml.valueToTree( loaded ) );
            JsonNode oldConfiguration = legacy.get( ResourceConfigurationLoader.getReloadableConfigurationUri( type ) );
            for ( ObjectMapper mapper : mappers() ) {
                Object actual = mapper.readValue( mapper.writeValueAsBytes( oldConfiguration ), type );
                assertEquals( mapper.valueToTree( expected ), mapper.valueToTree( actual ) );
            }
        }
    }

    @Test
    public void unknownKeysStillFail() throws Exception {
        Map<Class<?>, List<String>> invalidConfigurations = Map.of(
                RhizomeConfiguration.class, List.of(
                        "session-clustering-enabld: true", "password: wrong-owner",
                        "hazelcast: {replicaton-factor: 2}",
                        "hazelcast-clients: {IDS: {replicaton-factor: 2}}",
                        "graphite: {console-enabld: true}" ),
                HazelcastConfiguration.class, List.of( "passwrod: typo", "users: wrong-owner" ),
                GraphiteConfiguration.class, List.of( "hosst: typo" ),
                Auth0Configuration.class, List.of( "configurations: []\nuserz: []" ) );
        for ( var entry : invalidConfigurations.entrySet() ) {
            for ( String invalid : entry.getValue() ) {
                JsonNode node = ObjectMappers.getYamlMapper().readTree( invalid );
                for ( ObjectMapper mapper : mappers() ) {
                    assertThrows( UnrecognizedPropertyException.class,
                            () -> mapper.readValue( mapper.writeValueAsBytes( node ), entry.getKey() ) );
                }
            }
        }
    }

    private static List<ObjectMapper> mappers() {
        return List.of( ObjectMappers.getYamlMapper(), ObjectMappers.getJsonMapper(),
                ObjectMappers.getSmileMapper(), ObjectMappers.newJsonMapper() );
    }
}
