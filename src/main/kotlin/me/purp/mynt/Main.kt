package me.purp.mynt

class Test {
    external fun printHello()
    companion object {
        init {
            System.loadLibrary("mynt-hooks")
        }
    }
}

fun main() {
    val test = Test()
    test.printHello()
}
