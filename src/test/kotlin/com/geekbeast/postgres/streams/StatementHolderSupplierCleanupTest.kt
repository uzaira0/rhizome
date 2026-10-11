package com.geekbeast.postgres.streams

import com.zaxxer.hikari.HikariDataSource
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

class StatementHolderSupplierCleanupTest {
    @Test
    fun everySetupFailureReturnsItsConnectionAndClosesPartialStatements() {
        for (stage in listOf("autoCommit", "prepare", "bind", "timeout", "fetch", "execute")) {
            val pool = mock(HikariDataSource::class.java)
            val connection = mock(Connection::class.java)
            val statement = mock(PreparedStatement::class.java)
            val failure = SQLException("failure at $stage", "08006")
            `when`(pool.connection).thenReturn(connection)
            `when`(connection.prepareStatement("SELECT ?")).thenReturn(statement)
            `when`(statement.executeQuery()).thenReturn(mock(ResultSet::class.java))
            when (stage) {
                "autoCommit" -> doThrow(failure).`when`(connection).autoCommit = false
                "prepare" -> `when`(connection.prepareStatement("SELECT ?")).thenThrow(failure)
                "timeout" -> `when`(statement.execute(anyString())).thenThrow(failure)
                "fetch" -> doThrow(failure).`when`(statement).fetchSize = 1
                "execute" -> `when`(statement.executeQuery()).thenThrow(failure)
            }
            val supplier = PreparedStatementHolderSupplier(pool, "SELECT ?", 1, false, 1) {
                if (stage == "bind") throw failure
                it.setString(1, "bound")
            }
            assertSame(failure, assertThrows(SQLException::class.java) { supplier.get() })
            verify(connection, times(1)).close()
            if (stage != "autoCommit" && stage != "prepare") verify(statement, times(1)).close()
        }
        val pool = mock(HikariDataSource::class.java)
        val connection = mock(Connection::class.java)
        val statement = mock(PreparedStatement::class.java)
        val rows = mock(ResultSet::class.java)
        `when`(pool.connection).thenReturn(connection)
        `when`(connection.prepareStatement("SELECT ?")).thenReturn(statement)
        `when`(statement.executeQuery()).thenReturn(rows)
        val holder = PreparedStatementHolderSupplier(pool, "SELECT ?") { }.get()
        verify(connection, never()).close()
        holder.close()
        verify(rows).close(); verify(statement).close(); verify(connection).close()
    }
}
