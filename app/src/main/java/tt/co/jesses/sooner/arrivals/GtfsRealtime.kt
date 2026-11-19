package tt.co.jesses.sooner.arrivals

/**
 * A minimal reader for GTFS-realtime trip updates: just enough protobuf to pull
 * the predictions for one stop out of TransLink's whole-system feed (about
 * 400 KB) without a protobuf library. Field numbers follow gtfs-realtime.proto,
 * the same as tools/decode_gtfsrt.py.
 */
object GtfsRealtime {

    /** One live prediction at the requested stop. */
    data class Prediction(
        val tripId: String,
        val routeId: String,
        /** Unix seconds; arrival time, or departure time if the feed gives only that. */
        val time: Long,
        val vehicleLabel: String?,
    )

    data class Snapshot(
        /** Feed header timestamp, Unix seconds, or null if absent. */
        val timestamp: Long?,
        val predictions: List<Prediction>,
    )

    // TripDescriptor.ScheduleRelationship and StopTimeUpdate.ScheduleRelationship values.
    private const val TRIP_CANCELED = 3L
    private const val STOP_SKIPPED = 1L
    private const val STOP_NO_DATA = 2L

    /**
     * Predictions at [stopId], in feed order. Leaves out cancelled trips,
     * skipped stops and stop updates with no time, as the live feed check in
     * docs/PLAN.md found all three.
     */
    fun predictionsAt(feed: ByteArray, stopId: String): Snapshot {
        val target = stopId.encodeToByteArray()
        var timestamp: Long? = null
        val out = ArrayList<Prediction>()
        Reader(feed, 0, feed.size).forEachField { num, r ->
            when (num) {
                1 -> r.message().forEachField { n, h -> if (n == 3) timestamp = h.varint() else h.skip() }
                2 -> r.message().forEachField { n, e ->
                    if (n == 3) tripUpdate(e.message(), target, out) else e.skip()
                }
                else -> r.skip()
            }
        }
        return Snapshot(timestamp, out)
    }

    private fun tripUpdate(r: Reader, target: ByteArray, out: MutableList<Prediction>) {
        var tripId = ""
        var routeId = ""
        var canceled = false
        var vehicle: String? = null
        var times = LongArray(0)
        r.forEachField { num, f ->
            when (num) {
                1 -> f.message().forEachField { n, t ->
                    when (n) {
                        1 -> tripId = t.string()
                        4 -> canceled = t.varint() == TRIP_CANCELED
                        5 -> routeId = t.string()
                        else -> t.skip()
                    }
                }
                2 -> stopTimeAt(f.message(), target)?.let { times += it }
                3 -> f.message().forEachField { n, v -> if (n == 2) vehicle = v.string() else v.skip() }
                else -> f.skip()
            }
        }
        if (canceled) return
        for (time in times) out += Prediction(tripId, routeId, time, vehicle)
    }

    /** The predicted time if this StopTimeUpdate is for [target] and usable, else null. */
    private fun stopTimeAt(r: Reader, target: ByteArray): Long? {
        var matches = false
        var relationship = 0L
        var arrival: Long? = null
        var departure: Long? = null
        r.forEachField { num, f ->
            when (num) {
                2 -> arrival = eventTime(f.message())
                3 -> departure = eventTime(f.message())
                4 -> matches = f.bytesEqual(target)
                5 -> relationship = f.varint()
                else -> f.skip()
            }
        }
        if (!matches || relationship == STOP_SKIPPED || relationship == STOP_NO_DATA) return null
        return arrival ?: departure
    }

    /** StopTimeEvent.time (field 2), if set and non-zero. */
    private fun eventTime(r: Reader): Long? {
        var time: Long? = null
        r.forEachField { num, f -> if (num == 2) time = f.varint() else f.skip() }
        return time?.takeIf { it > 0 }
    }

    /** Walks the fields of one protobuf message held in buf[start, end). */
    private class Reader(private val buf: ByteArray, private var pos: Int, private val end: Int) {
        private var wireType = 0

        inline fun forEachField(action: (Int, Reader) -> Unit) {
            while (pos < end) {
                val key = rawVarint()
                wireType = (key and 7).toInt()
                val before = pos
                action((key ushr 3).toInt(), this)
                check(pos > before) { "field $wireType not read or skipped" }
            }
            check(pos == end) { "message overran its length" }
        }

        fun varint(): Long {
            expect(WIRE_VARINT)
            return rawVarint()
        }

        fun message(): Reader {
            val len = lengthDelimited()
            return Reader(buf, pos - len, pos)
        }

        fun string(): String {
            val len = lengthDelimited()
            return buf.decodeToString(pos - len, pos)
        }

        fun bytesEqual(other: ByteArray): Boolean {
            val len = lengthDelimited()
            if (len != other.size) return false
            val start = pos - len
            for (i in other.indices) if (buf[start + i] != other[i]) return false
            return true
        }

        fun skip() {
            when (wireType) {
                WIRE_VARINT -> rawVarint()
                WIRE_FIXED64 -> advance(8)
                WIRE_LENGTH -> lengthDelimited()
                WIRE_FIXED32 -> advance(4)
                else -> throw IllegalArgumentException("unsupported wire type $wireType")
            }
        }

        /** Consumes a length-delimited field and returns its length; pos ends after it. */
        private fun lengthDelimited(): Int {
            expect(WIRE_LENGTH)
            val len = rawVarint()
            require(len in 0..(end - pos).toLong()) { "bad length $len" }
            advance(len.toInt())
            return len.toInt()
        }

        private fun advance(n: Int) {
            require(n <= end - pos) { "truncated message" }
            pos += n
        }

        private fun expect(type: Int) {
            require(wireType == type) { "expected wire type $type, got $wireType" }
        }

        private fun rawVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < end) { "truncated varint" }
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "varint too long" }
            }
        }

        companion object {
            const val WIRE_VARINT = 0
            const val WIRE_FIXED64 = 1
            const val WIRE_LENGTH = 2
            const val WIRE_FIXED32 = 5
        }
    }
}
