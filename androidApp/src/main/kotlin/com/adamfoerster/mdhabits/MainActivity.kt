package com.adamfoerster.mdhabits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import com.adamfoerster.mdhabits.di.initKoinOnce
import com.adamfoerster.mdhabits.platform.AndroidHealthBridge
import com.adamfoerster.mdhabits.platform.AndroidVaultBridge

class MainActivity : ComponentActivity() {

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        AndroidVaultBridge.onTreePicked(uri)
    }

    private val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            AndroidHealthBridge.onPermissionsResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidVaultBridge.appContext = applicationContext
        AndroidVaultBridge.launchTreePicker = { pickFolder.launch(null) }
        AndroidHealthBridge.launchPermissionRequest = { requestHealthPermissions.launch(it) }
        initKoinOnce()

        setContent {
            App()
        }
    }
}
