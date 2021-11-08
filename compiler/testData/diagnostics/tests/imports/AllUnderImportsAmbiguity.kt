// FIR_IDENTICAL
// FILE: a.kt
package a

class X

// FILE: b.kt
package b

class X

// FILE: c.kt
package c

import a.*
import b.*

class Y : <!AMBIGUOUS_TYPES!>X<!>
