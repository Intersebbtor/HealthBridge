package com.healthbridge.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthBridgeTheme(isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Health Connect Permissions",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "This app needs access to your health data to sync it with your desktop dashboard. HealthBridge reads your steps and heart rate and sends them only to your own Mac on your local network.",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                }
            }
        }
    }
}
