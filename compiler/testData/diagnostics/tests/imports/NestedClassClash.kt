// FIR_IDENTICAL
// !DIAGNOSTICS: -UNUSED_PARAMETER
// FILE: a.kt
package a

class A {
    class B
}

// FILE: b.kt
package a

class D {
    class B
}

// FILE: c.kt
import a.A.<!CONFLICTING_IMPORT!>B<!>
import a.D.<!CONFLICTING_IMPORT!>B<!>

fun test(b: <!AMBIGUOUS_TYPES!>B<!>) {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>B<!>()
}

// FILE: d.kt
import a.A.*
import a.D.*

// todo ambiguvity here
fun test2(b: <!AMBIGUOUS_TYPES!>B<!>) {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>B<!>()
}
