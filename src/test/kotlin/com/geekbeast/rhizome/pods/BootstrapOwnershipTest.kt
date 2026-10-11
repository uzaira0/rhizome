package com.geekbeast.rhizome.pods

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.geekbeast.configuration.postgres.PostgresConfiguration
import com.geekbeast.jdbc.DataSourceManager
import com.geekbeast.jdbc.JdbcPod
import com.geekbeast.hazelcast.HazelcastClientProvider
import com.geekbeast.rhizome.configuration.ConfigurationConstants
import com.geekbeast.rhizome.configuration.RhizomeConfiguration
import com.geekbeast.rhizome.configuration.hazelcast.HazelcastConfiguration
import com.geekbeast.rhizome.configuration.jetty.JettyConfiguration
import com.geekbeast.rhizome.core.Rhizome
import com.geekbeast.rhizome.pods.hazelcast.BaseHazelcastInstanceConfigurationPod
import com.hazelcast.client.HazelcastClient
import com.hazelcast.config.MapConfig
import com.hazelcast.config.MapStoreConfig
import com.hazelcast.config.SerializationConfig
import com.hazelcast.core.HazelcastInstance
import com.zaxxer.hikari.HikariDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.support.GenericApplicationContext
import java.util.Optional
import java.util.Properties
import java.util.function.Supplier
import org.mockito.Mockito.mockStatic

class BootstrapOwnershipTest {
    @Test fun ossConfigurationDoesNotRequireOrAdvertiseAPassword() {
        val mapper = com.geekbeast.mappers.mappers.ObjectMappers.newJsonMapper()
        val configuration = mapper.readValue("{}", HazelcastConfiguration::class.java)
        assertEquals("rhizome-dev", configuration.group)
        assertFalse(mapper.writeValueAsString(configuration).contains("password"))
    }

    @Test fun selectedLoaderOwnsBootstrapAndMountedProfileSuppressesLocalDefault() {
        val loader = mock(ConfigurationLoader::class.java)
        val rhizome = mock(RhizomeConfiguration::class.java)
        val jetty = mock(JettyConfiguration::class.java)
        `when`(loader.logAndLoad("Rhizome", RhizomeConfiguration::class.java)).thenReturn(rhizome)
        `when`(loader.logAndLoad("Jetty", JettyConfiguration::class.java)).thenReturn(jetty)
        val pod = LocalConfigurationPod()
        assertSame(rhizome, pod.rhizomeConfiguration(loader))
        assertSame(jetty, pod.jettyConfiguration(loader))
        GenericApplicationContext().use { context ->
            context.environment.addActiveProfile(ConfigurationConstants.Profiles.KUBERNETES_CONFIGURATION_PROFILE)
            Rhizome.shoot(context)
            assertFalse(context.environment.activeProfiles.contains(ConfigurationConstants.Profiles.LOCAL_CONFIGURATION_PROFILE))
        }
    }

    @Test fun legacyDatasourceUsesManagerDefaultAndContextClosesAllPools() {
        val properties = Properties().apply {
            setProperty("jdbcUrl", "jdbc:postgresql://127.0.0.1:1/not_used")
            setProperty("initializationFailTimeout", "-1")
            setProperty("minimumIdle", "0")
            setProperty("maximumPoolSize", "1")
        }
        val configuration = PostgresConfiguration(properties, initializeTables = false, initializeIndices = false)
        val manager = DataSourceManager(mapOf("default" to configuration, "other" to configuration), HealthCheckRegistry(), MetricRegistry())
        val pod = object : JdbcPod() { override fun dataSourceManager() = manager }
        assertSame(manager.getDefaultDataSource(), pod.hikariDataSource())
        GenericApplicationContext().use { context ->
            context.registerBean("manager", DataSourceManager::class.java, Supplier { manager }, { it.destroyMethodName = "close" })
            context.refresh()
        }
        assertTrue(manager.dataSources.values.all(HikariDataSource::isClosed))
    }

    @Test fun providerDestructionShutsDownEachOwnedClient() {
        val configuration = mock(HazelcastConfiguration::class.java)
        `when`(configuration.group).thenReturn("test")
        `when`(configuration.hazelcastSeedNodes).thenReturn(listOf("127.0.0.1"))
        `when`(configuration.port).thenReturn(5701)
        val client = mock(HazelcastInstance::class.java)
        mockStatic(HazelcastClient::class.java).use { factory ->
            factory.`when`<HazelcastInstance> { HazelcastClient.newHazelcastClient(any(
                com.hazelcast.client.config.ClientConfig::class.java)) }.thenReturn(client)
            val provider = HazelcastClientProvider(mapOf("first" to configuration), SerializationConfig())
            GenericApplicationContext().use { context ->
                context.registerBean("provider", HazelcastClientProvider::class.java, Supplier { provider }, { it.destroyMethodName = "close" })
                context.refresh()
            }
            verify(client).shutdown()
        }
    }

    @Test fun finalMapstoresHonorDisabledPersistence() {
        val configuration = mock(RhizomeConfiguration::class.java)
        val hz = mock(HazelcastConfiguration::class.java)
        `when`(configuration.hazelcastConfiguration).thenReturn(Optional.of(hz))
        `when`(hz.isServer).thenReturn(true)
        `when`(hz.instanceName).thenReturn("test")
        `when`(hz.group).thenReturn("test")
        `when`(hz.hazelcastSeedNodes).thenReturn(listOf("127.0.0.1"))
        `when`(hz.scheduledExecutors).thenReturn(Optional.empty())
        `when`(hz.durableExecutors).thenReturn(Optional.empty())
        `when`(hz.cpMemberCount).thenReturn(0)
        val pod = object : BaseHazelcastInstanceConfigurationPod() {
            init { rhizomeConfiguration = configuration }
            override fun mapConfigs() = mapOf("users" to MapConfig("users").setMapStoreConfig(MapStoreConfig().setEnabled(true)))
        }
        assertFalse(pod.hazelcastServerConfiguration.getMapConfig("users").mapStoreConfig.isEnabled)
    }
}
