// Fails the build when app text breaks the copy rules in docs/STYLE.md:
// no em or en dashes, and none of the phrases listed in config/copy-lint/banned.txt.

val stringFiles = fileTree(rootDir) {
    // strings.xml plus each feature's own file, such as strings_plan.xml.
    include("**/src/*/res/values*/strings*.xml")
    exclude("**/build/**")
}
val bannedList = file("config/copy-lint/banned.txt")

tasks.register("checkCopy") {
    group = "verification"
    description = "Checks app text against the copy rules in docs/STYLE.md."

    val files = stringFiles
    val banned = bannedList
    val root = rootDir
    inputs.files(files)
    inputs.file(banned)

    doLast {
        val phrases = banned.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { phrase -> phrase to Regex("\\b" + Regex.escape(phrase) + "\\b", RegexOption.IGNORE_CASE) }

        val problems = mutableListOf<String>()
        files.files.sorted().forEach { file ->
            val path = file.relativeTo(root).path
            file.readLines().forEachIndexed { index, line ->
                val where = "$path:${index + 1}"
                if ('—' in line) problems += "$where: em dash"
                if ('–' in line) problems += "$where: en dash"
                phrases.forEach { (phrase, regex) ->
                    if (regex.containsMatchIn(line)) problems += "$where: \"$phrase\""
                }
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "App text breaks the copy rules in docs/STYLE.md:\n" + problems.joinToString("\n") { "  $it" },
            )
        }
    }
}
