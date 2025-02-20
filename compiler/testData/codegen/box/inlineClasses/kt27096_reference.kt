// WITH_STDLIB

@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@kotlin.jvm.JvmInline
value class Z1(val x: String)
@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@kotlin.jvm.JvmInline
value class Z2(val z: Z1)
@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@kotlin.jvm.JvmInline
value class ZN(val z: Z1?)
@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@kotlin.jvm.JvmInline
value class ZN2(val z: ZN)

fun wrap1(x: String): Z1? = if (x.length == 0) null else Z1(x)
fun wrap2(x: String): Z2? = if (x.length == 0) null else Z2(Z1(x))
fun wrapN(x: String): ZN? = if (x.length == 0) null else ZN(Z1(x))
fun wrapN2(x: String): ZN2? = if (x.length == 0) null else ZN2(ZN(Z1(x)))

fun box(): String {
    if (wrap1("") != null) throw AssertionError()
    if (wrap1("a") == null) throw AssertionError()
    if (wrap1("a")!!.x != "a") throw AssertionError()

    if (wrap2("") != null) throw AssertionError()
    if (wrap2("a") == null) throw AssertionError()
    if (wrap2("a")!!.z.x != "a") throw AssertionError()

    if (wrapN("") != null) throw AssertionError()
    if (wrapN("a") == null) throw AssertionError()
    if (wrapN("a")!!.z!!.x != "a") throw AssertionError()

    if (wrapN2("") != null) throw AssertionError()
    if (wrapN2("a") == null) throw AssertionError()
    if (wrapN2("a")!!.z.z!!.x != "a") throw AssertionError()

    return "OK"
}