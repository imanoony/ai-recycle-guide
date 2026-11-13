package com.example.airecycleguide.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.airecycleguide.ui.state.AnalysisOutcome

@Composable
fun AnalysisResultScreen(
    outcome: AnalysisOutcome,
    onRetry: () -> Unit,
    onReturnHome: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            when (outcome) {
                is AnalysisOutcome.Success -> {
                    Text(
                        text = "분석 결과를 받았습니다.",
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "분리배출 안내 화면은 다음 작업에서 연결합니다.",
                        textAlign = TextAlign.Center,
                    )
                }

                is AnalysisOutcome.Error -> {
                    Text(text = "분석 오류", textAlign = TextAlign.Center)
                    Text(text = outcome.failure.message, textAlign = TextAlign.Center)
                    if (outcome.failure.retryable) {
                        Button(onClick = onRetry) { Text("다시 시도") }
                    }
                }
            }
            Button(onClick = onReturnHome) { Text("처음으로") }
        }
    }
}
