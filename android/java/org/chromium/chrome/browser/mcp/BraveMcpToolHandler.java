/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.mcp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Base64;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.app.BraveActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executes MCP tools against the active Brave browser instance on Android.
 * Runs operations stealthily without triggering DevTools CDP or bot detection.
 */
public final class BraveMcpToolHandler {
    private static final String TAG = "BraveMcpToolHandler";

    private BraveMcpToolHandler() {}

    /**
     * Dispatches tool invocation and returns result string or throws exception.
     */
    public static String executeTool(String toolName, JSONObject args) throws Exception {
        BraveActivity activity = BraveMcpServerManager.getInstance().getCurrentActivity();
        if (activity == null) {
            throw new IllegalStateException("No active Brave browser activity available");
        }

        switch (toolName) {
            case "browser_navigate":
                return handleNavigate(activity, args);
            case "browser_get_dom":
                return handleGetDom(activity, args);
            case "browser_click":
                return handleClick(activity, args);
            case "browser_type":
                return handleType(activity, args);
            case "browser_screenshot":
                return handleScreenshot(activity, args);
            case "browser_tab_management":
                return handleTabManagement(activity, args);
            case "browser_evaluate_script":
                return handleEvaluateScript(activity, args);
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }

    private static String handleNavigate(BraveActivity activity, JSONObject args) throws Exception {
        String url = args.optString("url", "").trim();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: url");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("brave://") && !url.startsWith("chrome://")) {
            url = "https://" + url;
        }

        final String finalUrl = url;
        boolean newTab = args.optBoolean("new_tab", false);

        CompletableFuture<String> future = new CompletableFuture<>();
        ThreadUtils.runOnUiThread(() -> {
            try {
                if (newTab) {
                    if (activity.getTabCreator(false) != null) {
                        activity.getTabCreator(false).launchUrl(finalUrl, TabLaunchType.FROM_CHROME_UI);
                        future.complete("Opened new tab with URL: " + finalUrl);
                    } else {
                        future.completeExceptionally(new IllegalStateException("TabCreator unavailable"));
                    }
                } else {
                    Tab tab = activity.getActivityTab();
                    if (tab != null) {
                        tab.loadUrl(new LoadUrlParams(finalUrl));
                        future.complete("Navigated current tab to: " + finalUrl);
                    } else if (activity.getTabCreator(false) != null) {
                        activity.getTabCreator(false).launchUrl(finalUrl, TabLaunchType.FROM_CHROME_UI);
                        future.complete("No active tab, opened new tab with URL: " + finalUrl);
                    } else {
                        future.completeExceptionally(new IllegalStateException("No tab available for navigation"));
                    }
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get(5, TimeUnit.SECONDS);
    }

    private static String handleGetDom(BraveActivity activity, JSONObject args) throws Exception {
        Tab tab = getActiveTab(activity);
        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            throw new IllegalStateException("Tab web contents is null");
        }

        String mode = args.optString("mode", "markdown").toLowerCase();
        String selector = args.optString("selector", "").trim();
        String selectorJs = selector.isEmpty() ? "null" : "\"" + BraveMcpUtils.escapeJsString(selector) + "\"";

        String script;
        if ("text".equals(mode)) {
            script = "(function() {"
                    + "  var sel = " + selectorJs + ";"
                    + "  var el = sel ? document.querySelector(sel) : document.body;"
                    + "  return el ? el.innerText : 'Error: element not found';"
                    + "})()";
        } else if ("dom".equals(mode)) {
            script = "(function() {"
                    + "  var sel = " + selectorJs + ";"
                    + "  var el = sel ? document.querySelector(sel) : document.documentElement;"
                    + "  if (!el) return 'Error: element not found';"
                    + "  var clone = el.cloneNode(true);"
                    + "  var remove = clone.querySelectorAll('script, style, noscript, svg, canvas');"
                    + "  for (var i = 0; i < remove.length; i++) remove[i].remove();"
                    + "  return clone.outerHTML;"
                    + "})()";
        } else {
            // Default: clean markdown conversion optimized for LLM token efficiency
            script = "(function() {"
                    + "  var sel = " + selectorJs + ";"
                    + "  var root = sel ? document.querySelector(sel) : document.body;"
                    + "  if (!root) return 'Error: element not found';"
                    + "  function walk(node) {"
                    + "    if (node.nodeType === Node.TEXT_NODE) {"
                    + "      var t = node.textContent.trim();"
                    + "      return t ? t + ' ' : '';"
                    + "    }"
                    + "    if (node.nodeType !== Node.ELEMENT_NODE) return '';"
                    + "    var tag = node.tagName.toLowerCase();"
                    + "    if (['script', 'style', 'noscript', 'svg', 'canvas'].indexOf(tag) !== -1) return '';"
                    + "    var style = window.getComputedStyle(node);"
                    + "    if (style && (style.display === 'none' || style.visibility === 'hidden')) return '';"
                    + "    var ch = '';"
                    + "    for (var i = 0; i < node.childNodes.length; i++) ch += walk(node.childNodes[i]);"
                    + "    ch = ch.trim();"
                    + "    if (!ch && ['img', 'input', 'hr', 'br'].indexOf(tag) === -1) return '';"
                    + "    if (tag === 'h1') return '# ' + ch + '\\n\\n';"
                    + "    if (tag === 'h2') return '## ' + ch + '\\n\\n';"
                    + "    if (tag === 'h3') return '### ' + ch + '\\n\\n';"
                    + "    if (tag === 'h4') return '#### ' + ch + '\\n\\n';"
                    + "    if (tag === 'p') return ch + '\\n\\n';"
                    + "    if (tag === 'li') return '- ' + ch + '\\n';"
                    + "    if (tag === 'ul' || tag === 'ol') return '\\n' + ch + '\\n';"
                    + "    if (tag === 'a') {"
                    + "      var href = node.getAttribute('href') || '';"
                    + "      return '[' + ch + '](' + href + ') ';"
                    + "    }"
                    + "    if (tag === 'button') return '[' + ch + '] ';"
                    + "    if (tag === 'blockquote') return '> ' + ch + '\\n\\n';"
                    + "    if (tag === 'code' || tag === 'pre') return '`' + ch + '` ';"
                    + "    if (['div', 'section', 'article', 'main', 'header', 'footer'].indexOf(tag) !== -1) {"
                    + "      return ch ? ch + '\\n' : '';"
                    + "    }"
                    + "    return ch + ' ';"
                    + "  }"
                    + "  return walk(root).replace(/\\n{3,}/g, '\\n\\n').trim();"
                    + "})()";
        }

        return evaluateJs(webContents, script, 10);
    }

    private static String handleClick(BraveActivity activity, JSONObject args) throws Exception {
        Tab tab = getActiveTab(activity);
        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            throw new IllegalStateException("Tab web contents is null");
        }

        String selector = args.optString("selector", "").trim();
        String script;
        if (!selector.isEmpty()) {
            String escapedSelector = BraveMcpUtils.escapeJsString(selector);
            script = "(function() {"
                    + "  var el = document.querySelector(\"" + escapedSelector + "\");"
                    + "  if (!el) return 'Error: Element not found for selector: " + escapedSelector + "';"
                    + "  el.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});"
                    + "  var rect = el.getBoundingClientRect();"
                    + "  var x = rect.left + rect.width / 2;"
                    + "  var y = rect.top + rect.height / 2;"
                    + "  var events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];"
                    + "  for (var i = 0; i < events.length; i++) {"
                    + "    var evt = new MouseEvent(events[i], {bubbles: true, cancelable: true, view: window, clientX: x, clientY: y});"
                    + "    el.dispatchEvent(evt);"
                    + "  }"
                    + "  if (typeof el.click === 'function') el.click();"
                    + "  return 'Clicked ' + \"" + escapedSelector + "\" + ' at (' + Math.round(x) + ', ' + Math.round(y) + ')';"
                    + "})()";
        } else if (args.has("x") && args.has("y")) {
            double x = args.getDouble("x");
            double y = args.getDouble("y");
            script = "(function() {"
                    + "  var el = document.elementFromPoint(" + x + ", " + y + ");"
                    + "  if (!el) return 'Error: No element at point (" + x + ", " + y + ")';"
                    + "  var events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];"
                    + "  for (var i = 0; i < events.length; i++) {"
                    + "    var evt = new MouseEvent(events[i], {bubbles: true, cancelable: true, view: window, clientX: " + x + ", clientY: " + y + "});"
                    + "    el.dispatchEvent(evt);"
                    + "  }"
                    + "  if (typeof el.click === 'function') el.click();"
                    + "  return 'Clicked element <' + el.tagName.toLowerCase() + '> at (" + x + ", " + y + ")';"
                    + "})()";
        } else {
            throw new IllegalArgumentException("Either selector or (x, y) coordinates must be provided");
        }

        return evaluateJs(webContents, script, 8);
    }

    private static String handleType(BraveActivity activity, JSONObject args) throws Exception {
        Tab tab = getActiveTab(activity);
        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            throw new IllegalStateException("Tab web contents is null");
        }

        String text = args.optString("text", "");
        String selector = args.optString("selector", "").trim();
        String escapedText = BraveMcpUtils.escapeJsString(text);

        String script;
        if (!selector.isEmpty()) {
            String escapedSelector = BraveMcpUtils.escapeJsString(selector);
            script = "(function() {"
                    + "  var el = document.querySelector(\"" + escapedSelector + "\");"
                    + "  if (!el) return 'Error: Element not found for selector: " + escapedSelector + "';"
                    + "  el.focus();"
                    + "  el.value = \"" + escapedText + "\";"
                    + "  el.dispatchEvent(new Event('input', {bubbles: true}));"
                    + "  el.dispatchEvent(new Event('change', {bubbles: true}));"
                    + "  return 'Typed text into ' + \"" + escapedSelector + "\";"
                    + "})()";
        } else {
            script = "(function() {"
                    + "  var el = document.activeElement;"
                    + "  if (!el) return 'Error: No element currently focused';"
                    + "  el.value = (el.value || '') + \"" + escapedText + "\";"
                    + "  el.dispatchEvent(new Event('input', {bubbles: true}));"
                    + "  el.dispatchEvent(new Event('change', {bubbles: true}));"
                    + "  return 'Typed text into active element <' + el.tagName.toLowerCase() + '>';"
                    + "})()";
        }

        return evaluateJs(webContents, script, 8);
    }

    private static String handleScreenshot(BraveActivity activity, JSONObject args) throws Exception {
        Tab tab = getActiveTab(activity);
        int quality = args.optInt("quality", 80);
        final int finalQuality = Math.max(10, Math.min(100, quality));

        CompletableFuture<String> future = new CompletableFuture<>();
        ThreadUtils.runOnUiThread(() -> {
            try {
                View view = tab.getView();
                if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
                    future.complete("Error: Tab view is not initialized or has zero dimensions");
                    return;
                }
                Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, finalQuality, stream);
                bitmap.recycle();
                byte[] bytes = stream.toByteArray();
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                future.complete("data:image/jpeg;base64," + base64);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get(10, TimeUnit.SECONDS);
    }

    private static String handleTabManagement(BraveActivity activity, JSONObject args) throws Exception {
        String action = args.optString("action", "list").toLowerCase();

        CompletableFuture<String> future = new CompletableFuture<>();
        ThreadUtils.runOnUiThread(() -> {
            try {
                TabModel model = activity.getCurrentTabModel();
                if (model == null) {
                    future.complete("Error: Current tab model is null");
                    return;
                }

                switch (action) {
                    case "list": {
                        JSONArray array = new JSONArray();
                        int activeIdx = model.index();
                        for (int i = 0; i < model.getCount(); i++) {
                            Tab t = model.getTabAt(i);
                            if (t != null) {
                                JSONObject item = new JSONObject();
                                item.put("id", t.getId());
                                item.put("title", t.getTitle() != null ? t.getTitle() : "");
                                item.put("url", t.getUrl() != null ? t.getUrl().getSpec() : "");
                                item.put("active", i == activeIdx);
                                array.put(item);
                            }
                        }
                        future.complete(array.toString(2));
                        break;
                    }
                    case "switch": {
                        int tabId = args.optInt("tab_id", -1);
                        int count = model.getCount();
                        boolean found = false;
                        for (int i = 0; i < count; i++) {
                            Tab t = model.getTabAt(i);
                            if (t != null && t.getId() == tabId) {
                                TabModelUtils.setIndex(model, i);
                                future.complete("Switched to tab " + tabId);
                                found = true;
                                break;
                            }
                        }
                        if (!found) future.complete("Tab " + tabId + " not found");
                        break;
                    }
                    case "close": {
                        int tabId = args.optInt("tab_id", -1);
                        int count = model.getCount();
                        boolean found = false;
                        for (int i = 0; i < count; i++) {
                            Tab t = model.getTabAt(i);
                            if (t != null && t.getId() == tabId) {
                                model.closeTab(t);
                                future.complete("Closed tab " + tabId);
                                found = true;
                                break;
                            }
                        }
                        if (!found) future.complete("Tab " + tabId + " not found");
                        break;
                    }
                    case "new": {
                        String url = args.optString("url", "about:blank");
                        if (activity.getTabCreator(false) != null) {
                            activity.getTabCreator(false).launchUrl(url, TabLaunchType.FROM_CHROME_UI);
                            future.complete("Created new tab with URL: " + url);
                        } else {
                            future.complete("Error: TabCreator unavailable");
                        }
                        break;
                    }
                    default:
                        future.complete("Error: Unknown tab action " + action);
                        break;
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get(5, TimeUnit.SECONDS);
    }

    private static String handleEvaluateScript(BraveActivity activity, JSONObject args) throws Exception {
        Tab tab = getActiveTab(activity);
        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            throw new IllegalStateException("Tab web contents is null");
        }

        String script = args.optString("script", "").trim();
        if (script.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: script");
        }

        return evaluateJs(webContents, script, 10);
    }

    private static Tab getActiveTab(BraveActivity activity) {
        Tab tab = activity.getActivityTab();
        if (tab == null) {
            throw new IllegalStateException("No active tab in current activity");
        }
        return tab;
    }

    private static String evaluateJs(WebContents webContents, String script, int timeoutSeconds) {
        CompletableFuture<String> future = new CompletableFuture<>();
        ThreadUtils.runOnUiThread(() -> {
            try {
                webContents.evaluateJavaScript(script, (result) -> {
                    future.complete(result != null ? result : "");
                });
            } catch (Exception e) {
                future.complete("Error evaluating JS: " + e.getMessage());
            }
        });

        try {
            String res = future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (res.startsWith("\"") && res.endsWith("\"") && res.length() >= 2) {
                try {
                    res = new org.json.JSONTokener(res).nextValue().toString();
                } catch (Exception ignored) {
                }
            }
            return res;
        } catch (Exception e) {
            Log.e(TAG, "evaluateJs failed or timed out", e);
            return "Error: JS execution failed or timed out: " + e.getMessage();
        }
    }
}
