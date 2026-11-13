package org.siloserver.silo.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.siloserver.silo.common.R as CommonR
import org.siloserver.silo.common.player.watchparty.reactionDescription
import org.siloserver.silo.common.player.watchparty.reactionLabelRes
import org.siloserver.silo.common.player.watchparty.roomNoticeText
import org.siloserver.silo.model.watchtogether.ReactionPreset
import org.siloserver.silo.watchtogether.RoomNotice

/**
 * The player's reaction disc in a party. Tapping it opens the presets, and
 * "Pause: brb" for a member who may pause while the party plays. A choice
 * sends at once and closes the presets, so the viewer sees their notice.
 */
@Composable
internal fun WatchPartyReactionButton(
    onReact: (ReactionPreset) -> Unit,
    /** Null when this member may not pause, or the party is not playing. */
    onPauseWithBrb: (() -> Unit)?,
    /** The presets opened or closed; the controls stay up while they are open. */
    onOpenChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    DisposableEffect(open) {
        onOpenChange(open)
        onDispose { if (open) onOpenChange(false) }
    }
    val dropPx = with(LocalDensity.current) { (PlayerChrome.TouchSize + 4.dp).roundToPx() }
    Box {
        PlayerIconDisc(
            icon = Icons.Rounded.AddReaction,
            contentDescription = stringResource(CommonR.string.player_watchParty_reactions_label),
            onClick = { open = !open },
        )
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, dropPx),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PlayerChrome.Smoke)
                        .border(1.dp, PlayerChrome.PanelStroke, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(CommonR.string.player_watchParty_reactions_label).uppercase(),
                        style = PlayerType.Section,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReactionPreset.entries.forEach { preset ->
                            val description = reactionDescription(preset)
                            ReactionChip(
                                text = stringResource(reactionLabelRes(preset)),
                                modifier = Modifier.semantics { contentDescription = description },
                                onClick = {
                                    open = false
                                    onReact(preset)
                                },
                            )
                        }
                        onPauseWithBrb?.let { pause ->
                            ReactionChip(
                                text = stringResource(CommonR.string.player_watchParty_reactions_pauseBrb),
                                onClick = {
                                    open = false
                                    pause()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = PlayerType.SmallButton,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerChrome.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * Passing party notices, newest last: "Asha paused", "Asha jumped to 1:02:10",
 * and reactions as the sender's name with the reaction beside it.
 */
@Composable
internal fun WatchPartyRoomNoticeStack(notices: List<RoomNotice>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        notices.forEach { notice ->
            val text = roomNoticeText(notice) ?: return@forEach
            val reaction = notice.reaction?.takeIf { !notice.rateLimited }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, PlayerChrome.DiscStroke, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = text,
                    style = PlayerType.PillLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (reaction != null) {
                    Text(
                        text = stringResource(reactionLabelRes(reaction)),
                        style = PlayerType.PillLabel,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
