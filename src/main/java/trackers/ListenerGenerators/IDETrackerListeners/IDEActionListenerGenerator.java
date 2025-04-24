package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.ide.actions.BigPopupUI;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import entity.AOIBounds;
import trackers.TrackerInfo.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import trackers.IDETracker;
import utils.RelativePathGetter;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.Map;

public class IDEActionListenerGenerator {

    private static final Logger LOG = Logger.getInstance(IDEActionListenerGenerator.class);

    public static AnActionListener getAnActionListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new AnActionListener() {

            private void recordPopupBounds(SearchEverywhereUI ui, String popupId, String event ) {
                Point loc = ui.getLocationOnScreen();
                Dimension size = ui.getPreferredSize();

                Map<String,String> passedAttrs = IDETracker.addBoundsAttributes(loc, size, Map.of("aoi", popupId,
                        "event", event)
                );
                xmldoc.createElementTimestamp("popup", "popups", passedAttrs);

                LOG.info("we are recording popup bounds in the map");
                LOG.info(popupId);
                AOIBounds bounds = new AOIBounds(loc.x, loc.y, size.width, size.height, popupId);
                info.AOIMap.put(popupId, bounds);

            }
            @Override
            public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
                if (info.isTracking()) {
                    VirtualFile virtualFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);

                    String eventName = ActionManager.getInstance().getId(action);
                    Element actionElement =
                            xmldoc.createElementTimestamp("action", "actions",
                                    Map.of("event", eventName != null ? eventName : "",
                                            "path", virtualFile != null ?
                                                    RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : ""));
                    info.handleElement(actionElement);

                    // Handle the SearchEverywhere popup
                    String actionId = ActionManager.getInstance().getId(action);
                    if ("SearchEverywhere".equals(actionId)) {
                        // The SE popup is now open, so we set SEOpen to true
                        info.SEOpen = true;
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
                                recordPopupBounds(ui, "SearchEverywhere", "PopupOpened" );

                                // can tell when popup closes.

                                popup.addListener(new JBPopupListener() { // possible FIXME: do we ever want to deregister this listener? it used to be a class variable, back in IDE tracker
                                    @Override
                                    public void onClosed(@NotNull LightweightWindowEvent event) {
                                        if (!info.isTracking()) return;
                                        if (!info.SEOpen) return; // if the search everywhere popup isn't open, don't record closing it
                                        String popupId = "SearchEverywhere";
                                        info.AOIMap.remove(popupId);
                                        xmldoc.createElementTimestamp("popup", "popups",
                                                Map.of("aoi", popupId,
                                                        "event", "PopupClosed"));
                                        info.SEOpen = false;
                                    }
                                });
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
                    VirtualFile virtualFile = dataContext.getData(PlatformDataKeys.VIRTUAL_FILE);

                    Element typingElement = xmldoc.createElementTimestamp("typing", "typings",
                            Map.of("character", String.valueOf(c),
                                    "path", virtualFile != null ?
                                            RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : null)
                    );
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
        };
    }
}