package com.example.propertylisting.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SearchRoute(viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onFormChange = viewModel::updateForm,
        onSearch = viewModel::search,
        onClear = viewModel::clearForm,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadNextPage,
        onMergeDuplicatesChange = viewModel::setMergeDuplicates,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onFormChange: ((SearchForm) -> SearchForm) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMergeDuplicatesChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val results = state.results

    Scaffold(topBar = { TopAppBar(title = { Text("Property Search") }) }) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScrollbar(
                    state = listState,
                    trackTop = padding.calculateTopPadding() + 8.dp,
                    trackBottom = padding.calculateBottomPadding() + 8.dp,
                ),
        ) {
            item(key = "form") {
                SearchFormCard(
                    form = state.form,
                    errors = state.formErrors,
                    onFormChange = onFormChange,
                    onSearch = onSearch,
                    onClear = onClear,
                )
            }

            when (results) {
                ResultsState.Idle -> item(key = "status") {
                    MessageState(
                        icon = Icons.Outlined.Home,
                        title = "Find your next home",
                        body = "Enter a price range, city or keyword, then tap Search. Leave everything blank to browse all listings.",
                    )
                }

                ResultsState.Loading -> item(key = "status") { LoadingState() }

                is ResultsState.Error -> item(key = "status") {
                    MessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Search failed",
                        body = results.message,
                        action = { Button(onClick = onRetry) { Text("Try again") } },
                    )
                }

                is ResultsState.Loaded -> {
                    if (results.listings.isEmpty()) {
                        item(key = "status") {
                            MessageState(
                                icon = Icons.Outlined.SearchOff,
                                title = "No listings match",
                                body = "Try widening the price range or removing a filter.",
                            )
                        }
                    } else {
                        val groups = if (state.mergeDuplicates) {
                            groupDuplicates(results.listings)
                        } else {
                            ungrouped(results.listings)
                        }
                        item(key = "header") {
                            ResultsHeader(
                                totalResults = results.totalResults,
                                mergedCount = results.listings.size - groups.size,
                                mergeDuplicates = state.mergeDuplicates,
                                onMergeDuplicatesChange = onMergeDuplicatesChange,
                            )
                        }
                        items(groups, key = { it.primary.id }) { group ->
                            ListingCard(group = group, showDuplicateBadge = !state.mergeDuplicates)
                        }
                        item(key = "footer") {
                            ResultsFooter(results = results, onLoadMore = onLoadMore)
                        }
                    }
                }
            }
        }
    }

    if (results is ResultsState.Loaded && results.loadMoreError == null) {
        InfiniteScrollEffect(listState, onLoadMore)
    }
}

/** Requests the next page whenever the user scrolls within a few items of the end of the list. */
@Composable
private fun InfiniteScrollEffect(listState: LazyListState, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Pairing with the item count re-triggers after each page loads, so a short page that
            // still doesn't fill the screen keeps fetching until it does.
            (lastVisible >= layout.totalItemsCount - 3) to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { currentOnLoadMore() }
    }
}

@Composable
private fun SearchFormCard(
    form: SearchForm,
    errors: Map<FormField, String>,
    onFormChange: ((SearchForm) -> SearchForm) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSearch()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.city,
                onValueChange = { value -> onFormChange { it.copy(city = value) } },
                label = { Text("City") },
                leadingIcon = { Icon(Icons.Outlined.LocationCity, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.keyword,
                onValueChange = { value -> onFormChange { it.copy(keyword = value) } },
                label = { Text("Keyword") },
                placeholder = { Text("e.g. Metro, fenced yard") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(
                    label = "Min price",
                    value = form.minPrice,
                    error = errors[FormField.MinPrice],
                    onValueChange = { value -> onFormChange { it.copy(minPrice = value) } },
                    modifier = Modifier.weight(1f),
                )
                AmountField(
                    label = "Max price",
                    value = form.maxPrice,
                    error = errors[FormField.MaxPrice],
                    onValueChange = { value -> onFormChange { it.copy(maxPrice = value) } },
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Bedrooms", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf(null, 1, 2, 3, 4).forEach { beds ->
                    FilterChip(
                        selected = form.minBedrooms == beds,
                        onClick = { onFormChange { it.copy(minBedrooms = beds) } },
                        label = { Text(if (beds == null) "Any" else "$beds+") },
                    )
                }
            }

            AmountField(
                label = "Target budget (optional)",
                value = form.targetBudget,
                error = errors[FormField.TargetBudget],
                supportingText = "Ranks listings closest to this price first",
                imeAction = ImeAction.Search,
                onImeAction = submit,
                onValueChange = { value -> onFormChange { it.copy(targetBudget = value) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClear, enabled = !form.isEmpty) { Text("Clear") }
                Button(onClick = submit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Search", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        // Digits only, capped well below Long overflow.
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(10)) },
        label = { Text(label) },
        prefix = { Text("$") },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supportingText)?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier,
    )
}

@Composable
private fun ResultsHeader(
    totalResults: Int,
    mergedCount: Int,
    mergeDuplicates: Boolean,
    onMergeDuplicatesChange: (Boolean) -> Unit,
) {
    Column {
        Text(
            text = "$totalResults listing${if (totalResults == 1) "" else "s"} found",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = buildString {
                append("Ranked by relevance")
                if (mergeDuplicates && mergedCount > 0) {
                    append(" · $mergedCount duplicate${if (mergedCount == 1) "" else "s"} merged")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Merge listings from multiple sources",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = mergeDuplicates, onCheckedChange = onMergeDuplicatesChange)
        }
    }
}

@Composable
private fun ResultsFooter(results: ResultsState.Loaded, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            results.isLoadingMore -> CircularProgressIndicator(Modifier.size(28.dp))
            results.loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    results.loadMoreError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onLoadMore) { Text("Retry") }
            }
            results.canLoadMore -> OutlinedButton(onClick = onLoadMore) { Text("Load more") }
            else -> Text(
                "End of results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    ) {
        CircularProgressIndicator()
        Text("Searching listings…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
