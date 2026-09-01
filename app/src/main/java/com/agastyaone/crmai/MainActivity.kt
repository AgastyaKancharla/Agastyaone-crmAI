package com.agastyaone.crmai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.agastyaone.crmai.ui.AgastyaOneRoot
import com.agastyaone.crmai.ui.theme.AgastyaOneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgastyaOneTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgastyaOneRoot()
                }
            }
        }
    }
}
