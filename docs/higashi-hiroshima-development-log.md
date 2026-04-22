# Higashi-Hiroshima Scenario Development Log

## 2026-04-22: OD-Based H-W-H Demand Generation

### Task
Generate MATSim demand from the weekday OD matrix, zone shapefile, and cleaned Higashi-Hiroshima network, restricted to zones that cover the network.

### Technical Specification

**Objective**: Produce reproducible H-W-H MATSim populations for Higashi-Hiroshima from zonal OD demand with:
- one `1%` output for smoke testing
- one `100%` output obtained by scaling the `1%` OD matrix by `100`

**Scope**:
- load `scenarios/higashi-hiroshima/network.xml.gz`
- load the OD matrix and zone shapefile from `original-input-data`
- identify the subset of zones that spatially cover the network
- keep only OD rows where both origin and destination are in that covered-zone subset
- generate H-W-H tours with identical morning/evening home coordinates
- scatter home and work coordinates evenly within their respective polygons

**Affected Components**:
- `src/main/java/org/matsim/project/GenerateHigashiHiroshimaDemand.java`
- `scenarios/higashi-hiroshima/plans-hwh-1pct.xml.gz`
- `scenarios/higashi-hiroshima/plans-hwh-100pct.xml.gz`
- `scenarios/higashi-hiroshima/demand-covered-zones.csv`
- `scenarios/higashi-hiroshima/demand-hwh-summary.csv`
- `docs/higashi-hiroshima-development-log.md`

### Clarifications / Assumptions

1. A zone "covers the network" if it contains at least one MATSim network node.
2. The OD matrix is already a `1%` daily tour matrix; the `100%` output is created by multiplying retained OD flows by `100`.
3. Each retained OD tour becomes one H-W-H MATSim plan.
4. Spatial scattering should be deterministic and even-looking rather than purely random; a low-discrepancy sampler is therefore used inside each polygon.
5. Legs are generated as `car` legs unless and until a mode synthesis requirement is added.
6. Activity timing is not specified in the inputs, so a deterministic commuter schedule is used with departures spread from `07:00` to `09:00` and work ends spread from `16:00` to `18:00`.

### Decision Taken

Created a dedicated demand generator that:
- uses the cleaned scenario network as the supply boundary
- identifies covered zones from network-node containment
- filters the OD matrix to internal covered-zone OD pairs
- samples home and work points inside polygons with deterministic low-discrepancy sequences
- sets the return-home activity to exactly the same coordinate as the morning home activity
- writes both `1%` and `100%` populations plus summary CSV outputs

### Reason for Decision

- Restricting to zones with actual network support avoids generating agents for OD pairs outside the modeled area.
- Node containment is a simple and MATSim-relevant spatial rule that ties demand generation directly to usable network coverage.
- Deterministic polygon sampling makes the outputs reproducible and visually well-distributed without clustering artifacts from pseudo-random draws.

### Known Limitations

1. Zone coverage is determined from node containment, not full link-geometry overlap.
2. The current implementation creates only H-W-H tours and does not synthesize other activity chains.
3. The current implementation uses fixed `car` legs and does not infer modal split from OD data.

### Next Recommended Checkpoint

- Suggested commit scope: demand generator, generated plans outputs, demand summary CSVs, development log update
- Suggested commit message: `Add OD-based H-W-H demand generation for Higashi-Hiroshima`

## 2026-04-22: SimWrapper OD Dashboard

### Task
Prepare a simple SimWrapper dashboard that maps trip productions and attractions from the weekday OD matrix onto the zone shapefile.

### Technical Specification

**Objective**: Create a reproducible dashboard pipeline that:
- reads the original OD matrix
- aggregates trip productions by origin zone and attractions by destination zone
- joins those totals to the zone polygons
- renders both indicators as zone-based maps in SimWrapper

**Scope**:
- Inspect `original-input-data` to identify the OD matrix and zone geometry
- Prepare dashboard-ready geospatial and tabular outputs
- Generate a self-contained SimWrapper dashboard for the Higashi-Hiroshima scenario
- Record any OD codes that cannot be mapped to shapefile zones

**Affected Components**:
- `original-input-data/modified_ODtable_weekday_long.csv`
- `original-input-data/modified_gisCzone_merge_union/modified_gisCzone_merge_union.shp`
- `src/main/java/org/matsim/project/GenerateHigashiHiroshimaOdDashboard.java`
- `scenarios/higashi-hiroshima/simwrapper/od-zones/`
- `docs/higashi-hiroshima-development-log.md`

