package xyz.cdr.builderlauncher.read

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File

class GrowingFileDataSource(
    private val file: File,
    private val stillWriting: () -> Boolean,
) : BaseDataSource(false) {
    private var tail: FileTail? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val reader = FileTail(file, stillWriting)
        if (dataSpec.position > 0) reader.seek(dataSpec.position)
        tail = reader
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val n = tail?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        if (n < 0) return C.RESULT_END_OF_INPUT
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri = Uri.fromFile(file)

    override fun close() {
        tail?.close()
        tail = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val file: File, private val stillWriting: () -> Boolean) : DataSource.Factory {
        override fun createDataSource(): DataSource = GrowingFileDataSource(file, stillWriting)
    }
}
