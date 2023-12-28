package com.bluewhale.tomlgen

import org.jetbrains.annotations.VisibleForTesting

object Parser {

    const val PLUGINS = "plugins"
    const val GROUP = "group"
    const val NAME = "name"
    const val VERSION = "version"
    const val VERSIONS = "versions"
    const val LIBRARIES = "libraries"

    fun extractDependenciesFrom(string: String): List<Types.Dependency> {
        val dependenciesStr = Regex.DEPENDENCIES_SCOPE_REGEX.toRegex().find(string)?.value ?: ""
        val allDependencies = mutableListOf<Types.Dependency>()

        if (dependenciesStr.isEmpty()) return allDependencies
        val dependencies = Regex(Regex.DEPENDENCY_WITH_CONFIG_REGEX).findAll(dependenciesStr)

        dependencies.forEach {
            // all these will match with 3 parts
            // 1. configuation (
            // 2. whatever before the first ')'

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
                        extractGroupNameVersionWithSemiColon(content, config, originalStr)?.let {dep ->
                            allDependencies.add(dep)
                        }

                    } else if (content.contains(GenerateToml.GROUP)) {
                        // this should be group= ... name = ... version = ...
                        extractGroupNameVersionWithGroup(content, config, originalStr)?.let {dep ->
                            allDependencies.add(dep)
                        }
                    }
                }
            }

        }

        return allDependencies
    }

    fun extractPluginsFrom(str: String):  List<Types.Plugin>{
        val pluginsStr = Regex.PLUGINS_SCOPE_REGEX.toRegex().find(str)?.value ?: ""
        val finalPlugins = mutableListOf<Types.Plugin>()

        Regex.PLUGINS_WITH_VERSION_REGEX.toRegex().findAll(pluginsStr).forEach { matchResult ->

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

    /**
     * This function is used to extract info from [versions], [plugins], [libraries] in a toml file.
     *
     * @param str this is the string that contains toml content
     * @param versionHandler this function will be called when version is found. It will return in a form of (key, value)
     * @param dependencyHandler this function will be called when library is found. It will return in a form of (key, value)
     * @param pluginHandler this function will be called when plugin is found. It will return in a form of (key, value)
     *
     * @return This function will return the remaining content of the file that does not meet the requirement of [versions], [plugins] and [libraries]
     */
    fun extractInfoFromToml(str: String, versionHandler: (String, String) -> Unit, dependencyHandler: (String, String) -> Unit, pluginHandler: (String, String) -> Unit): String {
        var singleLine = str

        Regex.TOML_SEC_REGEX.toRegex().findAll(singleLine).forEach { section ->
            // each section
            val sec = section.groups[1]?.value ?: ""
            val fullContent = section.groups[2]?.value ?: ""
            var key: String
            var value: String

            singleLine = singleLine.replace(section.groups[0]?.value ?: "", "")

            when(sec) {
                VERSIONS -> {
                    Regex.NAME_EQUAL_STRING_VALUE_REGEX.toRegex().findAll(fullContent).forEach { match ->
                        key = match.groups[1]?.value ?: ""
                        value = match.groups[2]?.value ?: ""
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            versionHandler(key, value)
                        }
                    }
                }

                LIBRARIES -> {
                    Regex.TOML_DEP_PLUGIN_REGEX.toRegex().findAll(fullContent).forEach { depMatch ->
                        key = depMatch.groups[1]?.value ?: ""
                        value = depMatch.groups[2]?.value ?: ""
                        // extracts all group, name, version
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            dependencyHandler(key, value)
                        }
                    }
                }

                PLUGINS -> {
                    Regex.TOML_DEP_PLUGIN_REGEX.toRegex().findAll(fullContent).forEach { depMatch ->
                        key = depMatch.groups[1]?.value ?: ""
                        value = depMatch.groups[2]?.value ?: ""
                        // extracts all group, name, version
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            pluginHandler(key, value)
                        }
                    }
                }
            }
        }

        return singleLine
    }

    /**
     * This function will extract dependency with format of
     * `configuration("group:name:version")`
     *
     * See [extractGroupNameVersionWithGroup] if your dependency is defined in a format of
     * `configuration(group = "group", name = "name, version = "version')
     *
     * @param content this is the string that contain a single dependency statement without configuration and brackets
     * @param config this is the configuration used for this dependency. ie implementation, testImplementation ...
     * @param originalStr this is string that holds the complete definition of this dependency, including config
     *
     * @return this function will return a Types.Dependency?
     */
    fun extractGroupNameVersionWithSemiColon(content: String, config: String, originalStr: String): Types.Dependency? {
        val groupNameVersion = Regex.GROUP_NAME_VERSION_REGEX.toRegex().find(content)?.value?.split(":") ?: listOf()

        if (groupNameVersion.size == 3) {
            val group = groupNameVersion[0]
            val name = groupNameVersion[1]
            // Bug : the regex seems right, but it seems it caught \" inside the group
            val version = groupNameVersion[2].replace("\"", "")

            if (group.isNotEmpty() && name.isNotEmpty() && version.isNotEmpty()) {
                val key = generateKeyWith(group, name)
                return Types.Dependency(key, config, group, name, version, originalStr)
            }
        }
        return null
    }

    /**
     * This function will extract dependency with format of
     * `configuration(group = "group", name = "name" , version = "version')
     *
     * See [extractGroupNameVersionWithSemiColon] if the dependency has a format of
     * `configuration("group:name:version")`
     *
     * @param content this is the string that contain a single dependency statement without configuration and brackets
     * @param config this is the configuration used for this dependency. ie implementation, testImplementation ...
     * @param originalStr this is string that holds the complete definition of this dependency, including config
     *
     * @return this function will return a Types.Dependency?
     */
    fun extractGroupNameVersionWithGroup(content: String, config: String, originalStr: String): Types.Dependency? {
        val results = Regex.NAME_EQUAL_STRING_VALUE_REGEX.toRegex().findAll(content)
        val quotationValueRegex = Regex.QUOTED_REGEX.toRegex()

        var group = ""
        var name = ""
        var version = ""

        if (results.count() == 3) {
            results.forEach {matchResult ->
                val str = matchResult.value
                if (str.contains(GROUP)) {

                    group = quotationValueRegex.find(str)?.groups?.get(1)?.value ?: ""

                } else if (str.contains(NAME)) {

                    name = quotationValueRegex.find(str)?.groups?.get(1)?.value ?: ""

                } else if (str.contains(VERSION)) {

                    version = Regex.VERSION_REGEX.toRegex().find(str)?.groups?.get(1)?.value ?: ""
                }
            }

            if (group.isNotEmpty() && name.isNotEmpty() && version.isNotEmpty()) {
                val key = generateKeyWith(group, name)
                return Types.Dependency(key, config, group, name, version, originalStr)
            }
        }

        return null
    }

    private fun extractDependencyOrPluginFromToml() {

    }

    private fun generateKeyWith(group: String, name: String?): String {

        val nameSplit = name?.split(Regex("[.-]"))
        var finalStr = group
            .replace(Regex.TLD_REGEX.toRegex(),"")
            .replace(".", "-")

        nameSplit?.forEach {sub ->
            if (!group.contains(sub)) {
                finalStr += "-$sub"
            }
        }
        return finalStr
    }
}