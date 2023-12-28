package com.bluewhale.tomlgen

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