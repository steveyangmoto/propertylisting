package com.example.propertylisting.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.propertylisting.data.Listing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingCard(
    group: ListingGroup,
    showDuplicateBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    val listing = group.primary
    var expanded by rememberSaveable(listing.id) { mutableStateOf(false) }

    ElevatedCard(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatPrice(listing.price),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                listing.status?.takeUnless { it.equals("active", ignoreCase = true) }?.let { status ->
                    Tag(
                        text = status.replaceFirstChar { it.uppercase() },
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Text(listing.address, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listing.locationLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                Fact(Icons.Outlined.Bed, "${listing.bedrooms} bd")
                Fact(Icons.Outlined.Bathtub, "${formatBaths(listing.bathrooms)} ba")
                listing.sqft?.let { Fact(Icons.Outlined.SquareFoot, "${formatCount(it)} sqft") }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (listing.amenities?.petFriendly == true) Tag("Pet friendly", Icons.Outlined.Pets)
                if (listing.amenities?.parking == true) Tag("Parking", Icons.Outlined.DirectionsCar)
                if (group.alternates.isNotEmpty()) {
                    val count = group.alternates.size
                    Tag("Also listed by $count other source${if (count == 1) "" else "s"}", Icons.Outlined.ContentCopy)
                } else if (showDuplicateBadge && listing.duplicateGroupId != null) {
                    Tag("Duplicate listing", Icons.Outlined.ContentCopy)
                }
                listing.nearDuplicate?.let {
                    Tag("Possibly same as ${it.ofId}", Icons.Outlined.ContentCopy)
                }
            }

            AnimatedVisibility(visible = expanded) {
                ListingDetails(group)
            }

            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Show less" else "Show more",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ListingDetails(group: ListingGroup) {
    val listing = group.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider()
        listing.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        val pets = when (listing.amenities?.petFriendly) {
            true -> "Pets allowed"
            false -> "No pets"
            null -> null
        }
        val parking = when (listing.amenities?.parking) {
            true -> "Parking included"
            false -> "No dedicated parking"
            null -> null
        }
        DetailLine(listOfNotNull(pets, parking).joinToString(" · "))
        DetailLine(
            listOfNotNull(
                listing.listedDate?.let { "Listed ${formatListedDate(it)}" },
                listOfNotNull(listing.source, "#${listing.id}").joinToString(" "),
            ).joinToString(" · "),
        )
        listing.score?.let { DetailLine("Relevance score: ${"%.2f".format(it)}") }
        listing.nearDuplicate?.let {
            DetailLine("Looks similar to listing ${it.ofId} (${(it.confidence * 100).toInt()}% confidence)")
        }

        if (group.alternates.isNotEmpty()) {
            Text("Same property from other sources", style = MaterialTheme.typography.labelLarge)
            group.alternates.forEach { alt ->
                DetailLine("${alt.source ?: "Unknown source"} #${alt.id}: ${formatPrice(alt.price)} · ${alt.address}")
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    if (text.isBlank()) return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Fact(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Tag(
    text: String,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** "Vienna, VA 22180", tolerating a missing state or zip. */
private fun Listing.locationLine(): String {
    val stateZip = listOfNotNull(state, zip).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(city, stateZip).filter { it.isNotBlank() }.joinToString(", ")
}
