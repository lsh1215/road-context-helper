# Road data ingestion

`catalog.json` is intentionally empty: no real road geometry has been obtained and approved. This is not a sample road dataset. The real-time app must remain unsupported until an approved segment is supplied.

- `evidence/`: metadata and browser observations from official sources.
- `incoming/`: local original archives and shapefile companions after authenticated download; do not commit large/raw files by default.
- `tools/validate_ingestion.py`: validates the app-independent ingestion manifest before review. It does not prove road geometry correctness or approve a road by itself.
- `../docs/decisions/road-data.md`: current source findings and exact next steps.

Manifest fields: `schemaVersion=1`, `sourceMode=REAL_WORLD`, `datasetId`, `sourceUrl`, `license`, `attribution`, `sourceCrs`, `outputCrs=EPSG:4326`, `acquiredAt`, `sourceVersion`, `files=[{path,sha256}]`, `review={status,reviewer,reviewedAt,expiresAt,checks}`. Required approved checks are `direction`, `lanePersistence`, `mergeEndpoint`, `connectivity`, `crsTransform`, `currentness`, `license`. This contract is an ingestion manifest, not the core app's RoadSegment model.

Use `python3 tools/validate_ingestion.py path/to/manifest.json --today YYYY-MM-DD`. Any error exits 1. Empty source, hash mismatch, missing companion files, expired review, and synthetic data submitted as real are rejected. Store only relative file paths under the manifest directory.


## Runtime catalog (:data JVM module)

`CatalogLoader.load(json, nowEpochMs)` returns `CatalogLoadResult.Loaded(RuntimeRoadCatalog)` or `Rejected(errors)`. An empty valid catalog is `Loaded` with `supported=false`. Every entry must pass; malformed entries are never silently dropped. This is independent of the Python **ingestion manifest** above.

Runtime root:

- `schemaVersion`: integer 2 (legacy version 1 is accepted only when approvedSegments is empty)
- `catalogVersion`: nonempty string
- `sourceMode`: `REAL_WORLD`
- `approvedSegments`: array (max 256)

Each runtime entry:

- `id`, `version`, `status=APPROVED`, `synthetic=false`, `enabled=true`
- `approachStartMeters`, `mergeStartMeters`, `mergeEndMeters`: ordered distances along the centerline from its first vertex
- `headingDeg`: 0 <= heading < 360, consistent with initial centerline direction (within 45 degrees)
- `corridorHalfWidthMeters`: positive
- `mergeSide`: `LEFT` or `RIGHT`; **the side where the entrance lane lies relative to the main road**. RIGHT means an entrance lane on the right converges toward the left. This is not a detected current lane or maneuver instruction.
- `geometry`: `{crs:"EPSG:4326", centerline:[[longitude,latitude],...]}`; travel-ordered vertices; at least 100m of geometry beyond mergeEnd for passing detection
- `evidence`: `sourceUrl` (HTTPS), `sourceVersion`, `sourceSha256` (64 lowercase hex), `attribution`, `reviewer`, `checkedAtEpochMs`, `expiresAtEpochMs`, `originalCrs`, `license`, `checks`
- `evidence.checks`: all seven ingestion review checks above, each strictly boolean true

A successful load yields `RuntimeRoadEntry(segment: RoadSegment, centerline: RoadPolyline, mergeSide, provenance)`. Coordinates use GeoPoint(latitude, longitude) internally; JSON uses explicit geographic coordinate order [longitude, latitude]. The loader never transforms an unidentified source CRS: transform during ingestion and specify EPSG:4326 output.

The runtime validates provenance fields, not the raw-source checksum itself because raw SHP archives are not bundled in the app. Verify source checksums with the ingestion tool before independent human review. `APPROVED` must only be written after review. Loader unit tests contain fictional metadata and coordinates solely to exercise parsing; they are NOT production data and are not bundled as approved segments.

Runtime validation tests: `./gradlew :data:test` from the application project. Test results must be reported from the actual build, not assumed from source creation.


### Matching coverage is mandatory for supported roads (schema 2)

A collection of supported centerlines alone is not a complete matching universe. An unsupported nearby ramp, bridge or parallel road can otherwise be omitted and make a supported road appear falsely unique.

Every nonempty real catalog now requires:

- `matchingCoverage.crs = EPSG:4326`
- `matchingCoverage.bounds = {south,west,north,east}`: a reviewed geographic rectangle; no antimeridian crossing/polar regions in this implementation
- `matchingCoverage.evidence = {sourceUrl,sourceVersion,sourceSha256,attribution,reviewer,checkedAtEpochMs,expiresAtEpochMs,license,completenessChecks}`
- `completenessChecks`: `fullBounds`, `parallelRoads`, `gradeSeparatedRoads`, `ramps`, `intersections`, all boolean true after independent review of the actual source
- `candidateRoads = [{id,crs:"EPSG:4326",centerline:[[lon,lat],...],levelTag?}, ...]`: all plausible roads within the reviewed bounds, **including unsupported competitors**

Each approved entry's centerline must exactly match the candidate with the same ID and be inside the coverage. Candidate IDs must be unique. Coverage claims are source evidence that needs external review; the parser cannot discover omitted roads from an incomplete source. Do not invent completeness flags to make a bundle pass.

Runtime matching must first validate coverage freshness and ensure the entire search/position uncertainty envelope lies inside coverage, then project against all `candidateRoads`. A unique candidate that is not an approved entry gives no guidance. `levelTag` never proves the vehicle is on a particular road level.

Missing or expired coverage means no real guidance. `data/catalog.json` remains empty and schema 2; no real road data or completeness review has been created.
