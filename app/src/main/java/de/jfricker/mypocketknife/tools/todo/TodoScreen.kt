package de.jfricker.mypocketknife.tools.todo

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class NameDialog { NEW, RENAME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(onBack: () -> Unit, vm: TodoViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    // "Rückgängig" nach dem Löschen anbieten
    LaunchedEffect(state.undoMessage) {
        val msg = state.undoMessage ?: return@LaunchedEffect
        vm.undoShown()
        val result = snackbar.showSnackbar(msg, actionLabel = "Rückgängig", withDismissAction = true)
        if (result == SnackbarResult.ActionPerformed) vm.undo()
    }

    val current = state.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Listen") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
                actions = {
                    IconButton(onClick = { share(context, vm.shareText()) }) { Text("↗", fontSize = 20.sp) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Text("⋮", fontSize = 22.sp) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Neue Liste") }, onClick = { menuOpen = false; nameDialog = NameDialog.NEW })
                            DropdownMenuItem(text = { Text("Liste umbenennen") }, onClick = { menuOpen = false; nameDialog = NameDialog.RENAME })
                            DropdownMenuItem(text = { Text("Erledigte löschen") }, onClick = { menuOpen = false; vm.clearDone() })
                            if (state.data.lists.size > 1) {
                                DropdownMenuItem(text = { Text("Liste löschen") }, onClick = { menuOpen = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- Listen als Reiter ---
            val selectedIndex = state.data.lists.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp) {
                state.data.lists.forEach { list ->
                    val openCount = list.items.count { !it.done }
                    Tab(
                        selected = list.id == current?.id,
                        onClick = { vm.select(list.id); input = "" },
                        text = { Text(if (openCount > 0) "${list.name} ($openCount)" else list.name) },
                    )
                }
                Tab(selected = false, onClick = { nameDialog = NameDialog.NEW }, text = { Text("+") })
            }

            // --- Eingabe ---
            fun submit(text: String) {
                vm.add(text)
                input = ""
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Neuer Eintrag …") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                // Enter fügt hinzu, Tastatur bleibt offen für den nächsten Eintrag
                keyboardActions = KeyboardActions(onDone = { submit(input) }),
                trailingIcon = {
                    if (input.isNotBlank()) TextButton(onClick = { submit(input) }) { Text("Hinzu") }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val suggestions = state.suggestions(input)
            if (suggestions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    suggestions.forEach { s -> AssistChip(onClick = { submit(s) }, label = { Text(s) }) }
                }
            }

            // --- Einträge ---
            if (state.loaded && state.open.isEmpty() && state.done.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Noch nichts auf der Liste.")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                    items(state.open, key = { it.id }) { item ->
                        ItemRow(item, onToggle = { vm.toggle(item.id) }, onDelete = { vm.delete(item.id) }, Modifier.animateItem())
                    }
                    if (state.done.isNotEmpty()) {
                        item(key = "done-header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 12.dp)
                                    .animateItem(),
                            ) {
                                Text(
                                    "Erledigt (${state.done.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = vm::clearDone) { Text("Alle löschen") }
                            }
                            HorizontalDivider()
                        }
                        items(state.done, key = { it.id }) { item ->
                            ItemRow(item, onToggle = { vm.toggle(item.id) }, onDelete = { vm.delete(item.id) }, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }

    // --- Dialoge ---
    nameDialog?.let { mode ->
        var name by remember(mode) { mutableStateOf(if (mode == NameDialog.RENAME) current?.name.orEmpty() else "") }
        AlertDialog(
            onDismissRequest = { nameDialog = null },
            title = { Text(if (mode == NameDialog.NEW) "Neue Liste" else "Liste umbenennen") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("z. B. Baumarkt") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    if (mode == NameDialog.NEW) vm.addList(name) else vm.renameList(name)
                    nameDialog = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { nameDialog = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Liste löschen?") },
            text = { Text("„${current.name}“ mit allen Einträgen löschen?") },
            confirmButton = {
                Button(onClick = { vm.deleteList(); confirmDelete = false }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ItemRow(item: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp),
    ) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        Text(
            item.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = if (item.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Text("✕", fontSize = 16.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun share(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Liste teilen"))
}
