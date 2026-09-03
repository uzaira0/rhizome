package com.geekbeast.rhizome.core;

import com.geekbeast.rhizome.configuration.jetty.ConnectorConfiguration;
import com.geekbeast.rhizome.configuration.jetty.ContextConfiguration;
import com.geekbeast.rhizome.configuration.jetty.GzipConfiguration;
import com.geekbeast.rhizome.configuration.jetty.HardeningConfiguration;
import com.geekbeast.rhizome.configuration.jetty.JettyConfiguration;
import com.geekbeast.rhizome.configuration.service.ConfigurationService;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import com.google.common.io.Resources;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Handler.Sequence;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;

import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;

import org.eclipse.jetty.ee10.webapp.WebAppContext;

import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.core.io.ClassPathResource;

public class JettyLoam implements Loam {
    private static final String CLASSES = ".*/test-classes/.*,.*/classes/.*";
    private static final Logger logger = LoggerFactory.getLogger(JettyLoam.class);
    protected final JettyConfiguration config;
    private final Server server;

    protected JettyLoam() throws IOException {
        this(ConfigurationService.StaticLoader.loadConfiguration(JettyConfiguration.class));
    }

    public JettyLoam(JettyConfiguration config) throws IOException {
        this.config = config;

        // Apply hardening configuration
        HardeningConfiguration hardening = config.getHardeningConfiguration();
        logger.info( "Applying Jetty security hardening configuration:" );
        logger.info( "  - Idle timeout: {} ms", hardening.getIdleTimeout() );
        logger.info( "  - Request header size limit: {} bytes", hardening.getRequestHeaderSize() );
        logger.info( "  - Response header size limit: {} bytes", hardening.getResponseHeaderSize() );
        logger.info( "  - Max form content size: {} bytes", hardening.getMaxFormContentSize() );
        logger.info( "  - Max form keys: {}", hardening.getMaxFormKeys() );
        logger.info( "  - Thread pool: min={}, max={}", hardening.getMinThreads(), hardening.getMaxThreads() );
        logger.info( "  - Send server version: {}", hardening.isSendServerVersion() );

        WebAppContext context = new WebAppContext();

        // Security hardening: limit max form content size to prevent large payload DoS
        context.setMaxFormContentSize( hardening.getMaxFormContentSize() );
        // Security hardening: limit max form keys to prevent hash collision attacks
        context.setMaxFormKeys( hardening.getMaxFormKeys() );

        // Apply hardening configuration to thread pool
        QueuedThreadPool threadPool = new QueuedThreadPool(
                hardening.getMaxThreads(),
                hardening.getMinThreads(),
                (int) hardening.getIdleTimeout(),
                new BlockingArrayQueue<>( 6000 ) );
        threadPool.setName( "jetty-hardened" );

        server = new Server( threadPool );

        if (config.getContextConfiguration().isPresent()) {
            ContextConfiguration contextConfig = config.getContextConfiguration().get();
            logger.info("Using context configuration resource base: {}", contextConfig.getResourceBase());
            context.setContextPath(contextConfig.getPath());
            var cl = JettyLoam.class.getClassLoader();

            URL rootURL = cl.getResource(contextConfig.getResourceBase());
            if (rootURL!=null) {
                Resource root = ResourceFactory.of(context).newResource(rootURL);
                context.setBaseResource(root);
            } else {
                logger.warn("Could not find resource base: {}, using fallback", contextConfig.getResourceBase());
                context.setBaseResource(ResourceFactory.of(context).newClassPathResource("/"));
            }
            context.setParentLoaderPriority(contextConfig.isParentLoaderPriority());
        } else {
            // Jetty EE10 requires a baseResource to be set
            context.setBaseResource(ResourceFactory.of(context).newClassPathResource("/"));
        }

        // Apply server-level hardening: don't send server version
        // This is also set per-connector in configureEndpoint

        //This container jar pattern picks up both Rhizome and RhizomeSecurity initializers, but does not allow filtering RhizomeSecurity initializer out
        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*");

        if (config.getWebConnectorConfiguration().isPresent()) {
            configureEndpoint(config.getWebConnectorConfiguration().get());
        }
        if (config.getServiceConnectorConfiguration().isPresent()) {
            configureEndpoint(config.getServiceConnectorConfiguration().get());
        }

        Optional<GzipConfiguration> gzipConfig = config.getGzipConfiguration();
        if (gzipConfig.isPresent() && gzipConfig.get().isGzipEnabled()) {
            GzipHandler gzipHandler = new GzipHandler();


            Sequence s = new Sequence();
            s.addHandler(context);
            if(config.isDefaultServletEnabled()) {
                s.addHandler(new DefaultHandler());
            }

            gzipHandler.addIncludedMimeTypes(gzipConfig.get().getGzipContentTypes().toArray(new String[0]));
            gzipHandler.addIncludedMethods(gzipConfig.get().getGzipMethods().toArray(new String[0]));
            gzipHandler.setMinGzipSize(0);
            gzipHandler.setHandler(s);

            server.setHandler(gzipHandler);
        } else {
            server.setHandler(context);
        }

    }

    public JettyLoam(Class<? extends JettyConfiguration> clazz) throws IOException {
        this(ConfigurationService.StaticLoader.loadConfiguration(clazz));
    }

