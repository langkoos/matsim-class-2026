package org.matsim.other;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkUtils;
import org.matsim.project.TagLinksOfInterest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LinkOfInterestSignalControl {

    private LinkOfInterestSignalControl() {
    }

    static void apply(Scenario scenario, Config config, LinkOfInterestSignalSettings settings) {
        if (!settings.enabled()) {
            return;
        }

        List<SignalNode> signalNodes = findSignalNodes(scenario.getNetwork());
        if (signalNodes.isEmpty()) {
            System.out.println("Simple link-of-interest signals enabled, but no eligible nodes were found.");
            return;
        }

        double startTime = config.qsim().getStartTime().orElse(0.0);
        double endTime = config.qsim().getEndTime().orElse(30.0 * 3600.0);

        List<NetworkChangeEvent> events = new ArrayList<>();
        for (SignalNode signalNode : signalNodes) {
            createEventsForNode(events, signalNode, startTime, endTime, settings);
        }

        NetworkUtils.setNetworkChangeEvents(scenario.getNetwork(), events);

        System.out.println("Simple link-of-interest signals: nodes=" + signalNodes.size()
                + ", events=" + events.size()
                + ", cycleSeconds=" + settings.cycleSeconds());
    }

    private static List<SignalNode> findSignalNodes(Network network) {
        Set<Id<Node>> candidateNodeIds = new LinkedHashSet<>();
        for (Link link : network.getLinks().values()) {
            if (!TagLinksOfInterest.isLinkOfInterest(link)) {
                continue;
            }
            candidateNodeIds.add(link.getFromNode().getId());
            candidateNodeIds.add(link.getToNode().getId());
        }

        List<SignalNode> signalNodes = new ArrayList<>();
        for (Id<Node> nodeId : candidateNodeIds) {
            Node node = network.getNodes().get(nodeId);
            if (node == null) {
                continue;
            }

            List<Link> incoming = collectCarLinks(node.getInLinks().values());
            List<Link> outgoing = collectCarLinks(node.getOutLinks().values());
            if (incoming.size() <= 1 || outgoing.size() <= 1) {
                continue;
            }

            incoming.sort(Comparator.comparing(link -> link.getId().toString()));
            signalNodes.add(new SignalNode(node, incoming));
        }

        signalNodes.sort(Comparator.comparing(signalNode -> signalNode.node().getId().toString()));
        return signalNodes;
    }

    private static List<Link> collectCarLinks(Collection<? extends Link> links) {
        List<Link> carLinks = new ArrayList<>();
        for (Link link : links) {
            if (isCarLink(link)) {
                carLinks.add(link);
            }
        }
        return carLinks;
    }

    private static boolean isCarLink(Link link) {
        return link.getAllowedModes().isEmpty() || link.getAllowedModes().contains("car");
    }

    private static void createEventsForNode(List<NetworkChangeEvent> events, SignalNode signalNode, double startTime,
                                            double endTime, LinkOfInterestSignalSettings settings) {
        List<Link> incomingLinks = signalNode.incomingLinks();
        int phaseCount = incomingLinks.size();
        int phaseIndex = 0;

        for (double time = startTime; time <= endTime; time += settings.cycleSeconds()) {
            Link greenLink = incomingLinks.get(phaseIndex % phaseCount);
            for (Link incomingLink : incomingLinks) {
                boolean isGreen = incomingLink.getId().equals(greenLink.getId());
                events.add(createEvent(time, incomingLink, isGreen, settings));
            }
            phaseIndex++;
        }
    }

    private static NetworkChangeEvent createEvent(double time, Link link, boolean isGreen,
                                                  LinkOfInterestSignalSettings settings) {
        NetworkChangeEvent event = new NetworkChangeEvent(time);
        event.addLink(link);
        event.setFlowCapacityChange(new NetworkChangeEvent.ChangeValue(
                NetworkChangeEvent.ChangeType.ABSOLUTE_IN_SI_UNITS,
                link.getCapacity() * (isGreen ? 1.0 : settings.blockedFlowFactor())));
        event.setFreespeedChange(new NetworkChangeEvent.ChangeValue(
                NetworkChangeEvent.ChangeType.ABSOLUTE_IN_SI_UNITS,
                link.getFreespeed() * (isGreen ? 1.0 : settings.blockedSpeedFactor())));
        return event;
    }

    private record SignalNode(Node node, List<Link> incomingLinks) {
    }
}
