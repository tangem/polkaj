package io.emeraldpay.polkaj.apihttp.ext

import java.util.concurrent.CompletableFuture

fun <T> CompletableFuture<T>.failedFuture(ex: Throwable): CompletableFuture<T> {
    this.completeExceptionally(ex)
    return this
}

fun <T> failedFuture(ex: Throwable): CompletableFuture<T> {
    return CompletableFuture<T>().failedFuture(ex)
}