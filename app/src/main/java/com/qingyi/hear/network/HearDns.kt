package com.qingyi.hear.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Dns

class HearDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val fallbackServers: List<String> = listOf("223.5.5.5", "119.29.29.29", "1.1.1.1"),
) : Dns {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val random = SecureRandom()

    override fun lookup(hostname: String): List<InetAddress> {
        runCatching { systemDns.lookup(hostname) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val now = System.currentTimeMillis()
        cache[hostname]?.takeIf { it.expiresAtMs > now }?.addresses?.let { return it }

        val asciiHost = runCatching { IDN.toASCII(hostname) }.getOrDefault(hostname)
        val addresses = fallbackServers.firstNotNullOfOrNull { server ->
            runCatching { queryARecord(server, asciiHost) }.getOrNull()?.takeIf { it.isNotEmpty() }
        } ?: throw UnknownHostException("无法解析域名：$hostname")

        cache[hostname] = CacheEntry(addresses = addresses, expiresAtMs = now + CACHE_TTL_MS)
        return addresses
    }

    private fun queryARecord(server: String, hostname: String): List<InetAddress> {
        val queryId = random.nextInt(0x10000)
        val request = buildDnsQuery(queryId, hostname)
        DatagramSocket().use { socket ->
            socket.soTimeout = DNS_TIMEOUT_MS
            val serverAddress = InetSocketAddress(InetAddress.getByName(server), DNS_PORT)
            socket.send(DatagramPacket(request, request.size, serverAddress))

            val response = ByteArray(MAX_PACKET_SIZE)
            val packet = DatagramPacket(response, response.size)
            try {
                socket.receive(packet)
            } catch (error: SocketTimeoutException) {
                throw UnknownHostException("DNS 服务器超时：$server").apply { initCause(error) }
            }
            return parseARecordResponse(
                data = response.copyOf(packet.length),
                expectedId = queryId,
                hostname = hostname,
            )
        }
    }

    private fun buildDnsQuery(id: Int, hostname: String): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeShort(id)
        output.writeShort(0x0100)
        output.writeShort(1)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(0)
        hostname.split('.').filter(String::isNotBlank).forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 63) { "DNS label is too long" }
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.writeShort(TYPE_A)
        output.writeShort(CLASS_IN)
        return output.toByteArray()
    }

    private fun parseARecordResponse(data: ByteArray, expectedId: Int, hostname: String): List<InetAddress> {
        if (data.size < HEADER_SIZE || data.readUShort(0) != expectedId) {
            throw IOException("DNS 响应不匹配")
        }
        val flags = data.readUShort(2)
        if (flags and 0x000F != 0) {
            throw UnknownHostException("DNS 响应错误：rcode=${flags and 0x000F}")
        }
        val questionCount = data.readUShort(4)
        val answerCount = data.readUShort(6)
        var offset = HEADER_SIZE
        repeat(questionCount) {
            offset = skipDnsName(data, offset) + 4
            if (offset > data.size) throw IOException("DNS 问题区越界")
        }

        val addresses = mutableListOf<InetAddress>()
        repeat(answerCount) {
            offset = skipDnsName(data, offset)
            if (offset + 10 > data.size) throw IOException("DNS 回答区越界")
            val type = data.readUShort(offset)
            val clazz = data.readUShort(offset + 2)
            val length = data.readUShort(offset + 8)
            offset += 10
            if (offset + length > data.size) throw IOException("DNS 记录数据越界")
            if (type == TYPE_A && clazz == CLASS_IN && length == IPV4_LENGTH) {
                addresses += InetAddress.getByAddress(hostname, data.copyOfRange(offset, offset + IPV4_LENGTH))
            }
            offset += length
        }
        return addresses
    }

    private fun skipDnsName(data: ByteArray, startOffset: Int): Int {
        var offset = startOffset
        var jumps = 0
        while (offset < data.size) {
            val length = data[offset].toInt() and 0xFF
            if (length == 0) return offset + 1
            if (length and 0xC0 == 0xC0) return offset + 2
            offset += length + 1
            jumps++
            if (jumps > MAX_LABELS) throw IOException("DNS 名称压缩异常")
        }
        throw IOException("DNS 名称越界")
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArray.readUShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long,
    )

    companion object {
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 1_500
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val HEADER_SIZE = 12
        private const val MAX_PACKET_SIZE = 512
        private const val MAX_LABELS = 128
        private const val TYPE_A = 1
        private const val CLASS_IN = 1
        private const val IPV4_LENGTH = 4
    }
}
