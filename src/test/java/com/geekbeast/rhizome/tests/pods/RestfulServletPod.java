package com.geekbeast.rhizome.tests.pods;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import com.geekbeast.mappers.mappers.ObjectMappers;

@Configuration
@ComponentScan(
    basePackages = "com.geekbeast.rhizome.tests.controllers",
    includeFilters = @ComponentScan.Filter(
        value = { org.springframework.stereotype.Controller.class, },
        type = FilterType.ANNOTATION ) )
public class RestfulServletPod extends WebMvcConfigurationSupport {
    @Override
    // Jackson 2 converters deprecated in Spring 7; Jackson 3 migration tracked separately
    @SuppressWarnings("removal")
    protected void configureMessageConverters( HttpMessageConverters.ServerBuilder builder ) {
        builder.configureMessageConvertersList( converters -> {
            converters.add( 0, new ByteArrayHttpMessageConverter() );
            for ( var converter : converters ) {
                if ( converter instanceof MappingJackson2HttpMessageConverter ) {
                    ( (MappingJackson2HttpMessageConverter) converter )
                            .setObjectMapper( ObjectMappers.getJsonMapper() );
                }
            }
        } );
    }

}
