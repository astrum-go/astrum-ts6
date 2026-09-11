package br.app.astrum.ts6.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelTreeTest {
    @Test
    fun followsTeamSpeakPreviousSiblingOrderingAndDepth() {
        val channels = listOf(
            channel(id = 12, parent = 0, after = 10),
            channel(id = 21, parent = 10, after = 0),
            channel(id = 10, parent = 0, after = 0),
        )

        val rows = ChannelTree.flatten(channels)

        assertEquals(listOf(10, 21, 12), rows.map { it.channel.id })
        assertEquals(listOf(0, 1, 0), rows.map(ChannelRow::depth))
    }

    @Test
    fun keepsOrphanedChannelsVisible() {
        val rows = ChannelTree.flatten(listOf(channel(id = 7, parent = 99, after = 0)))

        assertEquals(listOf(7), rows.map { it.channel.id })
        assertEquals(0, rows.single().depth)
    }

    private fun channel(id: Int, parent: Int, after: Int) = Ts3Channel(
        id = id,
        parentId = parent,
        orderAfterId = after,
        name = "Channel $id",
        clientCount = 0,
        hasPassword = false,
        isDefault = false,
    )
}

