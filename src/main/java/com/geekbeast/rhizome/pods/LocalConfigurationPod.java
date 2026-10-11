package com.geekbeast.rhizome.pods;

import com.geekbeast.rhizome.configuration.RhizomeConfiguration;
import com.geekbeast.rhizome.configuration.jetty.JettyConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(ConfigurationLoaderPod.class)
public class LocalConfigurationPod {
    @Bean
    public RhizomeConfiguration rhizomeConfiguration(ConfigurationLoader loader) {
        return loader.logAndLoad("Rhizome", RhizomeConfiguration.class);
    }

    @Bean
    public JettyConfiguration jettyConfiguration(ConfigurationLoader loader) {
        return loader.logAndLoad("Jetty", JettyConfiguration.class);
    }
}
