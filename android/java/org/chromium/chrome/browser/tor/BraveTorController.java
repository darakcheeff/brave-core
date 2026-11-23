/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.tor;

import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.app.BraveActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * High-level coordinator between UI activities, tabs, and Tor daemon.
 */
public final class BraveTorController {

    private BraveTorController() {}

    /**
     * Opens a private tab dedicated to Tor browsing.
     * Automatically launches the embedded Tor daemon if not currently running
     * and displays live bootstrap progress on the Tor New Tab page (matching desktop).
     */
    public static void openTorTab(Activity activity, String targetUrl) {
        if (!(activity instanceof BraveActivity)) return;
        BraveActivity braveActivity = (BraveActivity) activity;

        BraveTorManager torManager = BraveTorManager.getInstance();
        final boolean isNtp = (targetUrl == null || targetUrl.isEmpty());
        final String url = isNtp ? buildTorNtpDataUrl(torManager.isTorRunning()) : targetUrl;

        Tab launchedTab = null;
        if (braveActivity.getTabCreator(true) != null) {
            launchedTab =
                    braveActivity
                            .getTabCreator(true)
                            .launchUrl(url, TabLaunchType.FROM_CHROME_UI);
        } else if (braveActivity.getActivityTab() != null) {
            launchedTab = braveActivity.getActivityTab();
            launchedTab.loadUrl(new LoadUrlParams(url));
        }

        final Tab torTab = launchedTab;
        if (!torManager.isTorRunning()) {
            torManager.startTor(
                    activity,
                    new BraveTorManager.TorBootstrapListener() {
                        @Override
                        public void onBootstrapProgress(int progress, String summary) {
                            updateTorTabStatus(torTab, progress, summary, false);
                        }

                        @Override
                        public void onTorReady() {
                            updateTorTabStatus(torTab, 100, "Done", false);
                        }

                        @Override
                        public void onTorStopped() {
                            updateTorTabStatus(torTab, 0, "Disconnected", false);
                        }

                        @Override
                        public void onError(String message) {
                            updateTorTabStatus(torTab, 0, message, true);
                            Toast.makeText(activity, "Tor error: " + message, Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
        }
    }

    private static void updateTorTabStatus(
            Tab tab, int progress, String summary, boolean isError) {
        if (tab == null) return;
        ThreadUtils.runOnUiThread(
                () -> {
                    if (tab.isDestroyed() || tab.getWebContents() == null) return;
                    String safeSummary = JSONObject.quote(summary != null ? summary : "");
                    String js =
                            "if (window.__setTorStatus) { window.__setTorStatus("
                                    + progress
                                    + ", "
                                    + safeSummary
                                    + ", "
                                    + isError
                                    + "); }";
                    tab.getWebContents().evaluateJavaScript(js, null);
                });
    }

    private static String buildTorNtpDataUrl(boolean alreadyConnected) {
        String initialTitle =
                alreadyConnected ? "Tor подключён (100%)" : "Tor подключается... 0%";
        String initialSummary = alreadyConnected ? "Done" : "Starting Tor daemon...";
        String initialDotColor = alreadyConnected ? "#4ade80" : "#c084fc";
        String html =
                "<!doctype html><html lang='ru'><head><meta charset='utf-8'>"
                        + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                        + "<title>Новая приватная вкладка с Tor</title>"
                        + "<style>"
                        + "*{box-sizing:border-box;margin:0;padding:0}"
                        + "body{min-height:100vh;background:radial-gradient(circle at 50% 25%,#2d1946 0%,#1a0e29 55%,#12091d 100%);"
                        + "color:#f3effa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                        + "display:flex;flex-direction:column;justify-content:space-between;padding:18px}"
                        + ".status{display:inline-flex;align-items:flex-start;gap:10px;background:rgba(43,27,66,.88);"
                        + "border:1px solid rgba(192,132,252,.28);border-radius:10px;padding:10px 14px;max-width:340px;"
                        + "box-shadow:0 6px 20px rgba(0,0,0,.35)}"
                        + ".dot{width:10px;height:10px;border-radius:50%;margin-top:5px;flex-shrink:0;background:"
                        + initialDotColor
                        + "}"
                        + ".st-title{font-size:14px;font-weight:600;color:#fff}"
                        + ".st-sub{font-size:12px;color:#c4b5d9;margin-top:2px;word-break:break-word}"
                        + ".center{display:flex;flex-direction:column;align-items:center;gap:22px;margin:auto 0;width:100%}"
                        + ".brand{font-size:32px;font-weight:700;letter-spacing:-.5px;display:flex;align-items:center;gap:10px}"
                        + ".badge{font-size:12px;background:#7e22ce;color:#fff;padding:3px 9px;border-radius:999px;font-weight:600}"
                        + "form{width:100%;max-width:520px}"
                        + "input{width:100%;padding:14px 18px;border-radius:28px;border:1px solid rgba(255,255,255,.16);"
                        + "background:rgba(255,255,255,.08);color:#fff;font-size:15px;outline:none}"
                        + "input::placeholder{color:#b6a6cd}"
                        + ".card{background:rgba(36,22,56,.92);border:1px solid rgba(255,255,255,.1);border-radius:12px;"
                        + "padding:14px 16px;max-width:360px;font-size:12px;line-height:1.45;color:#cbbde0;position:relative}"
                        + ".card h3{font-size:14px;color:#fff;margin-bottom:6px;padding-right:20px}"
                        + ".close{position:absolute;top:10px;right:12px;cursor:pointer;color:#a796c2;font-size:16px}"
                        + "</style></head><body>"
                        + "<div class='status'><div class='dot' id='dot'></div><div>"
                        + "<div class='st-title' id='stTitle'>"
                        + initialTitle
                        + "</div>"
                        + "<div class='st-sub' id='stSub'>"
                        + initialSummary
                        + "</div></div></div>"
                        + "<div class='center'>"
                        + "<div class='brand'>Brave <span class='badge'>Tor</span></div>"
                        + "<form action='https://search.brave.com/search' method='get'>"
                        + "<input type='search' name='q' placeholder='Конфиденциальный поиск в Интернете'>"
                        + "</form></div>"
                        + "<div class='card' id='infoCard'>"
                        + "<span class='close' onclick=\"document.getElementById('infoCard').style.display='none'\">✕</span>"
                        + "<h3>Приватное окно с подключением к Tor</h3>"
                        + "Brave не хранит информацию о ваших действиях при работе в приватном окне. "
                        + "Когда вы подключаетесь к Tor, сайтам труднее узнать ваш настоящий IP-адрес, "
                        + "а инструментам мониторинга сети — какие сайты вы посещаете."
                        + "</div>"
                        + "<script>"
                        + "window.__setTorStatus=function(p,s,err){"
                        + "var t=document.getElementById('stTitle'),sub=document.getElementById('stSub'),d=document.getElementById('dot');"
                        + "if(err){t.textContent='Ошибка Tor';d.style.background='#f87171';}"
                        + "else if(p>=100){t.textContent='Tor подключён (100%)';d.style.background='#4ade80';}"
                        + "else{t.textContent='Tor подключается... '+p+'%';d.style.background='#c084fc';}"
                        + "if(s!==undefined&&s!==null)sub.textContent=s;"
                        + "};</script></body></html>";
        return "data:text/html;charset=utf-8," + Uri.encode(html);
    }

    /**
     * Checks if a URL is an .onion address and ensures Tor is active.
     */
    public static boolean maybeRouteOnionUrl(Activity activity, String url) {
        if (BraveTorProxyController.isOnionUrl(url) && BraveTorProxyController.isAutoOnionEnabled()) {
            openTorTab(activity, url);
            return true;
        }
        return false;
    }

    /**
     * Shows a prompt when an Onion-Location response header is encountered.
     */
    public static void promptOnionLocation(Activity activity, String onionUrl) {
        if (activity == null || activity.isFinishing()) return;

        new AlertDialog.Builder(activity)
                .setTitle("Onion Site Available")
                .setMessage(
                        "This site offers an encrypted .onion hidden service version via the Tor"
                                + " network:\n\n"
                                + onionUrl)
                .setPositiveButton("Open via Tor", (dialog, which) -> openTorTab(activity, onionUrl))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
