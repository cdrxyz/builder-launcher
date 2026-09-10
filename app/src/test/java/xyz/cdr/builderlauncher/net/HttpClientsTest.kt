package xyz.cdr.builderlauncher.net

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class HttpClientsTest {
    @Test
    fun derivedSharesPoolAndDispatcher() {
        val base = HttpClients.shared
        val slow = HttpClients.derived(readSec = 30)
        assertSame(base.connectionPool, slow.connectionPool)
        assertSame(base.dispatcher, slow.dispatcher)
        assertNotSame(base, slow)
    }
}
