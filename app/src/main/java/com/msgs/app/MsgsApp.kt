package com.msgs.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.msgs.app.data.Prefs
import com.msgs.app.engine.AvatarRepo
import com.msgs.app.engine.Engine
import com.msgs.app.engine.MediaRepo
import com.msgs.app.notify.Notifications
import com.msgs.app.util.CrashLog

/** Whether a Msgs screen is currently on top. */
object AppState {
    @Volatile
    var visible = false
}

class MsgsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Prefs.init(this)
        AvatarRepo.init(this)
        MediaRepo.init(this)
        Notifications.createChannels(this)
        Engine.init(this)
        // Stay connected while Msgs is open; drop the connection a bit after it's closed.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Engine.onForeground()
            override fun onStop(owner: LifecycleOwner) = Engine.onBackground()
        })
    }
}
