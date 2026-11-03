package com.geekbeast.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

class HazelcastFixedRateTaskTest {
    @Test
    fun `a failure leaves the fixed rate executor running the next invocation`() {
        val calls = AtomicInteger()
        val failures = AtomicInteger()
        val failure = IllegalStateException("fixture first invocation failed")
        val succeeded = CountDownLatch(1)
        val logger = LogManager.getRootLogger() as Logger
        val appender = object : AbstractAppender("fixed-rate-failure-fixture", null, null, true, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                if (event.loggerName == HazelcastFixedRateTask::class.java.name && event.thrown === failure) {
                    failures.incrementAndGet()
                }
            }
        }
        appender.start()
        logger.addAppender(appender)
        val task = object : HazelcastFixedRateTask<HazelcastTaskDependencies> {
            override fun getName() = "fallback-fixture"
            override fun getInitialDelay() = 0L
            override fun getPeriod() = 1L
            override fun getTimeUnit() = TimeUnit.MILLISECONDS
            override fun getDependenciesClass() = HazelcastTaskDependencies::class.java
            override fun runTask() {
                if (calls.incrementAndGet() == 1) throw failure
                succeeded.countDown()
            }
        }
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            val future = executor.scheduleAtFixedRate(task, 0, 1, TimeUnit.MILLISECONDS)
            assertTrue("The executor must invoke the task after a failure", succeeded.await(5, TimeUnit.SECONDS))
            future.cancel(false)
            assertTrue(calls.get() >= 2)
            assertEquals(1, failures.get())
        } finally {
            logger.removeAppender(appender)
            appender.stop()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(0L, succeeded.count)
        }
    }
}
