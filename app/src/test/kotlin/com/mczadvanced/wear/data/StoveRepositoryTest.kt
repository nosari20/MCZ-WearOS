package com.mczadvanced.wear.data

import com.mczadvanced.wear.protocol.Registers
import com.mczadvanced.wear.protocol.StoveMode
import com.mczadvanced.wear.protocol.StoveState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoveRepositoryTest {
    private class MemoryCache(var value: CachedStove? = null) : StoveCache {
        override suspend fun load() = value
        override suspend fun save(value: CachedStove) {
            this.value = value
        }
    }

    private val fake = FakeTransport(
        mutableMapOf(
            Registers.ROOM_TEMP to 205, Registers.PHASE to Registers.PHASE_OFF, Registers.STATE to 0,
            Registers.SETPOINT to 215, Registers.MODE to 0, Registers.POWER_LEVEL to 3, Registers.FAN to 6,
        ),
    )
    private val cache = MemoryCache()

    private fun TestScope.repo(factory: suspend () -> StoveTransport? = { fake }) =
        StoveRepository(factory, cache, backgroundScope, clock = { testScheduler.currentTime + 1 })

    @Test
    fun `refresh reads all blocks and caches state`() = runTest {
        val repo = repo()
        assertTrue(repo.refresh())
        assertEquals(Registers.POLL_BLOCKS, fake.reads)
        val ui = repo.state.value
        assertEquals(20.5f, ui.stove.roomTemp)
        assertEquals(false, ui.stove.isOn)
        assertEquals(LinkStatus.CONNECTED, ui.link)
        assertEquals(ui.stove, cache.value?.state)
    }

    @Test
    fun `setOn toggles only when state differs`() = runTest {
        val repo = repo()
        assertTrue(repo.setOn(true))
        assertEquals(listOf(Registers.POWER_TOGGLE to 1), fake.writes)
        assertTrue(repo.setOn(true))
        assertEquals(1, fake.writes.size)
        assertEquals(true, repo.state.value.stove.isOn)
    }

    @Test
    fun `setOn re-reads phase instead of trusting cached state`() = runTest {
        val repo = repo()
        fake.registers[Registers.PHASE] = 3
        repo.refresh()
        assertEquals(true, repo.state.value.stove.isOn)
        fake.registers[Registers.PHASE] = Registers.PHASE_OFF  // turned off from the stove panel
        assertTrue(repo.setOn(false))
        assertTrue(fake.writes.isEmpty())
        assertEquals(false, repo.state.value.stove.isOn)
    }

    @Test
    fun `read failure during toggle sends no write`() = runTest {
        val repo = repo()
        fake.failReads = true
        assertFalse(repo.setOn(true))
        assertTrue(fake.writes.isEmpty())
        assertEquals(LinkStatus.UNREACHABLE, repo.state.value.link)
    }

    @Test
    fun `connect failure keeps cached state and reports unreachable`() = runTest {
        cache.value = CachedStove(StoveState(roomTemp = 19f), 42)
        val repo = repo()
        repo.loadCache()
        fake.failConnect = true
        assertFalse(repo.refresh())
        val ui = repo.state.value
        assertEquals(19f, ui.stove.roomTemp)
        assertEquals(42L, ui.updatedAt)
        assertEquals(LinkStatus.UNREACHABLE, ui.link)
        assertEquals("Stove unreachable", ui.error)
        assertFalse(ui.busy)
    }

    @Test
    fun `no paired stove is reported as error`() = runTest {
        val repo = repo(factory = { null })
        assertFalse(repo.refresh())
        assertEquals("No stove paired", repo.state.value.error)
    }

    @Test
    fun `setpoint is rounded to half degree and written x10`() = runTest {
        val repo = repo()
        assertTrue(repo.setSetpoint(21.3f))
        assertEquals(listOf(Registers.SETPOINT to 215), fake.writes)
        assertEquals(21.5f, repo.state.value.stove.setpoint)
    }

    @Test
    fun `mode power and fan writes`() = runTest {
        val repo = repo()
        assertTrue(repo.setMode(StoveMode.TURBO))
        assertTrue(repo.setPowerLevel(5))
        assertTrue(repo.setFan(Registers.FAN_AUTO))
        assertEquals(
            listOf(Registers.MODE to 4, Registers.POWER_LEVEL to 5, Registers.FAN to 6),
            fake.writes,
        )
        assertEquals(4, repo.state.value.stove.mode)
    }

    @Test
    fun `invalid values are rejected without writing`() = runTest {
        val repo = repo()
        assertFalse(repo.setSetpoint(50f))
        assertFalse(repo.setSetpoint(4.5f))
        assertFalse(repo.setPowerLevel(0))
        assertFalse(repo.setFan(7))
        assertTrue(fake.writes.isEmpty())
        assertEquals(0, fake.connects)
    }

    @Test
    fun `link closes after idle timeout and reconnects on demand`() = runTest {
        val repo = repo()
        repo.refresh()
        advanceTimeBy(29_000)
        assertEquals(0, fake.closes)
        advanceTimeBy(2_000)
        assertEquals(1, fake.closes)
        assertEquals(LinkStatus.IDLE, repo.state.value.link)
        repo.refresh()
        assertEquals(2, fake.connects)
    }

    @Test
    fun `broadcasts update state`() = runTest {
        val repo = repo()
        repo.refresh()
        runCurrent()  // let the broadcast collector subscribe
        fake.broadcasts.emit(mapOf(Registers.ROOM_TEMP to 230))
        runCurrent()
        assertEquals(23.0f, repo.state.value.stove.roomTemp)
    }

    @Test
    fun `no toggle while the stove is turning off`() = runTest {
        val repo = repo()
        fake.registers[Registers.PHASE] = 3
        fake.registers[Registers.STATE] = StoveState.TURNING_OFF
        assertTrue(repo.setOn(false))  // already going off: nothing to do
        assertFalse(repo.setOn(true))  // re-igniting mid shutdown is refused
        assertTrue(fake.writes.isEmpty())
        assertEquals("Stove is turning off", repo.state.value.error)
        assertEquals(LinkStatus.CONNECTED, repo.state.value.link)
    }

    @Test
    fun `unknown phase is refused`() = runTest {
        val repo = repo()
        fake.registers[Registers.PHASE] = 2
        assertFalse(repo.setOn(true))
        assertFalse(repo.setOn(false))
        assertTrue(fake.writes.isEmpty())
    }

    @Test
    fun `second toggle within cooldown is refused`() = runTest {
        val repo = repo()
        assertTrue(repo.setOn(true))
        fake.registers[Registers.PHASE] = Registers.PHASE_OFF  // stove still reports off (lag)
        advanceTimeBy(10_000)
        assertFalse(repo.setOn(true))
        assertEquals(1, fake.writes.size)
        advanceTimeBy(60_000)
        assertTrue(repo.setOn(true))
        assertEquals(2, fake.writes.size)
    }

    @Test
    fun `cancelled connect closes the transport`() = runTest {
        val repo = repo()
        fake.hangConnect = true
        val job = launch { repo.refresh() }
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals(1, fake.closes)
        fake.hangConnect = false
        assertTrue(repo.refresh())
    }

    @Test
    fun `dropped link is reopened before the next command`() = runTest {
        val repo = repo()
        repo.refresh()
        fake.connected = false  // stove went out of range while idle
        assertTrue(repo.setMode(StoveMode.AUTO))
        assertEquals(2, fake.connects)
        assertEquals(listOf(Registers.MODE to 1), fake.writes)
    }

    @Test
    fun `unexpected exception is reported as unreachable`() = runTest {
        val repo = repo(factory = { throw IllegalStateException("datastore broken") })
        assertFalse(repo.refresh())
        val ui = repo.state.value
        assertEquals(LinkStatus.UNREACHABLE, ui.link)
        assertFalse(ui.busy)
    }

    @Test
    fun `refreshIfStale skips fresh data`() = runTest {
        val repo = repo()
        repo.refresh()
        advanceTimeBy(5_000)
        repo.refreshIfStale(10_000)
        assertEquals(3, fake.reads.size)
        advanceTimeBy(10_000)
        repo.refreshIfStale(10_000)
        assertEquals(6, fake.reads.size)
    }

    @Test
    fun `reset closes link and clears state`() = runTest {
        val repo = repo()
        repo.refresh()
        repo.reset()
        assertEquals(1, fake.closes)
        assertNull(repo.state.value.updatedAt)
        assertEquals(StoveState(), repo.state.value.stove)
    }
}
