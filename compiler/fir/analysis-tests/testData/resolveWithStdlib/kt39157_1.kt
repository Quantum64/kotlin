// FILE: TestCase5.kt
package testPackCase5
import libCase5.a.*
import libCase5.b.*

fun case5() {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>Regex<!>("")
}

// FILE: Lib1.kt
package libCase5.a
fun Regex(pattern: String) {}

// FILE: Lib2.kt
package libCase5.b
class Regex(pattern: String) {}
