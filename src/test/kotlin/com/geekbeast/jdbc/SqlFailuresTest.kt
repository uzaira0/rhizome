package com.geekbeast.jdbc

import java.sql.SQLException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SqlFailuresTest {
    @Test fun sanitizedFailuresKeepDriverFramesWithoutDriverText() {
        val failure = SQLException("private bound value", "23505")
        val sanitized = SqlFailures.sanitize(failure)
        assertArrayEquals(failure.stackTrace, sanitized.stackTrace)
        assertFalse(sanitized.stackTraceToString().contains(failure.message!!))
        assertNull(sanitized.cause)
    }
}
