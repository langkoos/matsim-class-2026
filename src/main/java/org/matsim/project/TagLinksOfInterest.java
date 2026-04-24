package org.matsim.project;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TagLinksOfInterest {

    private static final String INPUT_NETWORK = "scenarios/higashi-hiroshima/network-mapped.xml.gz";
    private static final String OUTPUT_NETWORK = "scenarios/higashi-hiroshima/network-mapped-tagged.xml.gz";
    private static final String LINK_OF_INTEREST_ATTRIBUTE = "linkOfInterest";
    private static final String[] KEYWORDS = {"西条停車場線", "ブールバール"};

    private TagLinksOfInterest() {
    }

    public static void main(String[] args) {
        String inputNetwork = args.length > 0 ? args[0] : INPUT_NETWORK;
        String outputNetwork = args.length > 1 ? args[1] : OUTPUT_NETWORK;

        Network network = NetworkUtils.readNetwork(inputNetwork);

        int taggedLinks = 0;
        List<String> samples = new ArrayList<>();

        for (Link link : network.getLinks().values()) {
            String linkName = readWayName(link.getAttributes());
            boolean matches = containsKeyword(linkName);
            link.getAttributes().putAttribute(LINK_OF_INTEREST_ATTRIBUTE, matches);

            if (matches) {
                taggedLinks++;
                if (samples.size() < 20) {
                    samples.add(link.getId() + " -> " + linkName);
                }
            }
        }

        NetworkUtils.writeNetwork(network, outputNetwork);

        System.out.println("Tagged links of interest: " + taggedLinks);
        samples.forEach(sample -> System.out.println("  " + sample));
        System.out.println("Wrote tagged network: " + outputNetwork);
    }

    public static boolean isLinkOfInterest(Link link) {
        Object attribute = link.getAttributes().getAttribute(LINK_OF_INTEREST_ATTRIBUTE);
        return attribute instanceof Boolean bool && bool;
    }

    public static String readWayName(Attributes attributes) {
        for (String key : List.of("osm:way:name", "name", "osm_way_name")) {
            Object value = attributes.getAttribute(key);
            if (value != null) {
                return Objects.toString(value);
            }
        }
        return null;
    }

    private static boolean containsKeyword(String linkName) {
        if (linkName == null) {
            return false;
        }
        for (String keyword : KEYWORDS) {
            if (linkName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
