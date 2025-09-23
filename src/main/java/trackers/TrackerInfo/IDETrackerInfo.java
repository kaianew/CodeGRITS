package trackers.TrackerInfo;

import entity.AOIBounds;
import org.w3c.dom.Element;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import com.intellij.openapi.fileEditor.impl.EditorWindow;

public class IDETrackerInfo {
    private boolean tracking = false;
    public String projectPath = "";
    public String dataOutputPath = "";

    // Variable for keeping track of visible AOIs and their bounds throughout recording.
    public Map<String, AOIBounds> AOIMap = new HashMap<>();

    // Data structure for tracking multiple editor windows/panes and their metadata
    // Each Editor instance is mapped to its tracking info (file, bounds, etc.)
    public final Map<Editor, EditorTrackingInfo> trackedEditors = new HashMap<>();

    // Helper class to store metadata for each editor window/pane
    public static class EditorTrackingInfo {
        public com.intellij.openapi.vfs.VirtualFile file;      // Currently displayed file in this editor
        public Rectangle bounds;                               // Editor window bounds
        public boolean isActive;                               // Is this editor currently focused/active
        // Extend with more fields as needed (e.g., listeners, AOI info)
    }

    public boolean SEOpen = false;

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


    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * This method sets the data output path.
     *
     * @param dataOutputPath The data output path.
     */
    public void setDataOutputPath(String dataOutputPath) {
        this.dataOutputPath = dataOutputPath;
    }

    public void registerAOIBounds(Component component, String componentID) {
        Point location = component.getLocationOnScreen();
        Dimension bounds = component.getSize();
        AOIBounds loc = new AOIBounds(location.x, location.y, bounds.width, bounds.height, componentID);
        AOIMap.put(componentID, loc);
    }

}
