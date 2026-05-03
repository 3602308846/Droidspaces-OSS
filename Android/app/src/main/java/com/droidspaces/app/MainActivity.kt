package com.droidspaces.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.droidspaces.app.ui.navigation.DroidspacesNavigation
import com.droidspaces.app.ui.theme.DroidspacesTheme
import com.droidspaces.app.ui.theme.rememberThemeState
import com.droidspaces.app.util.ContainerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private var isLoading by mutableStateOf(false)

    // =============== 自动启动容器核心配置 ===============
    // 你指定的配置文件完整路径
    private val CONFIG_FILE_PATH = "/storage/emulated/0/qy/peizhi.txt"
    // 读不到配置/文件不存在时的兜底默认容器名
    private val DEFAULT_CONTAINER_NAME = "arch"
    // 存储权限请求码
    private val STORAGE_PERMISSION_CODE = 1002
    // 防止APP切后台再切回重复执行启动逻辑
    private var isAutoStartExecuted = false
    // 存储权限申请弹窗控制
    private var showStorageRationale by mutableStateOf(false)
    // ===================================================

    // ── POST_NOTIFICATIONS 权限原有逻辑完全保留 ─────────────────
    private var showNotificationRationale by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                showNotificationRationale = true
            }
        }

    // =============== 新增：存储权限申请注册 ===============
    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                showStorageRationale = true
            } else {
                // 权限申请成功，执行自动启动
                autoStartContainerFromConfig()
            }
        }
    // ===================================================

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationRationale = true
                }
                else -> {
                    requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // =============== 新增：存储权限检查&申请方法 ===============
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 安卓11+ 需要申请管理所有文件权限
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            // 安卓10及以下申请普通存储权限
            when {
                checkStoragePermission() -> {
                    // 已经有权限
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    showStorageRationale = true
                }
                else -> {
                    requestStoragePermission.launch(
                        arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
        }
    }
    // ===================================================

    @Composable
    private fun NotificationRationaleDialog() {
        if (showNotificationRationale) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showNotificationRationale = false },
                title = { Text(getString(R.string.notification_permission_title)) },
                text = { Text(getString(R.string.notification_permission_rationale)) },
                confirmButton = {
                    TextButton(onClick = {
                        showNotificationRationale = false
                        if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                            startActivity(intent)
                        }
                    }) {
                        Text(getString(R.string.grant_permission))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotificationRationale = false }) {
                        Text(getString(R.string.i_understand))
                    }
                }
            )
        }
    }

    // =============== 新增：存储权限申请弹窗 ===============
    @Composable
    private fun StorageRationaleDialog() {
        if (showStorageRationale) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showStorageRationale = false },
                title = { Text("存储权限申请") },
                text = { Text("需要读取内部存储的配置文件，才能自动启动指定容器，请授予存储权限") },
                confirmButton = {
                    TextButton(onClick = {
                        showStorageRationale = false
                        requestStoragePermission()
                    }) {
                        Text("授予权限")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStorageRationale = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
    // ===================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate for faster display
        val splashScreen = installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        // Set condition immediately - UI will hide splash when ready
        // Start with false to show UI immediately (content is ready)
        splashScreen.setKeepOnScreenCondition { isLoading }

        // Request POST_NOTIFICATIONS early so TerminalSessionService is never
        // suppressed by Samsung's notification manager before it starts.
        requestNotificationPermission()

        // Render UI immediately - no blocking operations
        setContent {
            ThemeWrapper {
                NotificationRationaleDialog()
                StorageRationaleDialog() // 存储权限弹窗
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DroidspacesNavigation(
                        onContentReady = { 
                            isLoading = false
                            // =============== 核心：APP就绪后执行自动启动 ===============
                            if (!isAutoStartExecuted) {
                                isAutoStartExecuted = true
                                if (checkStoragePermission()) {
                                    autoStartContainerFromConfig()
                                } else {
                                    requestStoragePermission()
                                }
                            }
                            // ===================================================
                        }
                    )
                }
            }
        }
    }

    // =============== 核心：自动启动容器逻辑（100%复用APP原生方法） ===============
    private fun autoStartContainerFromConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configFile = File(CONFIG_FILE_PATH)
                // 读取容器名：文件存在就读第一行，不存在/空就用默认值
                val containerName = if (configFile.exists() && configFile.canRead()) {
                    configFile.readLines().firstOrNull { it.isNotBlank() }?.trim() ?: DEFAULT_CONTAINER_NAME
                } else {
                    DEFAULT_CONTAINER_NAME
                }

                // 切回主线程，调用APP原生启动方法
                withContext(Dispatchers.Main) {
                    // 精准匹配你项目的ContainerManager单例
                    val containerManager = ContainerManager.getInstance(this@MainActivity)
                    
                    // 先判断容器是否已经运行，避免重复启动
                    if (!containerManager.isContainerRunning(containerName)) {
                        // 调用APP原生启动方法，和手动点APP启动按钮完全一致
                        // 绝对不会触发Termux桥接、不会执行CLI二进制
                        containerManager.startContainer(containerName)
                    }
                }
            } catch (e: Exception) {
                // 异常兜底：启动默认容器，不影响APP正常使用
                withContext(Dispatchers.Main) {
                    try {
                        val containerManager = ContainerManager.getInstance(this@MainActivity)
                        if (!containerManager.isContainerRunning(DEFAULT_CONTAINER_NAME)) {
                            containerManager.startContainer(DEFAULT_CONTAINER_NAME)
                        }
                    } catch (e: Exception) {
                        // 静默处理异常，不崩溃APP
                    }
                }
            }
        }
    }
    // ===================================================
}

@Composable
private fun ThemeWrapper(content: @Composable () -> Unit) {
    // Use reactive theme state that updates instantly when preferences change
    val themeState = rememberThemeState()

    DroidspacesTheme(
        darkTheme = themeState.darkTheme,
        dynamicColor = themeState.useDynamicColor,
        amoledMode = themeState.amoledMode,
        themePalette = themeState.themePalette
    ) {
        content()
    }
}
