package com.yunmei.client.ui.screen.scan

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.yunmei.client.R
import com.yunmei.client.ui.LocalUiMode
import com.yunmei.client.ui.UiMode
import com.yunmei.client.ui.navigation3.LocalNavigator
import com.yunmei.client.ui.util.rememberPermissionRequester
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

const val SCAN_RESULT_KEY = "scan_result"

@Composable
fun ScanScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var decoded by remember { mutableStateOf<String?>(null) }

    val cameraRequester = rememberPermissionRequester(
        permissions = arrayOf(Manifest.permission.CAMERA),
        onGranted = { cameraGranted = true },
        onDenied = {
            Toast.makeText(context, R.string.scan_permission_denied, Toast.LENGTH_LONG).show()
            navigator.pop()
        },
    )
    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            cameraRequester()
        }
    }

    // 识别成功：回传内容并返回上一页（Navigator.setResult 会自动 pop）。
    LaunchedEffect(decoded) {
        decoded?.let { raw ->
            navigator.setResult(SCAN_RESULT_KEY, raw)
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ScanScreenMiuix(
            onBack = { navigator.pop() },
            cameraGranted = cameraGranted,
            onDecoded = { decoded = it },
        )

        UiMode.Material -> ScanScreenMaterial(
            onBack = { navigator.pop() },
            cameraGranted = cameraGranted,
            onDecoded = { decoded = it },
        )
    }
}

@Composable
private fun ScanScreenMaterial(
    onBack: () -> Unit,
    cameraGranted: Boolean,
    onDecoded: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.lock_detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BarcodeViewport(
                modifier = Modifier.weight(1f),
                cameraGranted = cameraGranted,
                onDecoded = onDecoded,
            )
            Text(
                text = stringResource(R.string.scan_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ScanScreenMiuix(
    onBack: () -> Unit,
    cameraGranted: Boolean,
    onDecoded: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.scan_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        MiuixIcon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.lock_detail_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = { },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BarcodeViewport(
                modifier = Modifier.weight(1f),
                cameraGranted = cameraGranted,
                onDecoded = onDecoded,
            )
            top.yukonga.miuix.kmp.basic.Text(
                text = stringResource(R.string.scan_hint),
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun BarcodeViewport(
    modifier: Modifier = Modifier,
    cameraGranted: Boolean,
    onDecoded: (String) -> Unit,
) {
    if (!cameraGranted) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.scan_permission_denied))
        }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                val callback = object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        val text = result.text
                        if (text != null) {
                            onDecoded(text)
                        }
                    }

                    override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint>) = Unit
                }
                decodeContinuous(callback)
                cameraSettings.isAutoFocusEnabled = true
                resume()
            }
        },
        onRelease = { view ->
            view.pause()
        },
    )
}
