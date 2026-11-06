package com.clonex.space

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clonex.space.data.CloneDatabase
import com.clonex.space.data.CloneEntry
import com.clonex.space.data.SettingsStore
import com.clonex.space.engine.EngineResult
import com.clonex.space.engine.StubVirtualEngine
import com.clonex.space.ui.CloneXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * CloneX v1.1 — premium library UI with REAL app icons.
 *
 * Real in this build:
 *  - clone library (Room), APK import via SAF with real package-name parsing,
 *    installed-app picker, settings (DataStore), search, delete,
 *  - REAL app icons loaded from PackageManager (installed apps) or from the
 *    imported APK file itself — no more letter tiles as the primary icon
 *    (a letter tile only shows while the real icon is loading, or when the
 *    app is neither installed nor has a readable APK).
 *
 * Stub (honest, unchanged): virtual launch — tapping a clone explains that
 * the virtual engine is not bundled yet. Real engine research is done
 * (NewBlackbox / MaxCloner, see ENGINE_RESEARCH.md) and is waiting on the
 * targetSdk 28 decision before integration.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = CloneDatabase.get(this)
        val dao = db.cloneDao()
        val settings = SettingsStore(this)
        val engine = StubVirtualEngine(this, dao)

        setContent {
            CloneXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CloneXHome(
                        dao = dao,
                        settings = settings,
                        onImportApk = { uri -> importApk(uri, engine) },
                        onAddInstalledApp = { packageName, label ->
                            addInstalledApp(packageName, label, dao)
                        },
                        onLaunchClone = { entry -> launchClone(entry, engine) },
                        onDeleteClone = { entry -> deleteClone(entry, dao) },
                        onListInstalledApps = { listInstalledApps() }
                    )
                }
            }
        }
    }

    private suspend fun importApk(uri: Uri, engine: StubVirtualEngine): String {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "import-${System.currentTimeMillis()}.apk"
                val dir = File(cacheDir, "clonex-imports").apply { mkdirs() }
                val outFile = File(dir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext "Could not read the picked file."
                when (val result = engine.installPackageAsUser(outFile.absolutePath, 0)) {
                    is EngineResult.Ok -> result.message
                    is EngineResult.Failed -> result.reason
                    is EngineResult.NotImplemented -> result.reason
                }
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
        }
    }

    private suspend fun addInstalledApp(
        packageName: String,
        label: String,
        dao: com.clonex.space.data.CloneDao
    ): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val index = dao.countForPackage(packageName)
            dao.insert(
                CloneEntry(
                    packageName = packageName,
                    label = label,
                    cloneIndex = index,
                    userId = index
                )
            )
            "$label added as Clone ${index + 1}. Virtual launch comes with the engine."
        } catch (e: Exception) {
            "Could not add app: ${e.message}"
        }
    }

    private suspend fun launchClone(entry: CloneEntry, engine: StubVirtualEngine): String {
        return when (val result = engine.launchApp(entry.packageName, entry.userId)) {
            is EngineResult.NotImplemented -> result.reason
            is EngineResult.Ok -> result.message
            is EngineResult.Failed -> result.reason
        }
    }

    private suspend fun deleteClone(
        entry: CloneEntry,
        dao: com.clonex.space.data.CloneDao
    ): String = withContext(Dispatchers.IO) {
        return@withContext try {
            dao.delete(entry.packageName, entry.cloneIndex)
            "${entry.label} removed from the library."
        } catch (e: Exception) {
            "Could not remove: ${e.message}"
        }
    }

    /** Launchable apps installed on the phone (for the Add App picker). */
    private suspend fun listInstalledApps(): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val pm = packageManager
            @Suppress("DEPRECATION")
            val infos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                pm.queryIntentActivities(intent, 0)
            }
            infos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null // never clone CloneX itself
                val label = info.loadLabel(pm)?.toString() ?: pkg
                pkg to label
            }.sortedBy { it.second.lowercase() }
        }
}

/**
 * Real app icons, loaded from PackageManager and cached in memory.
 *
 * - Installed app: the live launcher icon via getApplicationIcon().
 * - Imported APK that is NOT installed: the icon embedded in the APK file
 *   via getPackageArchiveInfo() (sourceDir must point at the file or
 *   loadIcon() returns the default icon).
 * - Anything else: null, and the UI falls back to a letter tile.
 */
object AppIconLoader {
    private val cache = ConcurrentHashMap<String, ImageBitmap?>()

