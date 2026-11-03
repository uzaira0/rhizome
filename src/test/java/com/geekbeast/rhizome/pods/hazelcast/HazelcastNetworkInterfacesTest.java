package com.geekbeast.rhizome.pods.hazelcast;

import com.geekbeast.rhizome.configuration.hazelcast.HazelcastConfiguration;
import com.hazelcast.config.InterfacesConfig;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

/**
 * The selfhost backend binds Hazelcast to loopback: rhizome.yaml lists interfaces [127.0.0.1] and the member's
 * network config must carry exactly that list, enabled.
 */
public class HazelcastNetworkInterfacesTest {
    private static HazelcastConfiguration configuration( Optional<List<String>> interfaces ) {
        return new HazelcastConfiguration( Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of( List.of( "127.0.0.1" ) ), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), interfaces );
    }

    @Test
    public void configuredInterfacesAreTheOnlyOnesTheMemberBindsTo() {
        InterfacesConfig interfaces = BaseHazelcastInstanceConfigurationPod
                .networkConfig( configuration( Optional.of( List.of( "127.0.0.1" ) ) ) ).getInterfaces();

        Assert.assertTrue( interfaces.isEnabled() );
        Assert.assertEquals( List.of( "127.0.0.1" ), List.copyOf( interfaces.getInterfaces() ) );
    }

    @Test
    public void withoutConfiguredInterfacesTheRestrictionIsOff() {
        InterfacesConfig interfaces = BaseHazelcastInstanceConfigurationPod
                .networkConfig( configuration( Optional.empty() ) ).getInterfaces();

        Assert.assertFalse( interfaces.isEnabled() );
        Assert.assertTrue( interfaces.getInterfaces().isEmpty() );
    }
}
