package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.KeepNote
import com.example.ui.AppViewModel
import com.example.ui.theme.WaterBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepNotesView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notes by viewModel.keepNotes.collectAsState()
    val syncStatus by viewModel.keepNotesSyncStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<KeepNote?>(null) }

    // Dialog state for add/edit
    var inputTitle by remember { mutableStateOf("") }
    var inputContent by remember { mutableStateOf("") }
    var inputColorHex by remember { mutableStateOf("#202124") }
    var inputIsPinned by remember { mutableStateOf(false) }

    // Keep standard dark mode backgrounds
    val keepColors = listOf(
        "#202124" to "Charcoal",
        "#5c2b29" to "Red",
        "#614a19" to "Orange",
        "#635d19" to "Yellow",
        "#345920" to "Green",
        "#16504b" to "Teal",
        "#2d555e" to "Blue",
        "#1e3a5f" to "Dark Blue",
        "#42275e" to "Purple",
        "#5b2245" to "Pink",
        "#442f19" to "Brown",
        "#3c3f41" to "Grey"
    )

    // Filtered lists
    val filteredNotes = notes.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
    }

    val pinnedNotes = filteredNotes.filter { it.isPinned }
    val otherNotes = filteredNotes.filter { !it.isPinned }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF06070D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Keep Notes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Cloud Backed & Styled Notes",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // Sync controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.syncKeepNotes(context) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WaterBlue.copy(alpha = 0.2f))
                            .testTag("sync_keep_notes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Notes",
                            tint = WaterBlue
                        )
                    }
                }
            }

            // Sync Status Indicator
            if (syncStatus != "Idle" && syncStatus.isNotEmpty()) {
                Surface(
                    color = WaterBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = WaterBlue
                        )
                        Text(
                            text = syncStatus,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search your notes...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("notes_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (filteredNotes.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "No Notes",
                            tint = WaterBlue.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No Notes Yet" else "No matching notes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Tap + to capture website links, videos, and ideas!" else "Try searching for another keyword",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                // Content grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                            Text(
                                "PINNED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(pinnedNotes) { note ->
                            NoteCard(
                                note = note,
                                onClick = {
                                    noteToEdit = note
                                    inputTitle = note.title
                                    inputContent = note.content
                                    inputColorHex = note.colorHex
                                    inputIsPinned = note.isPinned
                                    showAddDialog = true
                                },
                                onPinClick = {
                                    viewModel.updateKeepNote(note.copy(isPinned = !note.isPinned))
                                },
                                onDeleteClick = {
                                    viewModel.deleteKeepNote(note)
                                }
                            )
                        }
                    }

                    if (otherNotes.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                            Text(
                                if (pinnedNotes.isNotEmpty()) "OTHERS" else "NOTES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(otherNotes) { note ->
                            NoteCard(
                                note = note,
                                onClick = {
                                    noteToEdit = note
                                    inputTitle = note.title
                                    inputContent = note.content
                                    inputColorHex = note.colorHex
                                    inputIsPinned = note.isPinned
                                    showAddDialog = true
                                },
                                onPinClick = {
                                    viewModel.updateKeepNote(note.copy(isPinned = !note.isPinned))
                                },
                                onDeleteClick = {
                                    viewModel.deleteKeepNote(note)
                                }
                            )
                        }
                    }
                }
            }
        }

        // FAB to add new Note
        FloatingActionButton(
            onClick = {
                noteToEdit = null
                inputTitle = ""
                inputContent = ""
                inputColorHex = "#202124"
                inputIsPinned = false
                showAddDialog = true
            },
            containerColor = WaterBlue,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_note_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Note")
        }

        // Add / Edit Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = Color(0xFF13141C),
                title = {
                    Text(
                        text = if (noteToEdit == null) "Add Keep Note" else "Edit Keep Note",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = { Text("Title") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = inputContent,
                            onValueChange = { inputContent = it },
                            label = { Text("Take a note...") },
                            minLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Pin Note Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pin Note to Top", color = Color.LightGray, fontSize = 14.sp)
                            Switch(
                                checked = inputIsPinned,
                                onCheckedChange = { inputIsPinned = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = WaterBlue
                                )
                            )
                        }

                        // Color picker title
                        Text("Choose Card Color", color = Color.LightGray, fontSize = 14.sp)

                        // Horizontal color grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            keepColors.forEach { (colorHexCode, name) ->
                                val isSelected = inputColorHex.equals(colorHexCode, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(colorHexCode)))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) WaterBlue else Color.White.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .clickable { inputColorHex = colorHexCode }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputTitle.isNotBlank() || inputContent.isNotBlank()) {
                                val currentEdit = noteToEdit
                                if (currentEdit == null) {
                                    viewModel.insertKeepNote(
                                        title = inputTitle,
                                        content = inputContent,
                                        colorHex = inputColorHex,
                                        isPinned = inputIsPinned
                                    )
                                } else {
                                    viewModel.updateKeepNote(
                                        currentEdit.copy(
                                            title = inputTitle,
                                            content = inputContent,
                                            colorHex = inputColorHex,
                                            isPinned = inputIsPinned
                                        )
                                    )
                                }
                                showAddDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun NoteCard(
    note: KeepNote,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(note.colorHex))
    } catch (e: Exception) {
        Color(0xFF202124) // Fallback charcoal
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder(true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.1f))
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Logo Banner section if present
            if (!note.customLogoUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black.copy(alpha = 0.15f))
                ) {
                    AsyncImage(
                        model = note.customLogoUrl,
                        contentDescription = "Website Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Video badge overlay for youtube
                    val isYouTube = note.websiteUrl?.contains("youtube", ignoreCase = true) == true ||
                            note.websiteUrl?.contains("youtu.be", ignoreCase = true) == true

                    if (isYouTube) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "YouTube Link",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Floating domain label
                    note.websiteUrl?.let { url ->
                        val domain = try {
                            android.net.Uri.parse(url).host ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        if (domain.isNotEmpty()) {
                            Text(
                                text = domain,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header of card (Title + Pin toggle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (note.title.isNotEmpty()) {
                        Text(
                            text = note.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.PinDrop,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) WaterBlue else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (note.content.isNotEmpty()) {
                    Text(
                        text = note.content,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom row with Sync status and Delete action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (note.isSynced) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Synced to Keep",
                                tint = WaterBlue.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Synced",
                                color = WaterBlue.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Local Only",
                                tint = Color.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Local",
                                color = Color.Gray.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
