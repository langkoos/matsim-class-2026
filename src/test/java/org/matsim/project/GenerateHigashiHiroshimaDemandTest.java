package org.matsim.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateHigashiHiroshimaDemandTest {

    @RegisterExtension
    MatsimTestUtils utils = new MatsimTestUtils();

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Test
    void readAndFilterOdMatrixAggregatesDuplicatePairsAndDropsUncoveredZones() throws Exception {
        Path odMatrix = Path.of(utils.getOutputDirectory(), "od.csv");
        Files.writeString(odMatrix, String.join(System.lineSeparator(),
                "from,to,value",
                "A,B,1.25",
                "A,B,2.5",
                "A,C,3.0",
                "D,B,4.0"
        ));

        @SuppressWarnings("unchecked")
        Map<Object, Double> retained = (Map<Object, Double>) invokeStatic("readAndFilterOdMatrix",
                new Class<?>[]{String.class, Set.class},
                odMatrix.toString(),
                Set.of("A", "B"));

        assertEquals(1, retained.size());

        Map.Entry<Object, Double> entry = retained.entrySet().iterator().next();
        assertEquals(3.75, entry.getValue(), 1e-9);
        assertEquals("A", readRecordComponent(entry.getKey(), "originZone"));
        assertEquals("B", readRecordComponent(entry.getKey(), "destinationZone"));
    }

    @Test
    void readAndFilterOdMatrixRejectsUnexpectedHeader() throws Exception {
        Path odMatrix = Path.of(utils.getOutputDirectory(), "invalid-od.csv");
        Files.writeString(odMatrix, String.join(System.lineSeparator(),
                "origin,destination,value",
                "A,B,1.0"
        ));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                invokeStatic("readAndFilterOdMatrix",
                        new Class<?>[]{String.class, Set.class},
                        odMatrix.toString(),
                        Set.of("A", "B")));

        assertTrue(exception.getMessage().contains("Unexpected OD matrix header"));
    }

    @Test
    void identifyCoveredZonesReturnsZonesContainingNetworkNodes() throws Exception {
        Network network = createNetwork();
        addNode(network, "n1", 5, 5);
        addNode(network, "n2", 25, 25);
        addNode(network, "n3", 100, 100);

        Map<String, Object> zones = new LinkedHashMap<>();
        zones.put("A", newZoneData("A", rectangle(0, 0, 10, 10)));
        zones.put("B", newZoneData("B", rectangle(20, 20, 30, 30)));
        zones.put("C", newZoneData("C", rectangle(40, 40, 50, 50)));

        @SuppressWarnings("unchecked")
        Set<String> covered = (Set<String>) invokeStatic("identifyCoveredZones",
                new Class<?>[]{Network.class, Map.class},
                network,
                zones);

        assertEquals(Set.of("A", "B"), covered);
    }

    @Test
    void generatePopulationCreatesExpectedHomeWorkHomePlans() throws Exception {
        Network network = createLinearNetwork();

        Map<String, Object> zones = new LinkedHashMap<>();
        zones.put("A", newZoneData("A", rectangle(0, 0, 10, 10)));
        zones.put("B", newZoneData("B", rectangle(90, 0, 100, 10)));

        Map<Object, Double> retainedMatrix = new LinkedHashMap<>();
        retainedMatrix.put(newOdPair("A", "B"), 1.6);
        retainedMatrix.put(newOdPair("B", "A"), 0.004);

        Path output = Path.of(utils.getOutputDirectory(), "plans.xml.gz");
        invokeStatic("generatePopulation",
                new Class<?>[]{Network.class, Map.class, Map.class, int.class, String.class, String.class},
                network,
                zones,
                retainedMatrix,
                1,
                output.toString(),
                "sample");

        Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        PopulationUtils.readPopulation(population, output.toString());

        assertEquals(2, population.getPersons().size());
        assertTrue(population.getPersons().containsKey(Id.createPersonId("sample_0")));
        assertTrue(population.getPersons().containsKey(Id.createPersonId("sample_1")));

        Person firstPerson = population.getPersons().get(Id.createPersonId("sample_0"));
        Plan plan = firstPerson.getSelectedPlan();
        List<PlanElement> elements = plan.getPlanElements();

        assertEquals(5, elements.size());

        Activity homeMorning = assertInstanceOf(Activity.class, elements.get(0));
        Leg outbound = assertInstanceOf(Leg.class, elements.get(1));
        Activity work = assertInstanceOf(Activity.class, elements.get(2));
        Leg inbound = assertInstanceOf(Leg.class, elements.get(3));
        Activity homeEvening = assertInstanceOf(Activity.class, elements.get(4));

        assertEquals("h", homeMorning.getType());
        assertEquals("car", outbound.getMode());
        assertEquals("w", work.getType());
        assertEquals("car", inbound.getMode());
        assertEquals("h", homeEvening.getType());
        assertEquals(homeMorning.getCoord(), homeEvening.getCoord());
        assertEquals(7.0 * 3600 + 0.5 * 2.0 * 3600, homeMorning.getEndTime().seconds(), 1e-9);
        assertEquals(16.0 * 3600 + 0.5 * 2.0 * 3600, work.getEndTime().seconds(), 1e-9);
    }

    @Test
    void zoneDataSamplesPointsInsideGeometry() throws Exception {
        Object zone = newZoneData("L", lShape());
        Method nextCoord = zone.getClass().getDeclaredMethod("nextCoord");
        nextCoord.setAccessible(true);

        for (int i = 0; i < 25; i++) {
            Coord coord = (Coord) nextCoord.invoke(zone);
            Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(coord.getX(), coord.getY()));
            assertTrue(lShape().covers(point), "Sampled point must remain inside polygon");
        }
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = GenerateHigashiHiroshimaDemand.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static Object newZoneData(String zoneId, Geometry geometry) throws Exception {
        Class<?> zoneClass = Class.forName("org.matsim.project.GenerateHigashiHiroshimaDemand$ZoneData");
        Constructor<?> constructor = zoneClass.getDeclaredConstructor(String.class, Geometry.class);
        constructor.setAccessible(true);
        return constructor.newInstance(zoneId, geometry);
    }

    private static Object newOdPair(String origin, String destination) throws Exception {
        Class<?> odPairClass = Class.forName("org.matsim.project.GenerateHigashiHiroshimaDemand$OdPair");
        Constructor<?> constructor = odPairClass.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(origin, destination);
    }

    private static Object readRecordComponent(Object record, String accessorName) throws Exception {
        Method accessor = record.getClass().getDeclaredMethod(accessorName);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    private static Network createNetwork() {
        return NetworkUtils.createNetwork();
    }

    private static void addNode(Network network, String id, double x, double y) {
        NetworkFactory factory = network.getFactory();
        Node node = factory.createNode(Id.createNodeId(id), new Coord(x, y));
        network.addNode(node);
    }

    private static Network createLinearNetwork() {
        Network network = NetworkUtils.createNetwork();
        NetworkFactory factory = network.getFactory();

        Node n1 = factory.createNode(Id.createNodeId("n1"), new Coord(0, 0));
        Node n2 = factory.createNode(Id.createNodeId("n2"), new Coord(100, 0));
        network.addNode(n1);
        network.addNode(n2);

        Link l1 = factory.createLink(Id.createLinkId("l1"), n1, n2);
        l1.setLength(100);
        l1.setFreespeed(13.9);
        l1.setCapacity(1000);
        l1.setNumberOfLanes(1);
        network.addLink(l1);

        Link l2 = factory.createLink(Id.createLinkId("l2"), n2, n1);
        l2.setLength(100);
        l2.setFreespeed(13.9);
        l2.setCapacity(1000);
        l2.setNumberOfLanes(1);
        network.addLink(l2);

        return network;
    }

    private static Polygon rectangle(double minX, double minY, double maxX, double maxY) {
        return GEOMETRY_FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        });
    }

    private static Polygon lShape() {
        return GEOMETRY_FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(4, 0),
                new Coordinate(4, 1),
                new Coordinate(1, 1),
                new Coordinate(1, 4),
                new Coordinate(0, 4),
                new Coordinate(0, 0)
        });
    }
}
