package com.lark.music;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.webkit.*;
import java.io.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    WebView wv;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0C0B14);
        getWindow().setNavigationBarColor(0xFF0C0B14);
        wv = new WebView(this);
        wv.setBackgroundColor(0xFF0C0B14);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        wv.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return handle(r);
            }
        });
        String[] perms = Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        requestPermissions(perms, 1);
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] g) {
        try { startForegroundService(new Intent(this, KeepAlive.class)); } catch (Exception e) {}
        wv.loadUrl("https://lark.local/");
    }

    @Override public void onBackPressed() { moveTaskToBack(true); }

    WebResourceResponse handle(WebResourceRequest r) {
        try {
            String path = r.getUrl().getPath();
            if (path == null || path.equals("/"))
                return new WebResourceResponse("text/html", "utf-8", getAssets().open("index.html"));
            if (path.startsWith("/api/"))
                return new WebResourceResponse("application/json", "utf-8", new ByteArrayInputStream(songs().getBytes("UTF-8")));
            if (path.startsWith("/audio/"))
                return audio(Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, path.substring(7)), r.getRequestHeaders());
            if (path.startsWith("/cover/"))
                return cover(Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, path.substring(7)));
        } catch (Exception e) {}
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
    }

    String songs() throws Exception {
        JSONArray a = new JSONArray();
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                         MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                MediaStore.Audio.Media.DURATION + ">30000", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            while (c != null && c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("path", String.valueOf(c.getLong(0)));
                o.put("title", c.getString(1) == null ? "Unknown" : c.getString(1));
                String ar = c.getString(2), al = c.getString(3);
                o.put("artist", ar == null || ar.equals("<unknown>") ? "Unknown" : ar);
                o.put("album", al == null || al.equals("<unknown>") ? "Unknown" : al);
                o.put("dur", c.getLong(4) / 1000);
                a.put(o);
            }
        }
        return a.toString();
    }

    WebResourceResponse cover(Uri u) throws Exception {
        android.media.MediaMetadataRetriever mr = new android.media.MediaMetadataRetriever(); mr.setDataSource(this, u); byte[] pic = mr.getEmbeddedPicture(); mr.release(); if (pic == null) throw new Exception(); Bitmap bm = android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 85, o);
        return new WebResourceResponse("image/jpeg", null, new ByteArrayInputStream(o.toByteArray()));
    }

    WebResourceResponse audio(Uri u, Map<String, String> rh) throws Exception {
        AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(u, "r");
        long len = fd.getLength(), start = 0, end = len - 1;
        int code = 200;
        String reason = "OK";
        FileInputStream in = fd.createInputStream();
        String rg = null;
        for (Map.Entry<String, String> e : rh.entrySet())
            if (e.getKey().equalsIgnoreCase("Range")) rg = e.getValue();
        Map<String, String> h = new HashMap<>();
        if (rg != null && rg.startsWith("bytes=")) {
            String[] p = rg.substring(6).split("-");
            start = Long.parseLong(p[0]);
            if (p.length > 1 && p[1].length() > 0) end = Long.parseLong(p[1]);
            code = 206;
            reason = "Partial Content";
            long sk = start;
            while (sk > 0) { long n = in.skip(sk); if (n <= 0) break; sk -= n; }
            h.put("Content-Range", "bytes " + start + "-" + end + "/" + len);
        }
        h.put("Accept-Ranges", "bytes");
        h.put("Content-Length", String.valueOf(end - start + 1));
        h.put("Access-Control-Allow-Origin", "*");
        String mime = getContentResolver().getType(u);
        if (mime == null) mime = "audio/mpeg";
        return new WebResourceResponse(mime, null, code, reason, h, in);
    }
}
