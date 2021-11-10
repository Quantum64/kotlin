/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.cli.jvm.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.resolve.jvm.KotlinCliJavaFileManager
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModule
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleFinder
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleInfo
import java.io.File

class CliJavaModuleFinder(
    private val jdkHome: File?,
    private val messageCollector: MessageCollector?,
    private val javaFileManager: KotlinCliJavaFileManager,
    project: Project,
    private val releaseTarget: Int
) : JavaModuleFinder {

    private val jrtFileSystemRoot = jdkHome?.path?.let { path ->
        VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.JRT_PROTOCOL)?.findFileByPath(path + URLUtil.JAR_SEPARATOR)
    }

    private val modulesRoot = jrtFileSystemRoot?.findChild("modules")

    private val userModules = linkedMapOf<String, JavaModule>()

    private val allScope = GlobalSearchScope.allScope(project)

    private val ctSymFile: VirtualFile? by lazy {
        if (jdkHome == null) return@lazy reportError("JDK_HOME path is not specified in compiler configuration")

        val jdkRootFile = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL).findFileByPath(jdkHome.path)
            ?: return@lazy reportError("Can't create virtual file for JDK root under ${jdkHome.path}")

        val lib = jdkRootFile.findChild("lib") ?: return@lazy reportError("Can't find `lib` folder under JDK root: ${jdkHome.path}")

        val ctSym = lib.findChild("ct.sym") ?: return@lazy reportError("Can't find `ct.sym` file in ${jdkHome.path}")

        if (!ctSym.isValid) return@lazy reportError("`ct.sym` is invalid: ${ctSym.path}")

        ctSym
    }

    private val ctSymRootFolder: VirtualFile? by lazy {
        if (ctSymFile != null) {
            VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.JAR_PROTOCOL)
                ?.findFileByPath(ctSymFile?.path + URLUtil.JAR_SEPARATOR)
                ?: reportError("Can't open `ct.sym` as jar file, file path: ${ctSymFile?.path} ")
        } else {
            null
        }
    }

    private val compilationJdkVersion by lazy {
        // Observe all JDK codes from folder name chars in ct.sym file,
        //  there should be maximal one corresponding to used compilation JDK
        ctSymRootFolder?.children?.maxOf {
            if (it.name == "META-INF") -1
            else it.name.substringBeforeLast("-modules").maxOf { char ->
                char.toString().toIntOrNull(36) ?: -1
            }
        } ?: -1
    }

    val ctSymModules by lazy {
        collectModuleRoots()
    }

    private val isCompilationJDK12OrLater
        get() = compilationJdkVersion >= 12

    private val useLastJdkApi: Boolean
        get() = releaseTarget == 0 || releaseTarget == compilationJdkVersion

    fun addUserModule(module: JavaModule) {
        userModules.putIfAbsent(module.name, module)
    }

    val allObservableModules: Sequence<JavaModule>
        get() = systemModules + userModules.values

    val systemModules: Sequence<JavaModule.Explicit>
        get() = if (useLastJdkApi) modulesRoot?.children.orEmpty().asSequence().mapNotNull(this::findSystemModule) else
            ctSymModules.values.asSequence().mapNotNull { findSystemModule(it, true) }

    override fun findModule(name: String): JavaModule? =
        when {
            useLastJdkApi -> modulesRoot?.findChild(name)?.let(this::findSystemModule)
            else -> ctSymModules[name]?.let { findSystemModule(it, true) }
        } ?: userModules[name]

    private fun findSystemModule(moduleRoot: VirtualFile, useSig: Boolean = false): JavaModule.Explicit? {
        val file = moduleRoot.findChild(if (useSig) PsiJavaModule.MODULE_INFO_CLASS + ".sig" else PsiJavaModule.MODULE_INFO_CLS_FILE)
            ?: return null
        val moduleInfo = JavaModuleInfo.read(file, javaFileManager, allScope) ?: return null
        return JavaModule.Explicit(
            moduleInfo,
            when {
                useLastJdkApi -> listOf(JavaModule.Root(moduleRoot, isBinary = true, isBinarySignature = useSig))
                //TODO: distinguish roots from different modules under JDK 10-11
                useSig -> listFoldersForRelease().map { JavaModule.Root(it, isBinary = true, isBinarySignature = true) }
                else -> error("Can't find ${moduleRoot.path} module")
            },
            file, true
        )
    }

    private fun codeFor(release: Int): String = release.toString(36).toUpperCase()

    private fun matchesRelease(fileName: String, release: Int) =
        !fileName.contains("-") && fileName.contains(codeFor(release)) // skip `*-modules`

    fun listFoldersForRelease(): List<VirtualFile> {
        if (ctSymRootFolder == null) return emptyList()
        return ctSymRootFolder!!.children.filter { matchesRelease(it.name, releaseTarget) }.flatMap {
            if (isCompilationJDK12OrLater)
                it.children.toList()
            else {
                listOf(it)
            }
        }.apply {
            if (isEmpty()) reportError("'-Xrelease=${releaseTarget}' option is not supported by used JDK: ${jdkHome?.path}")
        }
    }

    private fun collectModuleRoots(): Map<String, VirtualFile> {
        val result = mutableMapOf<String, VirtualFile>()
        if (isCompilationJDK12OrLater) {
            listFoldersForRelease().forEach { modulesRoot ->
                modulesRoot.findChild("module-info.sig")?.let {
                    result[modulesRoot.name] = modulesRoot
                }
            }
        } else {
            if (this.releaseTarget > 8 && ctSymRootFolder != null) {
                ctSymRootFolder!!.findChild(codeFor(releaseTarget) + if (!isCompilationJDK12OrLater) "-modules" else "")?.apply {
                    children.forEach {
                        result[it.name] = it
                    }
                } ?: reportError("Can't find modules signatures in `ct.sym` file for `-Xrelease=$releaseTarget` in ${ctSymFile!!.path}")
            }
        }
        return result
    }

    private fun reportError(message: String): VirtualFile? {
        messageCollector?.report(CompilerMessageSeverity.ERROR, message)
        return null
    }
}
