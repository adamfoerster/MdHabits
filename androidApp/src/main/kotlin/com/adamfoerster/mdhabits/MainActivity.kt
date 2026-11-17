package com.adamfoerster.mdhabits

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import com.adamfoerster.mdhabits.di.initKoinOnce
import com.adamfoerster.mdhabits.platform.AndroidHealthBridge
import com.adamfoerster.mdhabits.platform.AndroidNotificationBridge
import com.adamfoerster.mdhabits.platform.AndroidVaultBridge
import com.adamfoerster.mdhabits.widget.TasksWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        AndroidVaultBridge.onTreePicked(uri)
    }

    private val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            AndroidHealthBridge.onPermissionsResult(granted)
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AndroidNotificationBridge.onPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidVaultBridge.appContext = applicationContext
        AndroidVaultBridge.launchTreePicker = { pickFolder.launch(null) }
        AndroidHealthBridge.launchPermissionRequest = { requestHealthPermissions.launch(it) }
        AndroidNotificationBridge.launchPermissionRequest = {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        initKoinOnce()

        setContent {
            App()
        }
    }

    override fun onStop() {
        super.onStop()
        // Whatever was checked off in the app shows on the home-screen widget once the user leaves.
        val context = applicationContext
        CoroutineScope(Dispatchers.Default).launch { runCatching { TasksWidget.refresh(context) } }
    }
}
