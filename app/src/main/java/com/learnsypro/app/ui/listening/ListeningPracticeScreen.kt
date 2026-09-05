package com.learnsypro.app.ui.listening

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeningPracticeScreen(
    dark: Boolean,
    onBack: () -> Unit,
    viewModel: ListeningViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val wordBoxDisplay by viewModel.wordBoxDisplay.collectAsState()
    val blanks by viewModel.blanks.collectAsState()
    val stmtSel by viewModel.stmtSel.collectAsState()
    val submitted by viewModel.submitted.collectAsState()
    val showScoreToast by viewModel.showScoreToast.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val isRestarting by viewModel.isRestarting.collectAsState()
    val muted by viewModel.muted.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val estimatedDurationSeconds by viewModel.estimatedDurationSeconds.collectAsState()
    val activeBlankIndex by viewModel.activeBlankIndex.collectAsState()
    val usedWords by viewModel.usedWords.collectAsState()

    val current = selected
    if (current == null) {
        ListeningListScreen(
            items = items,
            loading = loading,
            loadError = loadError,
            isOffline = isOffline,
            downloadedIds = downloadedIds,
            dark = dark,
            onBack = onBack,
            onOpenItem = { viewModel.openItem(it) },
            onDownloadItem = { viewModel.downloadItem(it) },
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() }
        )
    } else {
        val (correct, total) = viewModel.computeScore()
        ListeningDetailScreen(
            item = current,
            wordBoxDisplay = wordBoxDisplay,
            blanks = blanks,
            stmtSel = stmtSel,
            submitted = submitted,
            isPlaying = isPlaying,
            speechRate = speechRate,
            isRestarting = isRestarting,
            showScoreToast = showScoreToast,
            correct = correct,
            total = total,
            dark = dark,
            muted = muted,
            elapsedSeconds = elapsedSeconds,
            estimatedDurationSeconds = estimatedDurationSeconds,
            activeBlankIndex = activeBlankIndex,
            usedWords = usedWords,
            onToggleMuted = { viewModel.toggleMuted() },
            onBackToList = { viewModel.closeItem() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onRateChange = { viewModel.setSpeechRate(it) },
            onRestart = { viewModel.restart() },
            onSeek = { viewModel.seek(it) },
            onReshuffleWordBox = { viewModel.reshuffleWordBox() },
            onBlankChange = { i, v -> viewModel.setBlank(i, v) },
            onBlankFocus = { viewModel.setActiveBlank(it) },
            onWordTap = { viewModel.fillActiveBlankWithWord(it) },
            onStatementChange = { i, v -> viewModel.setStatementAnswer(i, v) },
            onSubmit = { viewModel.submit() },
            onRetry = { viewModel.openItem(current) },
            onDismissScoreToast = { viewModel.dismissScoreToast() }
        )
    }
}
