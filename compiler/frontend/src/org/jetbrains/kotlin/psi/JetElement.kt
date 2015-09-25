/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiReference

public interface JetElement : NavigatablePsiElement {
    public fun getContainingJetFile(): JetFile

    public fun <D> acceptChildren(visitor: JetVisitor<Void, D>, data: D)

    public fun <R, D> accept(visitor: JetVisitor<R, D>, data: D): R

    @Deprecated("Don't use getReference() on JetElement for the choice is unpredictable")
    override fun getReference(): PsiReference?
}

public fun JetElement.getModificationStamp(): Long {
    return when (this) {
        is JetFile -> this.modificationStamp
        is JetDeclarationStub<*> -> this.modificationStamp
        else -> (parent as JetElement).getModificationStamp()
    }
}
