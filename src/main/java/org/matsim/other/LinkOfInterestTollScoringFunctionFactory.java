package org.matsim.other;

import com.google.inject.Inject;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.functions.CharyparNagelScoringFunctionFactory;

final class LinkOfInterestTollScoringFunctionFactory implements ScoringFunctionFactory {

    private final CharyparNagelScoringFunctionFactory delegate;

    @Inject
    LinkOfInterestTollScoringFunctionFactory(Scenario scenario) {
        this.delegate = new CharyparNagelScoringFunctionFactory(scenario);
    }

    @Override
    public ScoringFunction createNewScoringFunction(Person person) {
        return new TollAwareScoringFunction(delegate.createNewScoringFunction(person));
    }

    private static final class TollAwareScoringFunction implements ScoringFunction {
        private final ScoringFunction delegate;
        private double tollScore;

        private TollAwareScoringFunction(ScoringFunction delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handleActivity(Activity activity) {
            delegate.handleActivity(activity);
        }

        @Override
        public void handleLeg(Leg leg) {
            delegate.handleLeg(leg);
        }

        @Override
        public void agentStuck(double time) {
            delegate.agentStuck(time);
        }

        @Override
        public void addMoney(double amount) {
            delegate.addMoney(amount);
        }

        @Override
        public void addScore(double amount) {
            delegate.addScore(amount);
        }

        @Override
        public void finish() {
            delegate.finish();
        }

        @Override
        public double getScore() {
            return delegate.getScore() + tollScore;
        }

        @Override
        public void handleEvent(Event event) {
            delegate.handleEvent(event);
            if (event instanceof PersonScoreEvent personScoreEvent
                    && LinkOfInterestTollEventHandler.SCORE_EVENT_KIND.equals(personScoreEvent.getKind())) {
                tollScore += personScoreEvent.getAmount();
            }
        }

        @Override
        public void handleTrip(TripStructureUtils.Trip trip) {
            delegate.handleTrip(trip);
        }

        @Override
        public void explainScore(StringBuilder sb) {
            delegate.explainScore(sb);
            if (tollScore != 0.0) {
                sb.append("linkOfInterestToll=").append(tollScore).append('\n');
            }
        }
    }
}
