package com.theblocker.app.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads DNS queries from the VPN TUN interface and forwards them to an upstream
 * filtered DNS server (CleanBrowsing), writing responses back onto the TUN.
 */
class DnsVpnForwarder(
    private val vpnService: VpnService,
    private val vpnInterface: ParcelFileDescriptor,
    private val upstreamDns: List<InetAddress>
) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread({
            val input = FileInputStream(vpnInterface.fileDescriptor)
            val output = FileOutputStream(vpnInterface.fileDescriptor)
            val buffer = ByteArray(32767)
            try {
                while (running.get()) {
                    val length = input.read(buffer)
                    if (length > 0) {
                        handlePacket(buffer, length, output)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e(TAG, "DNS forwarder stopped unexpectedly", e)
                }
            }
        }, "DnsVpnForwarder").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    private fun handlePacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 28) return // IPv4 (20) + UDP (8)
        if ((packet[0].toInt() ushr 4) != 4) return // IPv4 only

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return
        if (packet[9].toInt() and 0xFF != 17) return // UDP

        val udpOffset = ihl
        val destPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
            (packet[udpOffset + 3].toInt() and 0xFF)
        if (destPort != 53) return

        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or
            (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLength = udpLength - 8
        if (dnsLength <= 0 || dnsOffset + dnsLength > length) return

        val query = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val response = queryUpstream(query) ?: return

        val responsePacket = buildDnsResponsePacket(packet, ihl, response)
        output.write(responsePacket)
    }

    private fun queryUpstream(query: ByteArray): ByteArray? {
        for (dns in upstreamDns) {
            try {
                DatagramSocket().use { socket ->
                    vpnService.protect(socket)
                    socket.soTimeout = 3_000
                    val request = DatagramPacket(query, query.size, dns, 53)
                    socket.send(request)
                    val buf = ByteArray(4096)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    return buf.copyOf(reply.length)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upstream DNS failed for ${dns.hostAddress}", e)
            }
        }
        return null
    }

    private fun buildDnsResponsePacket(request: ByteArray, ihl: Int, dnsResponse: ByteArray): ByteArray {
        val udpOffset = ihl
        val totalLength = ihl + 8 + dnsResponse.size
        val out = ByteArray(totalLength)

        // Copy original IP header, then swap addresses.
        System.arraycopy(request, 0, out, 0, ihl)
        System.arraycopy(request, 12, out, 16, 4) // src <- original dest
        System.arraycopy(request, 16, out, 12, 4) // dest <- original src

        out[2] = (totalLength ushr 8).toByte()
        out[3] = (totalLength and 0xFF).toByte()
        out[10] = 0
        out[11] = 0
        val ipChecksum = ipChecksum(out, ihl)
        out[10] = (ipChecksum ushr 8).toByte()
        out[11] = (ipChecksum and 0xFF).toByte()

        // UDP header: swap ports, set length, skip checksum (0 is valid for IPv4).
        out[udpOffset] = request[udpOffset + 2]
        out[udpOffset + 1] = request[udpOffset + 3]
        out[udpOffset + 2] = request[udpOffset]
        out[udpOffset + 3] = request[udpOffset + 1]
        val udpLength = 8 + dnsResponse.size
        out[udpOffset + 4] = (udpLength ushr 8).toByte()
        out[udpOffset + 5] = (udpLength and 0xFF).toByte()
        out[udpOffset + 6] = 0
        out[udpOffset + 7] = 0

        System.arraycopy(dnsResponse, 0, out, udpOffset + 8, dnsResponse.size)
        return out
    }

    private fun ipChecksum(header: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    companion object {
        private const val TAG = "DnsVpnForwarder"
    }
}
