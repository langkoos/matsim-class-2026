package org.matsim.other;

record LinkOfInterestTollSettings(double utilsPerMeter, boolean applyInRouting) {

    static final LinkOfInterestTollSettings DEFAULT = new LinkOfInterestTollSettings(0.0, false);

    boolean isEnabled() {
        return utilsPerMeter != 0.0;
    }
}
