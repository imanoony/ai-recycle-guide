package com.example.airecycleguide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawingPadding
import com.example.airecycleguide.data.model.AnalysisResponse
import com.example.airecycleguide.data.model.GuidanceSource
import com.example.airecycleguide.data.model.WasteItemResult
import com.example.airecycleguide.ui.state.AnalysisOutcome

@Composable
fun AnalysisResultScreen(
    outcome: AnalysisOutcome,
    onRetry: () -> Unit,
    onReturnHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (outcome) {
                is AnalysisOutcome.Success -> AnalysisResponseContent(outcome.response)
                is AnalysisOutcome.Error -> {
                    Text("분석 오류", style = MaterialTheme.typography.headlineSmall)
                    Text(outcome.failure.message)
                }
            }
        }

        if (outcome is AnalysisOutcome.Error && outcome.failure.retryable) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("다시 시도")
            }
        }
        Button(onClick = onReturnHome, modifier = Modifier.fillMaxWidth()) {
            Text("처음으로")
        }
    }
}

@Composable
private fun AnalysisResponseContent(response: AnalysisResponse) {
    val items = response.items.filter { it.name.isNotBlank() }
    val isIdentified = response.status == "identified" && items.isNotEmpty()
    val isUnidentified = response.status == "unidentified" ||
        (response.status == "identified" && items.isEmpty())
    val isClarification = response.status == "needs_clarification"

    Text(
        text = when {
            isIdentified -> "식별 결과"
            isClarification -> "확인이 필요해요"
            isUnidentified -> "품목을 식별하지 못했어요"
            else -> "분석 결과를 확인해 주세요"
        },
        style = MaterialTheme.typography.headlineSmall,
    )

    response.region?.takeIf { it.isNotBlank() }?.let {
        Text("적용 기준 지역: $it", style = MaterialTheme.typography.bodyMedium)
    }
    response.message?.takeIf { it.isNotBlank() }?.let { Text(it) }

    when {
        isIdentified -> items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            WasteItemSummary(item = item, heading = item.name, showGuidance = true)
        }

        isClarification && items.isNotEmpty() -> {
            Text("가능성 있는 품목", style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                WasteItemSummary(item = item, heading = item.name, showGuidance = false)
            }
            Text("품목이 확실하지 않아 배출 방법을 단정하지 않았습니다.")
        }

        isClarification -> Text("사진을 더 가까이, 밝은 곳에서 다시 촬영해 주세요.")
        isUnidentified -> Text("품목이 잘 보이도록 사진을 다시 촬영해 주세요.")
        items.isNotEmpty() -> {
            Text("식별 후보", style = MaterialTheme.typography.titleMedium)
            items.forEach { WasteItemSummary(item = it, heading = it.name, showGuidance = false) }
            Text("분석 상태가 불확실해 배출 방법을 단정하지 않았습니다.")
        }

        else -> Text("표시할 수 있는 품목 정보가 없습니다.")
    }
}

@Composable
private fun WasteItemSummary(
    item: WasteItemResult,
    heading: String,
    showGuidance: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(heading, style = MaterialTheme.typography.titleLarge)
        item.material?.takeIf { it.isNotBlank() }?.let { Text("재질: $it") }
        item.confidence?.let { Text("AI 인식도: ${(it * 100).toInt()}%") }

        if (showGuidance) {
            Text("배출 상태: ${sortingStatusLabel(item.sortingStatus)}")
            when (item.sortingStatus) {
                "unknown" -> Text(
                    if (item.steps.isEmpty()) {
                        "지역별 배출 규칙이 없어 품목만 식별했습니다."
                    } else {
                        "아래 단계는 일반적인 준비 방법이며, 최종 배출 방법은 지역 기준을 확인해 주세요."
                    },
                )
                "local_confirmation_required" -> Text("최종 배출 방법은 지역 기준을 확인해 주세요.")
            }

            val sortedSteps = item.steps.sortedBy { it.order }
            if (sortedSteps.isNotEmpty()) {
                Text("배출 단계", style = MaterialTheme.typography.titleMedium)
                sortedSteps.forEach { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${step.order}. ${step.title}", style = MaterialTheme.typography.bodyLarge)
                        Text(step.instruction)
                    }
                }
            }

            if (item.cautions.isNotEmpty()) {
                Text("주의사항", style = MaterialTheme.typography.titleMedium)
                item.cautions.filter { it.isNotBlank() }.forEach { caution ->
                    Text("• $caution")
                }
            }

            if (item.sources.isNotEmpty()) {
                Text("근거", style = MaterialTheme.typography.titleMedium)
                item.sources.forEach { SourceInfo(it) }
            }
        }
    }
}

@Composable
private fun SourceInfo(source: GuidanceSource) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val label = source.title.ifBlank { source.url }
        if (source.url.isNotBlank()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri(source.url) }
                },
            )
        } else if (label.isNotBlank()) {
            Text(label)
        }
        source.region?.takeIf { it.isNotBlank() }?.let { Text("적용 지역: $it") }
        source.checkedAt?.takeIf { it.isNotBlank() }?.let { Text("확인일: $it") }
    }
}

private fun sortingStatusLabel(status: String): String = when (status) {
    "recyclable" -> "재활용 가능"
    "food_waste" -> "음식물 쓰레기"
    "general_waste" -> "일반 쓰레기"
    "local_confirmation_required" -> "지역 확인 필요"
    "unknown" -> "안내 확인 필요"
    else -> "안내 확인 필요"
}