package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

enum class Foo {
    BAR;
    fun implements() { implements() }

    fun test() {
        testNotRenamed("implements", { ::implements })
    }
}

fun box(): String {
    Foo.BAR.test()

    return "OK"
}