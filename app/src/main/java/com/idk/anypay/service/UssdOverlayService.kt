package com.idk.anypay.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Floating overlay service that covers USSD dialogs with a beautiful custom UI
 * This makes the USSD experience seamless by hiding the ugly system dialogs
 */
class UssdOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private val messageState = mutableStateOf("Connecting to USSD...")
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
        
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    
    companion object {
        private const val TAG = "UssdOverlay"
        private var serviceInstance: UssdOverlayService? = null
        
        fun start(context: Context, initialMessage: String = "Connecting to USSD...") {
            Log.d(TAG, "Starting overlay service")
            val intent = Intent(context, UssdOverlayService::class.java).apply {
                putExtra("message", initialMessage)
            }
            context.startService(intent)
        }
        
        fun updateMessage(message: String) {
            Log.d(TAG, "Updating overlay message: $message")
            serviceInstance?.messageState?.value = message
        }
        
        fun stop(context: Context) {
            Log.d(TAG, "Stopping overlay service")
            context.stopService(Intent(context, UssdOverlayService::class.java))
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        createOverlayView()
        Log.d(TAG, "Overlay service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message") ?: "Processing..."
        messageState.value = message
        return START_NOT_STICKY
    }
    
    private fun createOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Create ComposeView
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@UssdOverlayService)
            setViewTreeSavedStateRegistryOwner(this@UssdOverlayService)
            
            setContent {
                UssdOverlayContent(messageState)
            }
        }
        
        // Window parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        windowManager?.addView(overlayView, params)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        serviceInstance = null
        Log.d(TAG, "Overlay service destroyed")
    }
}

@Composable
fun UssdOverlayContent(messageState: State<String>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Processing UPI Transaction",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp)
                )
                
                Text(
                    text = messageState.value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
