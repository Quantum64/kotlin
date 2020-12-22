/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.components

import org.jetbrains.kotlin.psi.KtFile

abstract class KtReferenceShortener : KtAnalysisSessionComponent() {
    abstract fun collectShortenings(file: KtFile, from: Int, to: Int): ShortenCommand
}

interface ShortenCommand {
    fun invokeShortening()
}