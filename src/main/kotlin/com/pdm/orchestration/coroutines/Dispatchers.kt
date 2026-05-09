package com.pdm.orchestration.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

val Dispatchers.Virtual by lazy {
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
}