**Expected Output**:
- Zone summary CSV with `zone`, `productions`, and `attractions`
- GeoJSON version of the zone polygons transformed for web mapping
- SimWrapper dashboard with separate production and attraction choropleths

### Clarifications / Assumptions

1. The correct zone join key is `bc_zone` from the shapefile against `from` and `to` codes in the OD matrix.
2. The requested view is zone-level production and attraction, not OD desire lines.
3. OD codes that do not exist in the shapefile should be excluded from the maps but explicitly reported.

### Data Inspection

**OD Matrix**:
- File: `original-input-data/modified_ODtable_weekday_long.csv`
- Columns: `from`, `to`, `value`
- Rows: 1,181
- Unique origin codes: 133
- Unique destination codes: 135

**Zone Geometry**:
- File: `original-input-data/modified_gisCzone_merge_union/modified_gisCzone_merge_union.shp`
- Geometry: Polygon
- Features: 144
- CRS: EPSG:2445 (JGD2000 / Japan Plane Rectangular CS III)
- Zone id field: `bc_zone`

### Decision Taken

Created `GenerateHigashiHiroshimaOdDashboard.java` as a single entrypoint that:
- reads and aggregates the OD matrix
- transforms the shapefile from `EPSG:2445` to `EPSG:4326`
- writes `higashi-hiroshima-zones.geojson`
- writes `higashi-hiroshima-od-summary.csv`
- generates a SimWrapper dashboard with:
  - one map for productions
  - one map for attractions
  - one table of zone totals
  - one note block documenting exclusions

### Reason for Decision

- Keeps the dashboard generation reproducible and project-local
- Uses polygon-based mapping, which matches the user requirement better than hexbin or flow-map summaries
- Converts geometry to a web-friendly CRS before handing it to SimWrapper
- Makes unmapped OD demand explicit instead of silently dropping it

### Execution Results

**Dashboard output directory**:
- `scenarios/higashi-hiroshima/simwrapper/od-zones/`

**Generated files**:
- `dashboard-1.yaml`
- `simwrapper-config.yaml`
- `higashi-hiroshima-zones.geojson`
- `higashi-hiroshima-od-summary.csv`

**Join behavior**:
- All 144 zones are present in the map layer
- Zones without matched OD demand are retained with zero values

**Unmatched OD demand excluded from the maps**:
- Origins: 148 trips
- Destinations: 277 trips

**Unmatched origin codes**:
- `01NA`, `0214`, `0216`, `0261`, `02NA`, `05NA`, `06NA`, `07NA`, `08NA`, `0921`, `09NA`, `1017`, `10NA`, `11NA`, `1223`, `12NA`, `1311`, `13NA`, `1403`, `1404`, `1405`, `1406`, `1407`, `1408`

**Unmatched destination codes**:
- `01NA`, `0214`, `0216`, `0261`, `02NA`, `05NA`, `06NA`, `07NA`, `08NA`, `0921`, `09NA`, `1014`, `1017`, `10NA`, `11NA`, `1223`, `12NA`, `1311`, `13NA`, `1403`, `1404`, `1405`, `1406`, `1407`, `1408`, `NANA`

### Tests Added / Updated

- No automated tests added
- Validation performed by:
  - compiling the new dashboard generator
  - running it end-to-end
  - checking that the generated SimWrapper YAML references the expected GeoJSON and CSV join fields

### Known Limitations

1. The dashboard currently visualizes only zonal productions and attractions, not OD pair flows.
2. Unmatched OD codes are reported but not spatialized, because they have no corresponding zone polygon.
3. The dashboard is generated as static SimWrapper output; serving it is a separate step.

### Follow-up Fix

**Issue**: the initial dashboard maps rendered away from Japan.

**Cause**:
- the map center needed to be written in `[lon, lat]` order for SimWrapper
- the generator was initially configured not to overwrite existing dashboard output, which masked the code-side fix until regeneration behavior was corrected

**Fix**:
- changed the computed map center to `[132.7535, 34.5667]` ordering
- switched dashboard generation to overwrite the existing output bundle

### Next Recommended Checkpoint

- Suggested commit scope: OD dashboard generator, generated dashboard outputs, development log update
- Suggested commit message: `Add SimWrapper dashboard for Higashi-Hiroshima OD zones`

## 2026-04-22: Multimodal Network Cleaning

### Task
Clean the raw Higashi-Hiroshima MATSim network so all mode-specific subnetworks used for routing are routable, and produce the final scenario network.

