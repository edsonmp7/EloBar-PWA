package com.eloclub.elobar;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.splashscreen.SplashScreen;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Locale;
import java.util.Set;

/**
 * Elo Bar Android 1.0.7 — casca canônica.
 * HOME_READY = .home-primary-actions visível e boot inicial ausente.
 * ACCESS_READY = .elo-access-gate visível para permitir ativação do aparelho.
 * GOOGLE_AUTH_READY = accounts.google.com interativo quando a sessão exigir login.
 * onPageFinished() nunca encerra sozinho a splash.
 */
public final class BackAwareActivity extends Activity {
    private static final String START_URL = "https://script.google.com/macros/s/AKfycbyd7UHyQFJA4SsFZuKWmAO___NnfGXq0oNB0M0NWnG2hhLmPHcKTL_ck4yDgB4IqSkOnQ/exec?shell=android-native";
    private static final String SHELL_VERSION = "1.0.7";
    private static final int FILE_CHOOSER_REQUEST = 1907;
    private static final int APP_BACKGROUND = Color.rgb(18, 18, 18);
    private static final int SPLASH_BACKGROUND = Color.rgb(13, 8, 3);
    private static final int GOLD = Color.rgb(215, 167, 43);
    private static final int GOLD_SOFT = Color.rgb(245, 205, 100);
    private static final long MIN_SPLASH_MS = 650L;
    private static final long MAX_SPLASH_MS = 8000L;
    private static final long EXIT_CONFIRM_MS = 2000L;
    private static final int PAGE_PROGRESS_CEILING = 72;
    private static final int SHELL_PROGRESS = 88;
    private static final Set<String> TRUSTED_ORIGINS = Set.of(
            "https://script.google.com",
            "https://googleusercontent.com",
            "https://*.googleusercontent.com"
    );

