// FILE: TestCase4.kt
package testPackCase4
import libCase4.a.*
import libCase4.b.*
import kotlin.text.*

fun case4() {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>Regex<!>("")
}

// FILE: Lib1.kt
package libCase4.a
fun Regex(pattern: String) {}

// FILE: Lib2.kt
package libCase4.b
class Regex(pattern: String) {}
