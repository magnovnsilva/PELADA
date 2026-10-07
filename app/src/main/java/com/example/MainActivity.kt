package com.example

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.widget.Toast
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class MainActivity : ComponentActivity() {

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ ->
    // Result handled
  }

  fun requestPostNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestPostNotificationPermission()

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color(0xFF091836)
        ) {
          SituacoesApp(activity = this)
        }
      }
    }
  }
}

class AndroidBridge(private val activity: MainActivity) {

  @JavascriptInterface
  fun requestNotificationPermission() {
    activity.runOnUiThread {
      activity.requestPostNotificationPermission()
    }
  }

  @JavascriptInterface
  fun triggerTestNotification(title: String, message: String) {
    activity.runOnUiThread {
      activity.requestPostNotificationPermission()
      MatchReminderReceiver.showNotification(activity, title, message)
    }
  }

  @JavascriptInterface
  fun scheduleReminder(
    enabled: Boolean,
    dayOfWeek: Int,
    hour: Int,
    minute: Int,
    title: String,
    message: String
  ) {
    activity.runOnUiThread {
      val alarmManager =
        activity.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@runOnUiThread
      val intent = Intent(activity, MatchReminderReceiver::class.java).apply {
        putExtra(MatchReminderReceiver.EXTRA_TITLE, title)
        putExtra(MatchReminderReceiver.EXTRA_MESSAGE, message)
      }
      val pendingIntent = PendingIntent.getBroadcast(
        activity,
        2001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      if (!enabled) {
        alarmManager.cancel(pendingIntent)
        return@runOnUiThread
      }

      val calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, dayOfWeek)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(Calendar.getInstance())) {
          add(Calendar.WEEK_OF_YEAR, 1)
        }
      }

      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
          )
        } else {
          alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
          )
        }
      } catch (e: Exception) {
        alarmManager.set(
          AlarmManager.RTC_WAKEUP,
          calendar.timeInMillis,
          pendingIntent
        )
      }
    }
  }

  @JavascriptInterface
  fun downloadHtmlFile(htmlContent: String, fileName: String) {
    activity.runOnUiThread {
      try {
        val exportDir = File(activity.cacheDir, "exports").apply { mkdirs() }
        val targetName = if (fileName.endsWith(".html", ignoreCase = true)) fileName else "$fileName.html"
        val exportFile = File(exportDir, targetName)
        
        FileOutputStream(exportFile).use { output ->
          output.write(htmlContent.toByteArray(Charsets.UTF_8))
        }

        val contentUri: Uri = FileProvider.getUriForFile(
          activity,
          "${activity.packageName}.fileprovider",
          exportFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/html"
          putExtra(Intent.EXTRA_STREAM, contentUri)
          putExtra(Intent.EXTRA_SUBJECT, "Sideral F.C. • Arquivo do Aplicativo")
          putExtra(Intent.EXTRA_TEXT, "Segue em anexo o arquivo HTML do Sideral F.C. com os dados da rodada para abrir em qualquer navegador!")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Salvar ou compartilhar arquivo HTML")
        activity.startActivity(chooser)
        Toast.makeText(activity, "Arquivo HTML pronto para salvar ou compartilhar!", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(activity, "Erro ao exportar HTML: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SituacoesApp(activity: MainActivity) {
  val context = LocalContext.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isAppLoaded by remember { mutableStateOf(false) }

  // Custom back navigation handling for single-page hash routes or web history
  BackHandler(enabled = true) {
    val wv = webViewRef
    if (wv != null) {
      val currentUrl = wv.url ?: ""
      if (currentUrl.contains("#equipes") || currentUrl.contains("#sorteio") || currentUrl.contains("#placar") || currentUrl.contains("#cronometro") || currentUrl.contains("#financeiro") || currentUrl.contains("#lista-espera")) {
        wv.loadUrl("javascript:window.location.hash='#escalacao';")
      } else if (wv.canGoBack()) {
        wv.goBack()
      } else {
        (context as? ComponentActivity)?.finish()
      }
    } else {
      (context as? ComponentActivity)?.finish()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .systemBarsPadding()
      .testTag("situacoes_main_container")
  ) {
    AndroidView(
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )

          setBackgroundColor(android.graphics.Color.parseColor("#F2F5FA"))

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            displayZoomControls = false
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          }

          addJavascriptInterface(AndroidBridge(activity), "AndroidBridge")

          webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
              view: WebView?,
              url: String?,
              message: String?,
              result: JsResult?
            ): Boolean {
              AlertDialog.Builder(ctx)
                .setTitle("Sideral F.C.")
                .setMessage(message ?: "")
                .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                .setOnCancelListener { result?.cancel() }
                .show()
              return true
            }

            override fun onJsConfirm(
              view: WebView?,
              url: String?,
              message: String?,
              result: JsResult?
            ): Boolean {
              AlertDialog.Builder(ctx)
                .setTitle("Sideral F.C.")
                .setMessage(message ?: "")
                .setPositiveButton("Confirmar") { _, _ -> result?.confirm() }
                .setNegativeButton("Cancelar") { _, _ -> result?.cancel() }
                .setOnCancelListener { result?.cancel() }
                .show()
              return true
            }
          }

          webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              isAppLoaded = true
            }

            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?
            ): Boolean {
              val uri = request?.url ?: return false
              val scheme = uri.scheme ?: ""
              if (scheme == "file") {
                return false
              }
              return try {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                ctx.startActivity(intent)
                true
              } catch (e: Exception) {
                false
              }
            }
          }

          loadUrl("file:///android_asset/www/index.html")
          webViewRef = this
        }
      },
      update = {
        webViewRef = it
      },
      modifier = Modifier
        .fillMaxSize()
        .testTag("situacoes_webview")
    )

    // Animated splash screen overlay during initial boot
    AnimatedVisibility(
      visible = !isAppLoaded,
      exit = fadeOut(animationSpec = tween(durationMillis = 400)),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF091836)),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Image(
            painter = painterResource(id = R.drawable.splash_logo),
            contentDescription = stringResource(id = R.string.app_name),
            modifier = Modifier
              .size(150.dp)
              .clip(RoundedCornerShape(26.dp))
          )

          Spacer(modifier = Modifier.height(24.dp))

          Text(
            text = "SIDERAL F.C.",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "SITUAÇÕES • TRADIÇÃO & RESENHA",
            color = Color(0xFFA6BEDF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
          )

          Spacer(modifier = Modifier.height(32.dp))

          CircularProgressIndicator(
            color = Color(0xFFD31D28),
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
          )
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.destroy()
      webViewRef = null
    }
  }
}
