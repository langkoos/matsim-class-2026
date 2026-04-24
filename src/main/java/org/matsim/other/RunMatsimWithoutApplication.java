/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.other;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.controler.AbstractModule;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.simwrapper.SimWrapperModule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nagel
 *
 */
public class RunMatsimWithoutApplication {

	public static void main(String[] args) {
		ParsedArgs parsedArgs = parseArgs(args);
		LinkOfInterestTollSettings tollSettings = parsedArgs.tollSettings();

		Config config;
		if ( parsedArgs.matsimArgs().length==0 ){
			config = ConfigUtils.loadConfig( "scenarios/equil/config-2026.xml" );
		} else {
			config = ConfigUtils.loadConfig( parsedArgs.matsimArgs() );
		}

		config.controller().setOverwriteFileSetting( OverwriteFileSetting.deleteDirectoryIfExists );
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		System.out.println("Link-of-interest toll utilsPerMeter=" + tollSettings.utilsPerMeter()
				+ ", applyInRouting=" + tollSettings.applyInRouting());

        // possibly modify config here

		// ---
		
		Scenario scenario = ScenarioUtils.loadScenario(config) ;
		reconcileActivityLinks(scenario);

		// possibly modify scenario here
		
		// ---
		
		Controler controler = new Controler( scenario ) ;
		
		// possibly modify controler here

//		controler.addOverridingModule( new OTFVisLiveModule() ) ;
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(LinkOfInterestTollSettings.class).toInstance(tollSettings);
				bind(LinkOfInterestHourlyCounter.class).asEagerSingleton();
				addEventHandlerBinding().to(LinkOfInterestHourlyCounter.class);
				addControlerListenerBinding().to(LinkOfInterestHourlyCounter.class);
				if (tollSettings.isEnabled()) {
					bind(LinkOfInterestTollEventHandler.class).asEagerSingleton();
					addEventHandlerBinding().to(LinkOfInterestTollEventHandler.class);
					bindScoringFunctionFactory().to(LinkOfInterestTollScoringFunctionFactory.class);
					if (tollSettings.applyInRouting()) {
						bindCarTravelDisutilityFactory().to(LinkOfInterestTollTravelDisutilityFactory.class);
					}
				}
			}
		});

		controler.addOverridingModule( new SimWrapperModule() );
		
		// ---
		
		controler.run();
	}

	private static ParsedArgs parseArgs(String[] args) {
		if (args == null || args.length == 0) {
			return new ParsedArgs(new String[0], LinkOfInterestTollSettings.DEFAULT);
		}

		double utilsPerMeter = LinkOfInterestTollSettings.DEFAULT.utilsPerMeter();
		boolean applyInRouting = LinkOfInterestTollSettings.DEFAULT.applyInRouting();
		List<String> matsimArgs = new ArrayList<>();

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("--linkToll:utilsPerMeter")) {
				String value = extractOptionValue(arg, args, i);
				utilsPerMeter = Double.parseDouble(value);
				if (!arg.contains("=")) {
					i++;
				}
				continue;
			}
			if (arg.startsWith("--linkToll:applyInRouting")) {
				String value = extractOptionValue(arg, args, i);
				applyInRouting = Boolean.parseBoolean(value);
				if (!arg.contains("=")) {
					i++;
				}
				continue;
			}
			matsimArgs.add(arg);
		}

		return new ParsedArgs(matsimArgs.toArray(String[]::new), new LinkOfInterestTollSettings(utilsPerMeter, applyInRouting));
	}

	private static String extractOptionValue(String arg, String[] args, int index) {
		int separator = arg.indexOf('=');
		if (separator >= 0) {
			return arg.substring(separator + 1);
		}
		if (index + 1 >= args.length) {
			throw new IllegalArgumentException("Missing value for option " + arg);
		}
		return args[index + 1];
	}

	private static void reconcileActivityLinks(Scenario scenario) {
		int repairedActivities = 0;

		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				for (PlanElement planElement : plan.getPlanElements()) {
					if (!(planElement instanceof Activity activity)) {
						continue;
					}
					if (activity.getCoord() == null) {
						continue;
					}
					if (activity.getLinkId() != null && scenario.getNetwork().getLinks().containsKey(activity.getLinkId())) {
						continue;
					}

					Link nearestLink = NetworkUtils.getNearestLink(scenario.getNetwork(), activity.getCoord());
					activity.setLinkId(nearestLink.getId());
					repairedActivities++;
				}
			}
		}

		if (repairedActivities > 0) {
			System.out.println("Repaired activity link ids against loaded network: " + repairedActivities);
		}
	}

	private record ParsedArgs(String[] matsimArgs, LinkOfInterestTollSettings tollSettings) {
	}

}
