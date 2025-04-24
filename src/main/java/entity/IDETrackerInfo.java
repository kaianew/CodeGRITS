package entity;

public class IDETrackerInfo {
    private boolean tracking = false;
    /**
     * This method returns whether the IDE tracker is tracking.
     *
     * @return Whether the IDE tracker is tracking.
     */
    public boolean isTracking() { return tracking; }

    public void startTracking () { tracking = true; }

    public void stopTracking() { tracking = false; }

}
