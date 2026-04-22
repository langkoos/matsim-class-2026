package org.matsim.project;

import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.viz.MapPlot;
import org.matsim.simwrapper.viz.Table;
import org.matsim.simwrapper.viz.TextBlock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class GenerateHigashiHiroshimaOdDashboard {

    private static final Path OD_MATRIX = Path.of("original-input-data/modified_ODtable_weekday_long.csv");
    private static final Path ZONE_SHP = Path.of("original-input-data/modified_gisCzone_merge_union/modified_gisCzone_merge_union.shp");
    private static final Path OUTPUT_DIR = Path.of("scenarios/higashi-hiroshima/simwrapper/od-zones");
    private static final String GEOJSON_FILE = "higashi-hiroshima-zones.geojson";
    private static final String CSV_FILE = "higashi-hiroshima-od-summary.csv";

    private GenerateHigashiHiroshimaOdDashboard() {
    }

    public static void main(String[] args) throws Exception {
        DashboardData dashboardData = prepareDashboardData(OUTPUT_DIR);

        SimWrapper simWrapper = SimWrapper.create();
        simWrapper.addDashboard(new HigashiHiroshimaOdDashboard(dashboardData));
        simWrapper.generate(OUTPUT_DIR, false);

        System.out.println("Generated SimWrapper dashboard in " + OUTPUT_DIR);
        System.out.println("Zone GeoJSON: " + OUTPUT_DIR.resolve(GEOJSON_FILE));
        System.out.println("Zone summary CSV: " + OUTPUT_DIR.resolve(CSV_FILE));
    }

    private static DashboardData prepareDashboardData(Path outputDir) throws Exception {
        Files.createDirectories(outputDir);

        OdAggregation od = readOdMatrix(OD_MATRIX);
        ZoneExport zoneExport = exportZones(outputDir.resolve(GEOJSON_FILE), outputDir.resolve(CSV_FILE), od);

        return new DashboardData(
            GEOJSON_FILE,
            CSV_FILE,
            zoneExport.center,
            10.5,
            zoneExport.maxProduction,
            zoneExport.maxAttraction,
            zoneExport.zoneCount,
            od.originZones.size(),
            od.destinationZones.size(),
            od.unmatchedOriginDemand,
            od.unmatchedDestinationDemand,
            od.unmatchedOrigins,
            od.unmatchedDestinations
        );
    }

    private static OdAggregation readOdMatrix(Path odMatrixPath) throws IOException {
        Map<String, Double> productions = new TreeMap<>();
        Map<String, Double> attractions = new TreeMap<>();
        Set<String> originZones = new java.util.TreeSet<>();
        Set<String> destinationZones = new java.util.TreeSet<>();

        try (BufferedReader reader = Files.newBufferedReader(odMatrixPath)) {
            String header = reader.readLine();
            if (!"from,to,value".equals(header)) {
                throw new IllegalArgumentException("Unexpected OD matrix header: " + header);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid OD row: " + line);
                }

                double value = Double.parseDouble(parts[2]);
                productions.merge(parts[0], value, Double::sum);
                attractions.merge(parts[1], value, Double::sum);
                originZones.add(parts[0]);
                destinationZones.add(parts[1]);
            }
        }

        return new OdAggregation(productions, attractions, originZones, destinationZones);
    }

    private static ZoneExport exportZones(Path geoJsonPath, Path csvPath, OdAggregation od) throws Exception {
        List<SimpleFeature> features = new ArrayList<>();
        Map<String, double[]> zoneValues = new TreeMap<>();
        Envelope envelope = new Envelope();
        CoordinateReferenceSystem targetCrs = CRS.decode("EPSG:4326", true);

        FileDataStore store = openStore(ZONE_SHP);
        try {
            SimpleFeatureSource source = store.getFeatureSource();
            SimpleFeatureCollection collection = source.getFeatures();
            CoordinateReferenceSystem sourceCrs = source.getSchema().getCoordinateReferenceSystem();
            MathTransform transform = CRS.findMathTransform(sourceCrs, targetCrs, true);

            SimpleFeatureType featureType = buildTargetType(targetCrs);

            try (SimpleFeatureIterator iterator = collection.features()) {
                int featureId = 0;
                while (iterator.hasNext()) {
                    SimpleFeature sourceFeature = iterator.next();
                    String zone = String.valueOf(sourceFeature.getAttribute("bc_zone"));
                    Geometry geometry = (Geometry) sourceFeature.getDefaultGeometry();
                    Geometry transformed = JTS.transform(geometry, transform);
                    envelope.expandToInclude(transformed.getEnvelopeInternal());

                    double production = od.productions.getOrDefault(zone, 0.0);
                    double attraction = od.attractions.getOrDefault(zone, 0.0);

                    SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
                    builder.add(asMultiPolygon(transformed));
                    builder.add(zone);
                    builder.add(production);
                    builder.add(attraction);
                    features.add(builder.buildFeature(Integer.toString(featureId++)));

                    zoneValues.put(zone, new double[]{production, attraction});
                    od.productions.remove(zone);
                    od.attractions.remove(zone);
                }
            }

            writeGeoJson(geoJsonPath, featureType, features);
            writeSummaryCsv(csvPath, zoneValues);
        } finally {
            store.dispose();
        }

        od.unmatchedOrigins = new LinkedHashMap<>(od.productions);
        od.unmatchedDestinations = new LinkedHashMap<>(od.attractions);
        od.unmatchedOriginDemand = od.unmatchedOrigins.values().stream().mapToDouble(Double::doubleValue).sum();
        od.unmatchedDestinationDemand = od.unmatchedDestinations.values().stream().mapToDouble(Double::doubleValue).sum();

        return new ZoneExport(
            new double[]{envelope.centre().x, envelope.centre().y},
            zoneValues.size(),
            zoneValues.values().stream().mapToDouble(values -> values[0]).max().orElse(0.0),
            zoneValues.values().stream().mapToDouble(values -> values[1]).max().orElse(0.0)
        );
    }

    private static FileDataStore openStore(Path shapefile) throws MalformedURLException, IOException {
        FileDataStore store = FileDataStoreFinder.getDataStore(shapefile.toFile());
        if (store == null) {
            throw new IOException("Could not open shapefile " + shapefile);
        }
        return store;
    }

    private static SimpleFeatureType buildTargetType(CoordinateReferenceSystem targetCrs) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("higashi_hiroshima_zones");
        typeBuilder.setCRS(targetCrs);
        typeBuilder.add("geometry", MultiPolygon.class);
        typeBuilder.add("bc_zone", String.class);
        typeBuilder.add("productions", Double.class);
        typeBuilder.add("attractions", Double.class);
        return typeBuilder.buildFeatureType();
    }

    private static MultiPolygon asMultiPolygon(Geometry geometry) {
        if (geometry instanceof MultiPolygon multiPolygon) {
            return multiPolygon;
        }

        return geometry.getFactory().createMultiPolygon(new org.locationtech.jts.geom.Polygon[]{
            (org.locationtech.jts.geom.Polygon) geometry
        });
    }

    private static void writeGeoJson(Path path, SimpleFeatureType featureType, List<SimpleFeature> features) throws IOException {
        FeatureJSON featureJson = new FeatureJSON(new GeometryJSON(6));
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            featureJson.writeFeatureCollection(new ListFeatureCollection(featureType, features), writer);
        }
    }

    private static void writeSummaryCsv(Path path, Map<String, double[]> zoneValues) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("zone,productions,attractions");
            writer.newLine();
            for (Map.Entry<String, double[]> entry : zoneValues.entrySet()) {
                writer.write(entry.getKey());
                writer.write(",");
                writer.write(formatNumber(entry.getValue()[0]));
                writer.write(",");
                writer.write(formatNumber(entry.getValue()[1]));
                writer.newLine();
            }
        }
    }

    private static String formatNumber(double value) {
        DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(Locale.ROOT);
        format.applyPattern("0.######");
        return format.format(value);
    }

    private static String formatMap(Map<String, Double> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + formatNumber(entry.getValue()))
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private record DashboardData(
        String geoJsonFile,
        String csvFile,
        double[] center,
        double zoom,
        double maxProduction,
        double maxAttraction,
        int mappedZoneCount,
        int odOriginZoneCount,
        int odDestinationZoneCount,
        double unmatchedOriginDemand,
        double unmatchedDestinationDemand,
        Map<String, Double> unmatchedOrigins,
        Map<String, Double> unmatchedDestinations
    ) {
    }

    private static final class OdAggregation {
        private final Map<String, Double> productions;
        private final Map<String, Double> attractions;
        private final Set<String> originZones;
        private final Set<String> destinationZones;
        private double unmatchedOriginDemand;
        private double unmatchedDestinationDemand;
        private Map<String, Double> unmatchedOrigins = Map.of();
        private Map<String, Double> unmatchedDestinations = Map.of();

        private OdAggregation(Map<String, Double> productions, Map<String, Double> attractions, Set<String> originZones, Set<String> destinationZones) {
            this.productions = productions;
            this.attractions = attractions;
            this.originZones = originZones;
            this.destinationZones = destinationZones;
        }
    }

    private record ZoneExport(double[] center, int zoneCount, double maxProduction, double maxAttraction) {
    }

    private static final class HigashiHiroshimaOdDashboard implements Dashboard {

        private final DashboardData data;

        private HigashiHiroshimaOdDashboard(DashboardData data) {
            this.data = data;
        }

        @Override
        public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {
            header.tab = "Higashi-Hiroshima";
            header.title = "Zone-Level OD Demand";
            header.description = "Trip productions and attractions aggregated from the weekday OD matrix and joined to zone polygons.";
            header.fullScreen = true;

            layout.row("notes", "Scope and exclusions")
                .el(TextBlock.class, (viz, context) -> {
                    viz.title = "Data notes";
                    viz.content = buildNotes();
                });

            layout.row("maps", "Mapped productions and attractions")
                .el(MapPlot.class, (viz, context) -> configureMap(viz, "Trip productions", "Origin-side trip totals by bc_zone.", "productions", data.maxProduction))
                .el(MapPlot.class, (viz, context) -> configureMap(viz, "Trip attractions", "Destination-side trip totals by bc_zone.", "attractions", data.maxAttraction));

            layout.row("table", "Zone totals")
                .el(Table.class, (viz, context) -> {
                    viz.title = "Zone production and attraction totals";
                    viz.description = "Joined summary table used by the map layers.";
                    viz.dataset = data.csvFile;
                    viz.show = List.of("zone", "productions", "attractions");
                    viz.enableFilter = true;
                    viz.showAllRows = true;
                });
        }

        private void configureMap(MapPlot viz, String title, String description, String valueColumn, double maxValue) {
            viz.title = title;
            viz.description = description;
            viz.height = 9.0;
            viz.center = data.center;
            viz.zoom = data.zoom;
            viz.minValue = 0.0;
            viz.maxValue = maxValue;
            viz.mapIsIndependent = true;
            viz.setShape(data.geoJsonFile, "bc_zone");
            viz.addDataset("zoneTotals", data.csvFile);
            viz.display.fill.dataset = "zoneTotals";
            viz.display.fill.join = "zone";
            viz.display.fill.columnName = valueColumn;
            viz.display.fill.setColorRamp("YlOrRd", 7, false);
            viz.display.lineColor.fixedColors = new String[]{"#4B5563"};
            viz.display.lineWidth.scaleFactor = 0.5;
        }

        private String buildNotes() {
            return String.format(Locale.ROOT, """
                **Inputs**
                - OD matrix: `%s`
                - Zones: `%s`
                - Join key: `bc_zone` in the shapefile to `from`/`to` zone codes in the OD matrix

                **Mapped output**
                - All 144 zones are shown; zones without matched OD demand remain at zero
                - Unique OD origin zones observed: %d
                - Unique OD destination zones observed: %d

                **Excluded from the choropleths**
                - Unmatched origin demand: %s trips
                - Unmatched destination demand: %s trips
                - Unmatched origin codes: %s
                - Unmatched destination codes: %s
                """,
                OD_MATRIX,
                ZONE_SHP,
                data.odOriginZoneCount,
                data.odDestinationZoneCount,
                formatNumber(data.unmatchedOriginDemand),
                formatNumber(data.unmatchedDestinationDemand),
                formatMap(data.unmatchedOrigins),
                formatMap(data.unmatchedDestinations)
            );
        }
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
