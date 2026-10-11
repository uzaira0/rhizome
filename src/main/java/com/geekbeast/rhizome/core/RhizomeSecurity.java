package com.geekbeast.rhizome.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.filter.UrlHandlerFilter;

import jakarta.servlet.ServletContext;

/**
 * This class registers the Spring Security Filter chain with all servlet contexts. Do not remove.
 *
 * @author Matthew Tamayo-Rios &lt;matthew@kryptnostic.com&gt;
 */
public class RhizomeSecurity extends AbstractSecurityWebApplicationInitializer implements WebApplicationInitializer {
    private static final Logger logger = LoggerFactory.getLogger( RhizomeSecurity.class );

    protected RhizomeSecurity() {
        super();
        logger.info("Spring security initializer was propertly detected.");
    }

    @Override
    protected void beforeSpringSecurityFilterChain(ServletContext servletContext) {
        // Jetty ServletHandler.prependFilterMapping keeps programmatic prepends in registration order.
        insertFilters(servletContext, UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
    }
}
