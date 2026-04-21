package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.simwrapper.SimWrapperModule;

/**
 * Runs a simple MATSim simulation for the Saijo scenario.
 * 10 iterations, using the same strategy as the equil scenario.
 */
public class RunSaijoSimulation {

    public static void main(String[] args) {
        // 1. Create Config
        Config config = ConfigUtils.createConfig();

        // 2. Set input files
        config.network().setInputFile("saijo_network.xml.gz");
        config.plans().setInputFile("saijo_plans.xml.gz");

        // 3. Controller settings
        config.controller().setOutputDirectory("output_saijo");
        config.controller().setLastIteration(10);
        config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        // 4. Scoring settings (similar to equil scenario)
        // Trying direct parameters access via getOrCreateScoringParameters(null).
//        config.scoring().getOrCreateScoringParameters(null).addParam("lateArrival", "-18.0");
//        config.scoring().getOrCreateScoringParameters(null).addParam("performing", "+6.0");

        ScoringConfigGroup.ActivityParams homeParams = new ScoringConfigGroup.ActivityParams("h");
        homeParams.setTypicalDuration(12 * 3600);
        config.scoring().addActivityParams(homeParams);

        ScoringConfigGroup.ActivityParams workParams = new ScoringConfigGroup.ActivityParams("w");
        workParams.setTypicalDuration(8 * 3600);
        workParams.setOpeningTime(7 * 3600);
        workParams.setClosingTime(18 * 3600);
        config.scoring().addActivityParams(workParams);

        config.global().setCoordinateSystem("EPSG:6672");

        // 5. Replanning settings (strategy as in equil scenario)
        // BestScore (0.9) and ReRoute (0.1)
        
        // Clear default strategies if any (usually ChangeExpBeta)
        config.replanning().clearStrategySettings();

        ReplanningConfigGroup.StrategySettings bestScore = new ReplanningConfigGroup.StrategySettings();
        bestScore.setStrategyName("BestScore");
        bestScore.setWeight(0.9);
        config.replanning().addStrategySettings(bestScore);

        ReplanningConfigGroup.StrategySettings reRoute = new ReplanningConfigGroup.StrategySettings();
        reRoute.setStrategyName("ReRoute");
        reRoute.setWeight(0.1);
        config.replanning().addStrategySettings(reRoute);

        // 6. Run Simulation
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Controler controler = new Controler(scenario);

        SimWrapperModule simWrapperModule = new SimWrapperModule();
        controler.addOverridingModule(simWrapperModule);

        controler.run();
    }
}
