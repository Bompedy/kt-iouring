package me.purp.mynt

fun main() {
    val hooks = Hooks()
    if (hooks.setupRing(16) < 0) {
        println("Error setting up ring.")
    }
    val socket = hooks.connect("127.0.0.1", 8080)
    if (socket < 0) println("cant connect")
}
