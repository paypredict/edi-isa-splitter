package io.github.paypredict.isa.splitter

import java.io.File
import java.io.IOException
import java.io.Reader

class ISA private constructor(text: String, start: Int, end: Int, private val delimiters: Delimiters) {
    val data: String = when (end) {
        -1 -> text.substring(start)
        else -> text.substring(start, end)
    }

    override fun toString(): String = "ISA[${data.length}]"

    companion object {
        val CHARSET = Charsets.ISO_8859_1

        fun read(file: File): List<ISA> = mutableListOf<ISA>().apply {
            val delimiters = Delimiters.of(file)
            val (terminator, s1) = delimiters
            val text = file.reader(CHARSET).use {
                filter(it, terminator, StringBuilder(file.length().toInt()))
            }
            var index = 0
            var next: Int
            val isaPattern = "${terminator}ISA$s1"
            while (true) {
                next = text.indexOf(isaPattern, index + isaPattern.length, false)
                this += ISA(text, index, next, delimiters)
                if (next == -1) break
                index = next + 1
                if (index >= text.length) break
            }
        }

        private fun filter(reader: Reader, terminator: Char, builder: StringBuilder) : String {
            val buff = CharArray(4096)
            var tMode = false
            while (true) {
                val res = reader.read(buff)
                if (res == -1) break
                @Suppress("LoopToCallChain")
                for (i in 0 until res) {
                    val c = buff[i]
                    if (tMode) {
                        if (c > ' ') {
                            tMode = false
                            builder.append(c)
                        }
                    } else {
                        if (c >= ' ') builder.append(c)
                    }
                    if (c == terminator) tMode = true
                }
            }
            return builder.toString()
        }

        private data class Delimiters(
                val terminator: Char = '~',
                val separator1: Char = '*',
                val separator2: Char = ':') {
            companion object {
                fun of(file: File): Delimiters {
                    val header = file.reader(CHARSET).use {
                        val buff = CharArray(1024)
                        var offset = 0
                        var length = buff.size
                        while (length > 0) {
                            val res = it.read(buff, offset, length)
                            if (res == -1) break
                            length -= res
                            offset += res
                        }
                        StringBuilder(1024).apply {
                            @Suppress("LoopToCallChain")
                            for (c in buff) if (c >= ' ') append(c)
                        }.toString()
                    }
                    if (!header.startsWith("ISA"))
                        throw ISAException("Invalid file $file: starting 'ISA' not found")
                    if (header.length < 106)
                        throw ISAException("Invalid file $file: length < 106")
                    return Delimiters(
                            terminator = header[105],
                            separator1 = header[103],
                            separator2 = header[104])
                            .apply {
                                if (header[3] != separator1) {
                                    throw ISAException(
                                            "Invalid file $file: invalid separator1: '$separator1' or starting 'ISA$separator1' not found")
                                }
                            }
                }
            }
        }
    }

    val ST: String by lazy {
        "ST*".segments().getOrNull(1) ?: ""
    }

    val clientId: String by lazy {
        when(ST) {
            "835" -> client835Id()
            "837" -> client837Id()
            else -> ""
        }
    }

    private fun client835Id(): String {
        val segments = "N1*PE*".segments()
        val N104 = segments.getOrNull(4)
        return N104 ?: ""
    }

    private fun client837Id(): String {
        val segments = "NM1*85*".segments()
        val NM108 = segments.getOrNull(8)
        val NM109 = segments.getOrNull(9)
        return when(NM108) {
            "XX" -> NM109 ?: ""
            else -> ""
        }
    }

    private fun String.segments(): List<String> {
        val prefix = this.replace('*', delimiters.separator1)
        val code = data.splitToSequence(delimiters.terminator).firstOrNull { it.startsWith(prefix) }
                ?: return emptyList()
        return code.split(delimiters.separator1)
    }
}

class ISAException(message: String): IOException(message)