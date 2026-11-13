package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.common.i18n.uiText
import org.siloserver.silo.common.i18n.text
import org.siloserver.silo.common.i18n.stringResourceWithNames
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.siloserver.silo.common.player.watchparty.reactionDescription
import org.siloserver.silo.common.player.watchparty.reactionLabelRes
import org.siloserver.silo.common.player.watchparty.roomNoticeText
import org.siloserver.silo.model.watchtogether.ReactionPreset
import org.siloserver.silo.model.watchtogether.RoomPlaybackState
import org.siloserver.silo.watchtogether.RoomNotice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.siloserver.silo.common.R as CommonR
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.components.rememberProfileServerUrl
import org.siloserver.silo.model.watchtogether.MemberRole
import org.siloserver.silo.model.watchtogether.RoomPhase
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.WatchTogetherRepository
import org.siloserver.silo.tv.ui.components.rememberTvDialogInitialFocus
import org.siloserver.silo.tv.ui.focus.TvRestoreFocusOnModalDismiss
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.focus.tvModalFocusBoundary
import org.siloserver.silo.tv.ui.screens.watchparty.PARTY_KEY_OPTIONS
import org.siloserver.silo.tv.ui.screens.watchparty.TvPartyNotice
import org.siloserver.silo.tv.ui.screens.watchparty.TvPartyRoomActions
import org.siloserver.silo.tv.ui.screens.watchparty.TvWatchPartyConfirmDialog
import org.siloserver.silo.tv.ui.screens.watchparty.TvWatchPartyInvitePage
import org.siloserver.silo.tv.ui.screens.watchparty.TvWatchPartyOptionsOverlay
import org.siloserver.silo.tv.ui.screens.watchparty.TvWatchPartyRoomView
import org.siloserver.silo.tv.ui.screens.watchparty.rememberTvPartyFocus
import org.siloserver.silo.tv.ui.screens.watchparty.rememberTvStagedPreview
import org.siloserver.silo.tv.ui.screens.watchparty.rememberTvWatchPartyReconnectNotice
import org.siloserver.silo.tv.ui.screens.watchparty.tvPartyAnchorKey
import org.siloserver.silo.tv.ui.screens.watchparty.tvWatchPartyLeftBehind
import org.siloserver.silo.tv.ui.screens.watchparty.tvWatchPartyNameList
import org.siloserver.silo.tv.ui.screens.watchparty.tvWatchPartyWaitingFor
import org.siloserver.silo.tv.ui.theme.DarkBackground
import org.siloserver.silo.watchtogether.WatchPartyAvailabilityRepository
import org.siloserver.silo.watchtogether.watchPartyEligibility
import org.siloserver.silo.watchtogether.watchPartyErrorText
import org.siloserver.silo.watchtogether.watchPartyInviteUrl
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs

/**
 * The Watch Party layer over the TV player: the non-focusable status overlay
 * and the party panel that Back opens over the retained player.
 *
 * The panel is a focusable popup; the player keeps running underneath and
 * closing the panel never tears it down. Focus returns to the player when it
 * closes. Kept out of [TvPlayerScreen] so that composable stays within ART's
 * JIT method-size limit.
 */
