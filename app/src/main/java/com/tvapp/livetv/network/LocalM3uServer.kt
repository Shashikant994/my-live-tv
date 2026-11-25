package com.tvapp.livetv.network

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

class LocalM3uServer(
    private val port: Int = 8765,
    private val onUrlReceived: (String) -> Unit
) {
    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        runCatching {
            serverSocket = ServerSocket(port)
            while (running) {
                val client = serverSocket?.accept() ?: break
                runCatching {
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine().orEmpty()
                    var contentLength = 0
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }

                    var message = ""
                    if (requestLine.startsWith("POST") && contentLength > 0) {
                        val bodyChars = CharArray(contentLength)
                        reader.read(bodyChars, 0, contentLength)
                        val body = String(bodyChars)
                        val urlParam = body.split("&")
                            .firstOrNull { it.startsWith("url=") }
                            ?.substringAfter("url=")
                            ?.let { URLDecoder.decode(it, "UTF-8") }
                            ?.trim()
                        if (!urlParam.isNullOrBlank() &&
                            (urlParam.startsWith("http://") || urlParam.startsWith("https://"))
                        ) {
                            withContext(Dispatchers.Main) { onUrlReceived(urlParam) }
                            message = "<p style='color:#22c55e;font-weight:bold'>Playlist sent to Tarang TV!</p>"
                        } else {
                            message = "<p style='color:#ef4444'>Please enter a valid http/https .m3u URL.</p>"
                        }
                    }

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>Tarang TV - Add Playlist</title>
                          <style>
                            body { font-family: sans-serif; background: #090d16; color: #fff; padding: 24px; }
                            input { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid #334155; background: #111827; color: #fff; margin: 12px 0; box-sizing: border-box; }
                            button { width: 100%; padding: 14px; border: none; border-radius: 8px; background: #38bdf8; color: #090d16; font-weight: bold; font-size: 16px; }
                          </style>
                        </head>
                        <body>
                          <h2>Tarang TV — Custom M3U</h2>
                          <p>Paste any .m3u or .m3u8 playlist URL below to load it on your TV:</p>
                          $message
                          <form method="POST" action="/">
                            <input type="url" name="url" placeholder="https://example.com/playlist.m3u" required />
                            <button type="submit">Send to TV</button>
                          </form>
                        </body>
                        </html>
                    """.trimIndent()

                    val out = PrintWriter(client.getOutputStream(), true)
                    out.print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n$html")
                    out.flush()
                    client.close()
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    companion object {
        fun getLocalIpAddress(): String? {
            return runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            }.getOrNull()
        }

        fun generateQrBitmap(text: String, size: Int = 360): Bitmap {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        }
    }
}
