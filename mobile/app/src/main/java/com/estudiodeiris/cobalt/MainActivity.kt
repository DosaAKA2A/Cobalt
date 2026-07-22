package com.estudiodeiris.cobalt

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private val HOME = "file:///android_asset/hub.html"

    private val prefs by lazy { getSharedPreferences("cobalt", Context.MODE_PRIVATE) }
    private var adblock = true

    private val adHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagservices.com", "2mdn.net", "adnxs.com", "adsafeprotected.com",
        "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "pubmatic.com",
        "rubiconproject.com", "openx.net", "scorecardresearch.com", "quantserve.com", "zedo.com",
        "popads.net", "propellerads.com", "adroll.com", "moatads.com", "adform.net", "smartadserver.com",
        "teads.tv", "exoclick.com", "doubleverify.com", "applovin.com", "mopub.com", "inmobi.com",
        "mgid.com", "revcontent.com", "casalemedia.com", "adsrvr.org", "hotjar.com", "mouseflow.com"
    )
    private val ytAdPaths = listOf("/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webview)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        adblock = prefs.getBoolean("adblock", true)

        setupWebView()

        findViewById<Button>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<Button>(R.id.btnFwd).setOnClickListener { if (web.canGoForward()) web.goForward() }
        findViewById<Button>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { showMenu(it) }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                go(urlBar.text.toString()); true
            } else false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        val data = intent?.data?.toString()
        web.loadUrl(if (!data.isNullOrBlank()) data else HOME)
    }

    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (adblock) {
                    val host = request.url.host ?: ""
                    val path = request.url.path ?: ""
                    val isAd = adHosts.any { host == it || host.endsWith(".$it") } ||
                            ytAdPaths.any { path.contains(it) }
                    if (isAd) {
                        return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://"))
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (url != null && url != HOME) urlBar.setText(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if ((url ?: "").contains("youtube.com")) {
                    view?.evaluateJavascript(
                        "(function(){if(window.__cbYT)return;window.__cbYT=1;setInterval(function(){try{var p=document.querySelector('.html5-video-player');var v=document.querySelector('video');if(p&&p.classList.contains('ad-showing')&&v){v.muted=true;if(isFinite(v.duration))v.currentTime=v.duration;}var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button');if(b)b.click();}catch(e){}},400);})();",
                        null
                    )
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val req = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    setTitle(name)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                toast("Descargando $name")
            } catch (e: Exception) {
                toast("No se pudo descargar")
            }
        }
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(" ") && t.contains(".") -> "https://$t"
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        }
        web.loadUrl(url)
        hideKeyboard()
        web.requestFocus()
    }

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add("Inicio")
        pm.menu.add("Añadir marcador")
        pm.menu.add("Marcadores")
        pm.menu.add("Contraseñas")
        pm.menu.add(if (adblock) "Bloqueo de anuncios: ON" else "Bloqueo de anuncios: OFF")
        pm.menu.add("Compartir")
        pm.setOnMenuItemClickListener { item ->
            val title = item.title.toString()
            when {
                title == "Inicio" -> web.loadUrl(HOME)
                title == "Añadir marcador" -> addBookmark()
                title == "Marcadores" -> showBookmarks()
                title == "Contraseñas" -> startActivity(Intent(this, PasswordActivity::class.java))
                title.startsWith("Bloqueo") -> {
                    adblock = !adblock
                    prefs.edit().putBoolean("adblock", adblock).apply()
                    toast(if (adblock) "Bloqueador activado" else "Bloqueador desactivado")
                    web.reload()
                }
                title == "Compartir" -> shareUrl()
            }
            true
        }
        pm.show()
    }

    private fun addBookmark() {
        val url = web.url ?: return
        if (url == HOME) { toast("Abre una página primero"); return }
        val set = prefs.getStringSet("bookmarks", emptySet())!!.toMutableSet()
        set.add(url)
        prefs.edit().putStringSet("bookmarks", set).apply()
        toast("Marcador guardado")
    }

    private fun showBookmarks() {
        val urls = prefs.getStringSet("bookmarks", emptySet())!!.toList()
        if (urls.isEmpty()) { toast("No tienes marcadores"); return }
        val labels = urls.map { Uri.parse(it).host ?: it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Marcadores")
            .setItems(labels) { _, i -> web.loadUrl(urls[i]) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun shareUrl() {
        val url = web.url ?: return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(i, "Compartir enlace"))
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