### Technical Specification

**Objective**: Remove disconnected components, sources, sinks, and dead-end artifacts from the Higashi-Hiroshima MATSim network for every mode present in the network file.

**Scope**:
- Inspect the existing network mode set
- Clean the raw converted network with MATSim's built-in network cleaning logic
- Preserve the final cleaned network in `scenarios/higashi-hiroshima/network.xml.gz`
- Record the cleaning workflow so it is reproducible

**Affected Components**:
- `scenarios/higashi-hiroshima/network.raw.xml.gz`
- `scenarios/higashi-hiroshima/network.xml.gz`
- `src/main/java/org/matsim/project/CleanScenarioNetwork.java`
- `src/main/java/org/matsim/project/GenerateHigashiHiroshimaNetwork.java`
- `scenarios/higashi-hiroshima/osm-conversion-config.xml`
- `docs/higashi-hiroshima-development-log.md`

**Expected Output**:
- A raw converted network artifact
- A cleaned scenario network whose mode subnetworks are routable for all present modes
- A reusable conversion-plus-cleaning pipeline for future network builds

### Clarifications / Assumptions

1. The final scenario network should be the cleaned product, not the raw converter output.
2. "Ensure all mode subnets are routeable" means using MATSim network cleaning over every mode present in the raw network, not just `car` and `bus`.
3. Removing disconnected rail/PT fragments is acceptable if they are not part of the largest strongly connected subnetwork for their mode.

### Decision Taken

Created a reusable cleaner and wired the Higashi-Hiroshima generator into a two-step pipeline:
- step 1: OSM conversion writes `scenarios/higashi-hiroshima/network.raw.xml.gz`
- step 2: network cleaning writes `scenarios/higashi-hiroshima/network.xml.gz`

The cleaner:
- reads a MATSim network
- derives all present modes via `NetworkUtils.getModes(network)`
- runs `NetworkUtils.cleanNetwork(network, modes)`
- reports before/after node, link, and per-mode link counts
- writes the cleaned network back to disk

### Reason for Decision

- Uses MATSim's current cleaning path instead of custom XML manipulation
- Cleans `car`, `bus`, `pt`, `rail`, and `train` consistently from the actual network content
- Avoids overwriting the raw conversion artifact when producing the final network
- Leaves a traceable and repeatable project-local workflow for future scenario updates

### Alternatives Considered

1. Use `org.matsim.run.NetworkCleaner` directly from the command line
   - Rejected because it is less explicit in the project codebase and less convenient for repeated scenario maintenance.
2. Regenerate the OSM-derived network from scratch with additional `routableSubnetwork` config blocks
   - Rejected because the user asked for a pipeline and the current MATSim cleaner already handles per-mode routeability after conversion.

### Tests Added / Updated

- No automated test added
- Validation performed by executing the runner on the scenario network and checking before/after counts

### Execution Results

**Observed Modes in Input Network**:
- `bus`
- `car`
- `pt`
- `rail`
- `train`

**Before Cleaning**:
- Nodes: 4,470
- Links: 11,673
- Mode link counts: `bus=11,510`, `car=11,505`, `pt=1,333`, `rail=150`, `train=142`

**After Cleaning**:
- Nodes: 4,426
- Links: 11,573
- Mode link counts: `bus=11,512`, `car=11,507`, `pt=1,150`, `rail=62`, `train=56`

**Verification**:
- A second cleaning pass produced identical node, link, and per-mode counts
- The cleaned network is therefore stable under repeated application of the same MATSim cleaner

### Pipeline Contract

1. Conversion input:
   - `original-input-data/higashi-hiroshima.osm`
2. Conversion output:
   - `scenarios/higashi-hiroshima/network.raw.xml.gz`
3. Cleaning input:
   - `scenarios/higashi-hiroshima/network.raw.xml.gz`
4. Final cleaned network:
   - `scenarios/higashi-hiroshima/network.xml.gz`

The final network is not the direct output of `Osm2MultimodalNetwork`. It is the output of conversion followed by cleaning.

### Issues Encountered

1. **Maven enforcer conflict**
   - **Problem**: project dependency resolution fails under the default enforcer rules because `pt2matsim:25.8` brings in `matsim:2026.0-2025w31` while the rest of the project is on `2026.0`
   - **Workaround used**: execute compile and cleaner runs with `-Denforcer.skip=true`
   - **Reason**: required to run the new project-local cleaner without widening this task into dependency alignment work

