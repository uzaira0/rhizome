package com.geekbeast.rhizome.configuration.graphite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * @author Matthew Tamayo-Rios &lt;matthew@kryptnostic.com&gt; 
 *
 */
public class GraphiteConfiguration {
    protected static final String GRAPHITE_GLOBAL_PREFIX_PROPERTY   = "global-prefix";

    protected static final String GRAPHITE_GLOBAL_PREFIX_DEFAULT    = "rhizome";

    protected final String        graphiteGlobalPrefix;

    @JsonCreator
    public GraphiteConfiguration(
            @JsonProperty( GRAPHITE_GLOBAL_PREFIX_PROPERTY ) Optional<String> graphiteGlobalPrefix ) {
        this.graphiteGlobalPrefix = graphiteGlobalPrefix.orElse( GRAPHITE_GLOBAL_PREFIX_DEFAULT );
    }

    @JsonProperty( GRAPHITE_GLOBAL_PREFIX_PROPERTY )
    public String getGraphiteGlobalPrefix() {
        return graphiteGlobalPrefix;
    }
    
}
