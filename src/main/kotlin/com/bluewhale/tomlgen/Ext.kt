package com.bluewhale.tomlgen

fun List<String>.toVersionKey(isPlugin: Boolean) =
    mapIndexed { index, s -> if (index>0)s.replaceFirstChar { it.uppercaseChar() } else s }.joinToString("").plus( if (isPlugin) "Plugin" else "Dep")