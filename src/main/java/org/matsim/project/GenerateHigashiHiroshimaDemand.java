package org.matsim.project;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generates H-W-H MATSim demand from the Higashi-Hiroshima OD matrix and zone shapefile.
 */
public final class GenerateHigashiHiroshimaDemand {

    private static final String NETWORK_PATH = "scenarios/higashi-hiroshima/network.xml.gz";
    private static final String OD_MATRIX_PATH = "original-input-data/modified_ODtable_weekday_long.csv";
    private static final String ZONE_SHP_PATH = "original-input-data/modified_gisCzone_merge_union/modified_gisCzone_merge_union.shp";
    private static final String OUTPUT_1PCT = "scenarios/higashi-hiroshima/plans-hwh-1pct.xml.gz";
    private static final String OUTPUT_100PCT = "scenarios/higashi-hiroshima/plans-hwh-100pct.xml.gz";
    private static final String OUTPUT_SUMMARY = "scenarios/higashi-hiroshima/demand-hwh-summary.csv";
    private static final String OUTPUT_COVERED_ZONES = "scenarios/higashi-hiroshima/demand-covered-zones.csv";
    private static final int SCALE_1PCT = 1;
    private static final int SCALE_100PCT = 100;

    private GenerateHigashiHiroshimaDemand() {
    }

    public static void main(String[] args) throws Exception {
        Network network = NetworkUtils.readNetwork(NETWORK_PATH);
        Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(ZONE_SHP_PATH);

        Map<String, ZoneData> zones = readZones(features);
        Set<String> coveredZones = identifyCoveredZones(network, zones);
        Map<OdPair, Double> retainedMatrix = readAndFilterOdMatrix(OD_MATRIX_PATH, coveredZones);

        writeCoveredZones(OUTPUT_COVERED_ZONES, coveredZones);
        writeSummary(OUTPUT_SUMMARY, retainedMatrix, coveredZones);

        generatePopulation(network, zones, retainedMatrix, SCALE_1PCT, OUTPUT_1PCT, "sample1pct");
        generatePopulation(network, zones, retainedMatrix, SCALE_100PCT, OUTPUT_100PCT, "scaled100pct");

        System.out.println("Covered zones: " + coveredZones.size());
        System.out.println("Retained OD pairs: " + retainedMatrix.size());
        System.out.println("Wrote 1% demand: " + OUTPUT_1PCT);
        System.out.println("Wrote 100% demand: " + OUTPUT_100PCT);
        System.out.println("Wrote demand summary: " + OUTPUT_SUMMARY);
    }

    private static Map<String, ZoneData> readZones(Collection<SimpleFeature> features) {
        Map<String, ZoneData> zones = new TreeMap<>();
        for (SimpleFeature feature : features) {
            String zoneId = String.valueOf(feature.getAttribute("bc_zone"));
            Geometry geometry = (Geometry) feature.getDefaultGeometry();
            zones.put(zoneId, new ZoneData(zoneId, geometry));
        }
        return zones;
    }

    private static Set<String> identifyCoveredZones(Network network, Map<String, ZoneData> zones) {
        Set<String> covered = new TreeSet<>();
        for (Node node : network.getNodes().values()) {
            Point point = MGC.coord2Point(node.getCoord());
            for (ZoneData zone : zones.values()) {
                if (zone.geometry.covers(point)) {
                    covered.add(zone.zoneId);
                }
            }
        }
        return covered;
    }

