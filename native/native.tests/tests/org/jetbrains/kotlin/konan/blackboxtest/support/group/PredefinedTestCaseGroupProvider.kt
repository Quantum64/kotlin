/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest.support.group

import org.jetbrains.kotlin.konan.blackboxtest.support.TestCaseGroup
import org.jetbrains.kotlin.konan.blackboxtest.support.TestEnvironment
import java.io.File

internal class PredefinedTestCaseGroupProvider(
    private val environment: TestEnvironment,
    private val annotation: PredefinedTestCaseGroups
) : TestCaseGroupProvider {
    override fun getTestCaseGroup(testDataDir: File): TestCaseGroup? {
        TODO("Not yet implemented")
    }
}
