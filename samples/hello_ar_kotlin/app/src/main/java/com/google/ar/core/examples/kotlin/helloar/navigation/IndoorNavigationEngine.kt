package com.google.ar.core.examples.kotlin.helloar.navigation

import kotlin.math.*

/** Building-local meters: X east on floor plan, Z north on floor plan, Y up. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
  operator fun plus(b: Vec3) = Vec3(x + b.x, y + b.y, z + b.z)
  operator fun minus(b: Vec3) = Vec3(x - b.x, y - b.y, z - b.z)
  fun distance(b: Vec3) = sqrt((x-b.x).pow(2) + (y-b.y).pow(2) + (z-b.z).pow(2))
}
data class Marker(val id: String, val floor: Int, val position: Vec3, val yawRad: Double)
data class Beacon(val id: String, val floor: Int, val position: Vec3, val txPowerAt1m: Int? = null)
data class ObjectAnchor(val id: String, val label: String, val floor: Int, val position: Vec3)
data class Waypoint(val id: String, val floor: Int, val position: Vec3, val accessible: Boolean = true)
data class Edge(val from: String, val to: String, val meters: Double, val accessible: Boolean = true)
data class PositionFix(val floor: Int, val position: Vec3, val source: Source, val accuracyMeters: Double, val timestampMs: Long) {
  enum class Source { MARKER_AR, BLE_PROXIMITY }
}
data class Route(val nodes: List<Waypoint>, val distanceMeters: Double)

/**
 * The building coordinate system must be surveyed before use.
 * Marker localization is a rigid transform from the current ARCore session to building coordinates.
 * Beacon observations are proximity hints only; RSSI is not a reliable room-level distance measure.
 */
class IndoorNavigationEngine(
  val markers: Map<String, Marker>,
  val beacons: Map<String, Beacon>,
  val objects: Map<String, ObjectAnchor>,
  val waypoints: Map<String, Waypoint>,
  val edges: List<Edge>
) {
  private var alignment: Alignment? = null
  var lastFix: PositionFix? = null
    private set

  private data class Alignment(val floor: Int, val yaw: Double, val origin: Vec3, val arMarker: Vec3)

  /** Both yaw angles use the same axis convention; marker image orientation must be calibrated. */
  fun observeMarker(id: String, arPosition: Vec3, arYawRad: Double, nowMs: Long): PositionFix? {
    val marker = markers[id] ?: return null
    val deltaYaw = marker.yawRad - arYawRad
    alignment = Alignment(marker.floor, deltaYaw, marker.position, arPosition)
    val fix = PositionFix(marker.floor, marker.position, PositionFix.Source.MARKER_AR, 0.5, nowMs)
    lastFix = fix
    return fix
  }

  /** Transform ARCore world-frame camera/object position to the surveyed building plan. */
  fun observeArPose(arPosition: Vec3, nowMs: Long): PositionFix? {
    val a = alignment ?: return null
    val d = arPosition - a.arMarker
    val c = cos(a.yaw)
    val s = sin(a.yaw)
    val local = Vec3(c*d.x - s*d.z, d.y, s*d.x + c*d.z)
    val position = a.origin + local
    // Drift grows after a marker scan: accuracy is a conservative placeholder, not measured covariance.
    val drift = (position.distance(a.origin) * 0.05).coerceAtMost(5.0)
    return PositionFix(a.floor, position, PositionFix.Source.MARKER_AR, 0.5 + drift, nowMs).also { lastFix = it }
  }

  /** A known strongest BLE beacon may identify an approximate zone, not an exact location. */
  fun observeBeaconRssi(readings: Map<String, Int>, nowMs: Long): PositionFix? {
    val winner = readings.filterKeys { it in beacons }.maxByOrNull { it.value } ?: return null
    val b = beacons.getValue(winner.key)
    val fix = PositionFix(b.floor, b.position, PositionFix.Source.BLE_PROXIMITY, 8.0, nowMs)
    // Do not override an active marker-calibrated AR pose with coarse BLE.
    if (lastFix == null || lastFix!!.source == PositionFix.Source.BLE_PROXIMITY ||
      nowMs - lastFix!!.timestampMs > 15_000) lastFix = fix
    return fix
  }

  fun clearAlignment() { alignment = null; lastFix = null }

  /** Dijkstra routing, with floor links represented as explicit surveyed edges. */
  fun route(fromId: String, toId: String, wheelchair: Boolean = false): Route? {
    if (fromId !in waypoints || toId !in waypoints) return null
    if (wheelchair && (!waypoints.getValue(fromId).accessible || !waypoints.getValue(toId).accessible)) return null
    val distance = mutableMapOf(fromId to 0.0)
    val previous = mutableMapOf<String, String>()
    val remaining = waypoints.keys.filter { !wheelchair || waypoints.getValue(it).accessible }.toMutableSet()
    while (remaining.isNotEmpty()) {
      val current = remaining.minByOrNull { distance[it] ?: Double.POSITIVE_INFINITY } ?: break
      val cost = distance[current] ?: break
      if (!cost.isFinite()) break
      remaining.remove(current)
      if (current == toId) break
      edges.forEach { e ->
        if (e.meters < 0 || !e.meters.isFinite() || (wheelchair && !e.accessible)) return@forEach
        val next = when (current) { e.from -> e.to; e.to -> e.from; else -> return@forEach }
        if (next !in remaining) return@forEach
        val candidate = cost + e.meters
        if (candidate < (distance[next] ?: Double.POSITIVE_INFINITY)) {
          distance[next] = candidate
          previous[next] = current
        }
      }
    }
    val total = distance[toId] ?: return null
    val path = mutableListOf(toId)
    var node = toId
    while (node != fromId) {
      node = previous[node] ?: return null
      path.add(node)
    }
    return Route(path.asReversed().map { waypoints.getValue(it) }, total)
  }

  fun nearestWaypoint(position: Vec3, floor: Int, wheelchair: Boolean = false): Waypoint? =
    waypoints.values.filter { it.floor == floor && (!wheelchair || it.accessible) }
      .minByOrNull { it.position.distance(position) }

  fun objectNear(position: Vec3, floor: Int, radiusMeters: Double = 2.0): List<ObjectAnchor> =
    objects.values.filter { it.floor == floor && it.position.distance(position) <= radiusMeters }
}
