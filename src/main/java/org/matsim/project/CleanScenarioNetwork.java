package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cleans all mode-specific subnetworks in a MATSim network so each routed mode remains reachable.
 */
public final class CleanScenarioNetwork {

    private CleanScenarioNetwork() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: CleanScenarioNetwork <input-network.xml.gz> <output-network.xml.gz>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network network = scenario.getNetwork();
        new MatsimNetworkReader(network).readFile(inputPath);

        Set<String> modes = NetworkUtils.getModes(network);
        Map<String, Long> linkCountsBefore = countLinksByMode(network, modes);

        System.out.printf(
            "Cleaning network %s%nNodes before: %d%nLinks before: %d%nModes: %s%n",
            inputPath,
            network.getNodes().size(),
            network.getLinks().size(),
            modes
        );
        System.out.println("Mode link counts before cleaning: " + linkCountsBefore);

        NetworkUtils.cleanNetwork(network, modes);

        Map<String, Long> linkCountsAfter = countLinksByMode(network, modes);
        System.out.printf(
            "Nodes after: %d%nLinks after: %d%n",
            network.getNodes().size(),
            network.getLinks().size()
        );
        System.out.println("Mode link counts after cleaning: " + linkCountsAfter);

        new NetworkWriter(network).write(outputPath);
        System.out.println("Wrote cleaned network to " + outputPath);
    }

    private static Map<String, Long> countLinksByMode(Network network, Set<String> modes) {
        Map<String, Long> counts = new TreeMap<>();
        for (String mode : modes) {
            long count = network.getLinks().values().stream()
                .filter(link -> link.getAllowedModes().contains(mode))
                .count();
            counts.put(mode, count);
        }
        return counts;
    }
}
