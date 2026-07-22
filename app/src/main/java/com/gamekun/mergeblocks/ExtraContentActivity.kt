package com.gamekun.mergeblocks

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Extra content: how to play, traps, boosters and credits (icon attribution). */
class ExtraContentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extra)
        findViewById<TextView>(R.id.headerTitle).setText(R.string.extra_title)
        findViewById<View>(R.id.headerBack).setOnClickListener { finish() }
    }
}
