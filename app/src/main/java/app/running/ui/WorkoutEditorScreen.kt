package app.running.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.running.model.*

private sealed interface DialogState {
    data object AddSingle : DialogState
    data class AddToGroup(val itemId: String) : DialogState
    data class Edit(val itemId: String, val block: Block) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(vm: WorkoutViewModel, draft: Workout) {
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = draft != vm.original
    val close = { if (dirty) confirmDiscard = true else vm.closeEditor() }
    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            PolarTopBar(
                title = if (vm.isNew) "Nuevo entreno" else "Editar entreno",
                navigationIcon = {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    TextButton(onClick = vm::save, enabled = draft.items.isNotEmpty()) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = vm::setName,
                    label = { Text("Nombre del entreno") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SummaryCard(draft) }
            items(draft.items, key = { it.id }) { item ->
                when (item) {
                    is SingleItem -> SingleCard(
                        item = item,
                        onEdit = { dialog = DialogState.Edit(item.id, item.block) },
                        onUp = { vm.moveItem(item.id, -1) },
                        onDown = { vm.moveItem(item.id, 1) },
                        onDuplicate = { vm.duplicateItem(item.id) },
                        onDelete = { vm.deleteItem(item.id) },
                    )
                    is GroupItem -> GroupCard(
                        item = item,
                        onRepeats = { vm.setRepeats(item.id, it) },
                        onEditBlock = { dialog = DialogState.Edit(item.id, it) },
                        onAddBlock = { dialog = DialogState.AddToGroup(item.id) },
                        onBlockUp = { vm.moveBlock(item.id, it, -1) },
                        onBlockDown = { vm.moveBlock(item.id, it, 1) },
                        onBlockDelete = { vm.deleteBlock(item.id, it) },
                        onUp = { vm.moveItem(item.id, -1) },
                        onDown = { vm.moveItem(item.id, 1) },
                        onDuplicate = { vm.duplicateItem(item.id) },
                        onDelete = { vm.deleteItem(item.id) },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { dialog = DialogState.AddSingle }, Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Bloque")
                    }
                    FilledTonalButton(onClick = vm::addGroup, Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Default.Repeat, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Repetición")
                    }
                }
            }
        }
    }

    dialog?.let { state ->
        BlockDialog(
            initial = (state as? DialogState.Edit)?.block,
            onDismiss = { dialog = null },
            onConfirm = { block ->
                when (state) {
                    DialogState.AddSingle -> vm.addSingle(block)
                    is DialogState.AddToGroup -> vm.addToGroup(state.itemId, block)
                    is DialogState.Edit -> vm.replaceBlock(state.itemId, block)
                }
                dialog = null
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Has modificado el entreno y no lo has guardado.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.closeEditor() }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
        )
    }
}

@Composable
private fun SummaryCard(w: Workout) {
    val blocks = w.expanded()
    PolarCard(Modifier.fillMaxWidth()) {
        // Bloque rojo de resumen, como en Polar.
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (blocks.isEmpty()) {
                Text(
                    "Añade bloques para construir el entreno",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    blocks.summary().label(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    blocks.size.blocksLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
        if (blocks.isNotEmpty()) {
            Timeline(blocks, Modifier.padding(16.dp), height = 48)
        }
    }
}

@Composable
private fun SingleCard(
    item: SingleItem,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    PolarCard(Modifier.fillMaxWidth()) {
        BlockRow(item.block, onEdit) {
            SmallIconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
            SmallIconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, "Bajar") }
            SmallIconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicar") }
            SmallIconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Borrar") }
        }
    }
}

@Composable
private fun GroupCard(
    item: GroupItem,
    onRepeats: (Int) -> Unit,
    onEditBlock: (Block) -> Unit,
    onAddBlock: () -> Unit,
    onBlockUp: (String) -> Unit,
    onBlockDown: (String) -> Unit,
    onBlockDelete: (String) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    PolarCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Repeat, null, Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { onRepeats(item.repeats - 1) }) { Icon(Icons.Default.Remove, "Menos") }
                Text("×${item.repeats}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onRepeats(item.repeats + 1) }) { Icon(Icons.Default.Add, "Más") }
                Spacer(Modifier.weight(1f))
                SmallIconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
                SmallIconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, "Bajar") }
                SmallIconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicar") }
                SmallIconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Borrar repetición") }
            }
            item.blocks.forEach { b ->
                BlockRow(b, onClick = { onEditBlock(b) }) {
                    SmallIconButton(onClick = { onBlockUp(b.id) }) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
                    SmallIconButton(onClick = { onBlockDown(b.id) }) { Icon(Icons.Default.KeyboardArrowDown, "Bajar") }
                    SmallIconButton(onClick = { onBlockDelete(b.id) }) { Icon(Icons.Default.Delete, "Borrar") }
                }
            }
            TextButton(onClick = onAddBlock, Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Bloque en la repetición")
            }
        }
    }
}

@Composable
private fun SmallIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp), content = content)
}

@Composable
private fun BlockRow(block: Block, onClick: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).height(40.dp).background(block.type.color()))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(block.type.label, style = MaterialTheme.typography.titleSmall)
            Text(
                block.describe(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}
