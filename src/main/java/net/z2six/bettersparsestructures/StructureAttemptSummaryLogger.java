package net.z2six.bettersparsestructures;

public final class StructureAttemptSummaryLogger {
    private static final Object LOCK = new Object();

    private static long totalAttempts;
    private static long acceptedAttempts;
    private static long acceptedWhitelisted;
    private static long acceptedProtected;
    private static long acceptedForced;
    private static long rejectedAttempts;
    private static long rejectedSpacing;
    private static long rejectedOverlap;
    private static long rejectedRepetition;

    private StructureAttemptSummaryLogger() {
    }

    public static void recordAttempt(boolean whitelisted, GlobalStructureIndexSavedData.DecisionResult decision) {
        int interval = ServerConfig.structureAttemptSummaryInterval();
        if (!ServerConfig.logStructureAttempts() || interval <= 0) {
            return;
        }

        synchronized (LOCK) {
            totalAttempts++;
            if (decision.accepted()) {
                acceptedAttempts++;
                if (whitelisted) {
                    acceptedWhitelisted++;
                } else if (decision.firstOccurrence().forcedAcceptance()) {
                    acceptedForced++;
                    acceptedProtected++;
                } else if (decision.firstOccurrence().protectionApplied()) {
                    acceptedProtected++;
                }
            } else {
                rejectedAttempts++;
                switch (decision.rejectionReason()) {
                    case SPACING -> rejectedSpacing++;
                    case OVERLAP -> rejectedOverlap++;
                    case REPETITION -> rejectedRepetition++;
                    default -> {
                    }
                }
            }

            if (totalAttempts % interval == 0L) {
                Bettersparsestructures.LOGGER.info(
                        """
                        Better Sparse Structures summary
                          attempts={}
                          accepted={}
                          acceptedWhitelisted={}
                          acceptedProtected={}
                          acceptedForced={}
                          rejected={}
                          rejectedSpacing={}
                          rejectedOverlap={}
                          rejectedRepetition={}
                        """,
                        totalAttempts,
                        acceptedAttempts,
                        acceptedWhitelisted,
                        acceptedProtected,
                        acceptedForced,
                        rejectedAttempts,
                        rejectedSpacing,
                        rejectedOverlap,
                        rejectedRepetition
                );
            }
        }
    }
}
