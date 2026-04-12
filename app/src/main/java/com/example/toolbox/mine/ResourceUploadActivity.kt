package com.example.toolbox.mine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import coil3.compose.rememberAsyncImagePainter
import com.example.toolbox.ApiAddress
import com.example.toolbox.R
import com.example.toolbox.TokenManager
import com.example.toolbox.community.uploadImageFile
import com.example.toolbox.ui.theme.ToolBoxTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ResourceUploadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedApkInfo = readSharedApkInfo(intent)

        setContent {
            ToolBoxTheme {
                ResourceUploadScreen(
                    sharedApkInfo = sharedApkInfo,
                    onFinish = { finish() }
                )
            }
        }
    }

    private fun readSharedApkInfo(intent: Intent?): SharedApkInfo? {
        if (intent?.action != Intent.ACTION_SEND) return null

        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
        if (!isApkShare(intent.type, uri)) return null

        return parseSharedApk(uri)
    }

    private fun isApkShare(mimeType: String?, uri: Uri): Boolean {
        if (mimeType == "application/vnd.android.package-archive" || mimeType == "application/octet-stream") {
            return true
        }
        return queryDisplayName(this, uri)?.endsWith(".apk", ignoreCase = true) == true
    }

    private fun parseSharedApk(uri: Uri): SharedApkInfo? {
        val apkFile = copyUriToCache(this, uri, "shared_apk", ".apk") ?: return null
        val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
            ?: return null
        val applicationInfo = packageInfo.applicationInfo ?: return null

        applicationInfo.sourceDir = apkFile.absolutePath
        applicationInfo.publicSourceDir = apkFile.absolutePath

        val appName = packageManager.getApplicationLabel(applicationInfo).toString().ifBlank {
            apkFile.nameWithoutExtension
        }
        val iconFile = saveDrawableToCache(
            context = this,
            drawable = packageManager.getApplicationIcon(applicationInfo),
            fileName = "shared_apk_icon_${System.currentTimeMillis()}.png"
        )

        return SharedApkInfo(
            appName = appName,
            packageName = applicationInfo.packageName.orEmpty(),
            versionName = packageInfo.versionName.orEmpty(),
            sizeText = Formatter.formatShortFileSize(this, apkFile.length()),
            iconPreview = iconFile?.toUri()?.toString().orEmpty(),
            iconFilePath = iconFile?.absolutePath
        )
    }
}

