package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

data class DataClass(val `break`: String) {
    init {
        testNotRenamed("break", { `break` })
    }
}

fun box(): String {
    DataClass("123")

    return "OK"
}