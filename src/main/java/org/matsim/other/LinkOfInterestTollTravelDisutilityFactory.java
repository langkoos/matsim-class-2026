package org.matsim.other;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.project.TagLinksOfInterest;
import org.matsim.vehicles.Vehicle;

final class LinkOfInterestTollTravelDisutilityFactory implements TravelDisutilityFactory {

    private final Scenario scenario;
    private final LinkOfInterestTollSettings tollSettings;
    private final RandomizingTimeDistanceTravelDisutilityFactory delegateFactory;

    @Inject
    LinkOfInterestTollTravelDisutilityFactory(Scenario scenario, Config config, LinkOfInterestTollSettings tollSettings) {
        this.scenario = scenario;
        this.tollSettings = tollSettings;
        this.delegateFactory = new RandomizingTimeDistanceTravelDisutilityFactory("car", config);
    }

    @Override
    public TravelDisutility createTravelDisutility(TravelTime travelTime) {
        TravelDisutility delegate = delegateFactory.createTravelDisutility(travelTime);
        return new TravelDisutility() {
            @Override
            public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
                return delegate.getLinkTravelDisutility(link, time, person, vehicle) + getTollDisutility(link);
            }

            @Override
            public double getLinkMinimumTravelDisutility(Link link) {
                return delegate.getLinkMinimumTravelDisutility(link) + getTollDisutility(link);
            }
        };
    }

    private double getTollDisutility(Link link) {
        Link scenarioLink = scenario.getNetwork().getLinks().get(link.getId());
        if (scenarioLink == null || !TagLinksOfInterest.isLinkOfInterest(scenarioLink)) {
            return 0.0;
        }
        return -tollSettings.utilsPerMeter() * scenarioLink.getLength();
    }
}
