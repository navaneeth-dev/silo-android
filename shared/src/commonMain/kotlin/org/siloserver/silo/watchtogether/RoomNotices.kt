package org.siloserver.silo.watchtogether

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.siloserver.silo.model.watchtogether.MemberIdentity
import org.siloserver.silo.model.watchtogether.Reaction
import org.siloserver.silo.model.watchtogether.ReactionPreset
import org.siloserver.silo.model.watchtogether.RoomMember
import org.siloserver.silo.model.watchtogether.TransportAction
import org.siloserver.silo.model.watchtogether.TransportCommand
import org.siloserver.silo.util.parseRfc3339ToEpochMillis

/** Room events the player shows as passing notices, one per message. */
sealed interface WatchPartyRoomEvent {
    /**
     * A transport command that names its issuer. [serverReceivedAtMs] is the
     * server time the frame arrived, from the room clock estimate.
     */
    data class Transport(val command: TransportCommand, val serverReceivedAtMs: Long) : WatchPartyRoomEvent

    data class ReactionReceived(val reaction: Reaction) : WatchPartyRoomEvent

    /** The server refused this member's reaction: `error rate_limited`. */
    data object ReactionRateLimited : WatchPartyRoomEvent
}

/**
 * One passing notice. A pause and the reaction sent with it share the pause's
 * command id as [key], so they show as one notice whichever arrives first.
 */
data class RoomNotice(
    val key: String,
    val from: MemberIdentity? = null,
    /** The member is this viewer: shown as "You". Only reactions are announced back. */
    val fromSelf: Boolean = false,
    val action: TransportAction? = null,
    val positionSeconds: Double = 0.0,
    val reaction: ReactionPreset? = null,
    val rateLimited: Boolean = false,
    /** Bumped on every update, so an expiry only removes what it showed. */
    val version: Long = 0L,
)

/**
 * Turns room events into the notices a player shows: "Asha paused", "Asha
 * jumped to 1:02:10", "Asha · brb". The viewer's own play, pause and seek are
 * not announced back; their own reactions are, as confirmation they went out.
 * A command delivered long after it ran is a resend, not news.
 */
class RoomNoticeBoard(
    private val scope: CoroutineScope,
    private val self: () -> RoomMember?,
    private val noticeMs: Long = ROOM_NOTICE_MS,
) {
    private val _notices = MutableStateFlow<List<RoomNotice>>(emptyList())

    /** Oldest first, at most [MAX_ROOM_NOTICES]. */
    val notices: StateFlow<List<RoomNotice>> = _notices.asStateFlow()

    private var version = 0L

    fun onEvent(event: WatchPartyRoomEvent) {
        when (event) {
            WatchPartyRoomEvent.ReactionRateLimited -> show(RoomNotice(key = RATE_LIMITED_KEY, rateLimited = true))
            is WatchPartyRoomEvent.ReactionReceived -> {
                val reaction = event.reaction
                val preset = ReactionPreset.fromWire(reaction.preset) ?: return
                show(
                    RoomNotice(
                        key = reaction.commandId?.takeIf { it.isNotBlank() } ?: reaction.reactionId,
                        from = reaction.from,
                        fromSelf = reaction.from.isSameMember(self()),
                        reaction = preset,
                    ),
                )
            }
            is WatchPartyRoomEvent.Transport -> {
                val command = event.command
                val issuer = command.issuedBy ?: return
                if (issuer.isSameMember(self())) return
                if (command.action == TransportAction.Unknown) return
                val executeAt = parseRfc3339ToEpochMillis(command.executeAt) ?: return
                if (event.serverReceivedAtMs - executeAt > STALE_COMMAND_MS) return
                show(
                    RoomNotice(
                        key = command.commandId,
                        from = issuer,
                        action = command.action,
                        positionSeconds = command.positionSeconds,
                    ),
                )
            }
        }
    }

    private fun show(update: RoomNotice) {
        val shown = ++version
        _notices.update { current ->
            val existing = current.firstOrNull { it.key == update.key }
            val merged = RoomNotice(
                key = update.key,
                from = update.from ?: existing?.from,
                fromSelf = if (update.from != null) update.fromSelf else existing?.fromSelf == true,
                action = update.action ?: existing?.action,
                positionSeconds = if (update.action != null) update.positionSeconds else existing?.positionSeconds ?: 0.0,
                reaction = update.reaction ?: existing?.reaction,
                rateLimited = update.rateLimited || existing?.rateLimited == true,
                version = shown,
            )
            (current.filter { it.key != update.key } + merged).takeLast(MAX_ROOM_NOTICES)
        }
        scope.launch {
            delay(noticeMs)
            _notices.update { current -> current.filterNot { it.key == update.key && it.version == shown } }
        }
    }

    companion object {
        /** How long a notice stays up. */
        const val ROOM_NOTICE_MS = 4_000L

        /** Older notices give way to newer ones past this many. */
        const val MAX_ROOM_NOTICES = 3

        /** A command delivered this long after it ran is a replay (a resent room command). */
        const val STALE_COMMAND_MS = 10_000L

        private const val RATE_LIMITED_KEY = "rate-limited"
    }
}
