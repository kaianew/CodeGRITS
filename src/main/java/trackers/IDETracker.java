package trackers;

import javax.xml.parsers.*;
import javax.xml.transform.TransformerException;

import com.intellij.ide.actions.BigPopupUI;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.*;

import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import entity.AOIBounds;
import entity.IDESimpleListenerGenerators;
import entity.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.Timer;
import java.util.function.Consumer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import utils.RelativePathGetter;
import utils.XMLWriter;

// Imports for tool window bounds recording.
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;

/**
 * This class is the IDE tracker.
 */
public final class IDETracker implements Disposable {

    private IDETrackerInfo info = new IDETrackerInfo();
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
    private boolean SEOpen = false;


    // Class for the Map below.

    // Variable for keeping track of visible AOIs and their bounds throughout recording.
    // The ToolWindowListener edits this, and the getAOIMap function allows the EyeTracker class to access it.
    private Map<String, AOIBounds> AOIMap;
    private Map<String, Editor> EditorMap;
    private int editorCtr = 0;

    /**
     * This variable is the document listener for the IDE tracker. When the document is changed, if the {@code EditorKind} is {@code CONSOLE}, the console output is archived. Otherwise, the {@code changedFilepath} and {@code changedFileText} are updated.
     */
    DocumentListener documentListener = IDESimpleListenerGenerators.getDocumentListener(info, xmldoc);

    /**
     * This variable is the mouse listener for the IDE tracker.
     * When the mouse is pressed, clicked, or released, the mouse event is tracked.
     */
    EditorMouseListener editorMouseListener = IDESimpleListenerGenerators.getMouseListener(info, xmldoc);

    /**
     * This variable is the mouse motion listener for the IDE tracker.
     * When the mouse is moved or dragged, the mouse event is tracked.
     */
    EditorMouseMotionListener editorMouseMotionListener = IDESimpleListenerGenerators.getMouseMotionListener(info, xmldoc);
    /**
     * This variable is the caret listener for the IDE tracker.
     * When the caret position is changed, the caret event is tracked.
     */
    CaretListener caretListener = IDESimpleListenerGenerators.getCaretListener(info, xmldoc);

    /**
     * This variable is the selection listener for the IDE tracker.
     * When the selection is changed, the selection event is tracked.
     */
    SelectionListener selectionListener = IDESimpleListenerGenerators.getSelectionListener(info, xmldoc);

    /**
     * This variable is the visible area listener for the IDE tracker.
     * When the visible area is changed, the visible area event is tracked.
     */
    VisibleAreaListener visibleAreaListener = IDESimpleListenerGenerators.getVisibleAreaListener(info, xmldoc);

    private void registerBoundsToElement(Component component, Element componentElement, String componentID) {
        Point location = component.getLocationOnScreen();
        Dimension bounds = component.getSize();
        componentElement.setAttribute("x", String.valueOf(location.x));
        componentElement.setAttribute("y", String.valueOf(location.y));
        componentElement.setAttribute("width", String.valueOf(bounds.width));
        componentElement.setAttribute("height", String.valueOf(bounds.height));

        // add to map
        AOIBounds loc = new AOIBounds(location.x, location.y, bounds.width, bounds.height, componentID);
        AOIMap.put(componentID, loc);

    }

    // Listener for when the state of tool windows changes to record AOI bounds dynamically.
    ToolWindowManagerListener toolWindowManagerListener = new ToolWindowManagerListener() {
        // Enums for state changes do not work for when windows are shown. Thus, we maintain our own visibility states.
        private final Map<String, Boolean> previousVisibilityState = new HashMap<>();
        @Override
        public void stateChanged(@NotNull ToolWindowManager toolWindowManager,
                                 @NotNull ToolWindow toolWindow,
                                 @NotNull ToolWindowManagerListener.ToolWindowManagerEventType changeType) {
            if (!info.isTracking()) return;
            String windowId = toolWindow.getId();
            boolean isCurrentlyVisible = toolWindow.isVisible();
            boolean wasVisible = previousVisibilityState.getOrDefault(windowId, false);

            // Only create an element if visibility was changed or window was movedorresized
                if (isCurrentlyVisible != wasVisible ||
                        changeType == ToolWindowManagerListener.ToolWindowManagerEventType.MovedOrResized) {
                    Element toolWindowElement = xmldoc.createElementAtNamedParent("tool_window", "tool_windows");
                    toolWindowElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                    toolWindowElement.setAttribute("AOI", windowId);
                    if (changeType == ToolWindowManagerEventType.HideToolWindow) {
                        toolWindowElement.setAttribute("event", "WindowHidden");
                        // Remove AOI from map (no longer visible).
                        AOIMap.remove(toolWindow.getId());
                    }
                    else if (changeType == ToolWindowManagerEventType.MovedOrResized) {
                        toolWindowElement.setAttribute("event", "WindowChanged");
                        Component component = toolWindow.getContentManager().getComponent();

                        registerBoundsToElement(component, toolWindowElement, toolWindow.getId());
                    }
                    else if (changeType == ToolWindowManagerEventType.ActivateToolWindow) {
                        // Tool window was just shown.
                        toolWindowElement.setAttribute("event", "WindowShown");
                        Component component = toolWindow.getContentManager().getComponent();
                        registerBoundsToElement(component, toolWindowElement, toolWindow.getId());

                    }
                }
                previousVisibilityState.put(windowId, isCurrentlyVisible);
            }
    };


