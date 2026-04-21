package org.matsim.project;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.Random;

/**
 * Generates 100 agents for the Saijo grid network.
 * Each agent has a home and a work location within the grid.
 * Outbound trip: 8:00 AM - 9:00 AM (random uniform).
 * Return trip: 5:00 PM - 6:00 PM (random uniform).
 */
public class GenerateSaijoPopulation {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Population population = scenario.getPopulation();
        PopulationFactory factory = population.getFactory();

        // We need the network bounds to scatter agents randomly.
        // The Saijo network is a 10x10 grid with 100m blocks centered at Saijo station.
        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(
                TransformationFactory.WGS84, "EPSG:6672");
        Coord center = ct.transform(new Coord(132.743639, 34.431333));
        
        double step = 100.0;
        int blocks = 10;
        double offset = (blocks * step) / 2.0;
        
        double minX = center.getX() - offset;
        double maxX = center.getX() + offset;
        double minY = center.getY() - offset;
        double maxY = center.getY() + offset;

        Random random = new Random(42);

        for (int i = 0; i < 100; i++) {
            Person person = factory.createPerson(Id.createPersonId("agent_" + i));
            Plan plan = factory.createPlan();

            // Home location
            Coord homeCoord = new Coord(
                    minX + (maxX - minX) * random.nextDouble(),
                    minY + (maxY - minY) * random.nextDouble()
            );

            // Work location
            Coord workCoord = new Coord(
                    minX + (maxX - minX) * random.nextDouble(),
                    minY + (maxY - minY) * random.nextDouble()
            );

            // Activity 1: Home
            Activity homeAct = factory.createActivityFromCoord("h", homeCoord);
            // Leave between 8am and 9am (28800s to 32400s)
            double departureTime = 8 * 3600 + random.nextDouble() * 3600;
            homeAct.setEndTime(departureTime);
            plan.addActivity(homeAct);

            // Leg 1: Home -> Work
            plan.addLeg(factory.createLeg("car"));

            // Activity 2: Work
            Activity workAct = factory.createActivityFromCoord("w", workCoord);
            // Leave between 5pm and 6pm (17 * 3600 to 18 * 3600)
            double returnTime = 17 * 3600 + random.nextDouble() * 3600;
            workAct.setEndTime(returnTime);
            plan.addActivity(workAct);

            // Leg 2: Work -> Home
            plan.addLeg(factory.createLeg("car"));

            // Activity 3: Home again
            Activity homeAct2 = factory.createActivityFromCoord("h", homeCoord);
            plan.addActivity(homeAct2);

            person.addPlan(plan);
            population.addPerson(person);
        }

        new PopulationWriter(population).write("saijo_plans.xml.gz");
        System.out.println("Population generation complete: saijo_plans.xml.gz");
    }
}