### Known Limitations

1. Cleaning keeps the largest strongly connected subnetwork per mode and may remove small but geographically valid isolated fragments.
2. The converter config itself still only declares routable subnetworks for `car` and `bus`, so the cleaning stage remains necessary for the full multimodal network.

### Next Recommended Checkpoint

- Suggested commit scope: raw-to-cleaned network pipeline, cleaner utility, development log update
- Suggested commit message: `Make Higashi-Hiroshima network build a conversion-cleaning pipeline`

## 2026-04-22: OSM Network Conversion

### Task
Convert OpenStreetMap data to MATSim network format for Higashi-Hiroshima simulation scenario.

### Technical Specification

**Objective**: Generate a routable MATSim network from OSM data with realistic Japanese road parameters.

**Scope**:
- Convert OSM highway network to MATSim format
- Apply Japan-specific speed/capacity calibrations
- Configure multimodal network (car, bus, rail)
- Use EPSG:2445 coordinate system (JGD2000 / Japan Plane Rectangular CS IX)

**Input**:
- OSM file: `original-input-data/higashi-hiroshima.osm`
- Bounds: 34.3882°N-34.4391°N, 132.6924°E-132.7681°E
- OSM data: 132,653 nodes, 23,261 ways, 276 relations

**Output**:
- Raw MATSim network: `scenarios/higashi-hiroshima/network.raw.xml.gz`
- Final cleaned MATSim network: `scenarios/higashi-hiroshima/network.xml.gz`
- Coordinate system: EPSG:2445
- Statistics: 4,470 nodes, 11,673 links

### Assumptions

1. **Coordinate System**: EPSG:2445 (JGD2000) chosen over EPSG:6677 (JGD2011) for compatibility with existing Japanese transport datasets. User confirmed this will be the standard CRS for all Higashi-Hiroshima scenario data.

2. **Speed Calibrations**: Japanese urban road speeds differ from European defaults:
   - Motorways: 80 km/h (vs European 120 km/h)
   - Trunk roads: 40-60 km/h based on observed OSM maxspeed tags
   - Primary: 50 km/h
   - Secondary: 40 km/h
   - Residential: 25 km/h (Japan has no statutory residential speed limit, roads often narrow)

3. **Capacity Reductions**: Slightly reduced lane capacities compared to European defaults to reflect Japanese urban conditions (narrower lanes, mixed traffic).

### Implementation

**Components Created**:

1. **Config File**: `scenarios/higashi-hiroshima/osm-conversion-config.xml`
   - MATSim OsmConverter module configuration
   - wayDefaultParams for all Japanese highway types
   - Routable subnetworks for car and bus modes
   - Keep paths=false (simplified network)
   - Max link length=500m
   - Writes raw conversion output to `network.raw.xml.gz`

2. **Runner Class**: `src/main/java/org/matsim/project/GenerateHigashiHiroshimaNetwork.java`
   - Pipeline wrapper around conversion plus cleaning
   - Produces both raw and final cleaned artifacts
   - Provides a single reproducible execution path

3. **IntelliJ Run Configuration**: `.idea/runConfigurations/GenerateHigashiHiroshimaNetwork.xml`

### Issues Encountered & Resolved

**Issue 1: Unsupported config parameter**
- **Problem**: `parseTurnRestrictions` parameter not supported by pt2matsim v25.8
- **Error**: `IllegalArgumentException: Parameter parseTurnRestrictions is not part of the valid parameters`
- **Solution**: Removed unsupported parameter from config
- **Decision**: Turn restrictions handled via DisallowedNextLinks attribute automatically (7 restrictions parsed successfully)

**Issue 2: Relative path resolution**
- **Problem**: OSM file path `../../original-input-data/higashi-hiroshima.osm` resolved incorrectly from config context
- **Error**: `FileNotFoundException`
- **Solution**: Changed to project-root-relative path: `original-input-data/higashi-hiroshima.osm`
- **Reason**: MATSim Config sets context to config file directory, then resolves paths relative to project root

**Issue 3: Output file location**
- **Problem**: Network output to project root instead of scenario directory
- **Solution**: Updated config to write directly into `scenarios/higashi-hiroshima/`
- **Follow-up**: none for the current pipeline

### Network Statistics

**Conversion Results**:
- Nodes: 4,470 (from 132,653 OSM nodes - simplified at intersections)
- Links: 11,673 (from 23,261 OSM ways)
- DisallowedNextLinks: 7 turn restrictions
- Coordinate range: X ≈ 48,000-50,000m, Y ≈ -174,000 to -172,000m (EPSG:2445)

