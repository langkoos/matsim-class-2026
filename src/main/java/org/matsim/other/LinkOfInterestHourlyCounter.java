package org.matsim.other;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.project.TagLinksOfInterest;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

final class LinkOfInterestHourlyCounter implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler,
        VehicleLeavesTrafficEventHandler, IterationStartsListener, AfterMobsimListener {

    private final Scenario scenario;
    private final OutputDirectoryHierarchy outputDirectoryHierarchy;
    private final Map<Id<Vehicle>, Id<Person>> vehicleToDriver = new HashMap<>();
    private final Map<CountKey, Integer> hourlyCounts = new HashMap<>();
    private int iteration;

    @Inject
    LinkOfInterestHourlyCounter(Scenario scenario, OutputDirectoryHierarchy outputDirectoryHierarchy) {
        this.scenario = scenario;
        this.outputDirectoryHierarchy = outputDirectoryHierarchy;
    }

    @Override
    public void notifyIterationStarts(IterationStartsEvent event) {
        this.iteration = event.getIteration();
        this.vehicleToDriver.clear();
        this.hourlyCounts.clear();
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

        int hour = (int) Math.floor(event.getTime() / 3600.0);
        String linkName = TagLinksOfInterest.readWayName(link.getAttributes());
        CountKey key = new CountKey(hour, link.getId(), linkName);
        hourlyCounts.merge(key, 1, Integer::sum);
    }

    @Override
    public void notifyAfterMobsim(AfterMobsimEvent event) {
        Path output = Path.of(outputDirectoryHierarchy.getIterationFilename(
                iteration, "link-of-interest-hourly-counts.csv"));
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("iteration,hour,link_id,link_name,count");
            writer.newLine();

            hourlyCounts.entrySet().stream()
                    .sorted(Comparator
                            .comparingInt((Map.Entry<CountKey, Integer> entry) -> entry.getKey().hour())
                            .thenComparing(entry -> entry.getKey().linkId().toString()))
                    .forEach(entry -> writeRow(writer, entry, iteration));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write hourly link-of-interest counts for iteration " + iteration, e);
        }
    }

    private static void writeRow(BufferedWriter writer, Map.Entry<CountKey, Integer> entry, int iteration) {
        try {
            CountKey key = entry.getKey();
            writer.write(Integer.toString(iteration));
            writer.write(",");
            writer.write(Integer.toString(key.hour()));
            writer.write(",");
            writer.write(key.linkId().toString());
            writer.write(",");
            writer.write(csvEscape(key.linkName()));
            writer.write(",");
            writer.write(Integer.toString(entry.getValue()));
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String csvEscape(String value) {
        String safe = value == null ? "" : value;
        if (!safe.contains(",") && !safe.contains("\"")) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private record CountKey(int hour, Id<Link> linkId, String linkName) {
    }
}
