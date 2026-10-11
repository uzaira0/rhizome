package com.geekbeast.rhizome.configuration;

import static com.geekbeast.jdbc.DataSourceManager.DEFAULT_DATASOURCE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geekbeast.configuration.postgres.PostgresConfiguration;
import com.google.common.collect.Maps;
import com.geekbeast.rhizome.configuration.configuration.annotation.ReloadableConfiguration;
import com.geekbeast.rhizome.configuration.graphite.GraphiteConfiguration;
import com.geekbeast.rhizome.configuration.hazelcast.HazelcastConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * @author Matthew Tamayo-Rios
 */
@ReloadableConfiguration( uri = "rhizome.yaml" )
public class RhizomeConfiguration implements Configuration {
    protected static final String PERSISTENCE_ENABLED_PROPERTY                    = "enable-persistence";
    protected static final String POSTGRES_CONFIGURATION                          = "postgres";
    protected static final String DATASOURCE_CONFIGURATIONS                       = "datasources";
    protected static final String GRAPHITE_CONFIGURATION_PROPERTY                 = "graphite";
    protected static final String HAZELCAST_CONFIGURATION_PROPERTY                = "hazelcast";
    protected static final String HAZELCAST_CLIENTS_PROPERTY                      = "hazelcast-clients";
    protected static final String NAME_PROPERTY                                   = "name";

    protected static final boolean                                       PERSISTENCE_ENABLED_DEFAULT        = true;
    private static final   long                                          serialVersionUID                   = -8444209890618166001L;
    protected static final ConfigurationKey                              key                                = new SimpleConfigurationKey(
            "rhizome.yaml" );
    protected final        Logger                                        logger                             = LoggerFactory
            .getLogger(
                    getClass() );
    protected final        boolean                                       persistData;
    protected final        Map<String, PostgresConfiguration>            datasourceConfigurations;
    protected final        Optional<GraphiteConfiguration>               graphiteConfiguration;
    protected final        Optional<PostgresConfiguration>               postgresConfiguration;

    protected final Optional<HazelcastConfiguration>              hazelcastConfiguration;
    protected final Optional<Map<String, HazelcastConfiguration>> hazelcastClients;

    protected final String name;

    @JsonCreator
    public RhizomeConfiguration(
            @JsonProperty( PERSISTENCE_ENABLED_PROPERTY ) Optional<Boolean> persistData,
            @JsonProperty( POSTGRES_CONFIGURATION ) Optional<PostgresConfiguration> postgresConfiguration,
            @JsonProperty( DATASOURCE_CONFIGURATIONS )
                    Optional<Map<String, PostgresConfiguration>> datasourceConfigurations,
            @JsonProperty( GRAPHITE_CONFIGURATION_PROPERTY )
                    Optional<GraphiteConfiguration> graphiteConfiguration,
            @JsonProperty( HAZELCAST_CONFIGURATION_PROPERTY )
                    Optional<HazelcastConfiguration> hazelcastConfiguration,
            @JsonProperty( NAME_PROPERTY ) Optional<String> name,
            @JsonProperty( HAZELCAST_CLIENTS_PROPERTY )
                    Optional<Map<String, HazelcastConfiguration>> hazelcastClients ) {
        this.persistData = persistData.orElse( PERSISTENCE_ENABLED_DEFAULT );
        this.postgresConfiguration = postgresConfiguration;
        this.datasourceConfigurations = Maps.newHashMap( datasourceConfigurations.orElse( Maps.newHashMap() ) );
        postgresConfiguration.ifPresent( pc -> this.datasourceConfigurations.putIfAbsent( DEFAULT_DATASOURCE, pc ) );
        this.graphiteConfiguration = graphiteConfiguration;
        this.hazelcastConfiguration = hazelcastConfiguration;
        this.hazelcastClients = hazelcastClients;
        this.name = name.orElse( "rhizome" );
    }

    @JsonProperty( PERSISTENCE_ENABLED_PROPERTY )
    public boolean isPersistenceEnabled() {
        return persistData;
    }

    @JsonProperty( GRAPHITE_CONFIGURATION_PROPERTY )
    public Optional<GraphiteConfiguration> getGraphiteConfiguration() {
        return graphiteConfiguration;
    }

    @JsonProperty( HAZELCAST_CONFIGURATION_PROPERTY )
    public Optional<HazelcastConfiguration> getHazelcastConfiguration() {
        return hazelcastConfiguration;
    }

    @JsonProperty( DATASOURCE_CONFIGURATIONS )
    public Map<String, PostgresConfiguration> getDatasourceConfigurations() {
        return datasourceConfigurations;
    }

    @JsonProperty( POSTGRES_CONFIGURATION )
    public Optional<PostgresConfiguration> getPostgresConfiguration() {
        return postgresConfiguration;
    }

    @JsonProperty( HAZELCAST_CLIENTS_PROPERTY )
    public Optional<Map<String, HazelcastConfiguration>> getHazelcastClients() {
        return hazelcastClients;
    }

    @JsonProperty( NAME_PROPERTY )
    public String getName() {
        return name;
    }

    @Override public String toString() {
        return "RhizomeConfiguration{" +
                "persistData=" + persistData +
                ", graphiteConfiguration=" + graphiteConfiguration +
                ", postgresConfiguration=" + postgresConfiguration +
                ", hazelcastConfiguration=" + hazelcastConfiguration +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    @JsonIgnore
    public ConfigurationKey getKey() {
        return key;
    }

    public static ConfigurationKey key() {
        return key;
    }
}
