package me.purp.mynt

class Hooks {
    external fun setupRing(queueDepth: Int): Int
    external fun connect(address: String, port: Int): Int
    external fun accept(address: String, port: Int): Int
    external fun close(port: Int): Int

    companion object {
        init {
            System.loadLibrary("mynt-hooks")
        }
    }
}