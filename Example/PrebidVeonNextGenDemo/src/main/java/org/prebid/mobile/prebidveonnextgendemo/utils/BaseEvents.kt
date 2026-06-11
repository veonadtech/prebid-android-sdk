package org.prebid.mobile.prebidveonnextgendemo.utils

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.IdRes
import org.prebid.mobile.prebidveonnextgendemo.widgets.EventCounterView
import java.lang.ref.WeakReference

abstract class BaseEvents(parentView: View) {

    private val parentViewReference = WeakReference(parentView)
    private val mainHandler = Handler(Looper.getMainLooper())

    protected fun enable(@IdRes idRes: Int, value: Boolean) {
        mainHandler.post {
            val view = parentViewReference.get() ?: return@post
            view.findViewById<EventCounterView>(idRes)?.isEnabled = value
        }
    }

}