package org.matsim.project;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * Generates a 10x10 block road network centered over Saijo station in Higashi Hiroshima.
 * Uses JGD2011 / Japan Plane Rectangular CS IV (EPSG:6672), appropriate for Hiroshima Prefecture.
 */
public class GenerateSaijoNetwork {

    public static void main(String[] args) {
        // 1. Initialize Network and Factory
        // NetworkUtils is a standard MATSim utility for network manipulation
        Network network = NetworkUtils.createNetwork();
        NetworkFactory factory = network.getFactory();

        // 2. Define Coordinate Transformation
        // Transform from WGS84 (Lat/Lon) to JGD2011 / Japan Plane Rectangular CS IV (EPSG:6672)
        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(
                TransformationFactory.WGS84, "EPSG:6672");

        // Approximate center of Saijo station (Lat: 34.431333, Lon: 132.743639)
        Coord center = ct.transform(new Coord(132.743639, 34.431333));

        double step = 100.0; // 100m per block
        int blocks = 10;
        double offset = (blocks * step) / 2.0;

        // 3. Create Nodes in a Grid
        for (int i = 0; i <= blocks; i++) {
            for (int j = 0; j <= blocks; j++) {
                double x = center.getX() - offset + (i * step);
                double y = center.getY() - offset + (j * step);
                Node node = factory.createNode(Id.createNodeId("node_" + i + "_" + j), new Coord(x, y));
                network.addNode(node);
            }
        }

        // 4. Create Bidirectional Links
        // Parameters: 1 lane per direction, 60 km/h (16.67 m/s), 1500 veh/h typical capacity
        double freespeed = 60 / 3.6;
        double capacity = 1500.0;
        double lanes = 1.0;

        for (int i = 0; i <= blocks; i++) {
            for (int j = 0; j <= blocks; j++) {
                // Horizontal links
                if (i < blocks) {
                    createBidirectionalLink(network, factory, i, j, i + 1, j, step, freespeed, capacity, lanes);
                }
                // Vertical links
                if (j < blocks) {
                    createBidirectionalLink(network, factory, i, j, i, j + 1, step, freespeed, capacity, lanes);
                }
            }
        }

        // 5. Write the network to file
        // Uses standard MATSim I/O utilities
        new NetworkWriter(network).write("saijo_network.xml.gz");
        System.out.println("Network generation complete: saijo_network.xml.gz");
    }

    private static void createBidirectionalLink(Network net, NetworkFactory fac, int i1, int j1, int i2, int j2,
                                                double length, double speed, double cap, double lanes) {
        Id<Node> fromId = Id.createNodeId("node_" + i1 + "_" + j1);
        Id<Node> toId = Id.createNodeId("node_" + i2 + "_" + j2);

        // Forward link
        Link linkFwd = fac.createLink(Id.createLinkId("link_" + fromId + "_to_" + toId), net.getNodes().get(fromId), net.getNodes().get(toId));
        setLinkAttributes(linkFwd, length, speed, cap, lanes);
        net.addLink(linkFwd);

        // Backward link
        Link linkBwd = fac.createLink(Id.createLinkId("link_" + toId + "_to_" + fromId), net.getNodes().get(toId), net.getNodes().get(fromId));
        setLinkAttributes(linkBwd, length, speed, cap, lanes);
        net.addLink(linkBwd);
    }

    private static void setLinkAttributes(Link link, double length, double speed, double cap, double lanes) {
        link.setLength(length);
        link.setFreespeed(speed);
        link.setCapacity(cap);
        link.setNumberOfLanes(lanes);
    }
}