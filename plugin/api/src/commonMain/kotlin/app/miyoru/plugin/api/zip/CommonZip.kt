package app.miyoru.plugin.api.zip

import app.miyoru.plugin.api.PackageEntry
import app.miyoru.plugin.api.ZipReader

private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
private fun u32(b: ByteArray, o: Int): Long =
    (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
        ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

private object Crc32 {
    private val table: IntArray = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 1 != 0) -0x12477ce0 xor (c ushr 1) else c ushr 1 }
        c
    }

    fun of(data: ByteArray, off: Int = 0, len: Int = data.size - off): Long {
        var crc = -1
        for (i in off until off + len) crc = table[(crc xor data[i].toInt()) and 0xFF] xor (crc ushr 8)
        return (crc xor -1).toLong() and 0xFFFFFFFFL
    }
}

private class BitReader(private val data: ByteArray, start: Int) {
    private var bytePos = start
    private var bitBuf = 0
    private var bitCount = 0

    fun readBits(n: Int): Int {
        var result = 0
        var shift = 0
        var need = n
        while (need > 0) {
            if (bitCount == 0) {
                bitBuf = data[bytePos++].toInt() and 0xFF
                bitCount = 8
            }
            val take = minOf(need, bitCount)
            result = result or ((bitBuf and ((1 shl take) - 1)) shl shift)
            bitBuf = bitBuf ushr take
            bitCount -= take
            need -= take
            shift += take
        }
        return result
    }

    fun alignToByte() {
        bitBuf = 0
        bitCount = 0
    }

    fun bytePos(): Int = bytePos
}

private class Huffman(val maxBits: Int, private val table: Map<Int, Pair<Int, Int>>) {
    fun decode(r: BitReader): Int {
        var code = 0
        var len = 0
        while (true) {
            code = (code shl 1) or r.readBits(1)
            len++
            if (len > maxBits) throw IllegalArgumentException("bad huffman code")
            // 前缀无关：规范 Huffman 码具有前缀无关性，同 key 不会对应两个有效码
            val hit = table[code shl (maxBits - len)]
            if (hit != null && hit.second == len) return hit.first
        }
    }

    companion object {
        fun build(lengths: IntArray): Huffman {
            var maxBits = 0
            lengths.forEach { if (it > maxBits) maxBits = it }
            if (maxBits == 0) return Huffman(1, emptyMap())
            if (maxBits > 15) throw IllegalArgumentException("code too long")
            val blCount = IntArray(maxBits + 1)
            lengths.forEach { if (it != 0) blCount[it]++ }
            val nextCode = IntArray(maxBits + 1)
            var code = 0
            for (bits in 1..maxBits) {
                code = (code + blCount[bits - 1]) shl 1
                nextCode[bits] = code
            }
            val table = mutableMapOf<Int, Pair<Int, Int>>()
            for (sym in lengths.indices) {
                val len = lengths[sym]
                if (len != 0) {
                    val c = nextCode[len]++
                    table[c shl (maxBits - len)] = sym to len
                }
            }
            return Huffman(maxBits, table)
        }
    }
}

private val LENGTH_BASE = intArrayOf(
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
    35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
)
private val LENGTH_EXTRA = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
    3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
)
private val DIST_BASE = intArrayOf(
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
    257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
)
private val DIST_EXTRA = intArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
    7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
)
private val CL_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

private val FIXED_LITLEN: Huffman by lazy {
    val lens = IntArray(288)
    for (i in 0..143) lens[i] = 8
    for (i in 144..255) lens[i] = 9
    for (i in 256..279) lens[i] = 7
    for (i in 280..287) lens[i] = 8
    Huffman.build(lens)
}
private val FIXED_DIST: Huffman by lazy {
    Huffman.build(IntArray(32) { 5 })
}

private fun inflateBlock(r: BitReader, out: ByteArray, startPos: Int, litLen: Huffman, dist: Huffman): Int {
    var pos = startPos
    while (true) {
        val sym = litLen.decode(r)
        when {
            sym < 256 -> {
                if (pos >= out.size) throw IllegalArgumentException("output overrun")
                out[pos++] = sym.toByte()
            }
            sym == 256 -> return pos
            else -> {
                val li = sym - 257
                var len = LENGTH_BASE[li] + if (LENGTH_EXTRA[li] > 0) r.readBits(LENGTH_EXTRA[li]) else 0
                val dsym = dist.decode(r)
                var d = DIST_BASE[dsym] + if (DIST_EXTRA[dsym] > 0) r.readBits(DIST_EXTRA[dsym]) else 0
                if (d > pos) throw IllegalArgumentException("bad distance")
                while (len-- > 0) {
                    if (pos >= out.size) throw IllegalArgumentException("output overrun")
                    out[pos] = out[pos - d]
                    pos++
                }
            }
        }
    }
}