    private void recordPopupBounds(SearchEverywhereUI ui, String popupId, String eventType) {
        Element popupElement = xmldoc.createElementAtNamedParent("popup", "popups");
        popupElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
        popupElement.setAttribute("event", eventType);
        popupElement.setAttribute("AOI", popupId);
        Point loc = ui.getLocationOnScreen();
        // Supposed to use the underlying viewType variable to get this info
        Dimension size = ui.getPreferredSize();
        popupElement.setAttribute("x", String.valueOf(loc.x));
        popupElement.setAttribute("y", String.valueOf(loc.y));
        popupElement.setAttribute("width", String.valueOf(size.width));
        popupElement.setAttribute("height", String.valueOf(size.height));
        // make AOIBounds and add to stack

        LOG.info("we are recording popup bounds in the map");
        LOG.info(popupId);
        AOIBounds bounds = new AOIBounds(loc.x, loc.y, size.width, size.height, popupId);
        AOIMap.put(popupId, bounds);
    }

    // Listener for the "Search Everywhere" window
    JBPopupListener popupListener = new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
            if (!info.isTracking()) return;
            if (!SEOpen) return; // if the search everywhere popup isn't open, don't record closing it
            String popupId = "SearchEverywhere";
            AOIMap.remove(popupId);

            Element popupElement = xmldoc.createElementAtNamedParent("popup", "popups");
            popupElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
            popupElement.setAttribute("event", "PopupClosed");
            popupElement.setAttribute("AOI", popupId);
            SEOpen = false;
        }
    };
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
            if (info.changedFilepath.length() > 0) {
                if (!info.isTracking()) return;
                xmldoc.archiveFile(info.dataOutputPath, info.projectPath, info.changedFilepath, String.valueOf(System.currentTimeMillis()),
                        "contentChanged", info.changedFileText);
                info.changedFilepath = "";
            }
        }
    };
    private ComponentListener componentListenerCreator(String key) {
        return new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                LOG.info("Editor resized.");
                // Need to update AOIBounds in AOIMap
                // BUT maybe component not done resizing.
                new UiNotifyConnector(e.getComponent(), new Activatable() {
                    @Override
                    public void showNotify() {
                        // NOW it's really resized.
                        Point point = e.getComponent().getLocationOnScreen();
                        Dimension dim = e.getComponent().getSize();
                        AOIBounds bounds = new AOIBounds(point.x, point.y, dim.width, dim.height, key);
                        AOIMap.put(key, bounds);

                        // Add XML resizing event
                        Element editorElement = xmldoc.createElementAtNamedParent("editor", "editors");
                        editorElement.setAttribute("AOI", key);
                        editorElement.setAttribute("event", "EditorResized");
                        editorElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                        registerBoundsToEditor(point, dim, editorElement);
                    }
                });
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                LOG.info("Editor moved");
            }

            @Override
            public void componentShown(ComponentEvent e) {
                LOG.info("Editor shown");
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                LOG.info("Editor hidden");
            }
        };
    }

    // FIXME CLG: this is a good place to refactor. within this file, I now have three different functions which save elements to the XML file
    // which take different args. but all ultimately do something very similar
    private void registerBoundsToEditor(Point point, Dimension bounds, Element editorElement) {
        editorElement.setAttribute("x", String.valueOf(point.x));
        editorElement.setAttribute("y", String.valueOf(point.y));
        editorElement.setAttribute("width", String.valueOf(bounds.width));
        editorElement.setAttribute("height", String.valueOf(bounds.height));
    }

    public void initializeListeners() {
        EditorFactory editorFactory = EditorFactory.getInstance();
        editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent editorFactoryEvent) {
                Editor editor = editorFactoryEvent.getEditor();
                LOG.info("EDITOR CREATED KAIA PART 2");

                // Create UiNotifyConnector to detect when editor is actually shown
                new UiNotifyConnector(editor.getContentComponent(), new Activatable() {
                    @Override
                    public void showNotify() {
                        LOG.info("EDITOR ON SCREEN KAIA");
                        Dimension bounds = editor.getContentComponent().getSize();
                        Point point = editor.getContentComponent().getLocationOnScreen();

                        // Add to EditorMap, AOIMap, and XML file
                        String key = "Editor" + editorCtr;
                        EditorMap.put(key, editor);
                        AOIBounds AOIBoundsVar = new AOIBounds(point.x, point.y, bounds.width, bounds.height, key);
                        AOIMap.put(key, AOIBoundsVar);
                        Element editorElement = xmldoc.createElementAtNamedParent("editor", "editors");
                        editorElement.setAttribute("AOI", key);
                        editorElement.setAttribute("event", "EditorCreated");
                        editorElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                        registerBoundsToEditor(point, bounds, editorElement);
                        editorCtr += 1;
                        // Now it's safe to add component listener since the editor is actually visible
                        ComponentListener editorListener = componentListenerCreator(key);
                        editor.getContentComponent().addComponentListener(editorListener);
                    }
                });
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                // Need to 1. remove from EditorMap and 2. remove from AOIMap
                // We don't know the key. For now, go through EditorMap values and find the one that matches...
                Editor editor = event.getEditor();
                String key = "";
                for (Map.Entry<String, Editor> entry : EditorMap.entrySet()) {
                    if (editor.equals(entry.getValue())) {
                        key = entry.getKey();
                        break;
                    }
                }
                EditorMap.remove(key);
                AOIMap.remove(key);
                Element editorElement = xmldoc.createElementAtNamedParent("editor", "editors");
                editorElement.setAttribute("AOI", key);
                editorElement.setAttribute("event", "EditorReleased");
                editorElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
            }
        });

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
                AnActionListener.TOPIC, new AnActionListener() {

                    @Override
                    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
                        if (info.isTracking()) {
                            Element actionElement = xmldoc.createElementAtNamedParent("action", "actions");
                            actionElement.setAttribute("id", ActionManager.getInstance().getId(action));
                            actionElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                            VirtualFile virtualFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);
                            actionElement.setAttribute("path", virtualFile != null ?
                                    RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : null);
                            info.handleElement(actionElement);

                            // Handle the SearchEverywhere popup
                            String actionId = ActionManager.getInstance().getId(action);
                            if ("SearchEverywhere".equals(actionId)) {
                                // The SE popup is now open, so we set SEOpen to true
                                SEOpen = true;
                                // FIXME: change out invokelater
                                javax.swing.Timer timer = new javax.swing.Timer(50, (ActionEvent time_e) -> {
                                    // Get the popup from the action and add our listener
                                    Project project = event.getProject();
                                    SearchEverywhereManager manager = SearchEverywhereManager.getInstance(project);
                                    if (manager.isShown()) {
                                        ((javax.swing.Timer) time_e.getSource()).stop();
                                        SearchEverywhereUI ui = manager.getCurrentlyShownUI();
                                        // Attach the viewTypeListener
                                        BigPopupUI.ViewTypeListener viewTypeListener = new BigPopupUI.ViewTypeListener() {
                                            @Override
                                            public void suggestionsShown(@NotNull BigPopupUI.ViewType viewType) {
                                                // viewType will either be FULL or SHORT, depending on the length of the panel
                                                // Record size of the popup
                                                SearchEverywhereManager manager = SearchEverywhereManager.getInstance(project);
                                                if (manager.isShown()) {
                                                    String popupId = "SearchEverywhere";
                                                    // adds to map, records to xml
                                                    recordPopupBounds(ui, popupId, "PopupViewChanged");
                                                }
                                            }
                                        };
                                        ui.addViewTypeListener(viewTypeListener);
                                        // Use encapsulation-breaking reflective access to get the private myBalloon variable of the manager
                                        Field balloonField;
                                        try {
                                            balloonField = manager.getClass().getDeclaredField("myBalloon");
                                        } catch (NoSuchFieldException e) {
                                            LOG.info("No myBalloon field in the SearchEverywhereManager.");
                                            throw new RuntimeException(e);
                                        }
                                        balloonField.setAccessible(true);
                                        JBPopup popup;
                                        try {
                                            popup = (JBPopup) balloonField.get(manager);
                                        } catch (IllegalAccessException e) {
                                            LOG.info("Illegal access of myBalloon.");
                                            throw new RuntimeException(e);
                                        }
                                        String popupId = "SearchEverywhere";
                                        // adds to stack, records to xml
                                        recordPopupBounds(ui, popupId, "PopupOpened");
                                        // can tell when popup closes.
                                        popup.addListener(popupListener);
                                    }
                                    LOG.info("Didn't find the SearchEverywhere window yet.");
                                });
                                timer.start();
                            }
                        }
                    }

                    @Override
                    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
                        if (info.isTracking()) {
                            Element typingElement = xmldoc.createElementAtNamedParent("typing", "typings");
                            typingElement.setAttribute("character", String.valueOf(c));
                            typingElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                            VirtualFile virtualFile = dataContext.getData(PlatformDataKeys.VIRTUAL_FILE);
                            typingElement.setAttribute("path", virtualFile != null ?
                                    RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : null);

                            Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
                            if (editor != null) {
                                Caret primaryCaret = editor.getCaretModel().getPrimaryCaret();
                                LogicalPosition logicalPos = primaryCaret.getLogicalPosition();
                                typingElement.setAttribute("line", String.valueOf(logicalPos.line));
                                typingElement.setAttribute("column", String.valueOf(logicalPos.column));
                            }
                            info.handleElement(typingElement);
                        }
                    }
                });

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {

                    @Override
                    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        if (info.isTracking()) {
                            Element fileElement = xmldoc.createElementAtNamedParent("file", "files");
                            fileElement.setAttribute("id", "fileOpened");
                            String timestamp = String.valueOf(System.currentTimeMillis());
                            fileElement.setAttribute("timestamp", timestamp);
                            fileElement.setAttribute("path",
                                    RelativePathGetter.getRelativePath(file.getPath(), info.projectPath));
                            xmldoc.archiveFile(info.dataOutputPath, info.projectPath, file.getPath(), timestamp, "fileOpened", null);
                            info.handleElement(fileElement);
                        }
                    }

                    @Override
                    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        if (info.isTracking()) {
                            Element fileElement = xmldoc.createElementAtNamedParent("file", "files");
                            fileElement.setAttribute("id", "fileClosed");
                            String timestamp = String.valueOf(System.currentTimeMillis());
                            fileElement.setAttribute("timestamp", timestamp);
                            fileElement.setAttribute("path",
                                    RelativePathGetter.getRelativePath(file.getPath(), info.projectPath));
                            xmldoc.archiveFile(info.dataOutputPath, info.projectPath, file.getPath(), timestamp, "fileClosed", null);
                            info.handleElement(fileElement);
                        }
                    }

                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        if (info.isTracking()) {
                            Element fileElement = xmldoc.createElementAtNamedParent("file", "files");

                            fileElement.setAttribute("id", "selectionChanged");
                            fileElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                            if (event.getOldFile() != null) {
                                fileElement.setAttribute("old_path",
                                        RelativePathGetter.getRelativePath(event.getOldFile().getPath(), info.projectPath));
                                xmldoc.archiveFile(info.dataOutputPath, info.projectPath, event.getOldFile().getPath(), String.valueOf(System.currentTimeMillis()),
                                        "selectionChanged | OldFile", null);
                            }
                            if (event.getNewFile() != null) {
                                fileElement.setAttribute("new_path",
                                        RelativePathGetter.getRelativePath(event.getNewFile().getPath(), info.projectPath));
                                xmldoc.archiveFile(info.dataOutputPath, info.projectPath, event.getNewFile().getPath(), String.valueOf(System.currentTimeMillis()),
                                        "selectionChanged | NewFile", null);
                            }
                            info.handleElement(fileElement);
                        }
                    }
                });
    }
    /**
     * This constructor initializes the IDE tracker.
     */
    IDETracker() throws ParserConfigurationException {
        AOIMap = new HashMap<>();
        EditorMap = new HashMap<>();
        for(String element : ELEMENTS) {
            xmldoc.initializeElementAtRoot(element);
        }
        Element environment = xmldoc.getElement("environment");

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
    public static IDETracker getInstance() throws ParserConfigurationException {
        return new IDETracker();
    }

    /**
     * This method starts tracking. All the listeners are added.
     *
     * @param project The project.
     */
    public void startTracking(Project project) {
        info.startTracking();
        Element environment = xmldoc.getElement("environment");
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
                Element initialToolWindowElement = xmldoc.createElementAtNamedParent("tool_window", "tool_windows");
                initialToolWindowElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                initialToolWindowElement.setAttribute("AOI", toolWindow.getId());
                initialToolWindowElement.setAttribute("event", "InitialWindow");
                Component component = toolWindow.getContentManager().getComponent();
                registerBoundsToElement(component, initialToolWindowElement, toolWindow.getId());
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



    // This method passes the AOIBounds Map so the EyeTracker can determine which AOI gazes are in.
    public Map<String, AOIBounds> getAOIMap() {
        return this.AOIMap;
    }

    /**
     * This method returns whether the IDE tracker is tracking.
     *
     * @return Whether the IDE tracker is tracking.
     */
    public boolean isTracking() {
        return info.isTracking();
    }

    public void setInfo(IDETrackerInfo info) {
        this.info = info;
    }

    public void setIsRealTimeDataTransmitting(boolean b) {
        IDETrackerInfo.isRealTimeDataTransmitting = b;
    }
}
