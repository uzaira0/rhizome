package com.geekbeast.postgres.streams

import com.geekbeast.streams.StreamUtil
import com.google.common.base.Preconditions.checkState
import com.zaxxer.hikari.HikariDataSource
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import java.util.stream.Stream

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
class BasePostgresIterable<T>(
    private val rsh: Supplier<StatementHolder>,
    private val mapper: (ResultSet) -> T
) : Iterable<T> {

    private val logger = LoggerFactory.getLogger(BasePostgresIterable::class.java)

    override fun iterator(): PostgresIterator<T> {
        try {
            return PostgresIterator(rsh.get(), mapper)
        } catch (e: SQLException) {
            logger.error("Error creating postgres stream iterator.")
            throw IllegalStateException("Unable to instantiate postgres iterator.", e)
        } catch (e: IOException) {
            logger.error("Error creating postgres stream iterator.")
            throw IllegalStateException("Unable to instantiate postgres iterator.", e)
        }
    }

    fun stream(): Stream<T> {
        return StreamUtil.stream(this)
    }
}

open class StatementHolderSupplier @JvmOverloads constructor(
    val hds: HikariDataSource,
    val sql: String,
    val fetchSize: Int = 0,
    val autoCommit: Boolean = (fetchSize == 0),
    private val longRunningQueryLimit: Long = 0,
    private val statementTimeoutMillis: Long = 0
) : Supplier<StatementHolder> {
    init {
        check(fetchSize >= 0) { "Fetch-size must be nonnegative." }
        check(((!autoCommit) && (fetchSize > 0)) || (fetchSize == 0)) {
            "Auto-commit must be false if fetch size > 0."
        }
    }

    protected val logger = LoggerFactory.getLogger(javaClass)!!

    @SuppressFBWarnings(value = ["SECSQLIJDBC"], justification = "Provided by caller.")
    open fun execute(statement: Statement): ResultSet {
        return statement.executeQuery(sql)
    }

    open fun buildStatement(connection: Connection): Statement {
        return connection.createStatement()
    }

    open fun setStatementTimeout(statement: Statement): Statement {
        if(statementTimeoutMillis > 0 ) {
            statement.execute("SET statement_timeout = '${statementTimeoutMillis}ms';")
        }
        return statement
    }

    // reason: boundary catch — any query-execution failure must roll back + close the connection (leak prevention) then rethrow
    @Suppress("TooGenericExceptionCaught")
    override fun get(): StatementHolder {
        val connection = hds.connection //okay to fail + propagate
        var statement: Statement? = null
        var resultSet: ResultSet? = null
        try {
            connection.autoCommit = autoCommit
            statement = buildStatement(connection)
            setStatementTimeout(statement)
            statement.fetchSize = fetchSize
            resultSet = execute(statement)
            return if (longRunningQueryLimit == 0L) {
                StatementHolder(connection, statement, resultSet)
            } else {
                StatementHolder(connection, statement, resultSet, longRunningQueryLimit)
            }
        } catch (failure: Throwable) {
            // Ownership passes to the holder only after all initialization succeeds.
            listOfNotNull(resultSet, statement).forEach { resource ->
                try { resource.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            }
            try { if (!connection.autoCommit) connection.rollback() }
            catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            try { connection.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}

class PreparedStatementHolderSupplier(
    hds: HikariDataSource,
    sql: String,
    fetchSize: Int = 0,
    autoCommit: Boolean = (fetchSize == 0),
    statementTimeoutMillis: Long = 0,
    val bind: (PreparedStatement) -> Unit
) : StatementHolderSupplier(
    hds = hds,
    sql = sql,
    fetchSize = fetchSize,
    autoCommit = autoCommit,
    statementTimeoutMillis = statementTimeoutMillis
) {

    override fun execute(statement: Statement): ResultSet {
        return (statement as PreparedStatement).executeQuery()
    }

    @SuppressFBWarnings(value = ["SECSQLIJDBC"], justification = "Provided by caller.")
    override fun buildStatement(connection: Connection): Statement {
        val ps = connection.prepareStatement(sql)
        try {
            bind(ps)
            return ps
        } catch (failure: Throwable) {
            try { ps.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}

class PostgresIterator<T> @Throws(SQLException::class)
@JvmOverloads constructor(
    private val rsh: StatementHolder,
    private val mapper: (ResultSet) -> T,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : Iterator<T>, AutoCloseable, Closeable {
    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS: Long = 600000
        private val WATCHDOG_EXECUTOR: ScheduledExecutorService = Executors.newScheduledThreadPool(2) { r ->
            Thread(r, "postgres-iterator-watchdog").apply { isDaemon = true }
        }
        private val logger = LoggerFactory.getLogger(PostgresIterator::class.java)
    }

    private val lock = ReentrantLock()
    private val rs: ResultSet = rsh.resultSet
    private var expiration: Long = 0

    private var notExhausted: Boolean = false

    // Null when the result was empty at construction. Every terminal path goes through finish(),
    // which cancels it: a periodic task left behind keeps this iterator (and its holder) reachable
    // from the executor queue forever.
    @Volatile
    private var watchdogFuture: ScheduledFuture<*>? = null

    init {
        notExhausted = try {
            this.rs.next()
        } catch (e: SQLException) {
            rsh.close()
            throw e
        }
        updateExpiration()

        if (notExhausted) {
            watchdogFuture = WATCHDOG_EXECUTOR.scheduleAtFixedRate(
                { runWatchdogCheck() }, timeoutMillis, timeoutMillis, TimeUnit.MILLISECONDS
            )
        } else {
            rsh.close()
        }
    }

    private fun finish() {
        watchdogFuture?.cancel(false)
        rsh.close()
    }

    // reason: boundary catch — a scheduled watchdog must swallow+log any failure so it never kills the shared executor
    @Suppress("TooGenericExceptionCaught")
    private fun runWatchdogCheck() {
        try {
            if (rsh.isOpen && (System.currentTimeMillis() > expiration || !notExhausted)) {
                logger.warn("PostgresIterator is closing because it has expired, even though it is not exhausted.")
            }
            if (!rsh.isOpen || System.currentTimeMillis() > expiration || !notExhausted) {
                finish()
            }
        } catch (e: Exception) {
            logger.error("Error in watchdog check", e)
        }
    }

    override fun hasNext(): Boolean {
        //We don't lock here, because multiple calls to has next can still cause an exception to be thrown while
        //calling next
        updateExpiration()
        return notExhausted
    }

    private fun updateExpiration() {
        this.expiration = System.currentTimeMillis() + timeoutMillis
    }

    // reason: boundary catch — SQLException maps to NoSuchElementException; any other failure is logged and rethrown as-is
    @Suppress("TooGenericExceptionCaught")
    override fun next(): T {
        updateExpiration()
        val nextElem: T
        try {
            lock.lock()
            checkState(hasNext(), "There are no more items remaining in the stream.")
            nextElem = mapper(rs)
            notExhausted = rs.next()
        } catch (e: SQLException) {
            logger.error("Unable to retrieve next element from result set.", e)
            notExhausted = false
            throw NoSuchElementException("Unable to retrieve next element from result set.")
        } catch (e: Exception) {
            logger.error("An error occurred while trying to retrieve next element from result set.", e)
            notExhausted = false
            throw e
        } finally {
            try {
                if (!notExhausted) {
                    finish()
                }
            } finally {
                lock.unlock()
            }
        }

        return nextElem
    }

    override fun close() {
        finish()
    }
}