    private FrameLayout root;
    private WebView webView;
    private FrameLayout splashOverlay;
    private BottleGlassLoaderView loadingMark;
    private ProgressBar progressBar;
    private TextView progressText;
    private View errorView;
    private TextView errorMessage;
    private ValueCallback<Uri[]> pendingFileChooser;
    private boolean splashDismissed;
    private boolean webReady;
    private boolean authSurfaceVisible;
    private boolean readinessProbeScheduled;
    private boolean backDispatchInFlight;
    private int displayedProgress = 5;
    private int stableReadyReads;
    private long activityStartedAt;
    private long exitArmedUntil;
    private String lastInternalUrl = START_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        activityStartedAt = SystemClock.elapsedRealtime();
        configureOrientation();
        createRoot();
        configureWindow();
        createWebView();
        createErrorView();
        createSplashOverlay();
        configureWebView();
        installDocumentStartContract();
        applySafeAreaInsets();
        registerBackCallback();
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) webView.loadUrl(START_URL);
        else beginReadinessProbe();
        root.postDelayed(this::handleSoftReadinessTimeout, MAX_SPLASH_MS);
    }

    private void configureOrientation() {
        setRequestedOrientation(getResources().getConfiguration().smallestScreenWidthDp >= 600
                ? ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void createRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(APP_BACKGROUND);
        setContentView(root);
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(APP_BACKGROUND);
        getWindow().setNavigationBarColor(APP_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getWindow().setDecorFitsSystemWindows(false);
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(APP_BACKGROUND);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setAlpha(0f);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void createErrorView() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(APP_BACKGROUND);
        overlay.setVisibility(View.GONE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(30), dp(32), dp(30), dp(32));
        TextView title = new TextView(this);
        title.setText("Não foi possível abrir o Elo Bar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        errorMessage = new TextView(this);
        errorMessage.setTextColor(Color.rgb(205, 205, 205));
        errorMessage.setTextSize(14f);
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setPadding(0, dp(12), 0, dp(18));
        Button retry = new Button(this);
        retry.setText("Tentar novamente");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> retryLoading());
        content.addView(title);
        content.addView(errorMessage);
        content.addView(retry);
        overlay.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        errorView = overlay;
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void createSplashOverlay() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(SPLASH_BACKGROUND);
        ImageView artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.app_splash_full);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artwork.setBackgroundColor(SPLASH_BACKGROUND);
        splashOverlay.addView(artwork, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(105, 8, 5, 2), Color.argb(225, 8, 5, 2)}));
        splashOverlay.addView(fade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230), Gravity.BOTTOM));

        LinearLayout loader = new LinearLayout(this);
        loader.setOrientation(LinearLayout.VERTICAL);
        loader.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        loader.setPadding(dp(30), 0, dp(30), dp(24));
        loadingMark = new BottleGlassLoaderView(this);
        loadingMark.setProgress(displayedProgress);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(182), dp(94));
        markLp.bottomMargin = dp(4);
        loader.addView(loadingMark, markLp);
        progressText = new TextView(this);
        progressText.setTextColor(GOLD_SOFT);
        progressText.setTextSize(13f);
        progressText.setGravity(Gravity.CENTER);
        progressText.setText("Carregando... 5%");
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.bottomMargin = dp(9);
        loader.addView(progressText, textLp);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(displayedProgress);
        progressBar.setIndeterminate(false);
        progressBar.setProgressTintList(ColorStateList.valueOf(GOLD));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(110, 122, 92, 34)));
        loader.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        splashOverlay.addView(loader, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        root.addView(splashOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        String ua = settings.getUserAgentString();
        if (ua != null) settings.setUserAgentString(ua.replaceAll("\\sEloBarAndroid/[0-9A-Za-z._-]+", "") + " EloBarAndroid/" + SHELL_VERSION);
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new EloBarWebViewClient());
        webView.setWebChromeClient(new EloBarWebChromeClient());
    }

    private void installDocumentStartContract() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, preloadJavascript(), TRUSTED_ORIGINS);
        }
    }

    private String preloadJavascript() {
        return "(function(){try{"
                + "function hideAppsScriptChrome(){try{"
                + "var w=document.getElementById('warning'),t=document.getElementById('warning-text');"
                + "if(w){var r=w.closest&&w.closest('tr');if(r)r.style.setProperty('display','none','important');else w.style.setProperty('display','none','important');}"
                + "if(t){var s=(t.textContent||'').toLowerCase();if(s.indexOf('google apps script')>=0||s.indexOf('criado por um usuário')>=0){var p=t.closest&&t.closest('tr');if(p)p.style.setProperty('display','none','important');else t.style.setProperty('display','none','important');}}"
                + "}catch(e){}}"
                + "window.__eloBarHideAppsScriptChrome=hideAppsScriptChrome;"
                + "if(document.addEventListener)document.addEventListener('DOMContentLoaded',hideAppsScriptChrome,false);"
                + "try{new MutationObserver(hideAppsScriptChrome).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}hideAppsScriptChrome();"
                + "}catch(e){}})();";
    }

    private String readinessProbeJavascript() {
        return "(function(){try{if(window.__eloBarHideAppsScriptChrome)window.__eloBarHideAppsScriptChrome();"
                + "function v(e){if(!e||!e.isConnected)return false;var s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)>0&&e.getClientRects().length>0;}"
                + "var gate=document.querySelector('.elo-access-gate');if(v(gate))return 'ACCESS_READY';"
                + "var home=document.querySelector('.home-primary-actions'),app=document.getElementById('app'),boot=app&&app.querySelector('.boot');"
                + "if(v(home)&&!v(boot))return 'HOME_READY';return 'WAIT';}catch(e){return 'WAIT';}})();";
    }

    private void beginReadinessProbe() {
        if (readinessProbeScheduled || webReady || webView == null) return;
        readinessProbeScheduled = true;
        webView.post(this::runReadinessProbe);
    }

    private void runReadinessProbe() {
        readinessProbeScheduled = false;
        if (webReady || webView == null || isFinishing()) return;
        Uri current = safeUri(webView.getUrl());
        if (isGoogleLogin(current)) { revealGoogleAuthSurface(); return; }
        if (!isAppsScriptSurface(current)) { scheduleProbe(120L); return; }
        webView.evaluateJavascript(readinessProbeJavascript(), raw -> {
            String state = unquote(raw);
            if ("HOME_READY".equals(state) || "ACCESS_READY".equals(state)) {
                updateLoadingProgress(SHELL_PROGRESS);
                stableReadyReads++;
                if (stableReadyReads >= 2) finishReadiness();
                else scheduleProbe(90L);
            } else {
                stableReadyReads = 0;
                scheduleProbe(110L);
            }
        });
    }

    private void scheduleProbe(long delay) {
        if (readinessProbeScheduled || webReady || webView == null) return;
        readinessProbeScheduled = true;
        webView.postDelayed(this::runReadinessProbe, delay);
    }

    private void finishReadiness() {
        if (webReady) return;
        webReady = true;
        updateLoadingProgress(100);
        String sweep = "(function(){try{if(window.__eloBarHideAppsScriptChrome)window.__eloBarHideAppsScriptChrome();}catch(e){}return true;})();";
        webView.evaluateJavascript(sweep, ignored -> {
            long wait = Math.max(0L, MIN_SPLASH_MS - (SystemClock.elapsedRealtime() - activityStartedAt));
            root.postDelayed(this::revealReadyWebView, wait + 120L);
        });
    }

    private void revealReadyWebView() {
        if (splashDismissed || webView == null) return;
        webView.animate().alpha(1f).setDuration(180L).start();
        if (loadingMark != null) loadingMark.complete();
        splashDismissed = true;
        splashOverlay.animate().alpha(0f).setDuration(220L).withEndAction(() -> splashOverlay.setVisibility(View.GONE)).start();
    }

    private void revealGoogleAuthSurface() {
        if (authSurfaceVisible || webView == null) return;
        authSurfaceVisible = true;
        splashDismissed = true;
        webView.setAlpha(1f);
        splashOverlay.setVisibility(View.GONE);
        if (loadingMark != null) loadingMark.stopAnimation();
    }

    private void showSplashAgain() {
        webReady = false;
        authSurfaceVisible = false;
        splashDismissed = false;
        stableReadyReads = 0;
        activityStartedAt = SystemClock.elapsedRealtime();
        webView.setAlpha(0f);
        splashOverlay.setAlpha(1f);
        splashOverlay.setVisibility(View.VISIBLE);
        updateLoadingProgress(5, true);
        if (loadingMark != null) loadingMark.startAnimation();
    }

    private void handleSoftReadinessTimeout() {
        if (webReady || webView == null) return;
        if (isGoogleLogin(safeUri(webView.getUrl()))) revealGoogleAuthSurface();
        else beginReadinessProbe();
    }

    private void updateLoadingProgress(int value) { updateLoadingProgress(value, false); }
    private void updateLoadingProgress(int value, boolean reset) {
        int bounded = Math.max(1, Math.min(100, value));
        if (!reset && bounded <= displayedProgress) return;
        displayedProgress = bounded;
        progressBar.setProgress(bounded);
        progressText.setText("Carregando... " + bounded + "%");
        loadingMark.setProgress(bounded);
    }

    @SuppressWarnings("deprecation")
    private void applySafeAreaInsets() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, ime.bottom));
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) root.post(() -> {
            WindowInsetsController c = root.getWindowInsetsController();
            if (c != null) c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        });
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::dispatchBackToPage);
    }

    private boolean routeUri(Uri uri) {
        if (uri == null) return false;
        String scheme = lower(uri.getScheme()), host = lower(uri.getHost());
        if ("whatsapp".equals(scheme) || "wa.me".equals(host) || "api.whatsapp.com".equals(host) || "chat.whatsapp.com".equals(host)) return openExternal(uri);
        if ("tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme)) return openExternal(uri);
        if ("intent".equals(scheme)) return openExternal(uri);
        if ("http".equals(scheme)) { Toast.makeText(this, "Link HTTP bloqueado por segurança.", Toast.LENGTH_SHORT).show(); return true; }
        if ("https".equals(scheme)) return isInternalUrl(uri) ? false : openExternal(uri);
        return scheme.isEmpty() || "about".equals(scheme) ? false : openExternal(uri);
    }

    private boolean isInternalUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = lower(uri.getHost());
        return "script.google.com".equals(host) || "accounts.google.com".equals(host) || "google.com".equals(host)
                || "www.google.com".equals(host) || "googleusercontent.com".equals(host) || host.endsWith(".googleusercontent.com");
    }

    private boolean isAppsScriptSurface(Uri uri) {
        String host = uri == null ? "" : lower(uri.getHost());
        return "script.google.com".equals(host) || "googleusercontent.com".equals(host) || host.endsWith(".googleusercontent.com");
    }

    private boolean isGoogleLogin(Uri uri) { return uri != null && "accounts.google.com".equals(lower(uri.getHost())); }

    private boolean openExternal(Uri uri) {
        try {
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME));
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Não há aplicativo disponível para abrir este link.", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private String nativeBackBridgeScript() {
        return "(function(){if(window.__eloAndroidBackInstalled)return;window.__eloAndroidBackInstalled=true;"
                + "function v(e){if(!e)return false;var s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden'&&e.getClientRects().length>0;}"
                + "window.__eloAndroidBack=function(){try{var list=[].slice.call(document.querySelectorAll('dialog[open],[aria-modal=\"true\"],.modal,.mobile-drawer-layer.open,.drawer.open,.elo-access-overlay')).filter(v);if(list.length){var o=list[list.length-1],c=o.querySelector('[aria-label=\"Fechar\"],[title=\"Fechar\"],[data-close],.modal-close,#drawerClose,#closeSettingsBtn');if(c){c.click();return true;}}var route=(location.hash||'#home').replace(/^#/,'');if(route&&route!=='home'){var b=document.querySelector('[data-action=\"back-nav\"]'),h=document.querySelector('[data-nav=\"home\"]');if(b){b.click();return true;}if(h){h.click();return true;}}return false;}catch(e){return false;}};})();";
    }

    private void installNativeBackBridge() { if (webView != null) webView.evaluateJavascript(nativeBackBridgeScript(), null); }

    private void dispatchBackToPage() {
        if (backDispatchInFlight) return;
        if (webView == null || !webReady) { confirmExitOrFinish(); return; }
        backDispatchInFlight = true;
        webView.evaluateJavascript("(function(){try{return !!(window.__eloAndroidBack&&window.__eloAndroidBack());}catch(e){return false;}})();", value -> {
            backDispatchInFlight = false;
            if ("true".equals(value)) { exitArmedUntil = 0L; return; }
            if (!isAppsScriptSurface(safeUri(webView.getUrl())) && webView.canGoBack()) webView.goBack();
            else confirmExitOrFinish();
        });
    }

    private void confirmExitOrFinish() {
        long now = SystemClock.elapsedRealtime();
        if (exitArmedUntil > now) { finish(); return; }
        exitArmedUntil = now + EXIT_CONFIRM_MS;
        Toast.makeText(this, "Pressione voltar novamente para sair.", Toast.LENGTH_SHORT).show();
    }

    private void retryLoading() {
        errorView.setVisibility(View.GONE);
        showSplashAgain();
        webView.loadUrl(lastInternalUrl == null ? START_URL : lastInternalUrl);
    }

    private void showError(String message) {
        splashOverlay.setVisibility(View.GONE);
        loadingMark.stopAnimation();
        webView.setAlpha(0f);
        errorMessage.setText(message);
        errorView.setVisibility(View.VISIBLE);
        errorView.bringToFront();
    }

    private Uri safeUri(String value) { try { return value == null ? null : Uri.parse(value); } catch (Throwable e) { return null; } }
    private String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private String unquote(String raw) {
        if (raw == null || "null".equals(raw)) return "";
        String v = raw.trim();
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class EloBarWebViewClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return routeUri(request == null ? null : request.getUrl()); }
        @Override @SuppressWarnings("deprecation") public boolean shouldOverrideUrlLoading(WebView view, String url) { return routeUri(safeUri(url)); }
        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            errorView.setVisibility(View.GONE);
            stableReadyReads = 0;
            Uri uri = safeUri(url);
            if (isInternalUrl(uri)) lastInternalUrl = url;
            if (isAppsScriptSurface(uri) && authSurfaceVisible) showSplashAgain();
        }
        @Override public void onPageFinished(WebView view, String url) {
            Uri uri = safeUri(url);
            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) { }
            if (isGoogleLogin(uri)) { revealGoogleAuthSurface(); return; }
            if (!webReady) {
                updateLoadingProgress(PAGE_PROGRESS_CEILING);
                installNativeBackBridge();
                beginReadinessProbe();
            }
        }
        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.cancel(); showError("Falha de segurança na conexão HTTPS."); }
        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) showError(error == null ? "Falha de conexão." : String.valueOf(error.getDescription()));
        }
        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { showError("O componente do navegador foi reiniciado. Toque em Tentar novamente."); return true; }
    }

    private final class EloBarWebChromeClient extends WebChromeClient {
        @Override public void onProgressChanged(WebView view, int newProgress) {
            if (webReady || authSurfaceVisible) return;
            int p = Math.max(0, Math.min(100, newProgress));
            updateLoadingProgress(5 + Math.round((PAGE_PROGRESS_CEILING - 5) * (p / 100f)));
        }
        @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = callback;
            try {
                Intent intent = params == null ? new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE) : params.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (Throwable e) { pendingFileChooser = null; return false; }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || pendingFileChooser == null) return;
        pendingFileChooser.onReceiveValue(resultCode == RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null);
        pendingFileChooser = null;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        try { if (webView != null) webView.saveState(outState); } catch (Throwable ignored) { }
        super.onSaveInstanceState(outState);
    }
    @Override protected void onPause() {
        try { CookieManager.getInstance().flush(); } catch (Throwable ignored) { }
        if (webView != null) webView.onPause();
        super.onPause();
    }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
    @Override @SuppressWarnings("deprecation") public void onBackPressed() { dispatchBackToPage(); }
    @Override protected void onDestroy() {
        if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
        try { CookieManager.getInstance().flush(); } catch (Throwable ignored) { }
        if (loadingMark != null) loadingMark.stopAnimation();
        if (webView != null) { try { webView.stopLoading(); webView.destroy(); } catch (Throwable ignored) { } }
        super.onDestroy();
    }
}
