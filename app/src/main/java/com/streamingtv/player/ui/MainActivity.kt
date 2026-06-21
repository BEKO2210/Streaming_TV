package com.streamingtv.player.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.streamingtv.player.R

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
