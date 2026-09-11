package br.app.astrum.ts6.protocol

object ChannelTree {
    fun flatten(channels: List<Ts3Channel>): List<ChannelRow> {
        if (channels.isEmpty()) return emptyList()

        val byParent = channels.groupBy(Ts3Channel::parentId)
        val result = mutableListOf<ChannelRow>()
        val visited = mutableSetOf<Int>()

        fun append(parentId: Int, depth: Int) {
            orderedSiblings(byParent[parentId].orEmpty()).forEach { channel ->
                if (!visited.add(channel.id)) return@forEach
                result += ChannelRow(channel, depth)
                append(channel.id, depth + 1)
            }
        }

        append(parentId = 0, depth = 0)

        // Malformed or cyclic trees should remain visible instead of disappearing.
        channels.sortedBy(Ts3Channel::id).forEach { channel ->
            if (visited.add(channel.id)) {
                result += ChannelRow(channel, 0)
                append(channel.id, 1)
            }
        }

        return result
    }

    private fun orderedSiblings(channels: List<Ts3Channel>): List<Ts3Channel> {
        if (channels.size < 2) return channels

        val byPrevious = channels.groupBy(Ts3Channel::orderAfterId)
        val ordered = mutableListOf<Ts3Channel>()
        val visited = mutableSetOf<Int>()
        var previousId = 0

        while (true) {
            val next = byPrevious[previousId]
                .orEmpty()
                .filterNot { it.id in visited }
                .minByOrNull(Ts3Channel::id)
                ?: break
            visited += next.id
            ordered += next
            previousId = next.id
        }

        ordered += channels.filterNot { it.id in visited }.sortedBy(Ts3Channel::id)
        return ordered
    }
}

