package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ValidateTransitScenario {

    private ValidateTransitScenario() {
    }

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "scenarios/higashi-hiroshima/config-test.xml";

        Config config = ConfigUtils.loadConfig(configPath);
        Scenario scenario = ScenarioUtils.loadScenario(config);

        Network network = scenario.getNetwork();
        TransitSchedule schedule = scenario.getTransitSchedule();

        Set<String> missingStopLinks = new LinkedHashSet<>();
        Set<String> missingRouteLinks = new LinkedHashSet<>();
        Set<String> missingPlanActivityLinks = new LinkedHashSet<>();

        for (TransitStopFacility stopFacility : schedule.getFacilities().values()) {
            if (stopFacility.getLinkId() != null && !network.getLinks().containsKey(stopFacility.getLinkId())) {
                missingStopLinks.add(stopFacility.getId() + " -> " + stopFacility.getLinkId());
            }
        }

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                if (route.getRoute() == null) {
                    continue;
                }

                checkLink(network, route.getRoute().getStartLinkId(), missingRouteLinks, line, route, "start");
                checkLink(network, route.getRoute().getEndLinkId(), missingRouteLinks, line, route, "end");

                for (org.matsim.api.core.v01.Id<Link> linkId : route.getRoute().getLinkIds()) {
                    if (!network.getLinks().containsKey(linkId)) {
                        missingRouteLinks.add(line.getId() + "/" + route.getId() + " mid -> " + linkId);
                    }
                }

                for (TransitRouteStop stop : route.getStops()) {
                    TransitStopFacility facility = stop.getStopFacility();
                    if (facility.getLinkId() != null && !network.getLinks().containsKey(facility.getLinkId())) {
                        missingStopLinks.add(facility.getId() + " -> " + facility.getLinkId());
                    }
                }

                for (Departure ignored : route.getDepartures().values()) {
                    // Touch departures so route diagnostics cover fully populated transit routes.
                }
            }
        }

        for (Person person : scenario.getPopulation().getPersons().values()) {
            for (PlanElement planElement : person.getSelectedPlan().getPlanElements()) {
                if (planElement instanceof Activity activity && activity.getLinkId() != null
                        && !network.getLinks().containsKey(activity.getLinkId())) {
                    missingPlanActivityLinks.add(person.getId() + " -> " + activity.getLinkId());
                }
            }
        }

        System.out.println("Config network: " + config.network().getInputFile());
        System.out.println("Config transit schedule: " + config.transit().getTransitScheduleFile());
        System.out.println("Config transit vehicles: " + config.transit().getVehiclesFile());
        System.out.println("Network links: " + network.getLinks().size());
        System.out.println("Transit facilities: " + schedule.getFacilities().size());
        System.out.println("Transit lines: " + schedule.getTransitLines().size());
        System.out.println("Missing stop link refs: " + missingStopLinks.size());
        missingStopLinks.stream().limit(20).forEach(value -> System.out.println("  stop " + value));
        System.out.println("Missing route link refs: " + missingRouteLinks.size());
        missingRouteLinks.stream().limit(20).forEach(value -> System.out.println("  route " + value));
        System.out.println("Missing plan activity link refs: " + missingPlanActivityLinks.size());
        missingPlanActivityLinks.stream().limit(20).forEach(value -> System.out.println("  plan " + value));
    }

    private static void checkLink(Network network, org.matsim.api.core.v01.Id<Link> linkId, Set<String> missingRouteLinks,
                                  TransitLine line, TransitRoute route, String position) {
        if (linkId != null && !network.getLinks().containsKey(linkId)) {
            missingRouteLinks.add(line.getId() + "/" + route.getId() + " " + position + " -> " + linkId);
        }
    }
}
