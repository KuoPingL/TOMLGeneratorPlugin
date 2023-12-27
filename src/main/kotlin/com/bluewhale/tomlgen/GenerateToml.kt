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

private fun List<String>.toVersionKey(isPlugin: Boolean) =
    mapIndexed { index, s -> if (index>0)s.replaceFirstChar { it.uppercaseChar() } else s }.joinToString("").plus( if (isPlugin) "Plugin" else "Dep")

class GenerateToml: AnAction() {

    private val lock1 = Any()
    private val lock2 = Any()
    private val errLock = Any()

    private val dataForFiles = ConcurrentHashMap<File, TypeBundle>()
    private val dependenciesForToml = mutableMapOf<String, String>()
    private val pluginsForToml = mutableMapOf<String, String>()
    private val versionsForToml = mutableMapOf<String, String>()

    private lateinit var progressIndicator: ProgressIndicator

    companion object {
        const val DEPENDENCIES_SCOPE_REGEX = "dependencies\\s*\\{([\\S\\s]*?)\\s*}"
        const val DEPENDENCY_WITH_CONFIG_REGEX = "(\\w*)\\s*\\(\\s*([^)]*)\\s*\\)"
        const val PLUGINS_SCOPE_REGEX = "plugins\\s*\\{([\\S\\s]*?)\\s*}"
        const val PLUGINS_WITH_VERSION_REGEX = "[/ *]*id\\s*\\(\\s*\"([^\"]+)\"\\s*\\)(\\s*version\\s*\"([\\d.]+)\\s*\")"
        const val GROUP_NAME_VERSION_REGEX = "([\\w.-]+):([\\w.-]+):([\\d.]+)"
        const val NAME_EQUAL_STRING_VALUE_REGEX = "([\\w.-]+)\\s*=\\s*\"([^\"]*)\""
        const val TOML_DEP_PLUGIN_REGEX = "([\\w-]+)\\s*=\\s*(\\{[^}]*})"
        const val QUOTED_REGEX = "\"([^\"]*)\""
        const val TLD_REGEX = "^\\w{1,3}[.]"
        const val TOML_SEC_REGEX = "\\[(\\w+)]([^\\[\\]]*)"

        const val VERSIONS = "versions"
        const val LIBRARIES = "libraries"
        const val PLUGINS = "plugins"
        const val GROUP = "group"
        const val NAME = "name"
        const val VERSION = "version"

        const val STATEMENT = "# GenerateToml at"
        const val DATE_TIME_PATTERN = "YYYY-MM-dd HH:mm:ss"
        const val TOML_FILE_NAME = "libs.versions.toml"

        const val STEP_SEARCH_FOR_GRADLE = 1
        const val STEP_EXTRACT_DEP_PLUGIN = 2
        const val STEP_EXTRACT_TOML = 3
        const val STEP_WRITE_IN_TEMP_TOML = 4
        const val STEP_WRITE_IN_TOML = 5
        const val STEP_CLEAN_UP = 6
        const val STEP_UPDATE = 7
        const val TOTAL_STEPS = 7
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

                    project.basePath?.let {basePath ->
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

                    // 2. Reading Gradle Files -> gradleFiles.count
                    val executors = Executors.newFixedThreadPool(gradleFiles.size)
                    val tasks = mutableListOf<Callable<Unit>>()

                    gradleFiles.forEach {file ->
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
                                singleLine = content.reduce {acc, str -> if (acc.contains(STATEMENT)) str else "$acc\n$str"  }

                                TOML_SEC_REGEX.toRegex().findAll(singleLine).forEach {section ->
                                    // each section
                                    val sec = section.groups[1]?.value ?: ""
                                    val fullContent = section.groups[2]?.value ?: ""
                                    var key: String
                                    var value: String

                                    singleLine = singleLine.replace(section.groups[0]?.value ?: "", "")

                                    when(sec) {
                                        VERSIONS -> {
                                            NAME_EQUAL_STRING_VALUE_REGEX.toRegex().findAll(fullContent).forEach {match ->
                                                key = match.groups[1]?.value ?: ""
                                                value = match.groups[2]?.value ?: ""
                                                if (key.isNotEmpty() && value.isNotEmpty() && !versionsForToml.contains(key)) {
                                                    versionsForToml[key] = value
                                                }
                                            }
                                        }

                                        LIBRARIES -> {
                                            TOML_DEP_PLUGIN_REGEX.toRegex().findAll(fullContent).forEach { depMatch ->
                                                key = depMatch.groups[1]?.value ?: ""
                                                value = depMatch.groups[2]?.value ?: ""
                                                // extracts all group, name, version
                                                if (key.isNotEmpty() && value.isNotEmpty()) {
                                                    if (!dependenciesForToml.contains(key)) {
                                                        dependenciesForToml[key] = value
                                                    }
                                                }
                                            }
                                        }

                                        PLUGINS -> {
                                            TOML_DEP_PLUGIN_REGEX.toRegex().findAll(fullContent).forEach { depMatch ->
                                                key = depMatch.groups[1]?.value ?: ""
                                                value = depMatch.groups[2]?.value ?: ""
                                                // extracts all group, name, version
                                                if (key.isNotEmpty() && value.isNotEmpty()) {
                                                    if (!pluginsForToml.contains(key)) {
                                                        pluginsForToml[key] = value
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            remainingTomlStr = singleLine
                        }
                    }

                    // 7. Sort all versions, dependencies and plugins
                    val versionKeys = versionsForToml.keys.sorted()
                    val sortedValidDependencies = dependenciesForToml.keys.sorted()
                    val sortedValidPlugins = pluginsForToml.keys.sorted()

                    // 8. Write versions, dependencies and plugins into a temp file
                    project.basePath?.let {basePath ->
                        progressIndicator.text = "Storing Data into Temporary TOML file."
                        progressIndicator.fraction = (STEP_WRITE_IN_TEMP_TOML.toDouble() / TOTAL_STEPS.toDouble())
                        Thread.sleep(500)
                        // 7. create a temp libs.versions.toml file
                        tempTomlFile = File("$basePath/gradle/libs.versions.temp.toml")
                        try {
                            // 9. write all into temp file
                            var writer = FileWriter(tempTomlFile!!)

                            val strBuilder = StringBuilder()
                            val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN))
                            strBuilder.append("$STATEMENT $dateStr\n\n")

                            // write version
                            strBuilder.append("[$VERSIONS]\n")
                            versionKeys.forEach {key ->
                                strBuilder.append("$key = \"${versionsForToml[key]}\"\n")
                            }

                            // write dependencies
                            strBuilder.append("\n[$LIBRARIES]\n")
                            sortedValidDependencies.forEach {key ->
                                // {group = "androidx.core", name = "core-ktx", version.ref ="androidxCore"}
                                strBuilder.append("$key = ${dependenciesForToml[key]}\n")
                            }

                            // write plugins
                            strBuilder.append("\n[$PLUGINS]\n")
                            sortedValidPlugins.forEach {key ->
                                // android-application = {id = "com.android.application", version.ref="androidApplication"}
                                strBuilder.append("$key = ${pluginsForToml[key]}\n")
                            }

                            strBuilder.append("\n")
                            strBuilder.append(remainingTomlStr)

                            writer.write(strBuilder.toString())

                            writer.close()

                            progressIndicator.text = "Storing Data into libs.versions.toml."
                            progressIndicator.fraction = (STEP_WRITE_IN_TOML.toDouble() / TOTAL_STEPS.toDouble())
                            Thread.sleep(500)
//                    dialog.setProgressMsg("Storing data in libs.versions.toml")
                            writer = FileWriter("$basePath/gradle/libs.versions.toml")
                            writer.write(strBuilder.toString())
                            writer.close()

                            progressIndicator.text = "Cleaning Up"
                            progressIndicator.fraction = (STEP_CLEAN_UP.toDouble() / TOTAL_STEPS.toDouble())
                            Thread.sleep(500)
                            tempTomlFile?.delete()
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
                            } catch (e: Exception) {/*do nothing*/}
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

        val dependenciesFound = extractDependencies(DEPENDENCIES_SCOPE_REGEX.toRegex().find(singleLine)?.value ?: "")
        dependenciesFound.forEach {

            dependencyMap[it.originalStr] = it.getReplacementText()
        }

        updateDependencies(dependenciesFound)

        val pluginsFound = extractPlugins(PLUGINS_SCOPE_REGEX.toRegex().find(singleLine)?.value ?: "")
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
        types.forEach {type ->
            when(type) {
                is Types.Dependency -> {
                    if (type.version.isNotEmpty()) {
                        if (versionsForToml.contains(type.getVersionKey()) && versionsForToml[type.getVersionKey()] != type.version) {
                            versionsForToml[type.getVersionKey() + "-" + type.version.replace(".", "-")] = type.version
                        } else {
                            versionsForToml[type.getVersionKey()] = type.version
                        }
                    }
                }

                is Types.Plugin -> {
                    if (type.version.isNotEmpty()) {
                        if (versionsForToml.contains(type.getVersionKey()) && versionsForToml[type.getVersionKey()] != type.version) {
                            versionsForToml[type.getVersionKey() + "-" + type.version.replace(".", "-")] = type.version
                        } else {
                            versionsForToml[type.getVersionKey()] = type.version
                        }
                    }
                }
            }
        }
    }

    private fun extractDependencies(dependenciesStr: String): List<Types.Dependency> {

        val allDependencies = mutableListOf<Types.Dependency>()

        if (dependenciesStr.isEmpty()) return allDependencies

        val groupNameVersionRegex = Regex(GROUP_NAME_VERSION_REGEX)
        val nameEqualStringValueRegex = Regex(NAME_EQUAL_STRING_VALUE_REGEX)
        val quotationValueRegex = Regex(QUOTED_REGEX)

        val dependencies = Regex(DEPENDENCY_WITH_CONFIG_REGEX).findAll(dependenciesStr)

        dependencies.forEach {
            // all these will match with 3 parts
            // 1. configuation (
            // 2. whatever before the first ')'

            var group = ""
            var name = ""
            var version = ""
            val key: String

            val config = it.groups[1]?.value ?: ""
            val content = it.groups[2]?.value ?: ""
            val originalStr = it.groups[0]?.value ?: ""

            if (config.isNotEmpty() && content.isNotEmpty()) {
                if (!content.contains("(")) {
                    // make sure there's no other configurations or functions within
                    //  fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"
                    //  project(":mylibrary"

                    if (content.contains(":")) {
                        // this should be {group} : {name} : {version}
                        val groupNameVersion = groupNameVersionRegex.find(content)?.value?.split(":") ?: listOf()

                        if (groupNameVersion.size == 3) {
                            group = groupNameVersion[0]
                            name = groupNameVersion[1]
                            version = groupNameVersion[2]
                            if (group.isNotEmpty() && name.isNotEmpty() && version.isNotEmpty()) {
                                key = generateKeyWith(group, name)
                                allDependencies.add(Types.Dependency(key, config, group, name, version, originalStr))
                            }
                        }

                    } else if (content.contains(GROUP)) {
                        // this should be group= ... name = ... version = ...
                        val results = nameEqualStringValueRegex.findAll(content)

                        if (results.count() == 3) {
                            results.forEach {matchResult ->
                                val str = matchResult.value
                                if (str.contains(GROUP)) {

                                    group = quotationValueRegex.find(str)?.groups?.get(1)?.value ?: ""

                                } else if (str.contains(NAME)) {

                                    name = quotationValueRegex.find(str)?.groups?.get(1)?.value ?: ""

                                } else if (str.contains(VERSION)) {

                                    version = quotationValueRegex.find(str)?.groups?.get(1)?.value ?: ""
                                }
                            }

                            if (group.isNotEmpty() && name.isNotEmpty() && version.isNotEmpty()) {
                                key = generateKeyWith(group, name)
                                allDependencies.add(Types.Dependency(key, config, group, name, version, originalStr))
                            }
                        }
                    }
                }
            }

        }

        return allDependencies
    }


    private fun extractPlugins(pluginsStr: String): List<Types.Plugin> {

        val finalPlugins = mutableListOf<Types.Plugin>()

        PLUGINS_WITH_VERSION_REGEX.toRegex().findAll(pluginsStr).forEach { matchResult ->

            val originalStr = matchResult.groups[0]?.value ?: ""

            // make sure this is not commented out
            if (!originalStr.contains("//") && !originalStr.contains("/*")) {
                when (matchResult.groups.size) {
                    4 -> {
                        val group = matchResult.groups[1]?.value ?: ""
                        val version = matchResult.groups[3]?.value ?: ""
                        val key = generateKeyWith(group, null)

                        finalPlugins.add(Types.Plugin(key, group, version, originalStr))
                    }
                }
            }
        }

        return finalPlugins
    }


    private fun generateKeyWith(group: String, name: String?): String {

        val nameSplit = name?.split(Regex("[.-]"))
        var finalStr = group
            .replace(TLD_REGEX.toRegex(),"")
            .replace(".", "-")

        nameSplit?.forEach {sub ->
            if (!group.contains(sub)) {
                finalStr += "-$sub"
            }
        }
        return finalStr
    }

    private fun updateFile(file: File, dependencies: Map<String, String>, plugins: Map<String, String>, basePath: String) {
        val content = file.readLines()
        if (content.isNotEmpty()) {
            var singleLine = content.reduce { acc, s -> "$acc\n$s" }

            dependencies.forEach {dep ->
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
    }

    sealed class Types {
        data class Dependency(val key: String, val config: String, val group: String, val name: String, val version: String, val originalStr: String):Types() {
            override fun getReplacementText(): String {
                return "${config}(libs.${key.replace("-",".")})"
            }

            override fun getTomlValue(): String {
                assert(group.isNotEmpty())
                assert(name.isNotEmpty())
                assert(version.isNotEmpty())
                return "{group = \"${group}\", name = \"${name}\", version.ref = \"${getVersionKey()}\"}"
            }

            override fun getVersionKey(): String {
                return key.split("-").toVersionKey(false)
            }

            override fun equals(other: Any?): Boolean {
                return other?.let { o->
                    (o as? Dependency)?.let {
                        it.config == config && it.group == group && it.name == name && it.version == version
                    } ?: false

                } ?: false
            }

            override fun hashCode(): Int {
                var result = config.hashCode()
                result = 31 * result + group.hashCode()
                result = 31 * result + name.hashCode()
                result = 31 * result + version.hashCode()
                return result
            }


        }
        data class Plugin (val key: String, val group: String, val version: String, val originalStr: String): Types() {
            override fun getReplacementText(): String {
                return "alias(libs.plugins.${key.replace("-",".")})"
            }

            override fun getTomlValue(): String {
                assert(version.isNotEmpty())
                return "{id = \"${group}\", version.ref = \"${getVersionKey()}\"}"
            }

            override fun getVersionKey(): String {
                return key.split("-").toVersionKey(true)
            }

            override fun equals(other: Any?): Boolean {
                return other?.let { o->
                    (o as? Plugin)?.let {
                        it.group == group && it.version == version
                    } ?: false

                } ?: false
            }

            override fun hashCode(): Int {
                var result = group.hashCode()
                result = 31 * result + version.hashCode()
                return result
            }

        }

        abstract fun getReplacementText(): String
        abstract fun getTomlValue(): String
        abstract fun getVersionKey(): String
    }

    /**
     * TypeBundle is simply a class that contains all the dependencies and plugins in certain file
     *
     * parameters
     * dependencies : this will hold the original string as the key and the final string as value
     * plugins : this will hold the original string as the key and the final string as value
     * file : the original file that these dependencies and plugins were from
     */
    data class TypeBundle(val dependencies: Map<String, String>, val plugins: Map<String, String>)

}