    protected void configureEndpoint( ConnectorConfiguration configuration ) throws IOException {
        // Apply hardening configuration to HTTP settings
        HardeningConfiguration hardening = config.getHardeningConfiguration();

        HttpConfiguration http_config = new HttpConfiguration();

        // Security hardening: limit request/response header sizes to prevent header bomb attacks
        http_config.setRequestHeaderSize( hardening.getRequestHeaderSize() );
        http_config.setResponseHeaderSize( hardening.getResponseHeaderSize() );

        // Security hardening: set output buffer size
        http_config.setOutputBufferSize( hardening.getOutputBufferSize() );

        // Security hardening: don't send server version to prevent information disclosure
        http_config.setSendServerVersion( hardening.isSendServerVersion() );
        http_config.setSendDateHeader( hardening.isSendDateHeader() );

        final var httpConnectionFactory = new HttpConnectionFactory(http_config);

        if (!configuration.requireSSL()) {
            final var http2CServerConnectionFactory = new HTTP2CServerConnectionFactory(http_config);
            ServerConnector http = new ServerConnector(server,
                    httpConnectionFactory,
                    http2CServerConnectionFactory
            );

            http.setPort( configuration.getHttpPort() );

            // Security hardening: set idle timeout on connector to prevent Slow Loris attacks
            http.setIdleTimeout( hardening.getIdleTimeout() );

            server.addConnector( http );
        }

        if ((configuration.requireSSL() || configuration.useSSL())
                && config.getTruststoreConfiguration().isPresent() && config.getKeystoreConfiguration().isPresent()) {
            http_config.setSecureScheme("https");
            http_config.setSecurePort(configuration.getHttpsPort());

            final var contextFactory = new SslContextFactory.Server();
            configureSslStores(contextFactory);
            String certAlias = configuration.getCertificateAlias().orElse("");
            if (StringUtils.isNotBlank(certAlias)) {
                contextFactory.setCertAlias(certAlias);
            }
            contextFactory.setKeyManagerPassword(config.getKeyManagerPassword().get());
            contextFactory.setWantClientAuth(configuration.wantClientAuth());
            // contextFactory.setNeedClientAuth( configuration.needClientAuth() );

            final HttpConfiguration https_config = new HttpConfiguration(http_config);
            final var src = new SecureRequestCustomizer();
            src.setSniHostCheck(false);
            https_config.addCustomizer(src);

            final ServerConnector ssl;
            final SslConnectionFactory connectionFactory;

            if (configuration.isHttp2Enabled()) {
                contextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
                contextFactory.setUseCipherSuitesOrder(true);

                final var http2ServerConnectionFactory = new HTTP2ServerConnectionFactory(https_config);
                final var alpnServerConnectionFactory = new ALPNServerConnectionFactory();

                alpnServerConnectionFactory.setDefaultProtocol(httpConnectionFactory.getProtocol());

                connectionFactory = new SslConnectionFactory(
                        contextFactory,
                        alpnServerConnectionFactory.getProtocol());

                ssl = new ServerConnector(
                        server,
                        connectionFactory,
                        alpnServerConnectionFactory,
                        http2ServerConnectionFactory,
                        httpConnectionFactory);
            } else {
                connectionFactory = new SslConnectionFactory(
                        contextFactory,
                        httpConnectionFactory.getProtocol());

                ssl = new ServerConnector(
                        server,
                        connectionFactory,
                        httpConnectionFactory);
            }

            // Jetty needs this twice, straight for the Jetty samples
            ssl.setPort( configuration.getHttpsPort() );

            // Security hardening: set idle timeout on SSL connector to prevent Slow Loris attacks
            ssl.setIdleTimeout( hardening.getIdleTimeout() );

            server.addConnector( ssl );
        } else if ( configuration.requireSSL()
                && ( !config.getTruststoreConfiguration().isPresent()
                || !config.getKeystoreConfiguration().isPresent() ) ) {
            throw new IllegalStateException(
                    "SSL is required but SSL configuration is incomplete (missing truststore or keystore). "
                    + "Refusing to start without SSL when requireSSL=true." );
        } else if ( configuration.useSSL()
                && ( !config.getTruststoreConfiguration().isPresent()
                || !config.getKeystoreConfiguration().isPresent() ) ) {
            logger.warn( "SSL Configuration is incomplete. SSL is optional (useSSL), falling back to HTTP only." );
        }
    }

    protected void configureSslStores(SslContextFactory contextFactory) throws IOException {
        contextFactory.setTrustStorePath(getFromClasspath(config.getTruststoreConfiguration().get()
                .getStorePath()));
        contextFactory.setTrustStorePassword(config.getTruststoreConfiguration().get().getStorePassword());

        contextFactory
                .setKeyStorePath(getFromClasspath(config.getKeystoreConfiguration().get().getStorePath()));
        contextFactory.setKeyStorePassword(config.getKeystoreConfiguration().get().getStorePassword());
    }

    private String getFromClasspath(String path) throws IOException {
        return new ClassPathResource(path).getURL().toString();
    }

    void initializeSslContextFactory() {
        /* No-Op */
    }

    public Server getServer() {
        return server;
    }

    @Override
    public synchronized void start() throws Exception {
        if (!server.isRunning()) {
            server.start();
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (server.isRunning()) {
            server.stop();
        }
    }

    @Override
    public synchronized void join() throws BeansException, InterruptedException {
        server.join();
    }

    public static void main(String[] args) throws Exception {
        JettyLoam server = new JettyLoam();
        server.start();
        server.join();
    }
}
