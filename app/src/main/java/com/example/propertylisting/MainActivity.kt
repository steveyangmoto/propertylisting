package com.example.propertylisting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.propertylisting.ui.SearchRoute
import com.example.propertylisting.ui.theme.PropertyListingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PropertyListingTheme {
                SearchRoute()
            }
        }
    }
}
