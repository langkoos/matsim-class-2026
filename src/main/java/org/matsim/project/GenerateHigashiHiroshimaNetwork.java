package org.matsim.project;

import org.matsim.pt2matsim.run.Osm2MultimodalNetwork;

/**
 * Converts OpenStreetMap data to MATSim network for Higashi-Hiroshima scenario.
 * Uses pt2matsim library to process OSM file with calibrated link parameters.
 *
 * Configuration: scenarios/higashi-hiroshima/osm-conversion-config.xml
 * Input: original-input-data/higashi-hiroshima.osm
 * Output: scenarios/higashi-hiroshima/network.xml.gz
 */
public class GenerateHigashiHiroshimaNetwork {

    public static void main(String[] args) {
        String configPath = "scenarios/higashi-hiroshima/osm-conversion-config.xml";

        System.out.println("Starting OSM to MATSim network conversion...");
        System.out.println("Config: " + configPath);

        Osm2MultimodalNetwork.main(new String[]{configPath});

        System.out.println("Network conversion complete.");
    }
}
