/*
 * Copyright (c) 2018, Jason Tsang.(https://github.com/gnastnosaj) All Rights Reserved.
 */

package com.github.gnastnosaj.filter.kaleidoscope.ui.activity

import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.util.Base64
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import com.github.gnastnosaj.boilerplate.Boilerplate
import com.github.gnastnosaj.boilerplate.util.keyboard.BaseActivity
import com.github.gnastnosaj.filter.dsl.core.Connection
import com.github.gnastnosaj.filter.kaleidoscope.Kaleidoscope
import com.github.gnastnosaj.filter.kaleidoscope.R
import com.github.gnastnosaj.filter.kaleidoscope.api.model.Plugin
import com.github.gnastnosaj.filter.kaleidoscope.ui.view.NestedScrollAdblockWebView
import com.just.agentweb.AgentWeb
import com.just.agentweb.BaseIndicatorView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.taobao.android.dexposed.DexposedBridge
import com.taobao.android.dexposed.XC_MethodHook
import org.adblockplus.libadblockplus.android.settings.AdblockHelper
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.themedAppBarLayout
import org.jetbrains.anko.support.v4.swipeRefreshLayout
import timber.log.Timber

class WebViewPageActivity : BaseActivity() {
    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PLUGIN = "plugin"
        const val EXTRA_CONNECTION_HASH_CODE = "connectionHashCode"

        const val DEFAULT_URL = "about:blank"

        const val HOOK_POINT = "console.log('finished injecting css rules');"
        const val HOOK_MAGIC = "//:)injectCSS"

        init {
            //this hook is a candidate
            DexposedBridge.findAndHookMethod(WebView::class.java, "evaluateJavascript", String::class.java, ValueCallback::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Timber.d("beforeHookedMethod: %s", param.method.name)
                    val webView = param.thisObject as? NestedScrollAdblockWebView
                    webView?.injectJS?.let {
                        val injectJS = param.args[0] as String
                        if (injectJS.contains(HOOK_POINT) && !injectJS.contains(HOOK_MAGIC)) {
                            //previous hook may fail
                            it.invoke(param.args[0] as String)
                            param.result = null
                        }
                    }
                }
            })
        }
    }

    private var id: String? = null
    private var plugin: Plugin? = null
    private var connection: Connection? = null

    private var agentWeb: AgentWeb? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        id = intent.getStringExtra(EXTRA_ID)
        title = intent.getStringExtra(EXTRA_TITLE)
        plugin = intent.getParcelableExtra(EXTRA_PLUGIN)
        connection = Kaleidoscope.restoreInstanceState(intent.getIntExtra(EXTRA_CONNECTION_HASH_CODE, -1))
        if (connection == null) {
            savedInstanceState?.apply {
                val hashCode = getInt(EXTRA_CONNECTION_HASH_CODE)
                connection = Kaleidoscope.restoreInstanceState(hashCode)
            }
        }

        frameLayout {
            fitsSystemWindows = true
            coordinatorLayout {
                themedAppBarLayout(R.style.AppTheme_AppBarOverlay) {
                    setSupportActionBar(toolbar {
                        popupTheme = R.style.AppTheme_PopupOverlay
                    }.lparams(matchParent, wrapContent) {
                        scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    })
                    supportActionBar?.apply {
                        setDisplayHomeAsUpEnabled(true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setTaskDescription(ActivityManager.TaskDescription(title.toString(), null, resources.getColor(R.color.colorPrimary)))
                    }
                }.lparams(matchParent, wrapContent)
                swipeRefreshLayout {
                    val webView = NestedScrollAdblockWebView(context)
                    webView.apply {
                        isDebugMode = Boilerplate.DEBUG
                        setProvider(AdblockHelper.get().provider)
                        injectJS = {
                            var script = it
                            url?.let {
                                val injectCSS = connection?.execute("injectCSS", it) as? String
                                injectCSS?.let {
                                    script = script.replace(
                                            HOOK_POINT,
                                            """
                                                $HOOK_MAGIC
                                                style = document.createElement('style');
                                                style.type = 'text/css';
                                                style.innerHTML = window.atob('${Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)}');
                                                head.appendChild(style);
                                                $HOOK_POINT
                                            """
                                    )
                                }
                            }
                            Timber.d("injectJS: %s", script)
                            agentWeb?.jsAccessEntrace?.callJs(script)
                        }
                        settings.userAgentString = "${settings.userAgentString} SearchCraft/2.6.2 (Baidu; P1 7.0)"
                        settings.setAppCacheEnabled(true)
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportZoom(true)
                        settings.loadWithOverviewMode = true
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                    frameLayout {
                        val progressBar = horizontalProgressBar(R.style.Widget_AppCompat_ProgressBar_Horizontal) {
                            scaleY = 0.5f
                            visibility = View.GONE
                        }.lparams(matchParent, wrapContent) {
                            topMargin = -dip(7)
                        }
                        connection?.let {
                            agentWeb = AgentWeb.with(this@WebViewPageActivity)
                                    .setAgentWebParent(this, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                                    .setCustomIndicator(object : BaseIndicatorView(context) {
                                        override fun offerLayoutParams(): LayoutParams {
                                            return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                                        }

                                        override fun reset() {
                                            progressBar.progress = 0
                                        }

                                        override fun show() {
                                            progressBar.visibility = View.VISIBLE
                                        }

                                        override fun hide() {
                                            progressBar.visibility = View.GONE
                                        }

                                        override fun setProgress(newProgress: Int) {
                                            progressBar.progress = newProgress
                                        }
                                    })
                                    .setWebView(webView)
                                    .setWebViewClient(object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            if (url == it.url || url == DEFAULT_URL) {
                                                view?.clearHistory()
                                            }
                                        }
                                    })
                                    .setWebChromeClient(object : WebChromeClient() {
                                        override fun onReceivedTitle(view: WebView, title: String) {
                                            this@WebViewPageActivity.title = title
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                setTaskDescription(ActivityManager.TaskDescription(title, null, resources.getColor(R.color.colorPrimary)))
                                            }
                                        }
                                    })
                                    .createAgentWeb()
                                    .ready()
                                    .go(it.url ?: DEFAULT_URL)
                        }
                    }
                    setOnRefreshListener {
                        webView.reload()
                        isRefreshing = false
                    }
                }.lparams(matchParent, matchParent) {
                    behavior = AppBarLayout.ScrollingViewBehavior()
                }
            }.lparams(matchParent, matchParent)
        }
    }

    override fun onResume() {
        super.onResume()
        agentWeb?.webLifeCycle?.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        menu?.findItem(R.id.action_home)?.icon = IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_circle_outline)
                .color(Color.WHITE).sizeDp(14)
        menu?.findItem(R.id.action_close)?.icon = IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_window_close)
                .color(Color.WHITE).sizeDp(14)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_home -> {
                agentWeb?.webCreator?.webView?.apply {
                    loadUrl(connection?.url ?: DEFAULT_URL)
                }
                true
            }
            R.id.action_close -> {
                finish()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (agentWeb?.handleKeyEvent(keyCode, event) == true) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (agentWeb?.back() == true) {
            return
        }
        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        connection?.let {
            outState?.putInt(DetailActivity.EXTRA_CONNECTION_HASH_CODE, Kaleidoscope.saveInstanceState(it))
        }
    }

    override fun onPause() {
        agentWeb?.webLifeCycle?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        agentWeb?.webLifeCycle?.onDestroy()
        super.onDestroy()
    }
}