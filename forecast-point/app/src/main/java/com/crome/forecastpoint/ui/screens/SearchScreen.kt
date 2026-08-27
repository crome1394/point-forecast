package com.crome.forecastpoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.data.GeocodeResult
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.SurfaceDark

/**
 * City search ("Add City").
 *
 * When [searchAtBottom] is true (same Settings toggle as Map search), the query
 * field docks at the bottom and lifts with the IME so the keyboard never covers it.
 */
@Composable
fun SearchScreen(
    results: List<GeocodeResult>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (GeocodeResult) -> Unit,
    searchAtBottom: Boolean = false,
) {
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (searchAtBottom) Modifier.imePadding() else Modifier)
            .padding(16.dp),
    ) {
        Text(
            text = "Search for a U.S. city or place. Selecting a result adds it to your saved cities and loads the forecast.",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (!searchAtBottom) {
            SearchField(
                query = query,
                onQueryChange = {
                    query = it
                    onQueryChange(it)
                },
            )
            if (searching) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            }
            ResultsList(
                results = results,
                onSelect = onSelect,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(top = 12.dp),
            )
        } else {
            if (searching) {
                CircularProgressIndicator(Modifier.padding(bottom = 8.dp))
            }
            ResultsList(
                results = results,
                onSelect = onSelect,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SearchField(
                query = query,
                onQueryChange = {
                    query = it
                    onQueryChange(it)
                },
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("City, state, or place") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
}

@Composable
private fun ResultsList(
    results: List<GeocodeResult>,
    onSelect: (GeocodeResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(results) { result ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .clickable { onSelect(result) }
                    .padding(16.dp),
            ) {
                Text(result.name, color = Color.White, fontSize = 16.sp)
                Text(result.displayName, color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
    }
}
