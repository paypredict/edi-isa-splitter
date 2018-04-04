package io.github.paypredict.isa.splitter

import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.*
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    Splitter(File(args[0]), File(args[1]))
        .start()
}

data class Progress(val max: Int, var value: Int)
enum class OnProgressRes { CONTINUE, CLOSE }

class Splitter(private val srcDir: File, private val dstDir: File) {
    var onStart: () -> Unit = {}
    var onFinish: () -> Unit = {}
    var onProgress: (Progress) -> OnProgressRes = { OnProgressRes.CONTINUE }
    var onError: (Throwable) -> Unit = {}

    fun start() {
        val now = Date()
        if (now > java.sql.Date.valueOf("2018-06-01"))
            throw RuntimeException(
                "Your licence has expired, contact wecare@pinnacleservice.co if you need to continue using the software"
            )

        thread(name = "Splitter") {
            var errLog: PrintWriter? = null
            try {
                onStart()
                if (srcDir.path.isBlank()) throw IOException("Invalid source directory '${srcDir.path}'")
                if (dstDir.path.isBlank()) throw IOException("Invalid target directory '${dstDir.path}'")
                if (!srcDir.isDirectory) throw IOException("Source directory '${srcDir.absolutePath}' not found")

                dstDir.mkdirs()
                errLog = PrintWriter(
                    dstDir.resolve("log.txt").also { it.backupLog() }.outputStream(),
                    true
                )

                val all = srcDir.walk().filter { it.isFile }.toList()
                val progress = Progress(all.size, 0)
                onProgress(progress)
                all.forEach {
                    try {
                        it.split()
                    } catch (e: ISAException) {
                        e.printStackTrace()
                        e.printStackTrace(errLog)
                    }
                    progress.value++
                    onProgress(progress.copy())
                }
                onFinish()
            } catch (e: Throwable) {
                e.printStackTrace()
                errLog?.let { e.printStackTrace(it) }
                onError(e)
            } finally {
                errLog?.close()
            }
        }
    }


    private fun File.backupLog() {
        val dir = parentFile
        val baseName = ".log-" + java.sql.Date(System.currentTimeMillis()).toString()
        for (index in 1..100000) {
            if (renameTo(dir.resolve("$baseName-$index.txt"))) break
        }
    }

    private class ClientDir(
        val srcDir: File,
        dstDir: File,
        client: Client
    ) {

        private val dir = dstDir.resolve(client.id).apply {
            mkdir()
        }

        fun add(file: File, index: Int, data: String) {
            val path = file.relativeTo(srcDir).normalize().path
            when (index) {
                -1 -> dir.resolve(path)
                else -> dir.resolve(path).resolve("$index")
            }.apply {
                parentFile.mkdirs()
                val bytes = data.toByteArray(ISA.CHARSET)
                if (!isFile || length() != bytes.size.toLong()) {
                    writeBytes(bytes)
                }
            }
        }
    }

    private val clientMap = mutableMapOf<String, ClientDir>()

    private fun File.split() {
        val isaList = ISA.read(this)
        isaList.forEachIndexed { index, isa ->
            isa.client?.let { client ->
                val clientDir = clientMap.getOrPut(client.id) {
                    ClientDir(srcDir, dstDir, client)
                }
                clientDir.add(
                    this,
                    if (isaList.size == 1) -1 else index,
                    isa.data
                )
            }
        }
    }
}

