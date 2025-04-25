package trackers;

import javax.xml.parsers.*;
import javax.xml.transform.TransformerException;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.event.*;
import java.util.*;

import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import entity.*;
import org.w3c.dom.Element;

import java.awt.*;
import java.util.List;
import java.util.Timer;
import java.util.function.Consumer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import trackers.ListenerGenerators.IDETrackerListeners.*;
import trackers.TrackerInfo.IDETrackerInfo;
import utils.XMLWriter;

// Imports for tool window bounds recording.
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;

/**
 * This class is the IDE tracker.
 */
public final class IDETracker implements Disposable {

    private IDETrackerInfo info;
    /**
     * This variable is the XML document for storing the tracking data.
     */
    private final XMLDocumentHandler xmldoc = new XMLDocumentHandler("ide_tracking");
    private final List<String> ELEMENTS = List.of("environment",
    "actions",
    "archives",
    "typings",
    "files",
    "mouses",
    "carets",
    "selections",
    "visible_areas",
    "tool_windows",
    "popups",
    "editors");

    // for debugging purposes
    private static final Logger LOG = Logger.getInstance(IDETracker.class);


    // Class for the Map below.


    /**
     * This variable is the document listener for the IDE tracker. When the document is changed, if the {@code EditorKind} is {@code CONSOLE}, the console output is archived. Otherwise, the {@code changedFilepath} and {@code changedFileText} are updated.
     */
    DocumentListener documentListener;
    /**
     * This variable is the mouse listener for the IDE tracker.
     * When the mouse is pressed, clicked, or released, the mouse event is tracked.
     */
    EditorMouseListener editorMouseListener;

    /**
     * This variable is the mouse motion listener for the IDE tracker.
     * When the mouse is moved or dragged, the mouse event is tracked.
     */
    EditorMouseMotionListener editorMouseMotionListener;
    /**
     * This variable is the caret listener for the IDE tracker.
     * When the caret position is changed, the caret event is tracked.
     */
    CaretListener caretListener;

    /**
     * This variable is the selection listener for the IDE tracker.
     * When the selection is changed, the selection event is tracked.
     */
    SelectionListener selectionListener;

    /**
     * This variable is the visible area listener for the IDE tracker.
     * When the visible area is changed, the visible area event is tracked.
     */
    VisibleAreaListener visibleAreaListener;

    // Listener for when the state of tool windows changes to record AOI bounds dynamically.
    ToolWindowManagerListener toolWindowManagerListener;


    /**
     * This variable is the editor event multicaster for the IDE tracker.
     * It is used to add and remove all the listeners.
     */
    EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    /**
     * This variable is the timer for tracking the document changes.
     */
    Timer timer = new Timer();

