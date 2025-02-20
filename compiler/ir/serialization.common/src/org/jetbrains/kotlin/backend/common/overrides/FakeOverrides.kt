/*
 * Copyright 2010-2020 JetBrains s.r.o.
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

package org.jetbrains.kotlin.backend.common.overrides

import org.jetbrains.kotlin.backend.common.serialization.CompatibilityMode
import org.jetbrains.kotlin.backend.common.serialization.DeclarationTable
import org.jetbrains.kotlin.backend.common.serialization.GlobalDeclarationTable
import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureSerializer
import org.jetbrains.kotlin.backend.common.serialization.signature.PublicIdSignatureComputer
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.overrides.FakeOverrideBuilderStrategy
import org.jetbrains.kotlin.ir.overrides.IrOverridingUtil
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrPropertySymbolImpl
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.*

class FakeOverrideGlobalDeclarationTable(
    mangler: KotlinMangler.IrMangler
) : GlobalDeclarationTable(mangler) {
    fun clear() = table.clear()
}

open class FakeOverrideDeclarationTable(
    mangler: KotlinMangler.IrMangler,
    globalTable: FakeOverrideGlobalDeclarationTable = FakeOverrideGlobalDeclarationTable(mangler),
    signatureSerializerFactory: (PublicIdSignatureComputer, DeclarationTable) -> IdSignatureSerializer
) : DeclarationTable(globalTable) {
    override val globalDeclarationTable: FakeOverrideGlobalDeclarationTable = globalTable
    override val signaturer: IdSignatureSerializer = signatureSerializerFactory(globalTable.publicIdSignatureComputer, this)
    fun clear() {
        this.table.clear()
        globalDeclarationTable.clear()
    }
}

interface FakeOverrideClassFilter {
    fun needToConstructFakeOverrides(clazz: IrClass): Boolean
}

interface FileLocalAwareLinker {
    fun tryReferencingSimpleFunctionByLocalSignature(parent: IrDeclaration, idSignature: IdSignature): IrSimpleFunctionSymbol?
    fun tryReferencingPropertyByLocalSignature(parent: IrDeclaration, idSignature: IdSignature): IrPropertySymbol?
}

object DefaultFakeOverrideClassFilter : FakeOverrideClassFilter {
    override fun needToConstructFakeOverrides(clazz: IrClass): Boolean = true
}

class FakeOverrideBuilder(
    val linker: FileLocalAwareLinker,
    val symbolTable: SymbolTable,
    mangler: KotlinMangler.IrMangler,
    typeSystem: IrTypeSystemContext,
    friendModules: Map<String, Collection<String>>,
    val platformSpecificClassFilter: FakeOverrideClassFilter = DefaultFakeOverrideClassFilter,
    private val fakeOverrideDeclarationTable: DeclarationTable = FakeOverrideDeclarationTable(mangler) { builder, table ->
        IdSignatureSerializer(builder, table)
    }
) : FakeOverrideBuilderStrategy(friendModules) {
    private val haveFakeOverrides = mutableSetOf<IrClass>()

    private val irOverridingUtil = IrOverridingUtil(typeSystem, this)

    // TODO: The declaration table is needed for the signaturer.
//    private val fakeOverrideDeclarationTable = FakeOverrideDeclarationTable(mangler, signatureSerializerFactory)

    val fakeOverrideCandidates = mutableMapOf<IrClass, CompatibilityMode>()
    fun enqueueClass(clazz: IrClass, signature: IdSignature, compatibilityMode: CompatibilityMode) {
        fakeOverrideDeclarationTable.assumeDeclarationSignature(clazz, signature)
        fakeOverrideCandidates[clazz] = compatibilityMode
    }

    private fun buildFakeOverrideChainsForClass(clazz: IrClass, compatibilityMode: CompatibilityMode) {
        if (haveFakeOverrides.contains(clazz)) return
        if (!platformSpecificClassFilter.needToConstructFakeOverrides(clazz)) return

        val superTypes = clazz.superTypes

        val superClasses = superTypes.map {
            it.getClass() ?: error("Unexpected super type: $it")
        }

        superClasses.forEach { superClass ->
            val mode = fakeOverrideCandidates[superClass] ?: compatibilityMode
            buildFakeOverrideChainsForClass(superClass, mode)
            haveFakeOverrides.add(superClass)
        }

        fakeOverrideDeclarationTable.run {
            inFile(clazz.fileOrNull) {
                irOverridingUtil.buildFakeOverridesForClass(clazz, compatibilityMode.oldSignatures)
            }
        }
    }

    override fun linkFunctionFakeOverride(declaration: IrFakeOverrideFunction, compatibilityMode: Boolean) {
        val signature = composeSignature(declaration, compatibilityMode)
        declareFunctionFakeOverride(declaration, signature)
    }

    override fun linkPropertyFakeOverride(declaration: IrFakeOverrideProperty, compatibilityMode: Boolean) {
        // To compute a signature for a property with type parameters,
        // we must have its accessor's correspondingProperty pointing to the property's symbol.
        // See IrMangleComputer.mangleTypeParameterReference() for details.
        // But to create and link that symbol we should already have the signature computed.
        // To break this loop we use temp symbol in correspondingProperty.

        val tempSymbol = IrPropertySymbolImpl().also {
            it.bind(declaration as IrProperty)
        }
        declaration.getter?.let {
            it.correspondingPropertySymbol = tempSymbol
        }
        declaration.setter?.let {
            it.correspondingPropertySymbol = tempSymbol
        }

        val signature = composeSignature(declaration, compatibilityMode)
        declarePropertyFakeOverride(declaration, signature)

        declaration.getter?.let {
            it.correspondingPropertySymbol = declaration.symbol
            linkFunctionFakeOverride(it as? IrFakeOverrideFunction ?: error("Unexpected fake override getter: $it"), compatibilityMode)
        }
        declaration.setter?.let {
            it.correspondingPropertySymbol = declaration.symbol
            linkFunctionFakeOverride(it as? IrFakeOverrideFunction ?: error("Unexpected fake override setter: $it"), compatibilityMode)
        }
    }

    private fun composeSignature(declaration: IrDeclaration, compatibleMode: Boolean) =
        fakeOverrideDeclarationTable.signaturer.composeSignatureForDeclaration(declaration, compatibleMode)

    private fun declareFunctionFakeOverride(declaration: IrFakeOverrideFunction, signature: IdSignature) {
        val parent = declaration.parentAsClass
        val symbol = linker.tryReferencingSimpleFunctionByLocalSignature(parent, signature)
            ?: symbolTable.referenceSimpleFunctionFromLinker(signature)
        symbolTable.declareSimpleFunction(signature, { symbol }) {
            assert(it === symbol)
            declaration.acquireSymbol(it)
        }
    }

    private fun declarePropertyFakeOverride(declaration: IrFakeOverrideProperty, signature: IdSignature) {
        val parent = declaration.parentAsClass
        val symbol = linker.tryReferencingPropertyByLocalSignature(parent, signature)
            ?: symbolTable.referencePropertyFromLinker(signature)
        symbolTable.declareProperty(signature, { symbol }) {
            assert(it === symbol)
            declaration.acquireSymbol(it)
        }
    }

    fun provideFakeOverrides(klass: IrClass, compatibleMode: CompatibilityMode) {
        buildFakeOverrideChainsForClass(klass, compatibleMode)
        irOverridingUtil.clear()
        haveFakeOverrides.add(klass)
    }

    fun provideFakeOverrides() {
        val entries = fakeOverrideCandidates.entries
        while (entries.isNotEmpty()) {
            val candidate = entries.last()
            entries.remove(candidate)
            provideFakeOverrides(candidate.key, candidate.value)
        }
    }
}