private fun inflateRaw(data: ByteArray, offset: Int, expectedSize: Int): ByteArray {
    val out = ByteArray(expectedSize)
    val r = BitReader(data, offset)
    var pos = 0
    var last = false
    while (!last) {
        last = r.readBits(1) == 1
        when (r.readBits(2)) {
            0 -> {
                r.alignToByte()
                val len = r.readBits(16)
                val nlen = r.readBits(16)
                if (len != (nlen xor 0xFFFF)) throw IllegalArgumentException("bad stored block")
                if (pos + len > out.size) throw IllegalArgumentException("output overrun")
                data.copyInto(out, pos, r.bytePos(), r.bytePos() + len)
                // 推进读取位置
                repeat(len) { r.readBits(8) }
                pos += len
            }
            1 -> pos = inflateBlock(r, out, pos, FIXED_LITLEN, FIXED_DIST)
            2 -> {
                val hlit = r.readBits(5) + 257
                val hdist = r.readBits(5) + 1
                val hclen = r.readBits(4) + 4
                val clLens = IntArray(19)
                for (i in 0 until hclen) clLens[CL_ORDER[i]] = r.readBits(3)
                val clTree = Huffman.build(clLens)
                val total = hlit + hdist
                val lens = IntArray(total)
                var i = 0
                var prev = 0
                while (i < total) {
                    val s = clTree.decode(r)
                    when (s) {
                        in 0..15 -> {
                            lens[i++] = s
                            prev = s
                        }
                        16 -> {
                            val rep = r.readBits(2) + 3
                            repeat(rep) { lens[i++] = prev }
                        }
                        17 -> {
                            val rep = r.readBits(3) + 3
                            repeat(rep) { lens[i++] = 0 }
                        }
                        else -> {
                            val rep = r.readBits(7) + 11
                            repeat(rep) { lens[i++] = 0 }
                        }
                    }
                }
                val litLen = Huffman.build(lens.copyOf(hlit))
                val dist = Huffman.build(lens.copyOfRange(hlit, total))
                pos = inflateBlock(r, out, pos, litLen, dist)
            }
            else -> throw IllegalArgumentException("bad block type")
        }
    }
    if (pos != out.size) throw IllegalArgumentException("size mismatch")
    return out
}

private data class CentralEntry(
    val name: String,
    val method: Int,
    val crc: Long,
    val compSize: Long,
    val uncompSize: Long,
    val localOffset: Long,
    val flags: Int,
    val isDir: Boolean,
)

/**
 * 纯 Kotlin ZIP 解包（stored + deflated，拒绝加密/分卷/zip64/data-descriptor）。
 * 全端共用，替代各平台 java.util.zip 实现。
 */
object CommonZipReader : ZipReader {
    override suspend fun read(zipBytes: ByteArray): List<PackageEntry> {
        if (zipBytes.size < 22) throw IllegalArgumentException("too small")
        var eocd = -1
        var i = zipBytes.size - 22
        val floor = maxOf(0, zipBytes.size - 22 - 65535)
        while (i >= floor) {
            if (u32(zipBytes, i) == 0x06054B50L) {
                eocd = i
                break
            }
            i--
        }
        if (eocd < 0) throw IllegalArgumentException("no eocd")
        val count = u16(zipBytes, eocd + 10)
        var coff = u32(zipBytes, eocd + 16).toInt()
        val entries = mutableListOf<CentralEntry>()
        repeat(count) {
            if (u32(zipBytes, coff) != 0x02014B50L) throw IllegalArgumentException("bad central dir")
            val flags = u16(zipBytes, coff + 8)
            val method = u16(zipBytes, coff + 10)
            val crc = u32(zipBytes, coff + 16)
            val comp = u32(zipBytes, coff + 20)
            val uncomp = u32(zipBytes, coff + 24)
            val nameLen = u16(zipBytes, coff + 28)
            val extraLen = u16(zipBytes, coff + 30)
            val commentLen = u16(zipBytes, coff + 32)
            val localOff = u32(zipBytes, coff + 42)
            val nameBytes = zipBytes.copyOfRange(coff + 46, coff + 46 + nameLen)
            val name = nameBytes.decodeToString()
            val isDir = name.endsWith("/")
            entries.add(CentralEntry(name, method, crc, comp, uncomp, localOff, flags, isDir))
            coff += 46 + nameLen + extraLen + commentLen
        }
        if (entries.size > 2000) throw IllegalArgumentException("too many entries")
        return entries.map { e ->
            if (e.flags and 0x1 != 0) throw IllegalArgumentException("encrypted")
            // bit3（data descriptor）：大小以中央目录为准，跳过描述符本身，无需解析。
            if (e.flags and 0x8 != 0 && e.compSize == 0L && e.uncompSize > 0) {
                throw IllegalArgumentException("streaming sizes unknown")
            }
            if (e.uncompSize > 200L * 1024 * 1024) throw IllegalArgumentException("entry too large")
            if (e.isDir) {
                PackageEntry(if (e.name.endsWith("/")) e.name else e.name + "/", ByteArray(0))
            } else {
                val lo = e.localOffset.toInt()
                if (u32(zipBytes, lo) != 0x04034B50L) throw IllegalArgumentException("bad local header")
                val lhNameLen = u16(zipBytes, lo + 26)
                val lhExtraLen = u16(zipBytes, lo + 28)
                val dataOff = lo + 30 + lhNameLen + lhExtraLen
                val raw: ByteArray = when (e.method) {
                    0 -> zipBytes.copyOfRange(dataOff, dataOff + e.uncompSize.toInt())
                    8 -> inflateRaw(zipBytes, dataOff, e.uncompSize.toInt())
                    else -> throw IllegalArgumentException("method ${e.method}")
                }
                if (Crc32.of(raw) != e.crc) throw IllegalArgumentException("crc mismatch: ${e.name}")
                PackageEntry(e.name, raw)
            }
        }
    }
}
