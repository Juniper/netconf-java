package net.juniper.netconf;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Reads from a device input stream on a dedicated daemon thread and hands the bytes to the
 * caller through a queue, so that the caller can wait with a timeout without abandoning a
 * blocked {@link InputStream#read} call. A timeout only ends the wait: the reader keeps
 * running and no bytes are lost.
 * <p>
 * Consumers must call the read methods from one thread at a time.
 */
final class DeviceInputReader {

    private static final int READ_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_QUEUED_CHUNKS = 256;

    /** A block of bytes from the device, or the end of the stream (data == null), optionally with a failure. */
    private record Chunk(byte[] data, IOException failure) { }

    private final BlockingQueue<Chunk> queue = new LinkedBlockingQueue<>(MAX_QUEUED_CHUNKS);
    private final Thread thread;

    private byte[] current = new byte[0];
    private int currentOffset;
    private Chunk terminal;

    DeviceInputReader(final InputStream source, final String threadName) {
        thread = new Thread(() -> pump(source), threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void pump(final InputStream source) {
        final byte[] buffer = new byte[READ_BUFFER_SIZE];
        try {
            while (true) {
                final int bytesRead = source.read(buffer);
                if (bytesRead < 0) {
                    queue.put(new Chunk(null, null));
                    return;
                }
                if (bytesRead > 0) {
                    final byte[] copy = new byte[bytesRead];
                    System.arraycopy(buffer, 0, copy, 0, bytesRead);
                    queue.put(new Chunk(copy, null));
                }
            }
        } catch (final IOException e) {
            offerTerminal(new Chunk(null, e));
        } catch (final InterruptedException e) {
            // stop() was called
            Thread.currentThread().interrupt();
        }
    }

    private void offerTerminal(final Chunk chunk) {
        try {
            queue.put(chunk);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reads up to {@code length} bytes.
     *
     * @param deadlineNanos a {@link System#nanoTime()} value
     * @return the number of bytes read, or -1 at end of stream
     * @throws SocketTimeoutException if no data arrived before the deadline
     * @throws IOException            if the underlying stream failed
     */
    int read(final byte[] buffer, final int offset, final int length, final long deadlineNanos) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (atEndOfStream(deadlineNanos)) {
            return -1;
        }
        final int count = Math.min(length, current.length - currentOffset);
        System.arraycopy(current, currentOffset, buffer, offset, count);
        currentOffset += count;
        return count;
    }

    /** Reads one byte, or returns -1 at end of stream. See {@link #read(byte[], int, int, long)}. */
    int read(final long deadlineNanos) throws IOException {
        if (atEndOfStream(deadlineNanos)) {
            return -1;
        }
        return current[currentOffset++] & 0xFF;
    }

    /** Returns true if a read would return immediately, without waiting for the device. */
    boolean hasBufferedData() {
        return currentOffset < current.length || terminal != null || !queue.isEmpty();
    }

    /** Stops the reader thread. A thread blocked inside the source stream ends when that stream is closed. */
    void stop() {
        thread.interrupt();
    }

    /**
     * Waits until there are unread bytes in {@link #current}, or the stream has ended.
     *
     * @return true at end of stream, false if unread bytes are available
     */
    private boolean atEndOfStream(final long deadlineNanos) throws IOException {
        while (currentOffset >= current.length) {
            if (terminal == null) {
                final Chunk chunk = next(deadlineNanos);
                if (chunk.data() != null) {
                    current = chunk.data();
                    currentOffset = 0;
                    continue;
                }
                terminal = chunk;
            }
            if (terminal.failure() != null) {
                throw terminal.failure();
            }
            return true;
        }
        return false;
    }

    private Chunk next(final long deadlineNanos) throws IOException {
        try {
            // Always poll at least once so data that is already queued is returned even past the deadline.
            final Chunk chunk = queue.poll(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (chunk == null) {
                throw new SocketTimeoutException("Timed out waiting for data from device");
            }
            return chunk;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetconfException("Thread interrupted whilst waiting for data from device", e);
        }
    }
}