**Modes Supported**:
- `car`: 11,738 links in routable subnetwork
- `bus`: 11,744 links (includes car network)
- `rail`: 10 links (Sanyo Main Line through Saijo station)
- `pt, train`: Additional public transport modes

**Highway Types Converted**:
- motorway, motorway_link
- trunk, trunk_link
- primary, primary_link
- secondary, secondary_link
- tertiary, tertiary_link
- unclassified, residential, living_street

**Highway Types NOT Converted** (as expected):
- path, footway, pedestrian, steps, cycleway (non-motorized)
- service, track (low-priority access roads)
- construction (incomplete roads)

**Railway Types Converted**:
- rail, tram, light_rail

**Railway Types NOT Converted**:
- platform (not for routing)

### Files Modified/Created

- ✅ Created: `scenarios/higashi-hiroshima/osm-conversion-config.xml`
- ✅ Created: `src/main/java/org/matsim/project/GenerateHigashiHiroshimaNetwork.java`
- ✅ Created: `src/main/java/org/matsim/project/CleanScenarioNetwork.java`
- ✅ Created: `.idea/runConfigurations/GenerateHigashiHiroshimaNetwork.xml`
- ✅ Generated: `scenarios/higashi-hiroshima/network.raw.xml.gz`
- ✅ Generated: `scenarios/higashi-hiroshima/network.xml.gz`
- ✅ Created: `docs/higashi-hiroshima-development-log.md` (this file)

### Tests Added/Updated
- **None** - Network generation is a data preparation step
- **Validation Performed**:
  - XML structure valid (MATSim network_v2 DTD)
  - Coordinate system correct (EPSG:2445 values in expected range)
  - Node/link counts reasonable for area size
  - Multimodal network includes expected modes

### Known Limitations

1. **No Speed Validation**: Calibrated speeds based on typical Japanese values, not validated against real traffic data
2. **Capacity Estimates**: Lane capacities are educated guesses, not empirically derived
3. **Turn Restrictions**: Only 7 OSM turn restrictions found - likely incomplete OSM data
4. **Zero-Length Links**: Warning about link 8793 with length=0.0 - minor geometry issue from OSM
5. **Output Path**: Network outputs to project root, requires manual move to scenario directory
6. **No Detailed Geometry**: CSV file not generated (likely path issue similar to network output)

### Next Recommended Checkpoint

**Ready for commit** with the following scope:
- OSM conversion config
- Network generation runner class
- IntelliJ run configuration
- Generated network file
- Development log

**Suggested Commit Message**:
```
Add OSM network conversion for Higashi-Hiroshima scenario

- Configure pt2matsim converter with Japan-calibrated road parameters
- Create GenerateHigashiHiroshimaNetwork runner class
- Generate multimodal network: 4,470 nodes, 11,673 links
- Support car, bus, rail modes with EPSG:2445 coordinates
- Fix config compatibility with pt2matsim v25.8
- Document conversion process in development log

Network statistics:
- Input: 132k OSM nodes, 23k ways
- Output: 4.5k MATSim nodes, 11.7k links
- Modes: car, bus, rail, pt
- CRS: EPSG:2445 (JGD2000 / Japan Plane IX)
```

### Alternatives Considered

1. **EPSG:6677 vs EPSG:2445**: Chose older JGD2000 standard for consistency with user's existing data
2. **Keep Paths True/False**: Chose false for simplified network (fewer nodes/links), acceptable for city-scale simulation
3. **Direct pt2matsim vs Wrapper Class**: Created wrapper for clean IDE integration and future parameterization
4. **Speed Calibration Sources**: Used typical Japanese posted limits rather than observed GPS speeds (not available)

### Behavioral Context

This network represents the **static supply side** of the transport system. Link speeds and capacities are initial estimates that should be:
- Validated against observed traffic patterns once agent demand is generated
- Potentially calibrated via simulation iterations
- Treated as behavioral parameters (representing driver choices under congestion) not just infrastructure capacity

The simplified network (keepPaths=false) means some geometric detail is lost, but routing behavior should remain realistic for city-scale agent-based simulation.

---

**Next Steps**:
1. Generate initial population/demand for Higashi-Hiroshima
2. Validate network with small test simulation
3. Calibrate speeds/capacities if needed based on simulation results
4. Add GTFS transit schedule if available
