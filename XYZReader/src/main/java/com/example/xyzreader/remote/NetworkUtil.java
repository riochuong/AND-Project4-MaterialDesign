package com.example.xyzreader.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import timber.log.Timber;

import static android.content.Context.CONNECTIVITY_SERVICE;

/**
 * Created by chuondao on 6/6/17.
 */

public final class NetworkUtil {

    /**
     * Helper for checking
     * @param ctx
     * @return
     */
    public static boolean isConnectToNetWork(Context ctx){
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            Timber.e("Not online, not refreshing.");
            return false;
        }
        return true;
    }
}
