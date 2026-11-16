package com.kamusisyarat.offline
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.net.Uri
import android.content.Intent
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20,83,45); window.navigationBarColor = Color.rgb(243,247,244)
        root=FrameLayout(this); root.setBackgroundColor(Color.rgb(243,247,244)); webView=WebView(this)
        root.addView(webView,FrameLayout.LayoutParams(-1,-1)); setContentView(root); applyInsets(); configureWebView()
        if(savedInstanceState==null) webView.loadUrl("https://appassets.androidplatform.net/assets/index.html") else webView.restoreState(savedInstanceState)
    }
    private fun applyInsets(){ root.setOnApplyWindowInsetsListener{_,i->val b=i.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars());val p=webView.layoutParams as FrameLayout.LayoutParams;p.topMargin=b.top;p.bottomMargin=b.bottom;webView.layoutParams=p;i};root.requestApplyInsets() }
    private fun configureWebView(){
        val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(this)).addPathHandler("/offline/",WebViewAssetLoader.InternalStoragePathHandler(this,File(filesDir,"offline"))).build()
        val s=webView.settings;s.javaScriptEnabled=true;s.domStorageEnabled=true;s.databaseEnabled=true;s.mediaPlaybackRequiresUserGesture=false;s.allowFileAccess=false;s.allowContentAccess=true;s.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.webViewClient=object:WebViewClientCompat(){
            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest)=loader.shouldInterceptRequest(r.url)
            override fun shouldInterceptRequest(v:WebView,u:String)=loader.shouldInterceptRequest(Uri.parse(u))
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean=if(r.url.host=="appassets.androidplatform.net")false else {try{startActivity(Intent(Intent.ACTION_VIEW,r.url))}catch(_:Exception){};true}
        }
        webView.webChromeClient=object:WebChromeClient(){
            override fun onShowCustomView(view:View,cb:CustomViewCallback){if(fullscreenView!=null){cb.onCustomViewHidden();return};fullscreenView=view;fullscreenCallback=cb;root.addView(view,FrameLayout.LayoutParams(-1,-1));webView.visibility=View.GONE;hideBars()}
            override fun onHideCustomView(){hideFullscreen()}
            override fun onShowFileChooser(w:WebView,cb:ValueCallback<Array<Uri>>,p:FileChooserParams):Boolean{uploadCallback?.onReceiveValue(null);uploadCallback=cb;return try{startActivityForResult(p.createIntent(),1001);true}catch(_:Exception){uploadCallback=null;false}}
        }
        webView.addJavascriptInterface(OfflineBridge(this,webView),"Android")
    }
    private fun hideBars(){if(android.os.Build.VERSION.SDK_INT>=30)window.insetsController?.let{it.hide(WindowInsets.Type.systemBars());it.systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}else{@Suppress("DEPRECATION") window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY}}
    private fun showBars(){if(android.os.Build.VERSION.SDK_INT>=30)window.insetsController?.show(WindowInsets.Type.systemBars())else{@Suppress("DEPRECATION") window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_VISIBLE}}
    private fun hideFullscreen(){fullscreenCallback?.onCustomViewHidden();fullscreenCallback=null;fullscreenView?.let{root.removeView(it)};fullscreenView=null;webView.visibility=View.VISIBLE;showBars();root.requestApplyInsets()}
    override fun onBackPressed(){if(fullscreenView!=null)hideFullscreen()else if(webView.canGoBack())webView.goBack()else super.onBackPressed()}
    override fun onSaveInstanceState(outState:Bundle){webView.saveState(outState);super.onSaveInstanceState(outState)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==1001){val result=if(resultCode==RESULT_OK&&data?.data!=null)arrayOf(data.data!!)else null;uploadCallback?.onReceiveValue(result);uploadCallback=null}}
    override fun onDestroy(){if(fullscreenView!=null)hideFullscreen();webView.destroy();super.onDestroy()}
}
