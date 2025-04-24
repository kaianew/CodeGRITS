package entity;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

import java.awt.event.MouseEvent;

public class IDEListenerGenerators {


    /**
     * This method returns the mouse XML element.
     *
     * @param e  The editor mouse event.
     * @param id The id of the mouse event.
     * @return The mouse element.
     */
    private static Element getMouseElement(IDETrackerInfo info, XMLDocumentHandler xmldoc, EditorMouseEvent e, String id) {
        Element mouseElement = xmldoc.createElementAtNamedParent("mouse", "mouses");
        mouseElement.setAttribute("id", id);
        mouseElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());
        mouseElement.setAttribute("path", virtualFile != null ?
                RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : null);
        MouseEvent mouseEvent = e.getMouseEvent();
        mouseElement.setAttribute("x", String.valueOf(mouseEvent.getXOnScreen()));
        mouseElement.setAttribute("y", String.valueOf(mouseEvent.getYOnScreen()));
        return mouseElement;
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
    public static CaretListener getCaretListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent e) {
                if (!info.isTracking()) return;
                Element caretElement = xmldoc.createElementAtNamedParent("caret", "carets");
                caretElement.setAttribute("id", "caretPositionChanged");
                caretElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());
                caretElement.setAttribute("path", virtualFile != null ?
                        RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : null);
                caretElement.setAttribute("line", String.valueOf(e.getNewPosition().line));
                caretElement.setAttribute("column", String.valueOf(e.getNewPosition().column));
                info.handleElement(caretElement);
            }
        };
    }
    public static SelectionListener getSelectionListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                if (!info.isTracking()) return;

                Element selectionElement = xmldoc.createElementAtNamedParent("selection", "selections");
                selectionElement.setAttribute("id", "selectionChanged");
                selectionElement.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
                VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());
                selectionElement.setAttribute("path", virtualFile != null ?
                        RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : null);
                LogicalPosition startLogicalPos = e.getEditor().offsetToLogicalPosition(e.getNewRange().getStartOffset());
                LogicalPosition endLogicalPos = e.getEditor().offsetToLogicalPosition(e.getNewRange().getEndOffset());
                selectionElement.setAttribute("start_position", startLogicalPos.line + ":" +
                        startLogicalPos.column);
                selectionElement.setAttribute("end_position", endLogicalPos.line + ":" +
                        endLogicalPos.column);
                selectionElement.setAttribute("selected_text", e.getEditor().getSelectionModel().getSelectedText());

                String currentSelectionInfo = selectionElement.getAttribute("path") + "-" +
                        selectionElement.getAttribute("start_position") + "-" +
                        selectionElement.getAttribute("end_position") + "-" +
                        selectionElement.getAttribute("selected_text");
                if (currentSelectionInfo.equals(info.lastSelectionInfo)) return;
                info.lastSelectionInfo = currentSelectionInfo;
                info.handleElement(selectionElement);
            }
        };
    }
}
