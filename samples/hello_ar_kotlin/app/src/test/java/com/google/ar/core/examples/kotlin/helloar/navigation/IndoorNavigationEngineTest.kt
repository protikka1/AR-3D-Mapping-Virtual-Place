package com.google.ar.core.examples.kotlin.helloar.navigation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class IndoorNavigationEngineTest {
  private val a = Waypoint("entrance", 0, Vec3(0.0, 0.0, 0.0))
  private val b = Waypoint("hall", 0, Vec3(3.0, 0.0, 0.0))
  private val c = Waypoint("room", 0, Vec3(6.0, 0.0, 0.0))
  private fun engine() = IndoorNavigationEngine(
    markers = mapOf("m1" to Marker("m1", 0, Vec3(10.0, 0.0, 20.0), PI / 2)),
    beacons = mapOf("beacon1" to Beacon("beacon1", 0, Vec3(3.0, 0.0, 0.0))),
    objects = mapOf("desk" to ObjectAnchor("desk", "Information desk", 0, Vec3(2.0, 0.0, 0.0))),
    waypoints = listOf(a,b,c).associateBy { it.id },
    edges = listOf(Edge("entrance","hall",3.0), Edge("hall","room",3.0))
  )

  @Test fun routesThroughExplicitEdges() {
    val route = engine().route("entrance","room")
    assertEquals(listOf("entrance","hall","room"), route!!.nodes.map { it.id })
    assertEquals(6.0, route.distanceMeters, 0.001)
  }

  @Test fun markerAlignmentTransformsCameraPose() {
    val engine = engine()
    engine.observeMarker("m1", Vec3(0.0, 0.0, 0.0), 0.0, 1L)
    val fix = engine.observeArPose(Vec3(1.0, 0.0, 0.0), 2L)!!
    assertEquals(10.0, fix.position.x, 0.001)
    assertEquals(21.0, fix.position.z, 0.001)
    assertEquals(PositionFix.Source.MARKER_AR, fix.source)
  }

  @Test fun beaconDoesNotOverwriteFreshVisualFix() {
    val engine = engine()
    engine.observeMarker("m1", Vec3(0.0, 0.0, 0.0), 0.0, 1000L)
    engine.observeBeaconRssi(mapOf("beacon1" to -55), 1001L)
    assertEquals(PositionFix.Source.MARKER_AR, engine.lastFix!!.source)
  }

  @Test fun noRouteWithoutGraphConnection() {
    val e = engine()
    assertNull(e.route("entrance","unknown"))
    assertEquals("desk", e.objectNear(Vec3(2.0, 0.0, 0.0), 0).single().id)
  }
}
