package com.gamehacker.app

import kotlinx.coroutines.*

object FreezeManager {
    private val frozen = mutableMapOf<Long, Long>()
    private var job: Job? = null
    var currentPid: Int = 0

    fun addFreeze(address: Long, value: Long) {
        frozen[address] = value
        start()
    }

    fun addAll(addresses: List<Long>, value: Long) {
        addresses.forEach { frozen[it] = value }
        start()
    }

    fun clear() {
        job?.cancel()
        job = null
        frozen.clear()
    }

    fun count() = frozen.size

    private fun start() {
        if (job?.isActive == true || currentPid == 0) return
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                frozen.toMap().forEach { (addr, value) ->
                    MemoryScanner.writeValue(currentPid, addr, value)
                }
                delay(60)
            }
        }
    }
}
