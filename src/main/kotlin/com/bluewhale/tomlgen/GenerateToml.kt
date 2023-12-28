package com.bluewhale.tomlgen

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


class GenerateToml : AnAction() {

    private val lock1 = Any()
    private val lock2 = Any()
    private val lock3 = Any()
    private val errLock = Any()

    private val dataForFiles = ConcurrentHashMap<File, TypeBundle>()
    private val dependenciesForToml = mutableMapOf<String, String>()
    private val pluginsForToml = mutableMapOf<String, String>()
    private val versionsForToml = mutableMapOf<String, String>()

    private lateinit var progressIndicator: ProgressIndicator

    companion object {
        const val VERSIONS = "versions"
        const val LIBRARIES = "libraries"
        const val PLUGINS = "plugins"
        const val GROUP = "group"

        const val STATEMENT = "# GenerateToml at"
        const val DATE_TIME_PATTERN = "YYYY-MM-dd HH:mm:ss"
        const val TOML_FILE_NAME = "libs.versions.toml"

        const val STEP_SEARCH_FOR_GRADLE = 1
        const val STEP_EXTRACT_DEP_PLUGIN = 2
        const val STEP_EXTRACT_TOML = 3
        const val STEP_WRITE_IN_TEMP_TOML = 4
        const val STEP_WRITE_IN_TOML = 5
        const val STEP_UPDATE = 6
        const val TOTAL_STEPS = 6
    }

