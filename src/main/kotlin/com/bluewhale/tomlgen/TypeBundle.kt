package com.bluewhale.tomlgen

/**
 * TypeBundle is simply a class that contains all the dependencies and plugins in certain file
 *
 * parameters
 * dependencies : this will hold the original string as the key and the final string as value
 * plugins : this will hold the original string as the key and the final string as value
 * file : the original file that these dependencies and plugins were from
 */
data class TypeBundle(val dependencies: Map<String, String>, val plugins: Map<String, String>)