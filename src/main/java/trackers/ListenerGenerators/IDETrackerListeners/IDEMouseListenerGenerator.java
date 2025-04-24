package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import trackers.TrackerInfo.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

import java.awt.event.MouseEvent;
import java.util.Map;

public class IDEMouseListenerGenerator {
    /**
     * This method returns the mouse XML element.
     *
     * @param e  The editor mouse event.
     * @param id The id of the mouse event.
     * @return The mouse element.
     */
    private static Element getMouseElement(IDETrackerInfo info, XMLDocumentHandler xmldoc, EditorMouseEvent e, String id) {
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());
        MouseEvent mouseEvent = e.getMouseEvent();
        return xmldoc.createElementTimestamp("mouse", "mouses",
                Map.of( "event", id,
                        "path", virtualFile != null ?
                                RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : "",
                        "x", String.valueOf(mouseEvent.getXOnScreen()),
                        "y", String.valueOf(mouseEvent.getYOnScreen())));
    }

    public static EditorMouseListener getMouseListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new EditorMouseListener() {
            @Override
            public void mousePressed(@NotNull EditorMouseEvent e) {
                if (!info.isTracking()) return; // FIXME: instead, make sure this is just deregistered when tracking stops, and vice versa
                getMouseElement(info, xmldoc, e, "mousePressed");
            }

            @Override
            public void mouseClicked(@NotNull EditorMouseEvent e) {
                if (!info.isTracking()) return;
                Element mouseElement = getMouseElement(info, xmldoc, e, "mouseClicked");
                info.handleElement(mouseElement);
            }

            @Override
            public void mouseReleased(@NotNull EditorMouseEvent e) {
                if (!info.isTracking()) return;
                Element mouseElement = getMouseElement(info, xmldoc, e, "mouseReleased");
                info.handleElement(mouseElement);
            }
        };
    }
    public static EditorMouseMotionListener getMouseMotionListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new EditorMouseMotionListener() {
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent e) {
                if (!info.isTracking()) return;
                Element mouseElement = getMouseElement(info, xmldoc, e, "mouseMoved");
                info.handleElement(mouseElement);
            }

            @Override
            public void mouseDragged(@NotNull EditorMouseEvent e) {
                if (!info.isTracking()) return;
                Element mouseElement = getMouseElement(info, xmldoc, e, "mouseDragged");
                info.handleElement(mouseElement);
            }
        };

    }
}