    override fun actionPerformed(e: AnActionEvent) {

        val gradleFiles = mutableListOf<File>()
        var tomlFile: File? = null
        var tempTomlFile: File? = null

        // 1. Finding Project
        e.project?.let { project ->

            object : Task.Backgroundable(project, "Generating TOML") {
                override fun run(indicator: ProgressIndicator) {
                    progressIndicator = indicator
                    progressIndicator.text = "Searching for Gradle Files"
                    progressIndicator.fraction = STEP_SEARCH_FOR_GRADLE.toDouble() / 7.toDouble()

                    Thread.sleep(1000)

                    project.basePath?.let { basePath ->
                        File(basePath).walk(FileWalkDirection.BOTTOM_UP).forEach {
                            if (it.isFile && it.name.contains(".gradle")) {
                                gradleFiles.add(it)
                            }
                            if (it.isFile && it.name == TOML_FILE_NAME) {
                                tomlFile = it
                            }
                        }

                        // clean up the log
                        resetErrorLog(basePath)
                    }

                    if (gradleFiles.size == 0) {
                        progressIndicator.text = "No Gradle File Found"
                        progressIndicator.fraction = 1.toDouble()
                        progressIndicator.stop()
                        Thread.sleep(500)
                        return
                    }

                    progressIndicator.text = "Extracting Info from Gradle Files."
                    progressIndicator.fraction = (STEP_EXTRACT_DEP_PLUGIN.toDouble() / TOTAL_STEPS.toDouble())
                    Thread.sleep(500)

                    // 2. Reading Gradle Files -> gradleFiles.count
                    val executors = Executors.newFixedThreadPool(gradleFiles.size)
                    val tasks = mutableListOf<Callable<Unit>>()

                    gradleFiles.forEach { file ->
                        // 3. Extracting Dependencies and Plugins (#/GradleFileCount)

                        val content = file.readLines()

                        if (content.isNotEmpty()) {
                            val singleLine = content.reduce { acc, s -> "$acc\n$s" }
                            tasks.add(Callable<Unit> { extractDependenciesAndPlugins(singleLine, file) })
                        }
                    }

                    executors.invokeAll(tasks)

                    var remainingTomlStr = ""

                    if (tomlFile != null) {

                        progressIndicator.text = "Extracting Data from libs.versions.toml."
                        progressIndicator.fraction = (STEP_EXTRACT_TOML.toDouble() / TOTAL_STEPS.toDouble())
                        Thread.sleep(500)

                        // read toml File
                        tomlFile?.let {
                            val content = it.readLines()
                            var singleLine = ""
                            if (content.isNotEmpty()) {
                                singleLine =
                                    content.reduce { acc, str -> if (acc.contains(STATEMENT)) str else "$acc\n$str" }

                                singleLine = Parser.extractInfoFromToml(singleLine,
                                    { vKey, vValue ->

                                        if (!versionsForToml.contains(vKey)) {
                                            versionsForToml[vKey] = vValue
                                        }

                                    }, { dKey, dValue ->

                                        if (!dependenciesForToml.contains(dKey)) {
                                            dependenciesForToml[dKey] = dValue
                                        }
                                    }, { pKey, pValue ->
                                        if (!pluginsForToml.contains(pKey)) {
                                            pluginsForToml[pKey] = pValue
                                        }
                                    })
                            }

                            remainingTomlStr = singleLine
                        }
                    }

                    // 7. Sort all versions, dependencies and plugins
                    val versionKeys = versionsForToml.keys.sorted()
                    val sortedValidDependencies = dependenciesForToml.keys.sorted()
                    val sortedValidPlugins = pluginsForToml.keys.sorted()

                    // 8. Write versions, dependencies and plugins into a temp file
                    project.basePath?.let { basePath ->
                        progressIndicator.text = "Storing Data into Temporary TOML file."
                        progressIndicator.fraction = (STEP_WRITE_IN_TEMP_TOML.toDouble() / TOTAL_STEPS.toDouble())
                        Thread.sleep(500)
                        // 7. create a temp libs.versions.toml file
                        if (tomlFile == null) {
                            tomlFile = File("$basePath/gradle/libs.versions.toml")
                        }

                        try {
                            // 9. write all into temp file
                            var writer = FileWriter(tomlFile!!)

                            val strBuilder = StringBuilder()
                            val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN))
                            strBuilder.append("$STATEMENT $dateStr\n\n")

                            // write version
                            strBuilder.append("[$VERSIONS]\n")
                            versionKeys.forEach { key ->
                                strBuilder.append("$key = \"${versionsForToml[key]}\"\n")
                            }

                            // write dependencies
                            strBuilder.append("\n[$LIBRARIES]\n")
                            sortedValidDependencies.forEach { key ->
                                // {group = "androidx.core", name = "core-ktx", version.ref ="androidxCore"}
                                strBuilder.append("$key = ${dependenciesForToml[key]}\n")
                            }

                            // write plugins
                            strBuilder.append("\n[$PLUGINS]\n")
                            sortedValidPlugins.forEach { key ->
                                // android-application = {id = "com.android.application", version.ref="androidApplication"}
                                strBuilder.append("$key = ${pluginsForToml[key]}\n")
                            }

                            strBuilder.append("\n")
                            strBuilder.append(remainingTomlStr)

                            progressIndicator.text = "Storing Data into libs.versions.toml."
                            progressIndicator.fraction = (STEP_WRITE_IN_TOML.toDouble() / TOTAL_STEPS.toDouble())
                            Thread.sleep(500)

                            writer.write(strBuilder.toString())
                            writer.close()

                            tasks.clear()

                            // update files
                            progressIndicator.text = "Update All Gradle Files."
                            progressIndicator.fraction = (STEP_UPDATE.toDouble() / TOTAL_STEPS.toDouble())
                            Thread.sleep(500)

                            dataForFiles.forEach {
                                tasks.add(Callable<Unit> {
                                    updateFile(it.key, it.value.dependencies, it.value.plugins, basePath)
                                })
                            }
                            executors.invokeAll(tasks)

                            progressIndicator.text = "Done"
                            progressIndicator.fraction = 1.toDouble()
                            progressIndicator.stop()
                            Thread.sleep(500)

                        } catch (e: Exception) {
                            // remove tomlFile and prompt error
                            // create a generatetoml_log.txt to store error logs
                            try {
                                tempTomlFile?.delete()
                                storesError(basePath, "[creating toml]\n\t" + e.localizedMessage)
                                indicator.stop()
                            } catch (e: Exception) {/*do nothing*/
                            }
                        }
                    }
                }

                override fun onCancel() {
                    // do clean up
                    tempTomlFile?.delete()
                    super.onCancel()
                }

            }.queue()
        }
    }

    private fun extractDependenciesAndPlugins(singleLine: String, file: File) {

        val dependencyMap = mutableMapOf<String, String>()
        val pluginMap = mutableMapOf<String, String>()

        val dependenciesFound = Parser.extractDependenciesFrom(singleLine)
        dependenciesFound.forEach {
            dependencyMap[it.originalStr] = it.getReplacementText()
        }

        updateDependencies(dependenciesFound)

        val pluginsFound = Parser.extractPluginsFrom(singleLine)
        pluginsFound.forEach {
            pluginMap[it.originalStr] = it.getReplacementText()
        }

        updatePlugins(pluginsFound)

        dataForFiles[file] = TypeBundle(dependencyMap, pluginMap)
        mutableListOf<Types>().apply {
            addAll(dependenciesFound)
            addAll(pluginsFound)
            updateVersions(this)
        }
    }

    @Synchronized
    private fun updateDependencies(dependencies: List<Types.Dependency>) {
        synchronized(lock1) {
            dependencies.forEach {
                dependenciesForToml[it.key] = it.getTomlValue()
            }
        }
    }

    @Synchronized
    private fun updatePlugins(plugins: List<Types.Plugin>) {
        synchronized(lock2) {
            plugins.forEach {
                pluginsForToml[it.key] = it.getTomlValue()
            }
        }
    }

    @Synchronized
    private fun updateVersions(types: List<Types>) {
        synchronized(lock3) {
            types.forEach { type ->
                when (type) {
                    is Types.Dependency -> {
                        if (type.version.isNotEmpty()) {
                            if (versionsForToml.contains(type.getVersionKey()) && versionsForToml[type.getVersionKey()] != type.version) {
                                versionsForToml[type.getVersionKey() + "-" + type.version.replace(".", "-")] =
                                    type.version
                            } else {
                                versionsForToml[type.getVersionKey()] = type.version
                            }
                        }
                    }

                    is Types.Plugin -> {
                        if (type.version.isNotEmpty()) {
                            if (versionsForToml.contains(type.getVersionKey()) && versionsForToml[type.getVersionKey()] != type.version) {
                                versionsForToml[type.getVersionKey() + "-" + type.version.replace(".", "-")] =
                                    type.version
                            } else {
                                versionsForToml[type.getVersionKey()] = type.version
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateFile(
        file: File,
        dependencies: Map<String, String>,
        plugins: Map<String, String>,
        basePath: String
    ) {
        val content = file.readLines()
        if (content.isNotEmpty()) {
            var singleLine = content.reduce { acc, s -> "$acc\n$s" }

            dependencies.forEach { dep ->
                singleLine = singleLine.replace(dep.key, "\t${dep.value}")
            }

            plugins.forEach { plugin ->
                singleLine = singleLine.replace(plugin.key, "\t${plugin.value}")
            }

            try {
                val writer = FileWriter(file)
                writer.write(singleLine)
                writer.close()
            } catch (e: Exception) {
                storesError(basePath, e.localizedMessage)
            }
        }
    }

    private fun resetErrorLog(basePath: String) {
        val file = File("$basePath/gradle/genTomlLog.txt")
        file.delete()
    }

    @Synchronized
    private fun storesError(basePath: String, errStr: String) {
        synchronized(errLock) {
            try {
                val file = File("$basePath/gradle/genTomlLog.txt")
                val content = file.readLines()
                var singleLine = ""

                if (content.isNotEmpty()) {
                    singleLine = content.reduce { acc, s -> "$acc\n$s" }
                }

                val writer = FileWriter("$basePath/gradle/genTomlLog.txt")
                singleLine += "\nError :\n\t$errStr"
                writer.write(singleLine)
                writer.close()

            } catch (e: Exception) {
                // do nothing
            }
        }

        progressIndicator.text = "Find root/gradle/genTomlLog.txt for Error"
        progressIndicator.fraction = 1.toDouble()
        progressIndicator.stop()
        Thread.sleep(1000)
    }
}