    private static Map<OdPair, Double> readAndFilterOdMatrix(String path, Set<String> coveredZones) throws IOException {
        Map<OdPair, Double> retained = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            String header = reader.readLine();
            if (!"from,to,value".equals(header)) {
                throw new IllegalArgumentException("Unexpected OD matrix header: " + header);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid OD matrix row: " + line);
                }

                String from = parts[0];
                String to = parts[1];
                double value = Double.parseDouble(parts[2]);
                if (!coveredZones.contains(from) || !coveredZones.contains(to)) {
                    continue;
                }

                retained.merge(new OdPair(from, to), value, Double::sum);
            }
        }
        return retained;
    }

    private static void generatePopulation(Network network, Map<String, ZoneData> zones, Map<OdPair, Double> retainedMatrix,
                                           int scaleFactor, String outputPath, String personPrefix) {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Population population = scenario.getPopulation();
        PopulationFactory factory = population.getFactory();

        long personIndex = 0;

        for (Map.Entry<OdPair, Double> entry : retainedMatrix.entrySet()) {
            OdPair od = entry.getKey();
            int tours = (int) Math.round(entry.getValue() * scaleFactor);
            if (tours <= 0) {
                continue;
            }

            ZoneData origin = zones.get(od.originZone);
            ZoneData destination = zones.get(od.destinationZone);

            for (int i = 0; i < tours; i++) {
                Coord homeCoord = origin.nextCoord();
                Coord workCoord = destination.nextCoord();

                Person person = factory.createPerson(Id.createPersonId(personPrefix + "_" + personIndex++));
                Plan plan = factory.createPlan();

                double timeFraction = halton(i + 1, 2);
                double homeEndTime = 7 * 3600 + timeFraction * 2 * 3600;
                double workEndTime = 16 * 3600 + timeFraction * 2 * 3600;

                Link homeLink = NetworkUtils.getNearestLink(network, homeCoord);
                Link workLink = NetworkUtils.getNearestLink(network, workCoord);

                Activity homeMorning = factory.createActivityFromCoord("h", homeCoord);
                homeMorning.setLinkId(homeLink.getId());
                homeMorning.setEndTime(homeEndTime);
                plan.addActivity(homeMorning);

                Leg outbound = factory.createLeg("car");
                plan.addLeg(outbound);

                Activity work = factory.createActivityFromCoord("w", workCoord);
                work.setLinkId(workLink.getId());
                work.setEndTime(workEndTime);
                plan.addActivity(work);

                Leg inbound = factory.createLeg("car");
                plan.addLeg(inbound);

                Activity homeEvening = factory.createActivityFromCoord("h", homeCoord);
                homeEvening.setLinkId(homeLink.getId());
                plan.addActivity(homeEvening);

                person.addPlan(plan);
                population.addPerson(person);
            }
        }

        new PopulationWriter(population).write(outputPath);
    }

    private static void writeCoveredZones(String outputPath, Set<String> coveredZones) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(outputPath))) {
            writer.write("zone");
            writer.newLine();
            for (String zone : coveredZones) {
                writer.write(zone);
                writer.newLine();
            }
        }
    }

    private static void writeSummary(String outputPath, Map<OdPair, Double> retainedMatrix, Set<String> coveredZones) throws IOException {
        Map<String, Double> originTotals = new TreeMap<>();
        Map<String, Double> destinationTotals = new TreeMap<>();
        double totalTours = 0.0;

        for (Map.Entry<OdPair, Double> entry : retainedMatrix.entrySet()) {
            OdPair od = entry.getKey();
            double value = entry.getValue();
            originTotals.merge(od.originZone, value, Double::sum);
            destinationTotals.merge(od.destinationZone, value, Double::sum);
            totalTours += value;
        }

        List<String> orderedZones = new ArrayList<>(coveredZones);
        orderedZones.sort(Comparator.naturalOrder());

        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(outputPath))) {
            writer.write("zone,origin_tours_1pct,destination_tours_1pct");
            writer.newLine();
            for (String zone : orderedZones) {
                writer.write(zone);
                writer.write(",");
                writer.write(formatNumber(originTotals.getOrDefault(zone, 0.0)));
                writer.write(",");
                writer.write(formatNumber(destinationTotals.getOrDefault(zone, 0.0)));
                writer.newLine();
            }

            writer.newLine();
            writer.write("metric,value");
            writer.newLine();
            writer.write("covered_zones," + coveredZones.size());
            writer.newLine();
            writer.write("retained_od_pairs," + retainedMatrix.size());
            writer.newLine();
            writer.write("retained_tours_1pct," + formatNumber(totalTours));
            writer.newLine();
            writer.write("retained_tours_100pct," + formatNumber(totalTours * 100.0));
            writer.newLine();
        }
    }

    private static String formatNumber(double value) {
        DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(Locale.ROOT);
        format.applyPattern("0.######");
        return format.format(value);
    }

    private static double halton(int index, int base) {
        double result = 0.0;
        double fraction = 1.0 / base;
        int i = index;
        while (i > 0) {
            result += fraction * (i % base);
            i /= base;
            fraction /= base;
        }
        return result;
    }

    private static final class ZoneData {
        private final String zoneId;
        private final Geometry geometry;
        private final Envelope envelope;
        private int sampleCounter = 1;

        private ZoneData(String zoneId, Geometry geometry) {
            this.zoneId = zoneId;
            this.geometry = geometry;
            this.envelope = geometry.getEnvelopeInternal();
        }

        private Coord nextCoord() {
            int attempts = 0;
            while (attempts < 100_000) {
                int index = sampleCounter++;
                double x = envelope.getMinX() + envelope.getWidth() * halton(index, 2);
                double y = envelope.getMinY() + envelope.getHeight() * halton(index, 3);
                Point point = MGC.xy2Point(x, y);
                if (geometry.covers(point)) {
                    return new Coord(x, y);
                }
                attempts++;
            }
            throw new IllegalStateException("Could not sample point inside zone " + zoneId);
        }
    }

    private record OdPair(String originZone, String destinationZone) {
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
