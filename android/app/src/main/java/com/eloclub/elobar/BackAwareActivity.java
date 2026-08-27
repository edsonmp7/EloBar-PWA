package com.eloclub.elobar;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class BackAwareActivity extends Activity {
    private static final String START_URL = "https://script.google.com/macros/s/AKfycbyd7UHyQFJA4SsFZuKWmAO___NnfGXq0oNB0M0NWnG2hhLmPHcKTL_ck4yDgB4IqSkOnQ/exec?shell=android-native";
    private static final int FILE_CHOOSER_REQUEST = 1907;
    private static final int BACKGROUND = Color.rgb(18, 18, 18);
    private static final long EXIT_CONFIRM_WINDOW_MS = 2000L;

    private FrameLayout root;
    private WebView webView;
    private View splashView;
    private ValueCallback<Uri[]> pendingFileChooser;
    private boolean backDispatchInFlight;
    private long lastExitBackAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureOrientation();
            createRoot();
            configureWindow();
            createWebView();
            createSplash();
            configureWebView();
            scheduleSystemBarsAndInsets();

            if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(START_URL);
            } else {
                hideSplash(300L);
            }
        } catch (Throwable error) {
            showFatalError(error);
        }
    }

    private void configureOrientation() {
        int smallestWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        if (smallestWidthDp >= 600) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void createRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        applySafeAreaInsets();
    }

    private void applySafeAreaInsets() {
        if (root == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, ime.bottom));
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    private void scheduleSystemBarsAndInsets() {
        if (root == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        root.post(() -> {
            if (root == null || !root.isAttachedToWindow()) return;
            WindowInsetsController controller = root.getWindowInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
            root.requestApplyInsets();
        });
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(BACKGROUND);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void createSplash() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(BACKGROUND);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int logoSize = dp(getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 280 : 220);
        panel.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));

        splashView = panel;
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSplash(long delayMs) {
        if (splashView == null || splashView.getVisibility() != View.VISIBLE) return;
        splashView.postDelayed(() -> {
            if (splashView == null) return;
            splashView.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (splashView != null) splashView.setVisibility(View.GONE);
                    })
                    .start();
        }, delayMs);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new EloBarWebViewClient());
        webView.setWebChromeClient(new EloBarWebChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                openExternal(Uri.parse(url))
        );
    }

    private boolean isInternalUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null) return false;
        return "script.google.com".equalsIgnoreCase(host)
                || "accounts.google.com".equalsIgnoreCase(host)
                || host.endsWith(".googleusercontent.com")
                || host.equalsIgnoreCase("googleusercontent.com");
    }

    private boolean isAppsScriptSurface(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost();
        return "script.google.com".equalsIgnoreCase(host)
                || host.endsWith(".googleusercontent.com")
                || host.equalsIgnoreCase("googleusercontent.com");
    }

    private boolean isGoogleLogin(Uri uri) {
        return uri != null && uri.getHost() != null && "accounts.google.com".equalsIgnoreCase(uri.getHost());
    }

    private void hideAppsScriptWarningBar() {
        if (webView == null) return;
        String js = "(function(){"
                + "function hide(){"
                + "var w=document.getElementById('warning');var t=document.getElementById('warning-text');"
                + "if(w){var r=w.closest&&w.closest('tr');if(r)r.style.display='none';else w.style.display='none';}"
                + "if(t){var s=(t.textContent||'').toLowerCase();if(s.indexOf('google apps script')>=0){var p=t.closest&&t.closest('tr');if(p)p.style.display='none';}}"
                + "}hide();try{new MutationObserver(hide).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                + "setTimeout(hide,150);setTimeout(hide,600);setTimeout(hide,1600);})();";
        webView.evaluateJavascript(js, null);
    }

    private void installNativeBackBridge() {
        if (webView == null) return;
        webView.evaluateJavascript(nativeBackBridgeScript(), null);
    }

    private String nativeBackBridgeScript() {
        return """
                (function(){
                  if(window.__eloAndroidBackInstalled)return;
                  window.__eloAndroidBackInstalled=true;
                  var tabStack=[];
                  var accountStack=[];
                  var restoring=false;

                  function visible(el){
                    if(!el||!el.isConnected)return false;
                    var s=getComputedStyle(el);
                    return s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)!==0&&el.getClientRects().length>0;
                  }
                  function pushUnique(stack,value){
                    if(!value)return;
                    if(stack.length&&stack[stack.length-1]===value)return;
                    stack.push(value);
                    if(stack.length>40)stack.shift();
                  }
                  function currentTab(){
                    try{if(typeof AppState!=='undefined'&&AppState.currentTab)return String(AppState.currentTab);}catch(e){}
                    var active=document.querySelector('[data-tab].active,[data-tab][aria-selected="true"]');
                    return active?String(active.getAttribute('data-tab')||''):'';
                  }
                  function currentAccountTab(){
                    var active=document.querySelector('[data-account-tab].active,[data-account-tab][aria-selected="true"]');
                    return active?String(active.getAttribute('data-account-tab')||''):'';
                  }
                  function clickData(attr,value){
                    var nodes=document.querySelectorAll('['+attr+']');
                    for(var i=0;i<nodes.length;i++){
                      if(String(nodes[i].getAttribute(attr)||'')===String(value||'')){
                        restoring=true;
                        nodes[i].click();
                        setTimeout(function(){restoring=false;},0);
                        return true;
                      }
                    }
                    return false;
                  }
                  function closeTopOverlay(){
                    var settings=document.getElementById('settingsDrawer');
                    if(visible(settings)){
                      var settingsClose=document.getElementById('closeSettingsBtn');
                      if(settingsClose){settingsClose.click();return true;}
                    }
                    var selectors='dialog[open],[aria-modal="true"],.modal,.app-choice-overlay:not(.hidden),.mobile-drawer-layer.open,.drawer.open';
                    var list=Array.prototype.slice.call(document.querySelectorAll(selectors)).filter(visible);
                    if(list.length){
                      list.sort(function(a,b){return (parseInt(getComputedStyle(a).zIndex,10)||0)-(parseInt(getComputedStyle(b).zIndex,10)||0);});
                      var overlay=list[list.length-1];
                      var close=overlay.querySelector('[aria-label="Fechar"],[aria-label^="Fechar "],[title="Fechar"],[data-close],.modal-close,.tech-close,.app-choice-close,#drawerClose,#closeSettingsBtn');
                      if(close&&typeof close.click==='function'){close.click();return true;}
                      if(overlay.tagName==='DIALOG'&&typeof overlay.close==='function'){overlay.close();return true;}
                    }
                    var drawerClose=document.getElementById('drawerClose');
                    if(drawerClose&&visible(drawerClose)){drawerClose.click();return true;}
                    return false;
                  }

                  document.addEventListener('click',function(event){
                    if(restoring)return;
                    var target=event.target&&event.target.closest?event.target:null;
                    var account=target&&target.closest('[data-account-tab]');
                    if(account){
                      var currentAccount=currentAccountTab();
                      var nextAccount=String(account.getAttribute('data-account-tab')||'');
                      if(currentAccount&&nextAccount&&currentAccount!==nextAccount)pushUnique(accountStack,currentAccount);
                      return;
                    }
                    var tab=target&&target.closest('[data-tab]');
                    if(tab){
                      var current=currentTab();
                      var next=String(tab.getAttribute('data-tab')||'');
                      if(current&&next&&current!==next)pushUnique(tabStack,current);
                    }
                  },true);

                  window.__eloAndroidBack=function(){
                    try{
                      if(closeTopOverlay())return true;
                      if(accountStack.length){
                        var accountPrevious=accountStack.pop();
                        if(clickData('data-account-tab',accountPrevious))return true;
                      }
                      if(tabStack.length){
                        var tabPrevious=tabStack.pop();
                        if(clickData('data-tab',tabPrevious))return true;
                      }
                      var route=(location.hash||'#home').replace(/^#/,'');
                      if(route&&route!=='home'){
                        var appBack=document.querySelector('[data-action="back-nav"]');
                        if(appBack&&typeof appBack.click==='function'){appBack.click();return true;}
                        var home=document.querySelector('[data-nav="home"]');
                        if(home&&typeof home.click==='function'){home.click();return true;}
                      }
                      var current=currentTab();
                      if(current&&current!=='Hoje'&&clickData('data-tab','Hoje'))return true;
                      return false;
                    }catch(e){return false;}
                  };
                })();
                """;
    }

    private void dispatchBackToPage() {
        if (backDispatchInFlight) return;
        if (webView == null) {
            confirmExitOrFinish();
            return;
        }
        backDispatchInFlight = true;
        webView.evaluateJavascript(
                "(function(){try{return !!(window.__eloAndroidBack&&window.__eloAndroidBack());}catch(e){return false;}})();",
                value -> {
                    backDispatchInFlight = false;
                    if ("true".equals(value)) return;
                    Uri current = Uri.parse(webView.getUrl() == null ? "" : webView.getUrl());
                    if (!isAppsScriptSurface(current) && webView.canGoBack()) {
                        webView.goBack();
                        return;
                    }
                    confirmExitOrFinish();
                }
        );
    }

    private void confirmExitOrFinish() {
        long now = System.currentTimeMillis();
        if (now - lastExitBackAt <= EXIT_CONFIRM_WINDOW_MS) {
            super.onBackPressed();
            return;
        }
        lastExitBackAt = now;
        Toast.makeText(this, "Pressione novamente para sair", Toast.LENGTH_SHORT).show();
    }

    private boolean openExternal(Uri uri) {
        if (uri == null) return false;
        try {
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (parsed.resolveActivity(getPackageManager()) != null) {
                    startActivity(parsed);
                    return true;
                }
                String fallback = parsed.getStringExtra("browser_fallback_url");
                if (fallback != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                    return true;
                }
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "Não há aplicativo disponível para abrir este link.", Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception error) {
            return false;
        }
    }

    private void showFatalError(Throwable error) {
        try {
            if (root == null) createRoot();
            root.removeAllViews();
            TextView message = new TextView(this);
            message.setTextColor(Color.WHITE);
            message.setTextSize(16f);
            message.setGravity(Gravity.CENTER);
            message.setPadding(dp(40), dp(40), dp(40), dp(40));
            String detail = error == null ? "Erro desconhecido" : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            message.setText("Elo Bar não conseguiu iniciar.\n\n" + detail + "\n\nEnvie esta tela para o suporte.");
            root.addView(message, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            scheduleSystemBarsAndInsets();
        } catch (Throwable ignored) {
            Toast.makeText(this, "Falha ao iniciar o Elo Bar.", Toast.LENGTH_LONG).show();
        }
    }

    private final class EloBarWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            Uri uri = request.getUrl();
            if (isInternalUrl(uri) || "about".equalsIgnoreCase(uri.getScheme()) || "blob".equalsIgnoreCase(uri.getScheme())) return false;
            return openExternal(uri);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isInternalUrl(uri) || url.startsWith("about:") || url.startsWith("blob:")) return false;
            return openExternal(uri);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Uri uri = Uri.parse(url);
            if (isAppsScriptSurface(uri)) {
                hideAppsScriptWarningBar();
                installNativeBackBridge();
                view.postDelayed(BackAwareActivity.this::installNativeBackBridge, 500L);
                hideSplash(450L);
            } else if (isGoogleLogin(uri)) {
                hideSplash(120L);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) hideSplash(100L);
        }
    }

    private final class EloBarWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = filePathCallback;
            try {
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException error) {
                pendingFileChooser = null;
                Toast.makeText(BackAwareActivity.this, "Nenhum seletor de arquivo disponível.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (pendingFileChooser != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                pendingFileChooser.onReceiveValue(result);
                pendingFileChooser = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureWindow();
        scheduleSystemBarsAndInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            configureWindow();
            scheduleSystemBarsAndInsets();
            if (webView != null) webView.onResume();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        dispatchBackToPage();
    }

    @Override
    protected void onDestroy() {
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
