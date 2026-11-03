package com.geekbeast.jdbc

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.geekbeast.configuration.postgres.PostgresConfiguration
import com.geekbeast.postgres.PostgresTableDefinition
import com.geekbeast.postgres.PostgresTableManager
import com.geekbeast.postgres.PostgresTables
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.postgresql.Driver
import org.postgresql.PGProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import kotlin.streams.asSequence

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
@Component //Open for mocking
class DataSourceManager(
    private val dataSourceConfigurations: Map<String, PostgresConfiguration>,
    healthCheckRegistry: HealthCheckRegistry,
    metricRegistry: MetricRegistry
) : AutoCloseable {

    companion object {
        const val DEFAULT_DATASOURCE = "default"
    }

    final val dataSources = dataSourceConfigurations.mapValues { (_, postgresConfiguration) ->

        val hc = HikariConfig(postgresConfiguration.hikariConfiguration)

        val detailsRequested = hc.jdbcUrl?.let { Driver.parseURL(it, null) }
            ?.getProperty(PGProperty.LOG_SERVER_ERROR_DETAIL.getName())?.equals("true", ignoreCase = true)
        require(detailsRequested != true) {
            "PostgreSQL server error details must remain disabled"
        }
        hc.addDataSourceProperty(PGProperty.LOG_SERVER_ERROR_DETAIL.getName(), "false")
        hc.healthCheckRegistry = healthCheckRegistry
        hc.metricRegistry = metricRegistry

        return@mapValues HikariDataSource(hc)
    }
    private val tableManagers = dataSources.mapValues { (dataSourceName, dataSource) ->
        val dataSourceConfiguration = dataSourceConfigurations.getValue(dataSourceName)
        PostgresTableManager(
            dataSource,
            dataSourceConfiguration.usingCitus,
            dataSourceConfiguration.initializeIndices,
            dataSourceConfiguration.initializeTables
        )
    }

    override fun close() {
        dataSources.values.forEach { it.close() }
    }

    fun getDefaultDataSource() = dataSources.getValue(DEFAULT_DATASOURCE)
    fun getDataSource(name: String) = dataSources.getValue(name)
    fun getFlavor(name: String) = dataSourceConfigurations.getValue(name).flavor

    fun registerTables(vararg tableDefinitions: PostgresTableDefinition) {
        tableDefinitions.forEach { tableDef ->
            val dataSourceNames = tableDef.dataSourcesNames

            //If not data source is specified use the default data source for registration.
            if (dataSourceNames.isEmpty()) {
                tableManagers.getValue(DEFAULT_DATASOURCE).registerTables(tableDef)
            }

            dataSourceNames.forEach { dsn -> tableManagers.getValue(dsn).registerTables(tableDef) }
        }
    }

    // reason: vararg API — registerTables(vararg ...) requires spreading the flattened table array
    @Suppress("SpreadOperator")
    @Autowired(required = false)
    fun registerTables(tableDefinitions: Collection<PostgresTables>) {
        registerTables(*tableDefinitions.asSequence().flatMap { it.tables().asSequence() }.toList().toTypedArray())
    }

    fun registerTables(datasourceName: String, vararg tableDefinitions: PostgresTableDefinition) {
        val tm = tableManagers.getValue(datasourceName)
        tm.registerTables(*tableDefinitions)
    }

    fun registerTablesWithAllDatasources(vararg tableDefinitions: PostgresTableDefinition) {
        tableManagers.values.forEach { it.registerTables(*tableDefinitions) }
    }

    fun getDefaultTableManager(): PostgresTableManager {
        check(tableManagers.containsKey(DEFAULT_DATASOURCE)) {
            "No default data source has been configured."
        }
        return tableManagers.getValue(DEFAULT_DATASOURCE)
    }
}
