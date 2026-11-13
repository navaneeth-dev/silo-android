package org.siloserver.silo.common.player.watchparty

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.siloserver.silo.common.R
import org.siloserver.silo.common.i18n.LocaleFormat
import org.siloserver.silo.common.i18n.stringResourceWithNames
import org.siloserver.silo.model.watchtogether.ReactionPreset
import org.siloserver.silo.model.watchtogether.TransportAction
import org.siloserver.silo.watchtogether.RoomNotice

/** The label of a reaction preset, on its button and on the notice. */
@StringRes
fun reactionLabelRes(preset: ReactionPreset): Int = when (preset) {
    ReactionPreset.Brb -> R.string.player_watchParty_reactions_brb
    ReactionPreset.Back -> R.string.player_watchParty_reactions_back
    ReactionPreset.SnackBreak -> R.string.player_watchParty_reactions_snackBreak
    ReactionPreset.Laugh -> R.string.player_watchParty_reactions_laugh
}

/** The accessible name of a reaction button: the emoji preset says what it is. */
@Composable
fun reactionDescription(preset: ReactionPreset): String = stringResource(
    if (preset == ReactionPreset.Laugh) R.string.player_watchParty_reactions_laughLabel else reactionLabelRes(preset),
)

/**
 * The words of a room notice: "Asha paused", "Asha jumped to 1:02:10", the
 * sender's name ("You" for this viewer) before a reaction, or the rate-limit
 * message. The reaction itself is shown beside it, from [RoomNotice.reaction].
 * Null when there is nothing to say.
 */
@Composable
fun roomNoticeText(notice: RoomNotice): String? {
    if (notice.rateLimited) return stringResource(R.string.player_watchParty_notice_rateLimited)
    val from = notice.from ?: return null
    val name = from.displayName
    return when (notice.action) {
        TransportAction.Pause -> stringResourceWithNames(R.string.player_watchParty_notice_paused, name)
        TransportAction.Play -> stringResourceWithNames(R.string.player_watchParty_notice_resumed, name)
        TransportAction.Seek -> stringResourceWithNames(
            R.string.player_watchParty_notice_jumped,
            name,
            roomNoticeClock(notice.positionSeconds),
        )
        TransportAction.Unknown, null -> when {
            notice.reaction == null -> null
            notice.fromSelf -> stringResource(R.string.player_watchParty_notice_you)
            else -> name
        }
    }
}

/** H:MM:SS, or M:SS under an hour, as the player's timeline shows positions. */
private fun roomNoticeClock(seconds: Double): String {
    val total = if (seconds.isFinite()) seconds.toLong().coerceAtLeast(0L) else 0L
    return LocaleFormat.clock(total / 3600, (total % 3600) / 60, total % 60)
}
