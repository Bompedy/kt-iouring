@file:Suppress("NOTHING_TO_INLINE")
package com.github.mynt

import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted

suspend inline fun <Type> continued(
    crossinline block: (Continuation<Type>) -> (Any?)
) = suspendCoroutineUninterceptedOrReturn(block)

@OptIn(InternalCoroutinesApi::class)
inline fun <Type> Continuation<Type>.intercept(
    noinline handler: CompletionHandler
) = intercepted().apply {
    context[Job]!!.invokeOnCompletion(
        onCancelling = true, invokeImmediately = true, handler
    )
}

inline val SUSPENDED get() = COROUTINE_SUSPENDED
