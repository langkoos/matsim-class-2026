package org.matsim.project;

import org.matsim.pt2matsim.run.Osm2MultimodalNetwork;

/**
 * Runs the Higashi-Hiroshima network build pipeline:
 * 1. convert OSM data to a raw MATSim network
 * 2. clean all routed mode subnetworks
 *
 * Configuration: scenarios/higashi-hiroshima/osm-conversion-config.xml
 * Input: original-input-data/higashi-hiroshima.osm
 * Intermediate output: scenarios/higashi-hiroshima/network.raw.xml.gz
 * Final output: scenarios/higashi-hiroshima/network.xml.gz
 */
public class GenerateHigashiHiroshimaNetwork {

    private static final String CONFIG_PATH = "scenarios/higashi-hiroshima/osm-conversion-config.xml";
    private static final String RAW_NETWORK_PATH = "scenarios/higashi-hiroshima/network.raw.xml.gz";
    private static final String CLEANED_NETWORK_PATH = "scenarios/higashi-hiroshima/network.xml.gz";

    public static void main(String[] args) {
        System.out.println("Starting Higashi-Hiroshima network build pipeline...");
        System.out.println("Config: " + CONFIG_PATH);
        System.out.println("Raw network output: " + RAW_NETWORK_PATH);
        System.out.println("Final cleaned network output: " + CLEANED_NETWORK_PATH);

        System.out.println("Step 1/2: converting OSM to raw MATSim network...");
        Osm2MultimodalNetwork.main(new String[]{CONFIG_PATH});
        System.out.println("Raw network conversion complete.");

        System.out.println("Step 2/2: cleaning all mode subnetworks...");
        CleanScenarioNetwork.main(new String[]{RAW_NETWORK_PATH, CLEANED_NETWORK_PATH});
        System.out.println("Network build pipeline complete.");
    }
}
