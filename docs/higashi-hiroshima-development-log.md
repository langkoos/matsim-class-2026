# Higashi-Hiroshima Scenario Development Log

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
- MATSim network: `scenarios/higashi-hiroshima/network.xml.gz`
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

2. **Runner Class**: `src/main/java/org/matsim/project/GenerateHigashiHiroshimaNetwork.java`
   - Simple wrapper around `org.matsim.pt2matsim.run.Osm2MultimodalNetwork`
   - Accepts config path as argument
   - Provides clean execution interface

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
- **Solution**: Manually moved `network.xml.gz` to `scenarios/higashi-hiroshima/`
- **Follow-up**: Config should specify absolute or properly-relative output paths

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
- ✅ Created: `.idea/runConfigurations/GenerateHigashiHiroshimaNetwork.xml`
- ✅ Generated: `scenarios/higashi-hiroshima/network.xml.gz` (351 KB gzipped, ~5.2 MB uncompressed)
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
