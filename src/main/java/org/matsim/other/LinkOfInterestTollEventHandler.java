package org.matsim.other;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.project.TagLinksOfInterest;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;

final class LinkOfInterestTollEventHandler implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler,
        VehicleLeavesTrafficEventHandler {

    static final String SCORE_EVENT_KIND = "linkOfInterestToll";

    private final Scenario scenario;
    private final EventsManager eventsManager;
    private final LinkOfInterestTollSettings tollSettings;
    private final Map<Id<Vehicle>, Id<Person>> vehicleToDriver = new HashMap<>();

    @Inject
    LinkOfInterestTollEventHandler(Scenario scenario, EventsManager eventsManager, LinkOfInterestTollSettings tollSettings) {
        this.scenario = scenario;
        this.eventsManager = eventsManager;
        this.tollSettings = tollSettings;
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        if (event.getPersonId() != null) {
            vehicleToDriver.put(event.getVehicleId(), event.getPersonId());
        }
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent event) {
        vehicleToDriver.remove(event.getVehicleId());
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Link link = scenario.getNetwork().getLinks().get(event.getLinkId());
        if (link == null || !TagLinksOfInterest.isLinkOfInterest(link)) {
            return;
        }

        Id<Person> personId = vehicleToDriver.get(event.getVehicleId());
        if (personId == null) {
            return;
        }

        double scoreDelta = tollSettings.utilsPerMeter() * link.getLength();
        if (scoreDelta == 0.0) {
            return;
        }

        eventsManager.processEvent(new PersonScoreEvent(event.getTime(), personId, scoreDelta, SCORE_EVENT_KIND));
    }

    @Override
    public void reset(int iteration) {
        vehicleToDriver.clear();
    }
}
