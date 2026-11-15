# Guidance core

Kotlin/JVM 17 deterministic guidance state machine. No Android, SDK calls, current-lane inference, or TBT distance arithmetic.

`GuidanceEngine` consumes **already projected** longitudinal/cross-track meters. A caller must establish the road match and its ambiguity before providing `ProjectedSample`; raw latitude/longitude must never be passed as meters. `syntheticSegment()` creates a fictional local road and cannot be used with live or captured-SDK sources.

```kotlin
val engine = GuidanceEngine(syntheticSegment())
val decision = engine.update(
    ProjectedSample(200.0, 0.0, 90.0, 1_000,
        InputSource.REPLAY_SYNTHETIC, 0, "synthetic-route"),
    nowElapsedMs = 1_000, nowEpochMs = 1_794_585_600_000,
)
```

Time contracts:

- Elapsed times share one monotonic clock. `measurementElapsedMs`, when present, is checked separately from receipt time.
- Expiry uses epoch time. A future evidence check date or expired evidence suppresses output.
- At exactly 10 seconds of sample age the input is stale. Preview, approach, and passage thresholds are inclusive.
- `changeRoute(generation)` clears old input and visible guidance even if route ID text is unchanged. Older generation samples are rejected.
- `reset(source)` starts a fresh diagnostic/session counter. `switchSource(source,generation)` preserves cumulative counters but discards source state.
- `setAvailable(true)` does not restore the old card; a new valid sample is needed.

`previewCount` is cumulative per engine session, not an instruction to announce on every render. The UI should announce only on a count increment within the active pass/source. The engine never emits a lane-keep or merge-now command.

Tests:

Run `./gradlew :core:test` from the app project. `AcceptanceFixtureTest` reads independent fixtures under `verification/scenarios/`; unsupported event types fail explicitly. It evaluates state and preview counts, not visual UI assertions or the actual quality of external road evidence. `GuidanceEngineTest` adds source isolation, invalid values, lifecycle, boundary, and deterministic replay checks.

Core unit/fixture success does **not** prove actual GPS movement, real-road geometry validity, Android screen usability, or human comprehension. Those remain separate acceptance gates.

## Geographic projection

`GeoProjector.project(GeoPoint(latitude, longitude), List<RoadPolyline>)` projects EPSG:4326 degrees onto bounded great-circle polyline segments. Vertex order determines forward heading and accumulated `alongMeters`; `crossMeters` is positive left of that direction. `distanceMeters` includes endpoint overshoot and is the corridor match metric. Do not use signed cross distance alone to accept a point beyond an endpoint.

The projector uses a mean-radius Earth sphere (6,371,008.8m), not an ellipsoidal surveying model. It does not identify lanes. Road provenance, geometry approval, sensor accuracy, and temporal alignment remain caller responsibilities. `levelTag` never selects an overpass over a surface road. Competing nearby roads and self-crossing longitudinal positions remain ambiguous; malformed catalogue entries make `unambiguousCandidate` unavailable. All synthetic local-meter fixtures stay in `GuidanceEngine`; they are not accepted as geographic CRS input.

## Short availability interruption recovery

An availability interruption immediately hides the card. The engine may resume the **same already-issued pass** only after a new valid measurement on the same route generation, route ID, segment version and source. The observation gap must be strictly less than the existing stale interval (10 seconds), measurement time must increase, and the position must not move backwards or violate the existing jump/direction/corridor/ambiguity checks. Real/captured sources need original measurement-clock values on both sides; receipt time alone cannot prove recovery. Resume retains the pass ID and preview count, so it emits no new preview.

Changing route generation/ID, data version or source discards recovery state. A ten-second-or-longer observation gap also cannot restore that pass. Cooldown duration is unchanged. **The ViewModel currently treats a newly created SDK reader as a new global route generation. A full Activity/reader restart therefore does not qualify for this narrow recovery path.** It must revalidate its navigation context and follows the existing route-change cooldown/late-arrival policy. This is deliberately different from a brief availability failure within one continuing reader/route session.
