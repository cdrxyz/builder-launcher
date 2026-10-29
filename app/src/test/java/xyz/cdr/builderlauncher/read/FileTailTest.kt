package xyz.cdr.builderlauncher.read

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTailTest {
    @Test
    fun aGrowingFileIsNotFinishedAtTheFirstEnd() {
        val file = File.createTempFile("article-speech", ".mp3")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val writing = AtomicBoolean(true)
        val tail = FileTail(file, writing::get)
        Thread {
            Thread.sleep(40)
            file.appendBytes(byteArrayOf(4, 5))
            writing.set(false)
        }.start()
        val buf = ByteArray(8)
        val first = tail.read(buf, 0, 8, 20)
        assertEquals(3, first)
        val second = tail.read(buf, 0, 8, 20)
        assertEquals(2, second)
        assertEquals(4, buf[0].toInt())
        assertEquals(-1, tail.read(buf, 0, 8, 20))
        tail.close()
        file.delete()
    }
}