    suspend fun load(pm: PackageManager, entry: CloneEntry): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = entry.packageName + "#" + entry.cloneIndex
            cache.getOrPut(key) {
                try {
                    val drawable: Drawable? = try {
                        pm.getApplicationIcon(entry.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        val apkPath = entry.apkPath ?: return@getOrPut null
                        @Suppress("DEPRECATION")
                        val info = if (Build.VERSION.SDK_INT >= 33) {
                            pm.getPackageArchiveInfo(
                                apkPath,
                                PackageManager.PackageInfoFlags.of(0)
                            )?.applicationInfo
                        } else {
                            pm.getPackageArchiveInfo(apkPath, 0)?.applicationInfo
                        } ?: return@getOrPut null
                        info.sourceDir = apkPath
                        info.publicSourceDir = apkPath
                        info.loadIcon(pm)
                    }
                    drawable?.toImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }

    suspend fun loadForPackage(pm: PackageManager, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.getOrPut("pkg:$packageName") {
                try {
                    pm.getApplicationIcon(packageName).toImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: 192
    val h = intrinsicHeight.takeIf { it > 0 } ?: 192
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bitmap.asImageBitmap()
}

private val HeaderGradient = Brush.linearGradient(
    listOf(Color(0xFF6C5CE7), Color(0xFF8E7CFF), Color(0xFFFD79A8))
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneXHome(
    dao: com.clonex.space.data.CloneDao,
    settings: SettingsStore,
    onImportApk: suspend (Uri) -> String,
    onAddInstalledApp: suspend (String, String) -> String,
    onLaunchClone: suspend (CloneEntry) -> String,
    onDeleteClone: suspend (CloneEntry) -> String,
    onListInstalledApps: suspend () -> List<Pair<String, String>>
) {
    val clones by dao.getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val maxClones by settings.maxClones.collectAsStateWithLifecycle(
        initialValue = SettingsStore.DEFAULT_MAX_CLONES
    )
    val notifications by settings.notificationsEnabled.collectAsStateWithLifecycle(
        initialValue = true
    )

    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<CloneEntry?>(null) }
    val scope = rememberCoroutineScope()

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch { status = onImportApk(uri) }
        }
    }

    val shown = remember(clones, query) {
        if (query.isBlank()) clones
        else clones.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Premium gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(HeaderGradient)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CX", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "CloneX",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Virtual Space  •  Premium",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSettings = true }) {
                        Text("Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip("${clones.size} clone${if (clones.size == 1) "" else "s"}")
                    HeaderChip("Engine • Pending")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = "${clones.size}",
                label = "Clones in library",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "$maxClones",
                label = "Max clones per app",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search clones…") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showPicker = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { Text("+  Add App", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Import APK", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }

        status?.let { message ->
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { status = null }) { Text("Dismiss") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(HeaderGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CX", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = if (clones.isEmpty()) "No clones yet." else "No clones match “$query”.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (clones.isEmpty()) {
                            "Tap “Add App” to clone an installed app,\n" +
                                "or “Import APK” to add an APK file.\n\n" +
                                "Long-press a clone to remove it."
                        } else {
                            "Try a different search."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(shown, key = { it.packageName + "#" + it.cloneIndex }) { entry ->
                    CloneCard(
                        entry = entry,
                        onClick = {
                            scope.launch { status = onLaunchClone(entry) }
                        },
                        onLongClick = { toDelete = entry }
                    )
                }
            }
        }
    }

    if (showPicker) {
        InstalledAppPicker(
            onList = onListInstalledApps,
            onPick = { pkg, label ->
                showPicker = false
                scope.launch { status = onAddInstalledApp(pkg, label) }
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Max clones per app", modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch { settings.setMaxClones(maxClones - 1) }
                        }) { Text("−", fontSize = 20.sp) }
                        Text("$maxClones", fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            scope.launch { settings.setMaxClones(maxClones + 1) }
                        }) { Text("+", fontSize = 20.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Clone notifications", modifier = Modifier.weight(1f))
                        Switch(
                            checked = notifications,
                            onCheckedChange = { enabled ->
                                scope.launch { settings.setNotificationsEnabled(enabled) }
                            }
                        )
                    }
                    Text(
                        "Virtual engine: research done (NewBlackbox / MaxCloner) — " +
                            "integration waits on the targetSdk 28 decision. " +
                            "See ENGINE_RESEARCH.md.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Done") }
            }
        )
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove clone?") },
            text = { Text("Remove ${entry.label} (Clone ${entry.cloneIndex + 1}) from the library?") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch { status = onDeleteClone(entry) }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HeaderChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }
    }
}

/** Real app icon with a letter tile only as a loading fallback. */
@Composable
private fun CloneAppIcon(entry: CloneEntry, size: androidx.compose.ui.unit.Dp) {
    val pm = LocalContext.current.packageManager
    var bitmap by remember(entry.packageName, entry.cloneIndex) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(entry.packageName, entry.cloneIndex) {
        bitmap = AppIconLoader.load(pm, entry)
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = entry.label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
        )
    } else {
        val letter = entry.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val hue = (entry.packageName.hashCode() and 0xFF) / 255f
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.hsv(hue * 360f, 0.55f, 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneCard(
    entry: CloneEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CloneAppIcon(entry = entry, size = 56.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                entry.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "Clone ${entry.cloneIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap to launch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun InstalledAppPicker(
    onList: suspend () -> List<Pair<String, String>>,
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var pickerQuery by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { onList() }
    }
    val shown = remember(apps, pickerQuery) {
        val list = apps ?: emptyList()
        if (pickerQuery.isBlank()) list
        else list.filter {
            it.second.contains(pickerQuery, ignoreCase = true) ||
                it.first.contains(pickerQuery, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app to clone") },
        text = {
            Column {
                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                when (val list = apps) {
                    null -> Text("Loading installed apps…")
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(shown, key = { it.first }) { (pkg, label) ->
                            PickerRow(
                                packageName = pkg,
                                label = label,
                                onClick = { onPick(pkg, label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PickerRow(packageName: String, label: String, onClick: () -> Unit) {
    val pm = LocalContext.current.packageManager
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = AppIconLoader.loadForPackage(pm, packageName)
    }
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = label,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                val letter = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(letter, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    textAlign = TextAlign.Start,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
