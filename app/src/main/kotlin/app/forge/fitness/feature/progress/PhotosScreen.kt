package app.forge.fitness.feature.progress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.fitness.data.db.ProgressPhotoEntity
import app.forge.fitness.data.photos.PhotoRepository
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.LocalAppUiScope
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.navigation.PhotoViewerRoute
import app.forge.fitness.ui.theme.Spacing
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.forge.fitness.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope

@HiltViewModel
class PhotosViewModel @Inject constructor(private val photos: PhotoRepository) : ViewModel() {
    val all: StateFlow<List<ProgressPhotoEntity>> = photos.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun file(photo: ProgressPhotoEntity): File = photos.fileFor(photo)
    fun newCameraFile(): File = photos.newCameraFile()
    suspend fun import(uri: Uri) = photos.import(uri)
    fun delete(id: String) { viewModelScope.launch { photos.delete(id) } }
    fun restore(id: String) { viewModelScope.launch { photos.restore(id) } }
    fun setPose(id: String, pose: String?) { viewModelScope.launch { photos.setPose(id, pose) } }
}

/** Your progress photos, newest first. Long-press two to compare them side by side. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    onBack: () -> Unit,
    onOpen: (String, String?) -> Unit,
    vm: PhotosViewModel = hiltViewModel(),
) {
    val photos by vm.all.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    var compareFirst by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }

    fun importUri(uri: Uri) = scope.launch {
        runCatching { vm.import(uri) }.onFailure { snackbar.showSnackbar("Couldn't import that photo") }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::importUri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera
        if (ok && path != null) importUri(Uri.fromFile(File(path)))
        pendingCamera = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (compareFirst != null) "Pick a photo to compare" else "Progress photos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (compareFirst != null) {
                        TextButton(onClick = { compareFirst = null }) { Text("Cancel") }
                    } else {
                        IconButton(onClick = {
                            val file = vm.newCameraFile()
                            pendingCamera = file.absolutePath
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                        }) { Icon(Icons.Rounded.CameraAlt, "Take a photo") }
                        IconButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Icon(Icons.Rounded.AddPhotoAlternate, "Add from gallery") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    Icons.Rounded.PhotoLibrary,
                    "No photos yet",
                    "Take one with 📷 or add from your gallery. Same spot, same light, every few weeks works best. " +
                        "Photos stay private inside Forge.",
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen, end = Spacing.screen,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tap to view · long-press to compare two",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            items(photos, key = { it.id }) { photo ->
                Column {
                    AsyncImage(
                        model = vm.file(photo),
                        contentDescription = "Progress photo from ${shortDate(photo.takenAt)}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (photo.id == compareFirst) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                else Modifier,
                            )
                            .combinedClickable(
                                onClick = {
                                    val first = compareFirst
                                    if (first != null && first != photo.id) {
                                        compareFirst = null
                                        onOpen(first, photo.id)
                                    } else {
                                        onOpen(photo.id, null)
                                    }
                                },
                                onLongClick = { compareFirst = photo.id },
                            ),
                    )
                    Text(
                        shortDate(photo.takenAt) + (photo.pose?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photos: PhotoRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val route: PhotoViewerRoute = savedStateHandle.toRoute()
    val all: StateFlow<List<ProgressPhotoEntity>> = photos.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun file(photo: ProgressPhotoEntity): File = photos.fileFor(photo)
    // App scope: the viewer closes straight after deleting, and Undo comes later.
    fun delete(id: String) { appScope.launch { photos.delete(id) } }
    fun restore(id: String) { appScope.launch { photos.restore(id) } }
    fun setPose(id: String, pose: String?) { viewModelScope.launch { photos.setPose(id, pose) } }
}

/** One photo full screen, or two side by side with their dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(onBack: () -> Unit, vm: PhotoViewerViewModel = hiltViewModel()) {
    val all by vm.all.collectAsStateWithLifecycle()
    val first = all.firstOrNull { it.id == vm.route.photoId }
    val second = vm.route.compareWith?.let { id -> all.firstOrNull { it.id == id } }
    val snackbar = LocalSnackbarHostState.current
    val appUiScope = LocalAppUiScope.current
    val scope = rememberCoroutineScope()
    // Older photo on the left when comparing.
    val pair = listOfNotNull(first, second).sortedBy { it.takenAt }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (pair.size == 2) "${shortDate(pair[0].takenAt)} → ${shortDate(pair[1].takenAt)}"
                        else first?.let { shortDate(it.takenAt) }.orEmpty(),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (pair.size == 1 && first != null) {
                        IconButton(onClick = {
                            vm.delete(first.id)
                            onBack()
                            appUiScope.launch { snackbar.showUndo("Photo deleted") { vm.restore(first.id) } }
                        }) { Icon(Icons.Rounded.DeleteOutline, "Delete photo") }
                    } else {
                        Icon(Icons.Rounded.Compare, null, modifier = Modifier.padding(end = Spacing.md))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.sm)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                pair.forEach { photo ->
                    AsyncImage(
                        model = vm.file(photo),
                        contentDescription = "Progress photo from ${shortDate(photo.takenAt)}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
            if (pair.size == 1 && first != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                    listOf("Front", "Side", "Back").forEach { pose ->
                        FilterChip(
                            selected = first.pose == pose,
                            onClick = { vm.setPose(first.id, if (first.pose == pose) null else pose) },
                            label = { Text(pose) },
                        )
                    }
                }
            }
        }
    }
}
