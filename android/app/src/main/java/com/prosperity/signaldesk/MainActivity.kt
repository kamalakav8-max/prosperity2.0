package com.prosperity.signaldesk

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 18, 32)
        window.navigationBarColor = Color.rgb(11, 18, 32)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 12)
            setBackgroundColor(Color.rgb(11, 18, 32))
        }
        val title = TextView(this).apply {
            text = "Prosperity Signal Desk"
            textSize = 22f
            setTextColor(Color.WHITE)
        }
        val help = TextView(this).apply {
            text = "Connect to the Windows bridge on the same Wi-Fi."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 12)
        }
        address = EditText(this).apply {
            hint = "http://192.168.1.10:8765"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(27, 39, 57))
            setPadding(14, 10, 14, 10)
        }
        val load = Button(this).apply {
            text = "Connect to Prosperity"
            setOnClickListener { openDashboard() }
        }
        status = TextView(this).apply {
            text = "Enter the Windows PC address printed by the bridge."
            setTextColor(Color.rgb(147, 197, 253))
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        root.addView(title)
        root.addView(help)
        root.addView(address, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(load)
        root.addView(status)
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun openDashboard() {
        val url = address.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            status.text = "Enter a full address beginning with http://"
            return
        }
        status.text = "Opening dashboard…"
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
