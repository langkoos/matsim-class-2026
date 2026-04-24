package org.matsim.other;

record LinkOfInterestSignalSettings(boolean enabled, double cycleSeconds, double blockedFlowFactor,
                                    double blockedSpeedFactor) {

    static final LinkOfInterestSignalSettings DEFAULT =
            new LinkOfInterestSignalSettings(false, 120.0, 1.0e-3, 1.0e-3);
}
