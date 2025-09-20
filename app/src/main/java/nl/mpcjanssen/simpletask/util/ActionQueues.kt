package nl.mpcjanssen.simpletask.util

import android.util.Log
import kotlinx.coroutines.*

open class ActionQueue(val qName: String) {

    // Create a background scope with a single thread dispatcher for sequential execution
    private val queueScope = CoroutineScope(
        Dispatchers.IO.limitedParallelism(1) + SupervisorJob()
    )

    fun add(description: String, r: () -> Unit) {
        Log.i(qName, "-> $description")
        queueScope.launch {
            Log.i(qName, "<- $description")
            r.invoke()
        }
    }

    // Optional: Method to cancel all pending operations
    fun cancelAll() {
        queueScope.cancel()
    }

    // Optional: Method to check if queue is active
    fun isActive(): Boolean = queueScope.isActive

    fun start() {
        queueScope.ensureActive()

    }
}

object FileStoreActionQueue : ActionQueue("FSQ")