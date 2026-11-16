
package com.example.helloandroid

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "Hello World!\n\nMy First Android App"
            textSize = 26f
            gravity = Gravity.CENTER
        }

        setContentView(textView)
    }
}