private data class SharedApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val sizeText: String,
    val iconPreview: String,
    val iconFilePath: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceUploadScreen(
    sharedApkInfo: SharedApkInfo?,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val token = TokenManager.get(context)
    val client = remember { OkHttpClient() }
    val coroutineScope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(sharedApkInfo?.appName.orEmpty()) }
    var packageName by rememberSaveable { mutableStateOf(sharedApkInfo?.packageName.orEmpty()) }
    var version by rememberSaveable { mutableStateOf(sharedApkInfo?.versionName.orEmpty()) }
    var size by rememberSaveable { mutableStateOf(sharedApkInfo?.sizeText.orEmpty()) }
    var downloadUrl by rememberSaveable { mutableStateOf("") }
    var iconPreview by rememberSaveable { mutableStateOf(sharedApkInfo?.iconPreview.orEmpty()) }
    var iconUrl by rememberSaveable { mutableStateOf("") }
    var localIconPath by rememberSaveable { mutableStateOf(sharedApkInfo?.iconFilePath) }
    var submitting by remember { mutableStateOf(false) }

    val categories = listOf("其他", "开源软件", "实用工具", "生活便利", "影音娱乐", "玩机工具", "社交", "金融理财", "网页")
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showIconSheet by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val imageFile = copyUriToCache(context, uri, "resource_icon", ".png")
        if (imageFile == null) {
            Toast.makeText(context, "读取图片失败", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        localIconPath = imageFile.absolutePath
        iconUrl = ""
        iconPreview = imageFile.toUri().toString()
    }

    BackHandler { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出") },
            text = { Text("确定退出吗？当前填写内容不会保存。") },
            confirmButton = { TextButton(onClick = onFinish) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资源投稿") },
                navigationIcon = {
                    FilledTonalIconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (submitting) return@ExtendedFloatingActionButton
                    if (token.isNullOrBlank()) {
                        Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                        return@ExtendedFloatingActionButton
                    }
                    if (name.isBlank() || packageName.isBlank() || version.isBlank() || size.isBlank() || downloadUrl.isBlank()) {
                        Toast.makeText(context, "请填写完整", Toast.LENGTH_SHORT).show()
                        return@ExtendedFloatingActionButton
                    }

                    coroutineScope.launch {
                        submitting = true

                        val finalIconUrl = when {
                            iconUrl.isNotBlank() -> iconUrl
                            !localIconPath.isNullOrBlank() -> {
                                uploadImageFile(localIconPath!!, token, 3) { }
                            }
                            else -> ""
                        }

                        if (!localIconPath.isNullOrBlank() && finalIconUrl.isNullOrBlank()) {
                            submitting = false
                            Toast.makeText(context, "图标上传失败", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val success = submitResource(
                            client = client,
                            token = token,
                            name = name,
                            packageName = packageName,
                            version = version,
                            categoryId = selectedIndex + 1,
                            downloadUrl = downloadUrl,
                            size = size,
                            iconUrl = finalIconUrl.orEmpty()
                        )

                        submitting = false
                        Toast.makeText(
                            context,
                            if (success) "投稿成功" else "投稿失败",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (success) onFinish()
                    }
                },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                text = { Text(if (submitting) "投稿中..." else "确认投稿") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(15.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = if (iconPreview.isNotBlank()) {
                        rememberAsyncImagePainter(iconPreview)
                    } else {
                        painterResource(R.drawable.resource)
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .clickable { showIconSheet = true },
                    contentScale = ContentScale.Crop,
                    colorFilter = if (iconPreview.isBlank()) {
                        ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                )
                Spacer(modifier = Modifier.width(15.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("资源名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = categories[selectedIndex],
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("资源类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEachIndexed { index, category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedIndex = index
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("包名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("版本号") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("资源大小") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = downloadUrl,
                onValueChange = { downloadUrl = it },
                label = { Text("资源链接") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }

    if (showIconSheet) {
        ModalBottomSheet(onDismissRequest = { showIconSheet = false }) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                ListItem(
                    headlineContent = { Text("选择图片") },
                    leadingContent = { Icon(Icons.Default.Photo, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showIconSheet = false
                        photoPicker.launch("image/*")
                    }
                )
                ListItem(
                    headlineContent = { Text("填写图床链接") },
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showIconSheet = false
                        showUrlDialog = true
                    }
                )
            }
        }
    }

    if (showUrlDialog) {
        var tempUrl by remember { mutableStateOf(iconUrl.ifBlank { iconPreview }) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("图标链接") },
            text = {
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("输入链接") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        iconUrl = tempUrl
                        iconPreview = tempUrl
                        localIconPath = null
                        showUrlDialog = false
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }
}

private suspend fun submitResource(
    client: OkHttpClient,
    token: String,
    name: String,
    packageName: String,
    version: String,
    categoryId: Int,
    downloadUrl: String,
    size: String,
    iconUrl: String
): Boolean = withContext(Dispatchers.IO) {
    val requestBody = buildJsonObject {
        put("name", name)
        put("package_name", packageName)
        put("version", version)
        put("category_id", categoryId)
        put("download_url", downloadUrl)
        put("size", size)
        put("icon_url", iconUrl.ifBlank { "drawable/archive_blue.png" })
    }.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(ApiAddress + "upload_resource")
        .addHeader("x-access-token", token)
        .post(requestBody)
        .build()

    return@withContext try {
        client.newCall(request).execute().use { response -> response.isSuccessful }
    } catch (_: IOException) {
        false
    }
}

private fun copyUriToCache(
    context: Context,
    uri: Uri,
    prefix: String,
    fallbackSuffix: String
): File? {
    return try {
        val originalName = queryDisplayName(context, uri)
        val suffix = originalName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            ?: fallbackSuffix
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}$suffix")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: return null

        file
    } catch (_: IOException) {
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
}

private fun saveDrawableToCache(context: Context, drawable: Drawable, fileName: String): File? {
    return try {
        val bitmap = drawable.toBitmap()
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        file
    } catch (_: IOException) {
        null
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }

    val bitmap = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
