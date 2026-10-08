package com.gamehacker.app

import java.io.File
import java.io.RandomAccessFile

data class MemoryRegion(val start: Long, val end: Long)

object MemoryScanner {

    // نتائج البحث السابق للمقارنة (Increased / Decreased / Fuzzy)
    var previousResults: List<Long> = emptyList()
    var previousValues: Map<Long, Long> = emptyMap() // address -> value

    fun getProcessMaps(pid: Int): List<MemoryRegion> {
        val regions = mutableListOf<MemoryRegion>()
        try {
            val mapsFile = File("/proc/$pid/maps")
            if (!mapsFile.exists()) return regions
            mapsFile.readLines().forEach { line ->
                try {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2 && parts[1].contains("rw")) {
                        val range = parts[0].split("-")
                        if (range.size == 2) {
                            val start = range[0].toLong(16)
                            val end = range[1].toLong(16)
                            if (end - start in 1..(32 * 1024 * 1024)) { // حد أقصى 32MB للمنطقة
                                regions.add(MemoryRegion(start, end))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return regions
    }

    fun readValue(pid: Int, address: Long): Long? {
        return try {
            val raf = RandomAccessFile("/proc/$pid/mem", "r")
            raf.seek(address)
            val v = raf.readInt().toLong() and 0xFFFFFFFFL
            raf.close()
            v
        } catch (_: Exception) {
            null
        }
    }

    fun writeValue(pid: Int, address: Long, newValue: Long): Boolean {
        return try {
            val raf = RandomAccessFile("/proc/$pid/mem", "rw")
            raf.seek(address)
            raf.writeInt(newValue.toInt())
            raf.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Exact search */
    fun searchExact(pid: Int, value: Long): List<Long> {
        val results = mutableListOf<Long>()
        val regions = getProcessMaps(pid)
        for (region in regions) {
            try {
                val raf = RandomAccessFile("/proc/$pid/mem", "r")
                var addr = region.start
                while (addr + 4 <= region.end) {
                    try {
                        raf.seek(addr)
                        val read = raf.readInt().toLong() and 0xFFFFFFFFL
                        if (read == value) {
                            results.add(addr)
                            if (results.size >= 500) {
                                raf.close()
                                return finalize(results, pid)
                            }
                        }
                    } catch (_: Exception) {}
                    addr += 4
                }
                raf.close()
            } catch (_: Exception) {}
        }
        return finalize(results, pid)
    }

    /** Fuzzy / Unknown initial value - أول مسح بياخد كل القيم الممكنة في نطاق */
    fun searchFuzzyFirst(pid: Int): List<Long> {
        // في الـPoC بنعمل مسح محدود للسرعة (أول 200 نتيجة من مناطق صغيرة)
        val results = mutableListOf<Long>()
        val regions = getProcessMaps(pid).take(30)
        for (region in regions) {
            try {
                val raf = RandomAccessFile("/proc/$pid/mem", "r")
                var addr = region.start
                var count = 0
                while (addr + 4 <= region.end && count < 50) {
                    results.add(addr)
                    addr += 4
                    count++
                }
                raf.close()
            } catch (_: Exception) {}
            if (results.size >= 300) break
        }
        return finalize(results, pid)
    }

    /** Increased / Decreased / Changed مقارنة بالنتائج السابقة */
    fun searchChanged(pid: Int, mode: String): List<Long> {
        if (previousResults.isEmpty()) return emptyList()
        val newResults = mutableListOf<Long>()
        for (addr in previousResults) {
            val oldVal = previousValues[addr] ?: continue
            val newVal = readValue(pid, addr) ?: continue
            val match = when (mode) {
                "increased" -> newVal > oldVal
                "decreased" -> newVal < oldVal
                "changed"   -> newVal != oldVal
                "unchanged" -> newVal == oldVal
                else -> false
            }
            if (match) newResults.add(addr)
        }
        return finalize(newResults, pid)
    }

    private fun finalize(results: List<Long>, pid: Int): List<Long> {
        previousResults = results
        previousValues = results.associateWith { readValue(pid, it) ?: 0L }
        return results
    }

    fun resetScan() {
        previousResults = emptyList()
        previousValues = emptyMap()
    }

    /**
     * محاولة اكتشاف أسعار شائعة وتحويلها لصفر تلقائيًا.
     * يبحث عن قيم الأسعار المعتادة (بالسنت أو مضاعفات) ويصفرها.
     */
    fun autoZeroPrices(pid: Int): Pair<Int, List<Long>> {
        // قيم شائعة للأسعار (بالسنت أو الوحدات الصحيحة)
        val commonPrices = listOf(
            99L, 199L, 299L, 399L, 499L, 599L, 699L, 799L, 899L, 999L,
            1499L, 1999L, 2499L, 2999L, 4999L, 9999L,
            100L, 200L, 300L, 400L, 500L, 1000L, 2000L, 5000L,
            // أحيانًا بتتخزن كـ float bits أو مضروب في 1000
            9900L, 49900L, 99900L
        )

        val found = mutableListOf<Long>()
        val regions = getProcessMaps(pid)

        for (price in commonPrices) {
            for (region in regions) {
                try {
                    val raf = RandomAccessFile("/proc/$pid/mem", "r")
                    var addr = region.start
                    while (addr + 4 <= region.end) {
                        try {
                            raf.seek(addr)
                            val read = raf.readInt().toLong() and 0xFFFFFFFFL
                            if (read == price) {
                                found.add(addr)
                                if (found.size >= 200) {
                                    raf.close()
                                    // صفر كل العناوين اللي اتلاقت
                                    found.forEach { writeValue(pid, it, 0L) }
                                    previousResults = found
                                    previousValues = found.associateWith { 0L }
                                    return found.size to found
                                }
                            }
                        } catch (_: Exception) {}
                        addr += 4
                    }
                    raf.close()
                } catch (_: Exception) {}
            }
        }

        // كتابة صفر على كل النتائج
        found.forEach { writeValue(pid, it, 0L) }
        previousResults = found
        previousValues = found.associateWith { 0L }
        return found.size to found
    }
}

