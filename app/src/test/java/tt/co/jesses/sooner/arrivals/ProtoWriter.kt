package tt.co.jesses.sooner.arrivals

import java.io.ByteArrayOutputStream

/** Builds protobuf bytes for tests, so fixtures read like the GTFS-realtime schema. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) = apply { key(field, 0); raw(value) }
    fun string(field: Int, value: String) = bytes(field, value.encodeToByteArray())
    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())
    fun fixed32(field: Int, value: Int) = apply {
        key(field, 5)
        repeat(4) { out.write((value ushr (8 * it)) and 0xFF) }
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun bytes(field: Int, value: ByteArray) = apply { key(field, 2); raw(value.size.toLong()); out.write(value) }
    private fun key(field: Int, wireType: Int) = raw((field shl 3 or wireType).toLong())
    private fun raw(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) { out.write(v.toInt()); return }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }
}

/** A trip update entity in GTFS-realtime field numbers. */
fun ProtoWriter.tripUpdate(
    tripId: String,
    routeId: String,
    vehicleLabel: String? = null,
    canceled: Boolean = false,
    stops: ProtoWriter.() -> Unit,
) = message(2) {
    string(1, "entity-$tripId")
    message(3) {
        message(1) {
            string(1, tripId)
            if (canceled) varint(4, 3)
            string(5, routeId)
            varint(6, 0)
        }
        stops()
        vehicleLabel?.let { label -> message(3) { string(1, "id-$label"); string(2, label) } }
    }
}

fun ProtoWriter.stopTime(stopId: String, arrival: Long?, departure: Long? = arrival, skipped: Boolean = false) =
    message(2) {
        varint(1, 7)
        arrival?.let { t -> message(2) { varint(1, (-50L)); varint(2, t) } }
        departure?.let { t -> message(3) { varint(2, t) } }
        string(4, stopId)
        if (skipped) varint(5, 1)
    }

fun feed(timestamp: Long, entities: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply {
    message(1) { string(1, "2.0"); varint(2, 0); varint(3, timestamp) }
    entities()
}.toByteArray()
