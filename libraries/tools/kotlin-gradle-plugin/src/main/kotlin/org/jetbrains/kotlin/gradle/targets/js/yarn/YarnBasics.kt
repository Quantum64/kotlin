/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.yarn

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.service.ServiceRegistry
import org.jetbrains.kotlin.gradle.internal.execWithProgress
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmApi
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency.Scope.PEER
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmEnvironment
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinCompilationNpmResolution
import java.io.File

abstract class YarnBasics : NpmApi {

    private val nonTransitiveResolvedDependencies = mutableMapOf<NpmDependency, Set<File>>()
    private val transitiveResolvedDependencies = mutableMapOf<NpmDependency, Set<File>>()

    override fun setup(project: Project) {
        YarnPlugin.apply(project).executeSetup()
    }

    fun yarnExec(
        services: ServiceRegistry,
        logger: Logger,
        nodeJs: NpmEnvironment,
        yarn: YarnEnv,
        dir: File,
        description: String,
        args: List<String>
    ) {
        services.execWithProgress(description) { exec ->
            val arguments = args +
                    if (logger.isDebugEnabled) "--verbose" else "" +
                            if (yarn.ignoreScripts) "--ignore-scripts" else ""

            val nodeExecutable = nodeJs.nodeExecutable
            exec.environment(
                "PATH",
                "$nodeExecutable${File.pathSeparator}${System.getenv("PATH")}"
            )

            val command = yarn.executable
            if (yarn.standalone) {
                exec.executable = command
                exec.args = arguments
            } else {
                exec.executable = nodeExecutable
                exec.args = listOf(command) + arguments
            }

            exec.workingDir = dir
        }

    }

    override fun resolveDependency(
        npmResolution: KotlinCompilationNpmResolution,
        dependency: NpmDependency,
        transitive: Boolean
    ): Set<File> {
        val files = (if (transitive) {
            transitiveResolvedDependencies
        } else {
            nonTransitiveResolvedDependencies
        })[dependency]

        if (files != null) {
            return files
        }

        val npmProject = npmResolution.npmProject

        val all = mutableSetOf<File>()

        npmProject.resolve(dependency.key)?.let {
            if (it.isFile) all.add(it)
            if (it.path.endsWith(".js")) {
                val baseName = it.path.removeSuffix(".js")
                val metaJs = File(baseName + ".meta.js")
                if (metaJs.isFile) all.add(metaJs)
                val kjsmDir = File(baseName)
                if (kjsmDir.isDirectory) {
                    kjsmDir.walkTopDown()
                        .filter { it.extension == "kjsm" }
                        .forEach { all.add(it) }
                }
            }
        }

        nonTransitiveResolvedDependencies[dependency] = all

        if (transitive) {
            dependency.dependencies.forEach {
                resolveDependency(
                    npmResolution,
                    it,
                    transitive
                ).also { files ->
                    all.addAll(files)
                }
            }
            transitiveResolvedDependencies[dependency] = all
        }

        return all
    }

    protected fun yarnLockReadTransitiveDependencies(
        nodeWorkDir: File,
        srcDependenciesList: Collection<NpmDependency>
    ) {
        val yarnLock = nodeWorkDir
            .resolve("yarn.lock")
            .takeIf { it.isFile }
            ?: return

        val entryRegistry = YarnEntryRegistry(yarnLock)
        val visited = mutableMapOf<NpmDependency, NpmDependency>()

        fun resolveRecursively(src: NpmDependency) {
            if (src.scope == PEER) {
                return
            }

            val copy = visited[src]
            if (copy != null) {
                src.resolvedVersion = copy.resolvedVersion
                src.integrity = copy.integrity
                src.dependencies.addAll(copy.dependencies)
                return
            }
            visited[src] = src

            val deps = entryRegistry.find(src.key, src.version)

            src.resolvedVersion = deps.version
            src.integrity = deps.integrity

            deps.dependencies.mapTo(src.dependencies) { dep ->
                val scopedName = dep.scopedName
                val child = NpmDependency(
                    project = src.project,
                    name = scopedName.toString(),
                    version = dep.version ?: "*"
                )
                child.parent = src

                resolveRecursively(child)

                child
            }
        }

        srcDependenciesList.forEach { src ->
            resolveRecursively(src)
        }
    }
}
