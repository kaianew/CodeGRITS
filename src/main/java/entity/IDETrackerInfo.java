package entity;

import org.w3c.dom.Element;
import trackers.IDETracker;

import java.util.function.Consumer;

public class IDETrackerInfo {
    private boolean tracking = false;
    public String projectPath = "";
    public String lastSelectionInfo = "";

    public String changedFilepath = "";
    public String changedFileText = "";
    /**
     * This variable is the handler for the IDE tracker data.
     */
    public Consumer<Element> ideTrackerDataHandler;


    /**
     * This variable indicates whether the data is transmitted in real time.
     */
    public static boolean isRealTimeDataTransmitting = false;
    /**
     * This method returns whether the IDE tracker is tracking.
     *
     * @return Whether the IDE tracker is tracking.
     */
    public boolean isTracking() { return tracking; }

    public void startTracking () { tracking = true; }

    public void stopTracking() { tracking = false; }

    /**
     * This method handles the XML element for real-time data transmission.
     *
     * @param element The XML element.
     */
    public void handleElement(Element element) {
        if (ideTrackerDataHandler == null) {
            return;
//            throw new RuntimeException("ideTrackerDataHandler is null");
        }
        if (isRealTimeDataTransmitting) {
            ideTrackerDataHandler.accept(element);
        }
    }

    /**
     * This method sets the {@code isRealTimeDataTransmitting} variable.
     *
     * @param isRealTimeDataTransmitting Indicates whether the data is transmitted in real time.
     */
    public void setIsRealTimeDataTransmitting(boolean isRealTimeDataTransmitting) {
        IDETrackerInfo.isRealTimeDataTransmitting = isRealTimeDataTransmitting;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
}
