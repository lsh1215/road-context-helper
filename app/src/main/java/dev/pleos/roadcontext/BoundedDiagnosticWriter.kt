package dev.pleos.roadcontext

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Best-effort diagnostic output, never part of the navigation success path.
 * All filesystem access and failure callbacks run on one daemon worker, not the submitter.
 * At most [queueCapacity] records wait; oversized records and overflow are dropped.
 * close() rejects new work but drains accepted work asynchronously. Create a new instance to reopen.
 */
class BoundedDiagnosticWriter(
    directory: File,
    fileName: String = "diagnostics.jsonl",
    private val maxBytes: Long = 2L * 1024 * 1024,
    queueCapacity: Int = 128,
    private val onFailure: (Throwable) -> Unit = {},
    private val outputStreamFactory: (File, Boolean) -> OutputStream = { file, append -> FileOutputStream(file, append) },
) : AutoCloseable {
    private val current = File(directory, fileName)
    private val previous = File(directory, "$fileName.old")
    private val maxRecordBytes = minOf(maxBytes, 64L * 1024).toInt()
    private val submissionLock = Any()
    private val completionLock = ReentrantLock()
    private val completedCondition = completionLock.newCondition()
    private val accepted = AtomicLong()
    private val completed = AtomicLong()
    private val dropped = AtomicLong()
    private val failures = AtomicLong()
    private val pendingFailure = AtomicReference<Throwable?>()
    @Volatile private var closed = false
    private val executor: ThreadPoolExecutor

    init {
        require(maxBytes > 0 && queueCapacity > 0)
        require(fileName.isNotBlank() && File(fileName).name == fileName && fileName != "." && fileName != "..")
        executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
            { runnable -> Thread(runnable, "road-diagnostic-writer").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy())
    }

    val droppedRecords: Long get() = dropped.get()
    val failureCount: Long get() = failures.get()

    /** True means queued, not persisted. Rejection never throws into the caller. */
    fun append(line: String): Boolean = synchronized(submissionLock) {
        if (closed) { dropped.incrementAndGet(); return@synchronized false }
        // Bound retained strings before encoding. UTF-8 byte validation is repeated on the worker.
        if (line.length >= maxRecordBytes) {
            dropped.incrementAndGet()
            queueFailure(IOException("Diagnostic record exceeds size budget"))
            scheduleFailureDelivery()
            return@synchronized false
        }
        accepted.incrementAndGet()
        try {
            executor.execute {
                try {
                    deliverPendingFailure()
                    writeRecord(line)
                } catch (failure: Exception) {
                    dropped.incrementAndGet()
                    queueFailure(failure)
                } finally {
                    deliverPendingFailure()
                    completed.incrementAndGet()
                    completionLock.withLock { completedCondition.signalAll() }
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            accepted.decrementAndGet()
            dropped.incrementAndGet()
            queueFailure(IOException("Diagnostic queue is full"))
            // Ensure delivery even if the previously full queue drains during this catch block.
            // A still-full queue already has a worker that will observe pendingFailure.
            scheduleFailureDelivery()
            false
        }
    }

    private fun writeRecord(line: String) {
        val bytes = (line.trimEnd('\r', '\n') + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > maxRecordBytes || bytes.size > maxBytes) throw IOException("Diagnostic record exceeds UTF-8 size budget")
        val directory = current.parentFile ?: throw IOException("Missing diagnostic directory")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create diagnostic directory")
        if (current.exists() && current.length() + bytes.size > maxBytes) {
            if (previous.exists() && !previous.delete()) throw IOException("Cannot replace old diagnostic file")
            if (!current.renameTo(previous)) throw IOException("Cannot rotate diagnostic file")
        }
        outputStreamFactory(current, true).use { stream -> stream.write(bytes) }
    }

    private fun queueFailure(error: Throwable) {
        failures.incrementAndGet()
        pendingFailure.set(error) // coalesce bursts; failureCount retains their count
    }

    private fun deliverPendingFailure() {
        val failure = pendingFailure.getAndSet(null) ?: return
        try { onFailure(failure) } catch (_: Exception) { /* Diagnostic reporting must not kill the worker. */ }
    }

    private fun scheduleFailureDelivery() {
        accepted.incrementAndGet()
        try {
            executor.execute {
                try { deliverPendingFailure() }
                finally {
                    completed.incrementAndGet()
                    completionLock.withLock { completedCondition.signalAll() }
                }
            }
        } catch (_: RejectedExecutionException) {
            accepted.decrementAndGet() // a full queue already has a worker that will report the failure
        }
    }

    /** Never waits for disk or for the worker. Existing accepted writes are allowed to finish. */
    override fun close() = synchronized(submissionLock) {
        if (!closed) { closed = true; executor.shutdown() }
    }

    /** Test/diagnostic synchronization only; do not call from a UI/lifecycle callback. */
    fun awaitIdle(timeoutMillis: Long = 5_000): Boolean {
        require(timeoutMillis >= 0)
        val target = synchronized(submissionLock) { accepted.get() }
        var remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        completionLock.withLock {
            while (completed.get() < target) {
                if (remaining <= 0) return false
                remaining = completedCondition.awaitNanos(remaining)
            }
        }
        return true
    }
}
