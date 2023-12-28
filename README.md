# TOMLGeneratorPlugin
This is a plugin used to generate libs.versions.toml file based on all the Gradle files available.

You can read more on the development in 

Feel free to file an issue if you have any question or suggestion.

Also, this code is definitely far from perfect, so any help from you is appreciated.

For anyone who wish to learn the process of creating this plugin, you are more than welcome to checkout my [medium](https://medium.com/kuos-funhouse/creating-a-plugin-for-android-studio-a-complete-walkthrough-47a154aacb7a).

## Purpose 
The purpose of this plugin is to save us from the trouble of all the copy and paste needed to create a `libs.versions.toml` file for Android Project.

## Process
The plugin takes 7 steps to make things happen :
1. **SEARCH** : Find all Gradle files (ones that contain `.gradle`)
2. **EXTRACT** : Extract dependencies and plugins asynchronously.
3. **EXTRACT TOML** : Fetch all the versions, plugins and dependencies in `libs.versions.toml` if there is one.
4. **STORE** : Create a temporary **TOML** file (`libs.versions.temp.toml`) and store extracted data based on `[versions]`, `[libraries]` and `[plugins]`. If the data inside cannot be extracted, then it will be seen as string and will placed at the end of `libs.versions.temp.toml`.
5. **WRITE** : Store all from `libs.versions.temp.toml` into `libs.versions.toml`. 
6. **CLEAN UP** : The temporary file will also be deleted.
7. **UPDATE** : Finally, the plugin will update all the Gradle files with the proper dependencies and plugins based on `libs.versions.toml`.

## FAQ
1. <u>What are the format that is covered in current version (v1.0-SNAPSHOT)?</u>

| components   | format                                                                                                          |
|:-------------|:----------------------------------------------------------------------------------------------------------------|
| Dependencies | `configuration("group:name:version")`<br> `configuration(group = "group", name = "name", version = "version")`  |
| Plugins      | `id("group:name") version "version"` (the `apply` statement will be ignored, but will keep in place at the end) |

These format will be converted into 
|toml components|format|
|:--|:--|
|`[versions]`|for dependency : `groupNameDep = "version"`<br>for plugin : `groupNamePlugin = "version"`<br>for different versions : `groupName{Dep|Plugin}-version = "version"`|
|`[libraries]`|`group-name = {group = "group", name = "name", version.ref = "groupNameDep"}` |
|`[plugins]`|`group-name = {id = "group.name", version.ref = "groupNamePlugin"}`|

# Versions 

- v1.0-beta
  - Extracted Parser, Regex and classes from `GenerateToml.kt`. 
  - Perform simple tests on Parser
  - (fix) Parser failed to recognize incorrect version format, namely "1.1.1" vs "1-1"

- v1.0-SNAPSHOT 
  - First commit 
  - (required) Need to clean up the code and add some testings

# CopyRight 
Copyright (c) <year> <copyright holders>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.