package dev.pleos.roadcontext

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedDiagnosticWriterTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun createsDirectoryAndWritesOnWorkerOnly() {
        val directory = File(folder.root, "new/subdirectory")
        val caller = Thread.currentThread()
        val writerThread = AtomicReference<Thread>()
        val writer = BoundedDiagnosticWriter(directory, outputStreamFactory = { file, append ->
            writerThread.set(Thread.currentThread()); FileOutputStream(file, append)
        })
        assertFalse(directory.exists())
        assertTrue(writer.append("{\"a\":1}"))
        assertTrue(writer.awaitIdle())
        assertNotSame(caller, writerThread.get())
        assertEquals("{\"a\":1}\n", File(directory, "diagnostics.jsonl").readText())
        writer.close()
    }

    @Test fun ioFailureIsIsolatedAndFollowingWriteSurvives() {
        val attempts = AtomicInteger()
        val failures = AtomicInteger()
        val callbackThread = AtomicReference<Thread>()
        val writer = BoundedDiagnosticWriter(folder.root, onFailure = {
            callbackThread.set(Thread.currentThread()); failures.incrementAndGet()
        }, outputStreamFactory = { file, append ->
            if (attempts.incrementAndGet() == 1) failingStream() else FileOutputStream(file, append)
        })
        assertTrue(writer.append("first"))
        assertTrue(writer.append("second"))
        assertTrue(writer.awaitIdle())
        assertEquals(1, failures.get())
        assertNotSame(Thread.currentThread(), callbackThread.get())
        assertEquals(1L, writer.droppedRecords)
        assertEquals("second\n", File(folder.root, "diagnostics.jsonl").readText())
        writer.close()
    }

    @Test fun failureCallbackExceptionDoesNotKillWorker() {
        val attempts = AtomicInteger()
        val writer = BoundedDiagnosticWriter(folder.root,
            onFailure = { throw IllegalStateException("callback failure") },
            outputStreamFactory = { file, append ->
                if (attempts.incrementAndGet() == 1) failingStream() else FileOutputStream(file, append)
            })
        writer.append("first")
        writer.append("survived")
        assertTrue(writer.awaitIdle())
        assertEquals("survived\n", File(folder.root, "diagnostics.jsonl").readText())
        writer.close()
    }

    @Test(timeout = 5000) fun overflowDropsWithoutBlockingAndReportsOnWorker() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failures = AtomicInteger()
        val writer = BoundedDiagnosticWriter(folder.root, queueCapacity = 1,
            onFailure = { failures.incrementAndGet() }, outputStreamFactory = { file, append ->
                entered.countDown()
                assertTrue(release.await(3, TimeUnit.SECONDS))
                FileOutputStream(file, append)
            })
        try {
            assertTrue(writer.append("one"))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertTrue(writer.append("two"))
            assertFalse(writer.append("three"))
            assertEquals(1L, writer.droppedRecords)
            assertFalse(writer.awaitIdle(5))
        } finally { release.countDown() }
        assertTrue(writer.awaitIdle())
        assertEquals("one\ntwo\n", File(folder.root, "diagnostics.jsonl").readText())
        assertEquals(1, failures.get())
        writer.close()
    }

    @Test fun rotationRetainsOnlyCurrentAndOneOldFile() {
        val writer = BoundedDiagnosticWriter(folder.root, maxBytes = 12)
        listOf("record1", "record2", "record3").forEach { assertTrue(writer.append(it)) }
        assertTrue(writer.awaitIdle())
        assertEquals("record3\n", File(folder.root, "diagnostics.jsonl").readText())
        assertEquals("record2\n", File(folder.root, "diagnostics.jsonl.old").readText())
        assertEquals(2, folder.root.listFiles()!!.size)
        writer.close()
    }

    @Test fun oversizedUtf8RecordIsDroppedWithoutBreakingSubsequentWrites() {
        val writer = BoundedDiagnosticWriter(folder.root, maxBytes = 12)
        assertTrue(writer.append("가나다라")) // 4 chars, 13 UTF-8 bytes including newline
        assertTrue(writer.append("ok"))
        assertTrue(writer.awaitIdle())
        assertEquals(1L, writer.droppedRecords)
        assertEquals("ok\n", File(folder.root, "diagnostics.jsonl").readText())
        writer.close()
    }

    @Test(timeout = 5000) fun closeReturnsWithoutWaitingForBlockedIoAndReopenAppends() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = BoundedDiagnosticWriter(folder.root, outputStreamFactory = { file, append ->
            entered.countDown()
            assertTrue(release.await(3, TimeUnit.SECONDS))
            FileOutputStream(file, append)
        })
        try {
            writer.append("before-close")
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            writer.close() // must return before this test releases the IO worker
            assertFalse(writer.append("after-close"))
        } finally { release.countDown() }
        assertTrue(writer.awaitIdle())
        val reopened = BoundedDiagnosticWriter(folder.root)
        assertTrue(reopened.append("reopened"))
        assertTrue(reopened.awaitIdle())
        assertEquals("before-close\nreopened\n", File(folder.root, "diagnostics.jsonl").readText())
        reopened.close()
    }

    private fun failingStream() = object : OutputStream() {
        override fun write(value: Int) { throw IOException("injected disk failure") }
    }
}
