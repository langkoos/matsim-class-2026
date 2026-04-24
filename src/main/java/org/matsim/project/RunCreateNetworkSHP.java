/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.project;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.GeoFileReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunCreateNetworkSHP {
	private static final String DEFAULT_INPUT_SHP =
		"/Users/fouriep/matsim-example-project/original-input-data/shapefile-network/network-wgs84.shp";
	private static final String DEFAULT_OUTPUT_NETWORK = "output/network-from-shp-tunnel.xml.gz";
	private static final String INPUT_CRS = TransformationFactory.WGS84;
	private static final String OUTPUT_CRS = "EPSG:6671";
	private static final Pattern OSM_TAG_PATTERN = Pattern.compile("\"([^\"]+)\"=>\"([^\"]*)\"");

	public static void main(String[] args) throws Exception {
		String inputShapefile = args.length > 0 ? args[0] : DEFAULT_INPUT_SHP;
		String outputNetwork = args.length > 1 ? args[1] : DEFAULT_OUTPUT_NETWORK;
		createNetwork(Path.of(inputShapefile), Path.of(outputNetwork));
	}

	static void createNetwork(Path inputShapefile, Path outputNetwork) throws Exception {
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem(OUTPUT_CRS);
		Scenario scenario = ScenarioUtils.createScenario(config);
		Network network = scenario.getNetwork();

		CoordinateTransformation transformation =
			TransformationFactory.getCoordinateTransformation(INPUT_CRS, OUTPUT_CRS);

		Map<String, Node> nodesByCoordinate = new LinkedHashMap<>();
		Collection<SimpleFeature> features = GeoFileReader.getAllFeatures(inputShapefile.toString());

		int createdLinks = 0;
		int skippedNonRoad = 0;
		int skippedUnsupported = 0;

		for (SimpleFeature feature : features) {
			String highway = stringAttribute(feature, "highway");
			if (highway == null) {
				skippedNonRoad++;
				continue;
			}

			HighwayDefaults defaults = HighwayDefaults.forType(highway);
			if (defaults == null) {
				skippedUnsupported++;
				continue;
			}

			Geometry geometry = (Geometry) feature.getDefaultGeometry();
			if (geometry instanceof LineString lineString) {
				createdLinks += addLinksForGeometry(network, feature, lineString, transformation, defaults, nodesByCoordinate);
			} else if (geometry instanceof MultiLineString multiLineString) {
				for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
					createdLinks += addLinksForGeometry(network, feature, (LineString) multiLineString.getGeometryN(i),
						transformation, defaults, nodesByCoordinate);
				}
			} else {
				skippedUnsupported++;
			}
		}

		NetworkUtils.removeNodesWithoutLinks(network);
		Files.createDirectories(Objects.requireNonNull(outputNetwork.getParent(), "output directory parent"));
		new NetworkWriter(network).write(outputNetwork.toString());

		System.out.printf(Locale.ROOT,
			"Created %d links and %d nodes from %s. Skipped %d non-road features and %d unsupported road features.%n",
			createdLinks, network.getNodes().size(), inputShapefile, skippedNonRoad, skippedUnsupported);
	}

	private static int addLinksForGeometry(Network network, SimpleFeature feature, LineString lineString,
			CoordinateTransformation transformation, HighwayDefaults defaults, Map<String, Node> nodesByCoordinate) {
		Coordinate[] sourceCoordinates = lineString.getCoordinates();
		if (sourceCoordinates.length < 2) {
			return 0;
		}

		LineInfo lineInfo = buildLineInfo(sourceCoordinates, transformation);
		if (lineInfo.lengthMeters() <= 0) {
			return 0;
		}

		Node fromNode = getOrCreateNode(network, nodesByCoordinate, sourceCoordinates[0], lineInfo.fromCoord());
		Node toNode = getOrCreateNode(network, nodesByCoordinate, sourceCoordinates[sourceCoordinates.length - 1], lineInfo.toCoord());
		if (fromNode.getId().equals(toNode.getId())) {
			return 0;
		}

		Map<String, String> tags = parseOtherTags(stringAttribute(feature, "other_tags"));
		String osmId = stringAttribute(feature, "osm_id");
		String highway = stringAttribute(feature, "highway");
		String name = stringAttribute(feature, "name");

		boolean oneway = isOneway(tags, defaults);
		boolean reverseOnly = isReverseOneway(tags);

		RoadParameters forwardParams = defaults.resolve(tags, oneway || reverseOnly);
		int created = 0;

		if (reverseOnly) {
			createLink(network, osmId + "_r", toNode, fromNode, lineInfo.lengthMeters(), highway, name, feature, forwardParams);
			created++;
		} else {
			createLink(network, osmId + "_f", fromNode, toNode, lineInfo.lengthMeters(), highway, name, feature, forwardParams);
			created++;
			if (!oneway) {
				RoadParameters reverseParams = defaults.resolve(tags, false);
				createLink(network, osmId + "_b", toNode, fromNode, lineInfo.lengthMeters(), highway, name, feature, reverseParams);
				created++;
			}
		}

		return created;
	}

	private static void createLink(Network network, String linkId, Node fromNode, Node toNode, double lengthMeters,
			String highway, String name, SimpleFeature feature, RoadParameters parameters) {
		Link link = NetworkUtils.createAndAddLink(network, Id.createLinkId(linkId), fromNode, toNode,
			lengthMeters, parameters.freeSpeedMetersPerSecond(), parameters.capacityPerHour(), parameters.lanes());
		link.setAllowedModes(parameters.allowedModes());
		NetworkUtils.setType(link, highway);
		NetworkUtils.setOrigId(link, stringAttribute(feature, "osm_id"));
		putAttributeIfPresent(link, "name", name);
		putAttributeIfPresent(link, "highway", highway);
		putAttributeIfPresent(link, "z_order", feature.getAttribute("z_order"));
	}

	private static void putAttributeIfPresent(Link link, String key, Object value) {
		if (value != null) {
			link.getAttributes().putAttribute(key, value);
		}
	}

	private static LineInfo buildLineInfo(Coordinate[] sourceCoordinates, CoordinateTransformation transformation) {
		Coord first = transform(sourceCoordinates[0], transformation);
		Coord previous = first;
		double lengthMeters = 0.0;

		for (int i = 1; i < sourceCoordinates.length; i++) {
			Coord current = transform(sourceCoordinates[i], transformation);
			double dx = current.getX() - previous.getX();
			double dy = current.getY() - previous.getY();
			lengthMeters += Math.hypot(dx, dy);
			previous = current;
		}

		return new LineInfo(first, previous, lengthMeters);
	}

	private static Coord transform(Coordinate coordinate, CoordinateTransformation transformation) {
		return transformation.transform(new Coord(coordinate.x, coordinate.y));
	}

	private static Node getOrCreateNode(Network network, Map<String, Node> nodesByCoordinate, Coordinate sourceCoordinate,
			Coord projectedCoord) {
		String key = coordinateKey(sourceCoordinate);
		return nodesByCoordinate.computeIfAbsent(key, ignored -> {
			Id<Node> nodeId = Id.createNodeId("n_" + nodesByCoordinate.size());
			Node node = NetworkUtils.createAndAddNode(network, nodeId, projectedCoord);
			NetworkUtils.setOrigId(node, key);
			return node;
		});
	}

	private static String coordinateKey(Coordinate coordinate) {
		return String.format(Locale.ROOT, "%.8f,%.8f", coordinate.x, coordinate.y);
	}

	private static boolean isOneway(Map<String, String> tags, HighwayDefaults defaults) {
		String value = tags.get("oneway");
		if (value == null) {
			return defaults.onewayByDefault();
		}
		return value.equalsIgnoreCase("yes") || value.equals("1") || value.equalsIgnoreCase("true")
			|| value.equalsIgnoreCase("-1");
	}

	private static boolean isReverseOneway(Map<String, String> tags) {
		String value = tags.get("oneway");
		return "-1".equals(value);
	}

	private static Map<String, String> parseOtherTags(String otherTags) {
		Map<String, String> tags = new LinkedHashMap<>();
		if (otherTags == null || otherTags.isBlank()) {
			return tags;
		}

		Matcher matcher = OSM_TAG_PATTERN.matcher(otherTags);
		while (matcher.find()) {
			tags.put(matcher.group(1), matcher.group(2));
		}
		return tags;
	}

	private static String stringAttribute(SimpleFeature feature, String attributeName) {
		Object value = feature.getAttribute(attributeName);
		if (value == null) {
			return null;
		}
		String string = value.toString().trim();
		return string.isEmpty() ? null : string;
	}

	private record LineInfo(Coord fromCoord, Coord toCoord, double lengthMeters) {
	}

	private record RoadParameters(double freeSpeedMetersPerSecond, double capacityPerHour, double lanes,
			Set<String> allowedModes) {
	}

	private record HighwayDefaults(double freeSpeedKilometersPerHour, double lanesPerDirection,
			double capacityPerLanePerHour, Set<String> allowedModes, boolean onewayByDefault) {
		private static final Map<String, HighwayDefaults> BY_TYPE = buildDefaults();

		private static Map<String, HighwayDefaults> buildDefaults() {
			Map<String, HighwayDefaults> defaults = new LinkedHashMap<>();
			defaults.put("trunk", road(60, 1.0, 1800, Set.of(TransportMode.car), false));
			defaults.put("trunk_link", road(50, 1.0, 1500, Set.of(TransportMode.car), true));
			defaults.put("primary", road(50, 1.0, 1600, Set.of(TransportMode.car), false));
			defaults.put("secondary", road(40, 1.0, 1400, Set.of(TransportMode.car, TransportMode.bike), false));
			defaults.put("tertiary", road(40, 1.0, 1200, Set.of(TransportMode.car, TransportMode.bike), false));
			defaults.put("tertiary_link", road(30, 1.0, 1000, Set.of(TransportMode.car, TransportMode.bike), true));
			defaults.put("unclassified", road(30, 1.0, 900, Set.of(TransportMode.car, TransportMode.bike, TransportMode.walk), false));
			defaults.put("residential", road(30, 1.0, 700, Set.of(TransportMode.car, TransportMode.bike, TransportMode.walk), false));
			defaults.put("service", road(20, 1.0, 400, Set.of(TransportMode.car, TransportMode.bike, TransportMode.walk), false));
			defaults.put("services", road(20, 1.0, 400, Set.of(TransportMode.car, TransportMode.bike, TransportMode.walk), false));
			defaults.put("track", road(15, 1.0, 200, Set.of(TransportMode.car, TransportMode.bike, TransportMode.walk), false));
			defaults.put("cycleway", road(15, 1.0, 800, Set.of(TransportMode.bike), false));
			defaults.put("footway", road(5, 1.0, 1200, Set.of(TransportMode.walk), false));
			defaults.put("pedestrian", road(5, 1.0, 1800, Set.of(TransportMode.walk), false));
			defaults.put("path", road(5, 1.0, 900, Set.of(TransportMode.walk, TransportMode.bike), false));
			defaults.put("steps", road(2, 1.0, 600, Set.of(TransportMode.walk), false));
			return defaults;
		}

		private static HighwayDefaults road(double freeSpeedKilometersPerHour, double lanesPerDirection,
				double capacityPerLanePerHour, Set<String> allowedModes, boolean onewayByDefault) {
			return new HighwayDefaults(freeSpeedKilometersPerHour, lanesPerDirection, capacityPerLanePerHour,
				new LinkedHashSet<>(allowedModes), onewayByDefault);
		}

		static HighwayDefaults forType(String highwayType) {
			if (highwayType == null) {
				return null;
			}
			return BY_TYPE.get(highwayType.toLowerCase(Locale.ROOT));
		}

		RoadParameters resolve(Map<String, String> tags, boolean oneway) {
			double speedKph = parseFirstNumber(tags.get("maxspeed"), freeSpeedKilometersPerHour);
			double taggedLanes = parseFirstNumber(tags.get("lanes"), Double.NaN);
			double lanes = Double.isNaN(taggedLanes) ? lanesPerDirection
				: Math.max(1.0, oneway ? taggedLanes : taggedLanes / 2.0);
			double capacity = capacityPerLanePerHour * lanes;
			return new RoadParameters(speedKph / 3.6, capacity, lanes, allowedModes);
		}
	}

	private static double parseFirstNumber(String value, double fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}

		String normalized = value.replace(',', '.');
		Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(normalized);
		if (!matcher.find()) {
			return fallback;
		}
		return Double.parseDouble(matcher.group());
	}
}
