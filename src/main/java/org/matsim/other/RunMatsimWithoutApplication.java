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
import org.matsim.core.config.CommandLine;
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
 * Runs MATSim without {@code MATSimApplication} wiring and optionally applies a simple toll on links tagged as
 * {@code linkOfInterest}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * RunMatsimWithoutApplication [config.xml] [MATSim --config:* overrides] \
 *     [--lt_upm=-0.02] [--lt_route=true]
 * }</pre>
 *
 * <p>Examples:
 *
 * <pre>{@code
 * # Use the built-in default config scenarios/equil/config-2026.xml
 * java ... org.matsim.other.RunMatsimWithoutApplication
 *
 * # Run a specific config
 * java ... org.matsim.other.RunMatsimWithoutApplication scenarios/higashi-hiroshima/config-test.xml
 *
 * # Run with tolling enabled on tagged links
 * java ... org.matsim.other.RunMatsimWithoutApplication \
 *     scenarios/higashi-hiroshima/config-test.xml \
 *     --lt_upm=-0.02
 *
 * # Also include toll disutility in routing
 * java ... org.matsim.other.RunMatsimWithoutApplication \
 *     scenarios/higashi-hiroshima/config-test.xml \
 *     --lt_upm=-0.02 \
 *     --lt_route=true
 *
 * # Pass standard MATSim config overrides alongside the toll options
 * java ... org.matsim.other.RunMatsimWithoutApplication \
 *     scenarios/higashi-hiroshima/config-test.xml \
 *     --config:controller.outputDirectory=output-link-toll \
 *     --config:controller.lastIteration=5 \
 *     --lt_upm=-0.02
 * }</pre>
 *
 * <p>Notes:
 * <ul>
 *   <li>If no positional config file is provided, {@code scenarios/equil/config-2026.xml} is loaded.</li>
 *   <li>{@code --lt_upm} is in score units per meter. Use a negative value to model a toll.</li>
 *   <li>{@code --lt_route=true} adds the same toll effect to car route choice.</li>
 *   <li>Tolling only applies to links for which {@code TagLinksOfInterest.isLinkOfInterest(link)} is true.</li>
 * </ul>
 *
 * @author nagel
 */
public class RunMatsimWithoutApplication {

	public static void main(String[] args) {
		CommandLine commandLine = parseCommandLine(args);
		LinkOfInterestTollSettings tollSettings = new LinkOfInterestTollSettings(
				commandLine.getOption("lt_upm").map(Double::parseDouble)
						.orElse(LinkOfInterestTollSettings.DEFAULT.utilsPerMeter()),
				getBooleanOption(commandLine, "lt_route")
						.orElse(LinkOfInterestTollSettings.DEFAULT.applyInRouting()));

		Config config = commandLine.getPositionalArgument(0)
				.map(ConfigUtils::loadConfig)
				.orElseGet(ConfigUtils::createConfig);
		ConfigUtils.applyCommandline(config, filterMatsimArgs(args));
		if (commandLine.getNumberOfPositionalArguments() == 0) {
			ConfigUtils.loadConfig(config, "scenarios/equil/config-2026.xml");
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

	private static CommandLine parseCommandLine(String[] args) {
		try {
			return new CommandLine.Builder(args == null ? new String[0] : args)
					.allowPositionalArguments(true)
					.allowAnyOption(false)
					.allowOptions("lt_upm", "lt_route")
					.allowPrefixes("config")
					.build();
		} catch (CommandLine.ConfigurationException e) {
			throw new IllegalArgumentException("Invalid command line arguments", e);
		}
	}

	private static String[] filterMatsimArgs(String[] args) {
		if (args == null || args.length == 0) {
			return new String[0];
		}

		List<String> matsimArgs = new ArrayList<>();
		boolean configPathConsumed = false;

		for (String arg : args) {
			if (!arg.startsWith("-") && !configPathConsumed) {
				configPathConsumed = true;
				continue;
			}
			if (isCustomOption(arg, "lt_upm") || isCustomOption(arg, "lt_route")) {
				continue;
			}
			matsimArgs.add(arg);
		}

		return matsimArgs.toArray(String[]::new);
	}

	private static boolean isCustomOption(String arg, String optionName) {
		return arg.equals("--" + optionName) || arg.startsWith("--" + optionName + "=");
	}

	private static java.util.Optional<Boolean> getBooleanOption(CommandLine commandLine, String optionName) {
		if (!commandLine.hasOption(optionName)) {
			return java.util.Optional.empty();
		}
		return commandLine.getOption(optionName).map(Boolean::parseBoolean).or(() -> java.util.Optional.of(true));
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
}
