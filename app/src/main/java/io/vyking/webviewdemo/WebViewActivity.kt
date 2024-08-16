package io.vyking.webviewdemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.vyking.webviewdemo.databinding.ActivityWebviewBinding
import java.net.URL

class WebViewActivity : AppCompatActivity() {
    private val tag: (String) -> String = { it -> "${this.javaClass.name}.$it" }

    private lateinit var binding: ActivityWebviewBinding
    private var vykingWebView: WebView? = null

    private val key = "io.vyking"
    private val config = "../assets/config/modeld.foot.bin"

    private val vykingApparelUrl = "https://sneaker-window.vyking.io/vyking-examples/with-service-worker/examples/in-app-vyking-apparel-camera.html"
    private val modelViewerUrl   = "https://sneaker-window.vyking.io/vyking-examples/with-service-worker/examples/in-app-model-viewer.html"

    enum class ViewMode {
        vykingApparel,
        modelViewer
    }

    private var viewMode: ViewMode = ViewMode.modelViewer

    private var shoeSelector = 0
    private val shoeList = listOf(
            listOf(
                    "Yeezy Boost 700 Carbon Blue",
                    "https://sneaker-window.vyking.io/vyking-assets/customer/vyking-io/yeezy_boost_700_carbon_blue/offsets.json"
            ),
            listOf(
                    "Adidas GY1121",
                    "https://sneaker-window.vyking.io/vyking-assets/customer/vyking-io/adidas_GY1121/offsets.json"
            ),
            listOf(
                    "Air Jordon 1 Turbo Green",
                    "https://sneaker-window.vyking.io/vyking-assets/customer/vyking-io/air_jordan_1_turbo_green/offsets.json"
            ),
            listOf(
                    "Jordon Off-white",
                    "https://sneaker-window.vyking.io/vyking-assets/customer/vyking-io/jordan_off_white_chicago/offsets.json"
            ),
            listOf(
                    "Monte Runner",
                    "https://sneaker-window.vyking.io/vyking-assets/customer/vyking-io/H209A4M00080M2056P04_Monte_Runner_Trainers/offsets.json"
            )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = BuildConfig.VERSION_NAME
        Log.i(tag("onCreate"), "Version: $versionName")

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        with(binding) {
            setContentView(root)

            nextShoe.apply {
                setOnClickListener {
                    shoeSelector = (shoeSelector + 1) % shoeList.size
                    vykingReplaceApparel(shoeList[shoeSelector][1], shoeList[shoeSelector][0]) {
                        Log.d(tag("nextShoe"), "${shoeList[shoeSelector][1]}")
                    }
                }
            }

            viewModeToggle.apply {
                setOnClickListener {
                    viewMode = when (viewMode) {
                        ViewMode.vykingApparel -> ViewMode.modelViewer
                        ViewMode.modelViewer -> ViewMode.vykingApparel
                    }

                    removeVykingWebView()
                    addVykingWebView()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
        ) {
            addVykingWebView()
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    MY_PERMISSIONS_REQUEST_USE_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            MY_PERMISSIONS_REQUEST_USE_CAMERA -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addVykingWebView()
            } else {
                Toast.makeText(
                        this,
                        "Camera permission is required.",
                        Toast.LENGTH_LONG
                ).apply {
                    show()
                }
            }
        }
    }

    private fun addVykingWebView() {
        vykingWebView = WebView(this).apply {
            this.setBackgroundColor(Color.TRANSPARENT)
            this.settings.javaScriptEnabled = true
            this.settings.mediaPlaybackRequiresUserGesture = false
            this.settings.cacheMode = WebSettings.LOAD_DEFAULT

            this.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.resources)
                    }
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(
                            "WEB-${message.messageLevel()}", "${message.message()} -- From line " +
                            "${message.lineNumber()} of ${message.sourceId()}"
                    )
                    return true
                }
            }

            this.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(tag("addVykingWebView.onPageFinished"), "onPageFinished")

                    vykingConfigure(config, key) {
                        vykingReplaceApparel(shoeList[shoeSelector][1], shoeList[shoeSelector][0], null)
                    }
                }

                override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler,
                        error: SslError
                ) {
                    Log.d(tag("addVykingWebView.onReceivedSslError"), " ")

                    val serverCertificate = error.certificate
                    val certDomain = serverCertificate.issuedTo.cName

                    Log.e(tag("addVykingWebView.onReceivedSslError"), "certDomain $certDomain, error $error")

                    when (error.primaryError) {
                        SslError.SSL_UNTRUSTED -> {
                            Log.e(
                                    "addVykingWebView.onReceivedSslError",
                                    "SslError : The certificate authority is not trusted."
                            )

                            // Typically, development web servers also use self-signed certificates so we need to handle these in a special way too.
                            when (URL(error.url).host) {
                                "localhost" -> handler.proceed()
                                "127.0.0.1" -> handler.proceed()
                                "192.168.0.20" -> handler.proceed()
                                else -> handler.cancel()
                            }
                        }
                        else -> handler.cancel()
                    }
                }
            }

            try {
                Log.d(tag("addVykingWebView"), "url. $modelViewerUrl")

                val url = when (viewMode) {
                    ViewMode.vykingApparel -> vykingApparelUrl
                    ViewMode.modelViewer -> modelViewerUrl
                }

                this.loadUrl(url)
            } catch (cause: Exception) {
                Log.e(tag("addVykingWebView"), "Failed to initialise WebView. $cause")
            }
        }

        with(binding) {
            container.apply {
                val layout = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                layout.startToStart = getId()
                layout.topToTop = getId()
                layout.bottomToBottom = getId()
                layout.endToEnd = getId()

                this.addView(vykingWebView, layout)

                val label = when(viewMode) {
                    ViewMode.vykingApparel -> "View Model"
                    ViewMode.modelViewer -> "Try-on"
                }
                binding.viewModeToggle.text = label
            }
        }
    }

    private fun removeVykingWebView() {
        binding.container.apply {
            this.removeView(vykingWebView)
            vykingWebView?.destroy()
            vykingWebView = null
        }
    }

    private fun vykingConfigure(config: String, key: String, resultCallback: ((String) -> Unit)?) {
        vykingWebView?.evaluateJavascript("""
        document.querySelector('vyking-apparel')?.setAttribute('key', '${key}');
        document.querySelector('vyking-apparel')?.setAttribute('config', '${config}');

        document.querySelector('model-viewer')?.setAttribute('vto', true);
        document.querySelector('model-viewer')?.setAttribute('vto-share', true);
        document.querySelector('model-viewer')?.setAttribute('vto-key', '${key}');
        document.querySelector('model-viewer')?.setAttribute('vto-config', '${config}');
      """.trimIndent(), resultCallback)
    }

    private fun vykingReplaceApparel(url: String, name: String, resultCallback: ((String) -> Unit)?) {
        vykingWebView?.evaluateJavascript("""
        document.querySelector('vyking-apparel')?.setAttribute('alt', '${name}');
        document.querySelector('vyking-apparel')?.setAttribute('apparel', '${url}');

        document.querySelector('model-viewer')?.setAttribute('alt', '${name}');
        document.querySelector('model-viewer')?.setAttribute('vyking-src', '${url}');
      """.trimIndent(), resultCallback)
    }

    private fun vykingRemoveApparel(resultCallback: ((String) -> Unit)?) {
        vykingWebView?.evaluateJavascript("""
        document.querySelector('vyking-apparel')?.removeAttribute('alt');
        document.querySelector('vyking-apparel')?.removeAttribute('apparel');

        document.querySelector('model-viewer')?.removeAttribute('alt');
        document.querySelector('model-viewer')?.removeAttribute('vyking-src');
      """.trimIndent(), resultCallback)
    }

    companion object {
        private const val MY_PERMISSIONS_REQUEST_USE_CAMERA = 1
    }
}