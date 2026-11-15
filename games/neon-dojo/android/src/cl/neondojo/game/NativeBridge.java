package cl.neondojo.game;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;

/** Expuesto a JS como window.NeonNative. */
public final class NativeBridge {
    private final MainActivity act;

    NativeBridge(MainActivity act) {
        this.act = act;
    }

    @JavascriptInterface
    public void speak(String text) {
        act.speak(text);
    }

    @JavascriptInterface
    @SuppressWarnings("deprecation")
    public void vibrate(int ms) {
        Vibrator v = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.max(1, ms), VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(ms);
    }
}
