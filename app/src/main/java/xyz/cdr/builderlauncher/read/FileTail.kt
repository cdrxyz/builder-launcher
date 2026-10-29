package xyz.cdr.builderlauncher.read

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads a file that another thread is still appending. A normal stream treats the
 * current end as finished, so a player opened on the first bytes never hears the rest.
 */
class FileTail(file: File, private val stillWriting: () -> Boolean) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")

    fun seek(position: Long) {
        raf.seek(position.coerceAtLeast(0L))
    }

    fun read(buffer: ByteArray, offset: Int, length: Int, waitMs: Long = 30): Int {
        while (true) {
            val n = raf.read(buffer, offset, length)
            if (n > 0) return n
            if (!stillWriting()) return -1
            Thread.sleep(waitMs)
        }
    }

    override fun close() {
        raf.close()
    }
}