@Composable
internal fun TvWatchPartyPlayerOverlays(
    watchParty: TvWatchPartyScreenController,
    panelOpen: Boolean,
    /** The reaction strip is up; it takes the remote until it closes. */
    reactionStripOpen: Boolean,
    /** The strip closed; [sent] is whether it sent something. */
    onReactionStripClosed: (sent: Boolean) -> Unit,
    /** Whether focus returns to the player when the strip closes, rather than to the transport. */
    reactionStripRestoresPlayerFocus: Boolean,
    isInPictureInPictureMode: Boolean,
    playerFocus: FocusRequester,
    playerFocused: Boolean,
    onPanelOpenChange: (Boolean) -> Unit,
    /** The player's terminal exit; it leaves the party on the way out. */
    exit: () -> Unit,
    repository: WatchTogetherRepository = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room by watchParty.playback.room.collectAsState()
    val latestPlayerFocused by rememberUpdatedState(playerFocused)
    if (!isInPictureInPictureMode && !panelOpen) {
        TvWatchPartyStatusOverlay(watchParty = watchParty, room = room, repository = repository)
    }
    // The party panel (Stop, Back) replaces the strip rather than hiding it.
    LaunchedEffect(panelOpen) {
        if (panelOpen && reactionStripOpen) onReactionStripClosed(true)
    }
    if (!isInPictureInPictureMode && !panelOpen && reactionStripOpen) {
        val canPauseWithBrb = room?.playbackState == RoomPlaybackState.Playing &&
            watchParty.canPlayPause()
        TvWatchPartyReactionStrip(
            canPauseWithBrb = canPauseWithBrb,
            onReact = { preset ->
                onReactionStripClosed(true)
                scope.launch {
                    if (!watchParty.playback.sendReaction(preset)) {
                        Toast.makeText(context, context.getString(CommonR.string.player_watchParty_undelivered), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPauseWithBrb = {
                onReactionStripClosed(true)
                watchParty.playback.requestPlayPause(pause = true, reaction = ReactionPreset.Brb)
            },
            onDismiss = { onReactionStripClosed(false) },
        )
    }
    TvRestoreFocusOnModalDismiss(
        visible = reactionStripOpen && !isInPictureInPictureMode,
        opener = playerFocus.takeIf { reactionStripRestoresPlayerFocus },
        isOpenerFocused = { latestPlayerFocused },
    )
    if (!isInPictureInPictureMode && panelOpen) {
        TvWatchPartyPanel(
            room = room,
            repository = repository,
            onClose = { onPanelOpenChange(false) },
            onReturnToLobby = {
                onPanelOpenChange(false)
                scope.launch {
                    val result = repository.stopPlayback()
                    if (result !is ApiResult.Success) {
                        Toast.makeText(
                            context,
                            watchPartyErrorText(result, uiText(CommonR.string.player_watchParty_stopFailed)).text(context),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onLeave = {
                onPanelOpenChange(false)
                exit()
            },
            onEndForEveryone = {
                onPanelOpenChange(false)
                watchParty.endForEveryone()
                exit()
            },
        )
    }
    TvRestoreFocusOnModalDismiss(
        visible = panelOpen && !isInPictureInPictureMode,
        opener = playerFocus,
        isOpenerFocused = { latestPlayerFocused },
    )
}

/**
 * Status that explains what the party is doing, top-center. Never focusable
 * and never announced on every update; it only reports.
 */
@Composable
private fun TvWatchPartyStatusOverlay(
    watchParty: TvWatchPartyScreenController,
    room: RoomSnapshot?,
    repository: WatchTogetherRepository,
) {
    val catchingUp by watchParty.playback.catchingUp.collectAsState()
    val connection by repository.connectionState.collectAsState()
    val reconnect = rememberTvWatchPartyReconnectNotice(connection = connection, inRoom = room != null)

    // "Continuing without …": members the room stopped waiting for, shown
    // for a few seconds each time the room leaves someone behind.
    var leftBehind by remember { mutableStateOf<List<String>>(emptyList()) }
    var leftBehindNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(watchParty) {
        var previous: RoomSnapshot? = null
        watchParty.playback.room.collect { next ->
            val names = tvWatchPartyLeftBehind(previous, next)
            if (names.isNotEmpty()) {
                leftBehind = names
                leftBehindNonce++
            }
            previous = next
        }
    }
    LaunchedEffect(leftBehindNonce) {
        if (leftBehindNonce == 0) return@LaunchedEffect
        delay(LEFT_BEHIND_NOTICE_MS)
        leftBehind = emptyList()
    }

    val roomNotices by watchParty.playback.roomNotices.collectAsState()

    val playing = room?.phase == RoomPhase.Playing
    val hostAway = playing && room?.selfRole != MemberRole.Host && room?.hostConnected == false
    val waitingFor = if (catchingUp) null else tvWatchPartyWaitingFor(room)
    if (reconnect == null && !hostAway && !catchingUp && waitingFor == null && leftBehind.isEmpty() &&
        roomNotices.isEmpty()
    ) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp)
            .zIndex(6f),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (reconnect != null) TvPartyNotice(title = reconnect)
            if (hostAway) {
                TvPartyNotice(
                    title = stringResource(CommonR.string.player_watchParty_hostDisconnected),
                    detail = stringResource(CommonR.string.player_watchParty_hostAwayNote),
                )
            }
            if (catchingUp) {
                TvPartyNotice(
                    title = stringResource(CommonR.string.player_watchParty_catchingUp),
                    detail = stringResource(CommonR.string.player_watchParty_keptPlaying)
                        .takeIf { room?.selfIgnoreWait == true },
                )
            }
            if (waitingFor != null) {
                TvPartyNotice(
                    title = if (waitingFor.isEmpty()) {
                        stringResource(CommonR.string.player_watchParty_syncing)
                    } else {
                        stringResourceWithNames(CommonR.string.player_watchParty_syncingWaitingFor, tvWatchPartyNameList(waitingFor))
                    },
                )
            }
            if (leftBehind.isNotEmpty()) {
                TvPartyNotice(
                    title = stringResourceWithNames(CommonR.string.player_watchParty_continuingWithout, tvWatchPartyNameList(leftBehind)),
                )
            }
            roomNotices.forEach { notice -> TvRoomNotice(notice) }
        }
    }
}

/**
 * The party room over the retained player, as tvOS does it: Back inside party
 * playback opens the lobby itself, with Return to playback as the primary
 * action. Invitation, playback policy, Return everyone to lobby, End, and
 * Leave all live where they do in the lobby (the code pill and "···").
 */
@Composable
private fun TvWatchPartyPanel(
    room: RoomSnapshot?,
    repository: WatchTogetherRepository,
    onClose: () -> Unit,
    onReturnToLobby: () -> Unit,
    onLeave: () -> Unit,
    onEndForEveryone: () -> Unit,
    availability: WatchPartyAvailabilityRepository = koinInject(),
) {
    val pending by repository.pendingAction.collectAsState()
    val connection by repository.connectionState.collectAsState()
    val suggestions by repository.suggestions.collectAsState()
    val features = availability.features
    val eligibility = watchPartyEligibility(
        room = room,
        features = features,
        busy = pending != null,
        personalVotesKnown = false,
    )
    val isHost = eligibility.isHost
    val serverUrl = rememberProfileServerUrl()
    val inviteUrl = room?.takeIf { isHost }?.let { watchPartyInviteUrl(serverUrl, it.invitePath) }
    val reconnect = rememberTvWatchPartyReconnectNotice(connection = connection, inRoom = room != null)
    val staged = rememberTvStagedPreview(
        contentId = room?.selectedContentId,
        fileId = room?.selectedFileId,
        libraryId = room?.selectedLibraryId,
        suggestions = suggestions,
        refreshKey = room?.phase,
    )
    val focus = rememberTvPartyFocus()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var optionsOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val modalOpen = optionsOpen || inviteOpen || confirmEnd || confirmLeave
    var opener by remember { mutableStateOf<String?>(null) }
    fun openModal(open: () -> Unit) {
        opener = focus.focusedKey
        open()
    }
    val anchor = room?.let { tvPartyAnchorKey(it, features, suggestions) } ?: PARTY_KEY_OPTIONS
    // The focusable popup hands focus to its first control (the code pill)
    // on its own, so put it on Return to playback once the room is laid out.
    LaunchedEffect(anchor, room != null) {
        if (room == null || optionsOpen || inviteOpen || confirmEnd || confirmLeave) return@LaunchedEffect
        requestFocusUntilObserved(
            maxAttempts = PANEL_FOCUS_ATTEMPTS,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = focus.requester(anchor)::requestFocus,
            isFocused = { focus.isFocused(anchor) },
        )
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onClose,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .tvModalFocusBoundary()
                .then(rememberTvDialogInitialFocus(focus.requester(anchor))),
        ) {
            if (room != null) {
                TvWatchPartyRoomView(
                    room = room,
                    suggestions = suggestions,
                    features = features,
                    eligibility = eligibility,
                    personalVotesKnown = false,
                    connection = connection,
                    staged = staged,
                    focus = focus,
                    notice = reconnect,
                    actions = TvPartyRoomActions(
                        onInvite = { openModal { inviteOpen = true } },
                        onOptions = { openModal { optionsOpen = true } },
                        onReturnToPlayback = onClose,
                    ),
                )
            }
        }
    }

    if (optionsOpen && room != null) {
        TvWatchPartyOptionsOverlay(
            room = room,
            features = features,
            eligibility = eligibility,
            onSetMode = {},
            onSetPolicy = { policy ->
                scope.launch {
                    val result = repository.updatePolicy(policy)
                    if (result !is ApiResult.Success) {
                        Toast.makeText(
                            context,
                            watchPartyErrorText(result, uiText(CommonR.string.player_watchParty_policyFailed)).text(context),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onStopPlayback = {
                optionsOpen = false
                onReturnToLobby()
            },
            onEnd = {
                optionsOpen = false
                confirmEnd = true
            },
            onLeave = {
                optionsOpen = false
                if (isHost) confirmLeave = true else onLeave()
            },
            onDismiss = { optionsOpen = false },
        )
    }
    if (inviteOpen && room != null) {
        TvWatchPartyInvitePage(
            code = room.code,
            inviteUrl = inviteUrl,
            backdropUrl = staged?.backdropUrl,
            backdropThumbhash = staged?.backdropThumbhash,
            backdropHidden = staged?.spoilers?.hidesBackdrop(LocalEpisodeSpoilerPrefs.current, staged.backdropUrl) == true,
            onDismiss = { inviteOpen = false },
        )
    }
    if (confirmEnd) {
        TvWatchPartyConfirmDialog(
            title = stringResource(CommonR.string.watchParty_end_confirmTitle),
            message = stringResource(CommonR.string.player_watchParty_endMessage),
            confirmLabel = stringResource(CommonR.string.player_watchParty_endConfirm),
            destructive = true,
            onConfirm = {
                confirmEnd = false
                onEndForEveryone()
            },
            onDismiss = { confirmEnd = false },
        )
    }
    if (confirmLeave) {
        TvWatchPartyConfirmDialog(
            title = stringResource(CommonR.string.player_watchParty_leaveTitle),
            message = stringResource(CommonR.string.player_watchParty_hostLeaveNote),
            confirmLabel = stringResource(CommonR.string.watchParty_options_leave),
            destructive = true,
            onConfirm = {
                confirmLeave = false
                onLeave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
    TvRestoreFocusOnModalDismiss(
        visible = modalOpen,
        opener = opener?.let(focus::requester),
        isOpenerFocused = { focus.isFocused(opener) },
    )
}

/** A member's play, pause or seek, or a reaction with the sender's name and the reaction beside it. */
@Composable
private fun TvRoomNotice(notice: RoomNotice) {
    val text = roomNoticeText(notice) ?: return
    val reaction = notice.reaction?.takeIf { !notice.rateLimited }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = TvPlayerType.ButtonLabel.copy(color = TvPlayerChrome.Paper, fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (reaction != null) {
            Text(
                text = stringResource(reactionLabelRes(reaction)),
                style = TvPlayerType.ButtonLabel.copy(color = TvPlayerChrome.Paper, fontSize = 16.sp),
                maxLines = 1,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The party's quick reactions as one row of buttons above the transport, so
 * a remote reaches every preset with Left/Right and sends it with Select.
 * Focus starts on brb; Back closes the row without sending.
 */
@Composable
private fun TvWatchPartyReactionStrip(
    canPauseWithBrb: Boolean,
    onReact: (ReactionPreset) -> Unit,
    onPauseWithBrb: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        requestFocusUntilObserved(
            maxAttempts = PANEL_FOCUS_ATTEMPTS,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = firstFocus::requestFocus,
            isFocused = { firstFocused },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(7f)
            .background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.7f),
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = TvPlayerChrome.BottomInset + 24.dp),
        ) {
            Text(
                text = stringResource(CommonR.string.player_watchParty_reactions_label).uppercase(),
                style = TvPlayerType.Eyebrow,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.tvModalFocusBoundary(),
            ) {
                ReactionPreset.entries.forEachIndexed { index, preset ->
                    TvReactionButton(
                        text = stringResource(reactionLabelRes(preset)),
                        description = reactionDescription(preset),
                        onClick = { onReact(preset) },
                        modifier = if (index == 0) {
                            Modifier
                                .focusRequester(firstFocus)
                                .onFocusChanged { firstFocused = it.isFocused }
                        } else {
                            Modifier
                        },
                    )
                }
                if (canPauseWithBrb) {
                    TvReactionButton(
                        text = stringResource(CommonR.string.player_watchParty_reactions_pauseBrb),
                        description = stringResource(CommonR.string.player_watchParty_reactions_pauseBrb),
                        onClick = onPauseWithBrb,
                    )
                }
            }
        }
    }
}

/** A pill in the transport's style: dark at rest, Paper with Ink text when focused. */
@Composable
private fun TvReactionButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(TvReactionButtonHeight)
            .widthIn(min = TvReactionButtonHeight * 1.6f)
            .clip(shape)
            .background(if (focused) TvPlayerChrome.Paper else TvPlayerChrome.ButtonRest)
            .then(if (focused) Modifier else Modifier.border(1.dp, TvPlayerChrome.ButtonRing, shape))
            .focusable(interactionSource = interactionSource)
            // Select acts on key up, as on the transport, so the press that
            // opened the row from a transport button never also sends.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 22.dp),
    ) {
        Text(
            text = text,
            style = TvPlayerType.ButtonLabel.copy(
                color = if (focused) TvPlayerChrome.Ink else TvPlayerChrome.Paper,
                fontSize = 18.sp,
            ),
            maxLines = 1,
        )
    }
}

private val TvReactionButtonHeight = 52.dp
private const val LEFT_BEHIND_NOTICE_MS = 6_000L
private const val PANEL_FOCUS_ATTEMPTS = 10
