package com.caidan.menu

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MenuApp : Application() {

    lateinit var container: AppContainer
        private set

    /** 只用来跑启动时的杂活；进程活着就够，不需要取消。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 用完的条目留两周就清掉。挂在启动时，不必等用户点开冰箱
        appScope.launch { container.fridgeRepository.purgeUsedUp() }
    }
}
