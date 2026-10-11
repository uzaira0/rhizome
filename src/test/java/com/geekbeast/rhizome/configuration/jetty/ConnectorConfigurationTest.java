package com.geekbeast.rhizome.configuration.jetty;

import com.geekbeast.rhizome.core.JettyLoam;
import java.util.Optional;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectorConfigurationTest {
    @Test
    public void zeroPortsStayZeroAndAbsentPortsUseDefaults() {
        ConnectorConfiguration ephemeral = connectorConfiguration( Optional.of( 0 ) );
        assertEquals( 0, ephemeral.getHttpPort() );
        assertEquals( 0, ephemeral.getHttpsPort() );

        ConnectorConfiguration defaults = connectorConfiguration( Optional.empty() );
        assertEquals( ConnectorConfiguration.HTTP_PORT_DEFAULT, defaults.getHttpPort() );
        assertEquals( ConnectorConfiguration.SSL_PORT_DEFAULT, defaults.getHttpsPort() );
    }

    @Test
    public void startPublishesBoundHttpPortsForBothEndpoints() throws Exception {
        ConnectorConfiguration web = connectorConfiguration( Optional.of( 0 ) );
        ConnectorConfiguration service = connectorConfiguration( Optional.of( 0 ) );
        JettyConfiguration configuration = new JettyConfiguration(
                Optional.of( web ), Optional.of( service ), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty() );
        JettyLoam loam = new JettyLoam( configuration );
        // Keep servlet initializers and the Rhizome bootstrap out of this connector test.
        loam.getServer().setHandler( new DefaultHandler() );
        try {
            loam.start();
            ConnectorConfiguration[] endpoints = { web, service };
            for ( int i = 0; i < endpoints.length; i++ ) {
                ServerConnector http = (ServerConnector) loam.getServer().getConnectors()[i];
                assertTrue( endpoints[i].getHttpPort() > 0 );
                assertEquals( http.getLocalPort(), endpoints[i].getHttpPort() );
                assertEquals( 0, endpoints[i].getHttpsPort() );
            }
        } finally {
            loam.getServer().stop();
        }
    }

    private static ConnectorConfiguration connectorConfiguration( Optional<Integer> port ) {
        return new ConnectorConfiguration( port, port, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty() );
    }
}
