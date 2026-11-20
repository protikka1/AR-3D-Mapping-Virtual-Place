# Indoor navigation foundation (Kotlin ARCore sample)

This branch introduces a building-local navigation model under
`samples/hello_ar_kotlin/app/src/main/java/com/google/ar/core/examples/kotlin/helloar/navigation/`.

## Implemented in source
- Rigid 2D yaw + 3D translation alignment from a **known physical marker** to the ARCore session.
- AR session pose -> building floor-plan (meters) transformation and explicit floor numbers.
- Permission-gated, opt-in, foreground Android BLE scanning; scanner stops on pause.
- Coarse strongest-beacon zone observation with an 8 m **placeholder** uncertainty; it does **not** claim meter-accurate trilateration.
- Surveyed object anchor lookup and Dijkstra waypoint routing, including accessible-route filtering.

## Not yet implemented / not production ready
This is not a complete indoor-navigation application. The ARCore sample UI/renderer
is **not yet connected** to these classes. There is no live marker database, beacon
inventory, indoor floor-plan editor, navigation overlay, or persistence. No device
build or location-accuracy validation has been performed.

## Integration steps
1. Survey a real building and define meter-scale floor-plan coordinates and floors.
2. Install unique high-quality printed markers. Create an ARCore
   `AugmentedImageDatabase` using documented marker images (or adopt an AprilTag library).
   Map each detected image name/index to a `Marker` with surveyed position/yaw.
3. Enable `Config.AugmentedImageDatabase` in the sample session, then process
   `frame.getUpdatedTrackables(AugmentedImage::class.java)`. Only call
   `observeMarker` when a known image is FULL_TRACKING and its pose is reliable.
   Extract its ARCore world pose, including yaw in an agreed right-handed convention.
4. Every tracking frame, pass camera world coordinates to `observeArPose`.
   Clear alignment on session reset or map change; never silently reuse a stale frame.
5. Ask user consent for BLE and Android 12+ `BLUETOOTH_SCAN` /
   `BLUETOOTH_CONNECT` (Android <=11 requires fine location at runtime).
   Create `IndoorBeaconScanner`, call `start()` after consent and `stop()`
   in `onPause`. Aggregate RSSI samples per provisioned beacon; do not use raw MAC
   addresses as permanent identity for privacy-address beacons.
6. Build a connected floor/route graph, with **explicit stair, elevator, door and
   accessible-path edges**. Never route through a wall using straight-line distance.
7. Render directional waypoints in AR, plus a 2D accessible fallback and spoken
   directions. Offer a manual start/location selector for low-confidence fixes.
8. Test in a physical building for calibration, BLE multipath, door transitions,
   ARCore drift, lost tracking, battery, permissions and accessibility.

## Coordinate conventions
`Vec3(x,y,z)` is in surveyed building-local meters: X along floor-plan east,
Z along floor-plan north, Y upwards. The AR camera and marker poses must share a
consistent **yaw** convention for the rigid transformation. GPS latitude/longitude
may be attached to a *building entrance* for outdoor-to-indoor handoff, but does
not replace indoor marker/beacon localization.

## Privacy and navigation safety
Scan only after informed opt-in. Avoid retaining device identifiers or storing raw
camera images. Keep the surveyed map local when possible. BLE RSSI does not give
reliable absolute distance without calibration. Never present this system as an
emergency evacuation aid or guarantee obstacle avoidance. Markers must be
recalibrated if moved.
