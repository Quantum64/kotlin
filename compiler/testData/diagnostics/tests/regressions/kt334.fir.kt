// !CHECK_TYPE
// WITH_EXTENDED_CHECKERS

import <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.Comparable<!> as Comparable

fun f(c: <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>Comparable<*><!>) {
    checkSubtype<kotlin.Comparable<*>>(<!ARGUMENT_TYPE_MISMATCH!>c<!>)
    checkSubtype<<!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.Comparable<*><!>>(c)
}
