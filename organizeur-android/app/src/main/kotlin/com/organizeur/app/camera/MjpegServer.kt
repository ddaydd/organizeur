package com.organizeur.app.camera

import android.util.Log
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class MjpegServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<ClientConnection>()
    @Volatile
    private var running = false

    val clientCount: Int get() = clients.size

    fun start() {
        if (running) return
        running = true
        thread(name = "MjpegServer") {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "MJPEG server started on port $port")
                while (running) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        handleClient(socket)
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "Accept error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        running = false
        clients.forEach { it.close() }
        clients.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        Log.i(TAG, "MJPEG server stopped")
    }

    fun sendFrame(jpegBytes: ByteArray) {
        val deadClients = mutableListOf<ClientConnection>()
        for (client in clients) {
            if (!client.isStream) continue
            try {
                client.outputStream.write("--frame\r\n".toByteArray())
                client.outputStream.write("Content-Type: image/jpeg\r\n".toByteArray())
                client.outputStream.write("Content-Length: ${jpegBytes.size}\r\n\r\n".toByteArray())
                client.outputStream.write(jpegBytes)
                client.outputStream.write("\r\n".toByteArray())
                client.outputStream.flush()
            } catch (_: Exception) {
                deadClients.add(client)
            }
        }
        deadClients.forEach {
            it.close()
            clients.remove(it)
        }
    }

    private fun handleClient(socket: Socket) {
        thread(name = "MjpegClient-${socket.inetAddress}") {
            try {
                val input = socket.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@thread
                // Read remaining headers
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                val output = BufferedOutputStream(socket.getOutputStream())

                when {
                    path == "/stream" -> {
                        val header = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: multipart/x-mixed-replace; boundary=frame\r\n")
                            append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
                            append("Connection: close\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("\r\n")
                        }
                        output.write(header.toByteArray())
                        output.flush()
                        val conn = ClientConnection(socket, output, isStream = true)
                        clients.add(conn)
                        // Keep thread alive while streaming
                        while (running && !socket.isClosed) {
                            Thread.sleep(1000)
                        }
                    }
                    path == "/" || path == "/index.html" -> {
                        serveHtml(output)
                        socket.close()
                    }
                    else -> {
                        val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                        output.write(response.toByteArray())
                        output.flush()
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client handler error", e)
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun serveHtml(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Organizeur - Caméra</title>
                <style>
                    body { margin: 0; background: #1a1a1a; display: flex; justify-content: center; align-items: center; min-height: 100vh; font-family: sans-serif; }
                    img { max-width: 100%; max-height: 100vh; }
                </style>
            </head>
            <body>
                <img src="/stream" alt="Camera stream">
            </body>
            </html>
        """.trimIndent()
        val bytes = html.toByteArray()
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private class ClientConnection(
        private val socket: Socket,
        val outputStream: OutputStream,
        val isStream: Boolean
    ) {
        fun close() {
            try { outputStream.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "MjpegServer"
    }
}
