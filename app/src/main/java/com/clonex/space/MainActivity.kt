package com.clonex.space

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Color
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

/**
 * CloneX MVP — single activity.
 *
 * Real in this MVP: clone library (Room), APK import via SAF with real
 * package-name parsing, installed-app picker, settings (DataStore),
 * search, delete.
 * Stub (honest): virtual launch — tapping a clone explains that the
 * virtual engine is not bundled yet. See engine/ENGINE_NOTES.md.
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
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("CX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "CloneX",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Virtual Space  •  ${clones.size} clone${if (clones.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSettings = true }) { Text("Settings") }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search clones…") },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f)
            ) { Text("+  Add App") }
            OutlinedButton(
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
                modifier = Modifier.weight(1f)
            ) { Text("Import APK") }
        }

        status?.let { message ->
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
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
                Text(
                    text = if (clones.isEmpty()) {
                        "No clones yet.\n\nTap “Add App” to clone an installed app,\n" +
                            "or “Import APK” to add an APK file.\n\n" +
                            "Long-press a clone to remove it."
                    } else {
                        "No clones match “$query”."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
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
                        "Virtual engine: not bundled in this MVP.\n" +
                            "See engine/ENGINE_NOTES.md for the honest status.",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneCard(
    entry: CloneEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val letter = entry.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val hue = (entry.packageName.hashCode() and 0xFF) / 255f
    val tileColor = Color.hsv(hue * 360f, 0.55f, 0.75f)
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(tileColor),
                contentAlignment = Alignment.Center
            ) {
                Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                entry.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Clone ${entry.cloneIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { onList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app to clone") },
        text = {
            when (val list = apps) {
                null -> Text("Loading installed apps…")
                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(list, key = { it.first }) { (pkg, label) ->
                        TextButton(
                            onClick = { onPick(pkg, label) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(label, textAlign = TextAlign.Start)
                                Text(
                                    pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
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
