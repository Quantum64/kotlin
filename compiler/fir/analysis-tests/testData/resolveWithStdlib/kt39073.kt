// MODULE: A
// FILE: foo/A.kt

class Regex(val string: String) {
    fun check() = this
}

// FILE: foo.kt
import kotlin.text.* // explicit star import
import foo.*

fun testFoo() {
    val resolvesToFoo = Regex("").check()
}

// MODULE: B
// FILE: bar/B.kt

class Regex {
    fun check() = this
}

// FILE: bar.kt
import kotlin.text.* // explicit star import
import bar.*

fun testBar() {
    val resolvesToStdlib = Regex("").<!UNRESOLVED_REFERENCE!>check<!>()
}