    /**
     * This variable is the timer task for tracking the document changes. If the {@code changedFilepath} is not empty, the file is archived.
     */
    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            if (!info.changedFilepath.isEmpty()) {
                if (!info.isTracking()) return;
                xmldoc.archiveFile(info.dataOutputPath, info.projectPath, info.changedFilepath, String.valueOf(System.currentTimeMillis()),
                        "contentChanged", info.changedFileText);
                info.changedFilepath = "";
            }
        }
    };

    // CLG isn't convinced this should stay here
    public static Map<String,String> addBoundsAttributes(Point point, Dimension bounds, Map<String,String> attrs) {
        Map<String, String> union = new HashMap<>();
        union.putAll(attrs);
        union.putAll(Map.of(
        "x", String.valueOf(point.x),
        "y", String.valueOf(point.y),
        "width", String.valueOf(bounds.width),
        "height", String.valueOf(bounds.height)));
        return union;
    }
    public void initializeListeners() {
       documentListener = IDESimpleListenerGenerators.getDocumentListener(info, xmldoc);
        editorMouseListener = IDEMouseListenerGenerator.getMouseListener(info, xmldoc);
        editorMouseMotionListener = IDEMouseListenerGenerator.getMouseMotionListener(info, xmldoc);
        caretListener = IDESimpleListenerGenerators.getCaretListener(info, xmldoc);
        selectionListener = IDESimpleListenerGenerators.getSelectionListener(info, xmldoc);
        visibleAreaListener = IDESimpleListenerGenerators.getVisibleAreaListener(info, xmldoc);
        toolWindowManagerListener =  IDEToolWindowListenerGenerator.getToolWindowManagerListener(info, xmldoc);

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
                AnActionListener.TOPIC, IDEActionListenerGenerator.getAnActionListener(info, xmldoc));

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                IDEFileEditorManagerListenerGenerator.getFileEditorManagerListener(info, xmldoc));

    }
    /**
     * This constructor initializes the IDE tracker.
     */
    IDETracker(IDETrackerInfo info) throws ParserConfigurationException {
        this.info = info;
        for(String element : ELEMENTS) {
            xmldoc.initializeElementAtRoot(element);
        }
        Element environment = xmldoc.getParentElement("environment");

        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        environment.setAttribute("screen_size", "(" + size.width + "," + size.height + ")");
        GraphicsConfiguration config = GraphicsEnvironment.getLocalGraphicsEnvironment().
                getDefaultScreenDevice().getDefaultConfiguration();
        environment.setAttribute("scale_x", String.valueOf(config.getDefaultTransform().getScaleX()));
        environment.setAttribute("scale_y", String.valueOf(config.getDefaultTransform().getScaleY()));
        environment.setAttribute("os_name", System.getProperty("os.name"));
        environment.setAttribute("java_version", System.getProperty("java.version"));
        environment.setAttribute("ide_version", ApplicationInfo.getInstance().getFullVersion());
        environment.setAttribute("ide_name", ApplicationInfo.getInstance().getVersionName());

        // FIXME: is this timer what makes it so slow to open the config file?
        timer.schedule(timerTask, 0, 1);
    }

    /**
     * This method returns the IDE tracker instance.
     *
     * @return The IDE tracker instance.
     */
    public static IDETracker getInstance(IDETrackerInfo info) throws ParserConfigurationException {
        return new IDETracker(info);
    }

    /**
     * This method starts tracking. All the listeners are added.
     *
     * @param project The project.
     */
    public void startTracking(Project project) {
        info.startTracking();
        Element environment = xmldoc.getParentElement("environment");
        environment.setAttribute("project_path", info.projectPath);
        environment.setAttribute("project_name", info.projectPath.substring(
                info.projectPath.lastIndexOf('/') + 1));
        editorEventMulticaster.addDocumentListener(documentListener, () -> {
        });
        editorEventMulticaster.addEditorMouseListener(editorMouseListener, () -> {
        });
        editorEventMulticaster.addEditorMouseMotionListener(editorMouseMotionListener, () -> {
        });
        editorEventMulticaster.addCaretListener(caretListener, () -> {
        });
        editorEventMulticaster.addSelectionListener(selectionListener, () -> {
        });
        editorEventMulticaster.addVisibleAreaListener(visibleAreaListener, () -> {
        });
        ToolWindowManagerEx toolWindowManager = (ToolWindowManagerEx) ToolWindowManager.getInstance(project);
        // Record all current bounds of tool windows to map
        for (String id : toolWindowManager.getToolWindowIds()) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
            if (toolWindow != null && toolWindow.isVisible()) {
                Component component = toolWindow.getContentManager().getComponent();
                Map<String,String> attrs = IDETracker.addBoundsAttributes(
                        component.getLocationOnScreen(),
                        component.getSize(),
                        Map.of( "aoi", toolWindow.getId(),
                                "event", "InitialWindow")
                );
                xmldoc.createElementTimestamp("tool_window", "tool_windows",attrs);

            }
        }
        // Add listener for tool windows
        toolWindowManager.addToolWindowManagerListener(toolWindowManagerListener);
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        for (VirtualFile file : fileEditorManager.getOpenFiles()) {
            xmldoc.archiveFile(info.dataOutputPath, info.projectPath, file.getPath(), String.valueOf(System.currentTimeMillis()), "fileOpened", null);
        }
        // FIXME KLN: 1. You need to record all current editors to the list here, or somewhere like here, just like the toolwindows. 2. also attach listener
    }

    /**
     * This method sets the handler for the IDE tracker data for real-time data transmission.
     *
     * @param ideTrackerDataHandler The handler for the IDE tracker data.
     */
    public void setIdeTrackerDataHandler(Consumer<Element> ideTrackerDataHandler) {
        info.ideTrackerDataHandler = ideTrackerDataHandler;
    }

    /**
     * This method stops tracking. All the listeners are removed. The tracking data is written to the XML file.
     */
    public void stopTracking() throws TransformerException {
        info.stopTracking();
        editorEventMulticaster.removeDocumentListener(documentListener);
        editorEventMulticaster.removeEditorMouseListener(editorMouseListener);
        editorEventMulticaster.removeEditorMouseMotionListener(editorMouseMotionListener);
        editorEventMulticaster.removeCaretListener(caretListener);
        editorEventMulticaster.removeSelectionListener(selectionListener);
        editorEventMulticaster.removeVisibleAreaListener(visibleAreaListener);
        String filePath = info.dataOutputPath + "/ide_tracking.xml";
        XMLWriter.writeToXML(xmldoc.getDocument(), filePath);
    }

    /**
     * This method pauses tracking. The {@code isTracking} is set to false.
     */
    public void pauseTracking() {
        info.stopTracking();
    }

    /**
     * This method resumes tracking. The {@code isTracking} is set to true.
     */
    public void resumeTracking() {
        info.startTracking();
    }

    @Override
    public void dispose() {
    }

    /**
     * This method returns whether the IDE tracker is tracking.
     *
     * @return Whether the IDE tracker is tracking.
     */
    public boolean isTracking() {
        return info.isTracking();
    }

    public void setIsRealTimeDataTransmitting(boolean b) {
        IDETrackerInfo.isRealTimeDataTransmitting = b;
    }


}
