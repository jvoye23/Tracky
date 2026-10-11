package com.jvcs.tracky.features.onboarding.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.plan_recommended

private const val BADGE_ID = "badge"
private const val CARD_ID = "card"

/**
 * One subscription plan. Behaves as a radio button: a tap selects it, nothing is bought yet.
 *
 * A recommended plan carries a badge straddling the card's top border at the right corner; the card
 * is pushed down by half the badge's height so the badge never overlaps the card above it.
 */
@Composable
internal fun PlanCard(
    title: String,
    description: String?,
    price: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRecommended: Boolean = false,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // Composed first so a screen reader announces it before the plan it belongs to.
            if (isRecommended) {
                RecommendedBadge(modifier = Modifier.layoutId(BADGE_ID))
            }
            PlanCardBody(
                title = title,
                description = description,
                price = price,
                isSelected = isSelected,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.layoutId(CARD_ID),
            )
        },
    ) { measurables, constraints ->
        val badge =
            measurables
                .firstOrNull { it.layoutId == BADGE_ID }
                ?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val top = (badge?.height ?: 0) / 2
        val card = measurables.first { it.layoutId == CARD_ID }.measure(constraints.offset(vertical = -top))
        layout(card.width, card.height + top) {
            card.place(0, top)
            // Placed after the card so it is drawn over the border.
            badge?.place(card.width - badge.width - 16.dp.roundToPx(), 0)
        }
    }
}

@Composable
private fun PlanCardBody(
    title: String,
    description: String?,
    price: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.RadioButton
                    selected = isSelected
                },
        border =
            if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                CardDefaults.outlinedCardBorder(enabled)
            },
        colors =
            CardDefaults.outlinedCardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The card owns the click and the semantics; the radio is only the visual.
            RadioButton(selected = isSelected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun RecommendedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = stringResource(Res.string.plan_recommended),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun PlanCardSelectedPreview() {
    TrackyTheme {
        PlanCard(
            title = "Yearly",
            description = "Billed once a year. Cancel anytime.",
            price = "€29.99",
            isSelected = true,
            onClick = {},
            isRecommended = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun PlanCardUnselectedPreview() {
    TrackyTheme {
        PlanCard(
            title = "Monthly",
            description = "Billed every month. Cancel anytime.",
            price = "€3.99",
            isSelected = false,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun PlanCardDisabledPreview() {
    TrackyTheme {
        PlanCard(
            title = "Lifetime",
            description = "Pay once, keep Pro forever.",
            price = "€79.99",
            isSelected = false,
            onClick = {},
            enabled = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun PlanCardLargeFontPreview() {
    TrackyTheme {
        PlanCard(
            title = "Yearly",
            description = "Billed once a year. Cancel anytime.",
            price = "€29.99",
            isSelected = true,
            onClick = {},
            isRecommended = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "No description")
@Composable
private fun PlanCardNoDescriptionPreview() {
    TrackyTheme {
        PlanCard(
            title = "Tracky Pro Quarterly",
            description = null,
            price = "€9.99",
            isSelected = false,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Long description", widthDp = 320)
@Composable
private fun PlanCardLongDescriptionPreview() {
    TrackyTheme {
        PlanCard(
            title = "Yearly",
            description = "Billed once a year, renews automatically until you cancel it in the store settings.",
            price = "€29.99",
            isSelected = true,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun PlanCardRecommendedUnselectedPreview() {
    TrackyTheme {
        PlanCard(
            title = "Yearly",
            description = "Billed once a year. Cancel anytime.",
            price = "€29.99",
            isSelected = false,
            onClick = {},
            isRecommended = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
