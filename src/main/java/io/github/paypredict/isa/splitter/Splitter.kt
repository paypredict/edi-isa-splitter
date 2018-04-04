package io.github.paypredict.isa.splitter

import java.io.*
import java.util.*
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
                if (dstDir.exists()) throw IOException("Target directory '${dstDir.absolutePath}' already exists")

                dstDir.mkdirs()
                errLog = PrintWriter(GZIPOutputStream(dstDir.resolve("log.gz").outputStream()))

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
                clientMap.values.forEach { it.close() }
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


    private class ClientZip(
        val srcDir: File,
        dstDir: File,
        id: String
    ) : AutoCloseable {

        private val zip: ZipOutputStream = ZipOutputStream(FileOutputStream(dstDir.resolve("$id.zip"))).apply {
            setMethod(ZipOutputStream.DEFLATED)
            setLevel(9)
        }

        override fun close() {
            zip.close()
        }

        fun add(file: File, index: Int, data: String) {
            val name = file.relativeTo(srcDir).normalize().resolve("$index")
                .path.replace('\\', '/')
            zip.putNextEntry(ZipEntry(name))
            zip.write(data.toByteArray(ISA.CHARSET))
        }
    }

    private val clientMap = mutableMapOf<String, ClientZip>()

    private fun File.split() {
        ISA.read(this).forEachIndexed { index, isa ->
            val clientId = isa.clientId
            if (clientId.isNotEmpty()) {
                val clientZip = clientMap.getOrPut(clientId) { ClientZip(srcDir, dstDir, clientId) }
                clientZip.add(this, index, isa.data)
            }
        }
    }
}

