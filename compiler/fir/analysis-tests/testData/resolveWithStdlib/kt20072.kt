// FILE: 1.kt
package a
class A

fun A(s: String = "") {}

// FILE: 2.kt
package b
class A

// FILE: 3.kt
package c
import a.*
import b.*

val test = <!OVERLOAD_RESOLUTION_AMBIGUITY!>A<!>()
