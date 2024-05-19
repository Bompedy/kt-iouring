package com.github.mynt

import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executor

fun main() = runBlocking {
    val provider = Provider(65535)

    while (true)
        provider.accept(InetSocketAddress("127.0.0.1", 6969)).apply {

        }
}
