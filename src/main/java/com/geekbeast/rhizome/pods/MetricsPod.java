package com.geekbeast.rhizome.pods;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.geekbeast.rhizome.configuration.RhizomeConfiguration;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Provides Dropwizard Metrics integration with Spring AOP and Prometheus.
 * Replaces the abandoned ryantenney/metrics-spring library.
 *
 * @author Matthew Tamayo-Rios
 */
@Configuration
@Import( { AsyncPod.class, ConfigurationPod.class } )
public class MetricsPod {

    private static final Logger              logger              = LoggerFactory.getLogger( MetricsPod.class );
    private static final MetricRegistry      metricRegistry      = new MetricRegistry();
    private static final HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();

    @Inject
    private RhizomeConfiguration config;

    @PostConstruct
    public void configureReporters() {
        CollectorRegistry.defaultRegistry.register( new DropwizardExports( metricRegistry ) );
    }

    @Bean
    public HealthCheckRegistry getHealthCheckRegistry() {
        return healthCheckRegistry;
    }

    @Bean
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    @Bean
    public Advisor timedAdvisor() {
        AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut( null, Timed.class, true );
        MethodInterceptor interceptor = ( MethodInvocation invocation ) -> {
            String name = invocation.getMethod().getDeclaringClass().getName()
                    + "." + invocation.getMethod().getName();
            Timer timer = metricRegistry.timer( name );
            Timer.Context ctx = timer.time();
            try {
                return invocation.proceed();
            } finally {
                ctx.stop();
            }
        };
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor( pointcut, interceptor );
        advisor.setOrder( 0 );
        return advisor;
    }

    protected String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch ( UnknownHostException e ) {
            logger.warn( "Unable to determine hostname, default to Hazelcast UUID", e );
            return null;
        }
    }

    protected String getGlobalName() {
        if ( config.getGraphiteConfiguration().isPresent() ) {
            return config.getGraphiteConfiguration().get().getGraphiteGlobalPrefix();
        }
        return "global";
    }
}
