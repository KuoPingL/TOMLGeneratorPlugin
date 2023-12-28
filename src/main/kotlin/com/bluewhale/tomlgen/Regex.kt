package com.bluewhale.tomlgen

object Regex {
    const val DEPENDENCIES_SCOPE_REGEX = "dependencies\\s*\\{([\\S\\s]*?)\\s*}"
    const val DEPENDENCY_WITH_CONFIG_REGEX = "(\\w*)\\s*\\(\\s*([^)]*)\\s*\\)"
    const val PLUGINS_SCOPE_REGEX = "plugins\\s*\\{([\\S\\s]*?)\\s*}"
    const val PLUGINS_WITH_VERSION_REGEX = "[/ *]*id\\s*\\(\\s*\"([^\"]+)\"\\s*\\)(\\s*version\\s*\"([\\d.]+)\\s*\")"
    const val GROUP_NAME_VERSION_REGEX = "([\\w.-]+):([\\w.-]+):([\\d.]+)\""
    const val NAME_EQUAL_STRING_VALUE_REGEX = "([\\w.-]+)\\s*=\\s*\"([^\"]*)\""
    const val TOML_DEP_PLUGIN_REGEX = "([\\w-]+)\\s*=\\s*(\\{[^}]*})"
    const val QUOTED_REGEX = "\"([^\"]*)\""
    const val TLD_REGEX = "^\\w{1,3}[.]"
    const val TOML_SEC_REGEX = "\\[(\\w+)]([^\\[\\]]*)"
}