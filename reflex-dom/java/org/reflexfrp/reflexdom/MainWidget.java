package org.reflexfrp.reflexdom;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ShareActionProvider;
import android.graphics.Bitmap;
import java.io.IOException;
import java.io.InputStream;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.webkit.ValueCallback;

import java.nio.charset.StandardCharsets;

import systems.obsidian.HaskellActivity;
import com.gonimo.baby.R;

public class MainWidget {
  private static Object startMainWidget(final HaskellActivity a, String url, long jsaddleCallbacks, final String initialJS) {
      // Access Android features from Haskell via jsaddle:
    class AndroidInterface {
      @JavascriptInterface
      public void share(String url) {
        Intent i = new Intent(Intent.ACTION_SEND, Uri.parse(url));
        i.putExtra(Intent.EXTRA_TEXT, url);
        i.setType("text/plain");
        a.startActivity(Intent.createChooser(i, a.getString(R.string.share_using)));
      }

      @JavascriptInterface
      public void killApp() {
          a.finishAndRemoveTask();
      }

      @JavascriptInterface
      // Show a warning to the user that Gonimo should be in the foreground.
      public void requestStoppedWarning() {
          a.showStoppedWarningNotification();
      }
    }

    CookieManager.setAcceptFileSchemeCookies(true); //TODO: Can we do this just for our own WebView?

    // Remove title and notification bars
    a.requestWindowFeature(Window.FEATURE_NO_TITLE);

    final WebView wv = new WebView(a);
    wv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    a.setContentView(wv);
    final WebSettings ws = wv.getSettings();
    ws.setJavaScriptEnabled(true);
    ws.setAllowFileAccessFromFileURLs(true);
    ws.setAllowUniversalAccessFromFileURLs(true);
    ws.setDomStorageEnabled(true);
    wv.setWebContentsDebuggingEnabled(true);
    wv.addJavascriptInterface(new AndroidInterface(), "nativeHost");
    // allow video to play without user interaction
    wv.getSettings().setMediaPlaybackRequiresUserGesture(false);

    a.setBackEventListener(new HaskellActivity.BackEventListener() {
            public void backButtonClicked() {
                // Does not work properly (page reloads or something ..)
                // if (wv.canGoBack()) {
                //     wv.goBack();
                //     return true;
                // }
                // return false;
                wv.evaluateJavascript("window.gonimoHistoryCanGoBack", new ValueCallback<String>() {
                        public void onReceiveValue (String value) {
                            Log.i("reflex", "onReceiveValue called: '" + value + "'");
                            if(value.equals("true")) {
                                a.runOnUiThread(new Runnable() {
                                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                                        @Override
                                        public void run() {
                                            Log.i("reflex", "gonimoHistoryGoBack() ....");
                                            wv.evaluateJavascript("window.gonimoHistoryGoBack()", new ValueCallback<String>() {
                                                    public void onReceiveValue (String value)  {
                                                        Log.i("reflex", "gonimoHistoryGoBack result:" + value);
                                                    }
                                                });
                                        }
                                    });
                            }
                            else
                                a.finishAndRemoveTask();
                        }
                    });
            }
        });

    wv.setWebViewClient(new WebViewClient() {
        @Override
        public void onPageFinished(WebView _view, String _url) {
          wv.evaluateJavascript(initialJS, null);
        }

        // Re-route / to /android_asset
        @Override
        public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if(!uri.getScheme().equals("file"))
                return null;

            String path = uri.getPath();
            path = getAssetPath(path);

            String mimeType = getMimeType(uri.toString());
            String encoding = "";

            try {
                InputStream data = a.getApplicationContext().getAssets().open(path);
                return new WebResourceResponse(mimeType, encoding, data);
            }
            catch (IOException e) {
                Log.i("reflex", "Opening resource failed, Webview will handle the request ..");
                e.printStackTrace();
            }

            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if( url != null && !url.startsWith("file://")) {
                try {
                    view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                catch(ActivityNotFoundException  e) {
                    Log.e("reflex", "Starting activity for intent '" + url + "' failed!");
                }
                return true;
            } else {
                return false;
            }
        }
    });

    wv.setWebChromeClient(new WebChromeClient() {
        // Need to accept permissions to use the camera and audio
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if(request.getOrigin().toString().startsWith("file://")) {
                a.requestWebViewPermissions(request);
            }
            else {
                a.runOnUiThread(new Runnable() {
                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public void run() {
                            request.deny();
                        }
                    });
            }
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        }
    });

    wv.addJavascriptInterface(new JSaddleCallbacks(jsaddleCallbacks), "jsaddle");

    wv.loadUrl(url);

    final Handler hnd = new Handler();
    return new Object() {
      public final void evaluateJavascript(final byte[] js) {
        final String jsStr = new String(js, StandardCharsets.UTF_8);
        hnd.post(new Runnable() {
            @Override
            public void run() {
              wv.evaluateJavascript(jsStr, null);
            }
          });
      }
    };
  }

  private static String getMimeType(String url) {
      String type = "";
      String extension = MimeTypeMap.getFileExtensionFromUrl(url);
      if (extension != null) {
          type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
      }
      return type;
  }

  /** Get the path of an asset. Strips leading / and leading /android_asset/ */
  private static String getAssetPath(String path) {
      path = path.startsWith("/android_asset") ? path.substring("/android_asset".length()) : path;
      path = path.startsWith("/") ? path.substring(1) : path;
      path = path.startsWith("index.html") ? "index.html" : path; // Get rid of in-page routes, e.g. index.html/parent
      return path;
  }

  private static class JSaddleCallbacks {
    private final long callbacks;
    private native void startProcessing(long callbacks);
    private native void processMessage(long callbacks, byte[] msg);
    private native byte[] processSyncMessage(long callbacks, byte[] msg);

    public JSaddleCallbacks(long _callbacks) {
      callbacks = _callbacks;
    }

    @JavascriptInterface
    public boolean postReady() {
      startProcessing(callbacks);
      return true;
    }

    @JavascriptInterface
    public boolean postMessage(final String msg) {
      processMessage(callbacks, msg.getBytes(StandardCharsets.UTF_8));
      return true;
    }

    @JavascriptInterface
    public String syncMessage(final String msg) {
      return new String(processSyncMessage(callbacks, msg.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8);
    }
  }
}
