// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.syntax

/**
 * 语法高亮的 Token 分类。
 */
enum class TokenType {
    KEYWORD,
    TYPE,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    FUNCTION,
    OPERATOR,
    PLAIN,
}

/**
 * 一段被识别出的高亮片段。
 */
data class SyntaxToken(
    val range: com.mtopensource.editor.text.TextRange,
    val type: TokenType,
)

/**
 * 语法定义。
 *
 * 采用「关键字表 + 正则规则」的轻量方案，好处是无需引入完整词法分析器
 * 即可覆盖文本编辑场景；如后续需要更精确的解析，可替换 [Lexer] 实现。
 */
data class SyntaxDefinition(
    val name: String,
    val extensions: Set<String>,
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val annotations: Set<String> = emptySet(),
    val lineCommentPrefixes: List<String> = emptyList(),
    val blockCommentDelimiters: List<Pair<String, String>> = emptyList(),
    val stringDelimiters: List<Char> = listOf('"', '\''),
    /** 是否启用 `//`、`#` 之类的行注释。 */
    val supportsLineComment: Boolean = true,
) {

    /** 根据文件名判断是否匹配本语法定义。 */
    fun matches(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in extensions
    }

    companion object {
        /** 无高亮的纯文本定义。 */
        val PLAIN = SyntaxDefinition(name = "Plain Text", extensions = emptySet())

        /** 内置语法定义表。 */
        val BUILT_IN: List<SyntaxDefinition> = listOf(
            SyntaxDefinition(
                name = "Kotlin",
                extensions = setOf("kt", "kts"),
                keywords = setOf(
                    "package", "import", "class", "interface", "object", "fun", "val", "var", "when", "if",
                    "else", "for", "while", "do", "return", "break", "continue", "try", "catch", "finally",
                    "throw", "is", "in", "as", "this", "super", "null", "true", "false", "override", "open",
                    "private", "public", "protected", "internal", "sealed", "data", "enum", "companion",
                    "suspend", "inline", "reified", "lateinit", "by", "where", "typealias", "init", "constructor",
                ),
                types = setOf(
                    "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String", "Unit",
                    "Any", "Nothing", "List", "MutableList", "Map", "MutableMap", "Set", "MutableSet", "Array",
                ),
                annotations = setOf("Test", "JvmStatic", "JvmOverloads", "Deprecated", "Suppress", "Composable"),
                lineCommentPrefixes = listOf("//"),
                blockCommentDelimiters = listOf("/*" to "*/"),
                stringDelimiters = listOf('"', '\'', '`'),
            ),
            SyntaxDefinition(
                name = "Java",
                extensions = setOf("java"),
                keywords = setOf(
                    "package", "import", "class", "interface", "enum", "extends", "implements", "public",
                    "private", "protected", "static", "final", "abstract", "void", "new", "return", "if",
                    "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "try",
                    "catch", "finally", "throw", "throws", "this", "super", "null", "true", "false", "instanceof",
                    "synchronized", "volatile", "transient", "native", "assert", "record",
                ),
                types = setOf(
                    "int", "long", "short", "byte", "float", "double", "boolean", "char", "String", "Integer",
                    "Long", "Double", "Boolean", "Object", "List", "Map", "Set", "ArrayList", "HashMap",
                ),
                annotations = setOf("Override", "Deprecated", "SuppressWarnings", "Nullable", "NonNull"),
                lineCommentPrefixes = listOf("//"),
                blockCommentDelimiters = listOf("/*" to "*/"),
            ),
            SyntaxDefinition(
                name = "XML",
                extensions = setOf("xml", "html", "htm", "svg", "xsd", "plist"),
                keywords = emptySet(),
                lineCommentPrefixes = listOf(),
                blockCommentDelimiters = listOf("<!--" to "-->"),
                stringDelimiters = listOf('"', '\''),
                supportsLineComment = false,
            ),
            SyntaxDefinition(
                name = "JSON",
                extensions = setOf("json", "json5"),
                keywords = setOf("true", "false", "null"),
                lineCommentPrefixes = listOf("//"),
                blockCommentDelimiters = listOf("/*" to "*/"),
            ),
            SyntaxDefinition(
                name = "Shell",
                extensions = setOf("sh", "bash", "zsh", "prop", "conf", "ini"),
                keywords = setOf(
                    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
                    "function", "return", "export", "local", "readonly", "source", "echo", "exit",
                ),
                lineCommentPrefixes = listOf("#"),
                stringDelimiters = listOf('"', '\''),
            ),
            SyntaxDefinition(
                name = "Markdown",
                extensions = setOf("md", "markdown"),
                keywords = setOf("#", "##", "###", "####", "#####", "######"),
                lineCommentPrefixes = listOf("<!--"),
                stringDelimiters = listOf('`', '"'),
            ),
            SyntaxDefinition(
                name = "Python",
                extensions = setOf("py", "pyw"),
                keywords = setOf(
                    "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "try",
                    "except", "finally", "with", "return", "yield", "lambda", "pass", "break", "continue",
                    "raise", "global", "nonlocal", "assert", "del", "in", "is", "not", "and", "or", "None",
                    "True", "False", "async", "await",
                ),
                types = setOf("int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes"),
                lineCommentPrefixes = listOf("#"),
            ),
            SyntaxDefinition(
                name = "JavaScript",
                extensions = setOf("js", "mjs", "cjs", "ts", "tsx", "jsx", "vue"),
                keywords = setOf(
                    "const", "let", "var", "function", "class", "extends", "import", "export", "default",
                    "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
                    "try", "catch", "finally", "throw", "new", "this", "super", "typeof", "instanceof",
                    "await", "async", "null", "undefined", "true", "false", "interface", "type", "enum",
                ),
                lineCommentPrefixes = listOf("//"),
                blockCommentDelimiters = listOf("/*" to "*/"),
                stringDelimiters = listOf('"', '\'', '`'),
            ),
        )

        /** 依据文件名挑选语法定义，未命中返回 [PLAIN]。 */
        fun forFileName(fileName: String): SyntaxDefinition =
            BUILT_IN.firstOrNull { it.matches(fileName) } ?: PLAIN
    }
}
