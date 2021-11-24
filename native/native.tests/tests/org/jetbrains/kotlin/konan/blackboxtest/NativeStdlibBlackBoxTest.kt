/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")

package org.jetbrains.kotlin.konan.blackboxtest

import org.jetbrains.kotlin.konan.blackboxtest.support.group.PredefinedTestCaseGroups
import org.jetbrains.kotlin.konan.blackboxtest.support.group.PredefinedTestCaseGroups.Location
import org.junit.jupiter.api.TestFactory

@PredefinedTestCaseGroups(
    testCaseGroups = [
        Location("libraries/stdlib/test"),
        Location("libraries/kotlin.test/common/src/test/kotlin"),
        Location("kotlin-native/backend.native/tests/stdlib_external/text")
    ],
    sharedModule = Location(
        "libraries/stdlib/common/test",
        "kotlin-native/backend.native/tests/stdlib_external/utils.kt",
        "kotlin-native/backend.native/tests/stdlib_external/jsCollectionFactoriesActuals.kt"
    )
)
class NativeStdlibBlackBoxTest : AbstractNativeBlackBoxTest() {
    @TestFactory
    fun stdlib() = dynamicTest("libraries/stdlib/test")

    @TestFactory
    fun kotlinTest() = dynamicTest("libraries/kotlin.test/common/src/test/kotlin")

    @TestFactory
    fun nativeStdlib() = dynamicTest("kotlin-native/backend.native/tests/stdlib_external/text")
}
