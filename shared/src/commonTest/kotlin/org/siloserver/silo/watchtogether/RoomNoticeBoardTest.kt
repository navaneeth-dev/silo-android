package org.siloserver.silo.watchtogether

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.model.watchtogether.MemberIdentity
import org.siloserver.silo.model.watchtogether.Reaction
import org.siloserver.silo.model.watchtogether.ReactionPreset
import org.siloserver.silo.model.watchtogether.RoomMember
import org.siloserver.silo.model.watchtogether.RoomPlaybackState
import org.siloserver.silo.model.watchtogether.TransportAction
import org.siloserver.silo.model.watchtogether.TransportCommand
import org.siloserver.silo.network.RoomRealtimeEvent
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.decodeRoomFrame
import org.siloserver.silo.util.parseRfc3339ToEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoomNoticeBoardTest {
    private val self = RoomMember(userId = "7", profileId = "p-self", displayName = "Me", isSelf = true)
    private val asha = MemberIdentity(userId = "7", profileId = "p-asha", displayName = "Asha")
    private val executeAt = "2026-11-13T10:00:00Z"
    private val executeAtMs = parseRfc3339ToEpochMillis(executeAt)!!

    private fun command(id: String, action: TransportAction, issuedBy: MemberIdentity?, position: Double = 0.0) =
        TransportCommand(
            commandId = id,
            action = action,
            positionSeconds = position,
            executeAt = executeAt,
            playbackState = RoomPlaybackState.Paused,
            issuedBy = issuedBy,
        )

    @Test
    fun `announces another profile's command but not the viewer's own or the room's`() = runTest {
        val board = RoomNoticeBoard(backgroundScope, self = { self })
        // Same account, another profile: a different member.
        board.onEvent(WatchPartyRoomEvent.Transport(command("c1", TransportAction.Seek, asha, 3730.0), executeAtMs))
        board.onEvent(WatchPartyRoomEvent.Transport(command("c2", TransportAction.Pause, MemberIdentity("7", "p-self", "Me")), executeAtMs))
        board.onEvent(WatchPartyRoomEvent.Transport(command("c3", TransportAction.Play, null), executeAtMs))

        val notice = board.notices.value.single()
        assertEquals("c1", notice.key)
        assertEquals(TransportAction.Seek, notice.action)
        assertEquals(3730.0, notice.positionSeconds)
    }

    @Test
    fun `ignores a command delivered long after it ran`() = runTest {
        val board = RoomNoticeBoard(backgroundScope, self = { self })
        board.onEvent(WatchPartyRoomEvent.Transport(command("old", TransportAction.Pause, asha), executeAtMs + 10_001))
        board.onEvent(WatchPartyRoomEvent.Transport(command("new", TransportAction.Pause, asha), executeAtMs + 9_000))
        assertEquals(listOf("new"), board.notices.value.map { it.key })
    }

    @Test
    fun `a pause and its reaction show as one notice in either order`() = runTest {
        val board = RoomNoticeBoard(backgroundScope, self = { self })
        board.onEvent(WatchPartyRoomEvent.ReactionReceived(Reaction("r1", "brb", asha, commandId = "c9")))
        board.onEvent(WatchPartyRoomEvent.Transport(command("c9", TransportAction.Pause, asha), executeAtMs))

        val notice = board.notices.value.single()
        assertEquals(TransportAction.Pause, notice.action)
        assertEquals(ReactionPreset.Brb, notice.reaction)
    }

    @Test
    fun `the viewer's own reaction is shown, unknown presets are not, and notices expire`() = runTest {
        val board = RoomNoticeBoard(backgroundScope, self = { self }, noticeMs = 4_000)
        board.onEvent(WatchPartyRoomEvent.ReactionReceived(Reaction("r1", "laugh", MemberIdentity("7", "p-self", "Me"))))
        board.onEvent(WatchPartyRoomEvent.ReactionReceived(Reaction("r2", "confetti", asha)))
        val own = board.notices.value.single()
        assertTrue(own.fromSelf)
        assertEquals(ReactionPreset.Laugh, own.reaction)

        advanceTimeBy(3_000)
        // The rate-limit notice repeats under one key; a repeat keeps it up.
        board.onEvent(WatchPartyRoomEvent.ReactionRateLimited)
        advanceTimeBy(2_000)
        board.onEvent(WatchPartyRoomEvent.ReactionRateLimited)
        runCurrent()
        assertEquals(listOf("rate-limited"), board.notices.value.map { it.key })
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf("rate-limited"), board.notices.value.map { it.key })
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(board.notices.value.isEmpty())
    }

    @Test
    fun `socket frames carry the issuer and reactions with numeric user ids`() {
        val command = assertIs<RoomRealtimeEvent.TransportCommandEvent>(
            decodeRoomFrame(
                SiloJson,
                """{"type":"transport_command","command":{"command_id":"c1","action":"pause","position_seconds":12,""" +
                    """"execute_at":"$executeAt","issued_at":"$executeAt","playback_state":"paused",""" +
                    """"issued_by":{"user_id":7,"profile_id":"p-asha","display_name":"Asha"}}}""",
            ),
        ).command
        assertEquals(asha, command.issuedBy)

        val reaction = assertIs<RoomRealtimeEvent.ReactionEvent>(
            decodeRoomFrame(
                SiloJson,
                """{"type":"reaction","reaction":{"reaction_id":"r1","preset":"snack_break",""" +
                    """"from":{"user_id":7,"profile_id":"p-asha","display_name":"Asha"},"sent_at":"$executeAt"}}""",
            ),
        ).reaction
        assertEquals(ReactionPreset.SnackBreak, ReactionPreset.fromWire(reaction.preset))
        assertEquals(asha, reaction.from)
        assertEquals(null, reaction.commandId)
    }
}
