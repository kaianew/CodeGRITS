package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import trackers.TrackerInfo.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import trackers.IDETracker;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

public class IDEToolWindowListenerGenerator {
    public static ToolWindowManagerListener getToolWindowManagerListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {

        return new ToolWindowManagerListener() {
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
                    String aoiEvent = "";
                    boolean registerBounds = false; // hoisted on my own petard
                    switch(changeType) {
                        case HideToolWindow: aoiEvent = "WindowHidden";
                            info.AOIMap.remove(toolWindow.getId());
                            break;
                        case  MovedOrResized: aoiEvent = "WindowChanged";
                            registerBounds = true;
                            break;
                        case ActivateToolWindow: aoiEvent = "WindowShown";
                            registerBounds = true;
                            break;
                    }
                    Map<String,String> initialAttrs =
                            Map.of("aoi", windowId,
                                    "event", aoiEvent);
                    Map<String,String> passedAttrs = initialAttrs;
                    if(registerBounds) {
                        Component toolComponent = toolWindow.getContentManager().getComponent();
                        passedAttrs = IDETracker.addBoundsAttributes(toolComponent.getLocationOnScreen(),
                                toolComponent.getSize(),
                                initialAttrs);
                        info.registerAOIBounds(toolComponent, toolWindow.getId());
                    }
                    xmldoc.createElementTimestamp("tool_window", "tool_windows", passedAttrs);

                }
                previousVisibilityState.put(windowId, isCurrentlyVisible);
            }
        };
    }
}
