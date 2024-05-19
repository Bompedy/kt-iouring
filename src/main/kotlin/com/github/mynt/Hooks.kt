package com.github.mynt

import com.github.exerosis.mynt.SocketProvider
import kotlinx.coroutines.*
import sun.misc.Unsafe
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

object Hooks {
    external fun connect(epoll: Int, provider: Provider, uuid: UUID, address: String, port: Int)
    external fun accept(epoll: Int, provider: Provider, uuid: UUID, address: String, port: Int)
    external fun close(epoll: Int, socket: Int)
    external fun read(epoll: Int, connection: Connection, socket: Int, from: Long, to: Long, end: Long): Boolean
    external fun write(epoll: Int, connection: Connection, socket: Int, from: Long, to: Long): Boolean
    external fun epoll(): Int
}

interface Provider {
    suspend fun connect(address: InetSocketAddress): Connection
    suspend fun accept(address: InetSocketAddress): Connection
    fun onConnected(uuid: UUID, socket: Int, result: Int, error: Int)
}

interface Connection {
    val socket: Int
    val read: Read
    val write: Write
    fun close()
}

interface Write {
    suspend fun int(value: Int)
    fun onWrite()
    fun close()
}

interface Read {
    suspend fun int(): Int
    fun onRead(position: Long, next: Long)
    fun close()
}

const val ERROR_CREATE_SOCKET = 0
const val ERROR_SET_NON_BLOCKING = 1
const val ERROR_CREATE_CONNECTION = 2
const val ERROR_BIND_SOCKET = 3
const val ERROR_LISTEN_SOCKET = 4

fun Provider(size: Int): Provider {
    val epoll = Hooks.epoll()
    if (epoll < 0) throw Throwable("Error creating epoll: $epoll")

    println("Created provider with epoll fd: $epoll")
    return object : Provider {
        val continuations = HashMap<UUID, Continuation<Connection>>()

        override fun onConnected(uuid: UUID, socket: Int, result: Int, error: Int) {
            val continuation = continuations.remove(uuid)!!
            if (error != -1) {
                when (error) {
                    ERROR_CREATE_SOCKET -> {
                        continuation.resumeWithException(Throwable("Error creating socket: $result"))
                    }
                    ERROR_SET_NON_BLOCKING -> {
                        continuation.resumeWithException(Throwable("Error setting socket to non blocking: $result"))
                    }
                    ERROR_CREATE_CONNECTION -> {
                        continuation.resumeWithException(Throwable("Error creating connection: $result"))
                    }
                    ERROR_BIND_SOCKET -> {
                        continuation.resumeWithException(Throwable("Error binding socket: $result"))
                    }
                    ERROR_LISTEN_SOCKET -> {
                        continuation.resumeWithException(Throwable("Error listening on socket: $result"))
                    }
                }
            } else continuation.resume(Connection(epoll, socket, size))
        }

        override suspend fun accept(address: InetSocketAddress) = suspendCoroutineUninterceptedOrReturn {
            val continuationId = UUID.randomUUID()
            Hooks.accept(epoll, this, continuationId, address.hostName, address.port)
            continuations[continuationId] = it.intercepted()
            COROUTINE_SUSPENDED
        }

        override suspend fun connect(address: InetSocketAddress) = suspendCoroutineUninterceptedOrReturn {
            val continuationId = UUID.randomUUID()
            continuations[continuationId] = it.intercepted()
            Hooks.connect(epoll, this, continuationId, address.hostName, address.port)
            COROUTINE_SUSPENDED
        }
    }
}


fun Connection(epoll: Int, socket: Int, size: Int) = object : Connection {
    override val socket = socket
    override val read = Read(epoll, this, socket, size)
    override val write = Write(epoll, this, socket, size)
    override fun close() {
        Hooks.close(epoll, socket)
        read.close()
        write.close()
    }
}

//TODO: add to classpath later
val UNSAFE = Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as Unsafe

fun Write(epoll: Int, connection: Connection, socket: Int, size: Int): Write {
    return object : Write {
        val start = UNSAFE.allocateMemory(size.toLong())
        val end = start + size
//        var current = start

        var continuation: Continuation<Unit>? = null

        override fun close() {
            UNSAFE.freeMemory(start)
        }

        override fun onWrite() {
            UNSAFE.loadFence()
            continuation!!.resume(Unit)
            continuation = null
        }

        override suspend fun int(value: Int) = suspendCoroutineUninterceptedOrReturn<Unit> {
            val next = start + 4
            if (next > end) it.resumeWithException(Throwable("Buffer max is $size"))
            UNSAFE.putInt(start, value)
            if (Hooks.write(epoll, connection, socket, start, next)) Unit else {
                continuation = it.intercepted()
                UNSAFE.storeFence()
                COROUTINE_SUSPENDED
            }
        }
    }
}

fun Read(epoll: Int, connection: Connection, socket: Int, size: Int): Read {
    return object : Read {
        val start = UNSAFE.allocateMemory(size.toLong())
        val end = start + size
        var current = start
        var position = start

        var continuation: Continuation<Int>? = null

        override fun close() {
            UNSAFE.freeMemory(start)
        }

        override fun onRead(position: Long, next: Long) {
            UNSAFE.loadFence()
            this.position = position
            continuation!!.resume(UNSAFE.getInt(current))
            current = next
            continuation = null
        }

        override suspend fun int() = suspendCoroutineUninterceptedOrReturn {
            val next = current + 4
            if (next < position) {
                current = next
                UNSAFE.getInt(current)
            } else if (Hooks.read(epoll, connection, socket, current, next, end)) {
                current = next
                UNSAFE.getInt(current)
            } else {
                continuation = it.intercepted()
                UNSAFE.storeFence()
                COROUTINE_SUSPENDED
            }
        }
    }
}

fun main(): Unit = runBlocking {
    System.load(Paths.get("./libmynt-hooks.so").toFile().absolutePath)

    val address = InetSocketAddress("0.0.0.0", 6969)

    GlobalScope.launch {
        val executor = Executors.newFixedThreadPool(5)
        val group = AsynchronousChannelGroup.withThreadPool(executor)
        val provider = SocketProvider( 65535, group)

        while (isActive) {
            println("waiting for one")
            provider.accept(address).apply {
                println("got connection nice")
            }
        }
    }

    delay(3.seconds)

    val provider = Provider(65535)
    provider.connect(address).apply {
        println("Got connection lets go!!! ${this.socket}")
        delay(3.seconds)
        close()
    }

    delay(INFINITE)
}