package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import trackers.TrackerInfo.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

import java.util.Map;

public class IDESimpleListenerGenerators {

    public static DocumentListener getDocumentListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {

        return new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (!info.isTracking()) return; // FIXME: this should probably be deregistered
                if (event.getDocument().getText().length() == 0) return;
                if (EditorFactory.getInstance().getEditors(event.getDocument()).length == 0) return;
                Editor currentEditor = EditorFactory.getInstance().getEditors(event.getDocument())[0];
                if (currentEditor != null && currentEditor.getEditorKind() == EditorKind.CONSOLE) {
                    xmldoc.archiveFile(info.dataOutputPath, info.projectPath, "unknown", String.valueOf(System.currentTimeMillis()),
                            "", event.getDocument().getText());
                    return;
                }
                VirtualFile changedFile = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (changedFile != null) {
                    info.changedFilepath = changedFile.getPath();
                    info.changedFileText = event.getDocument().getText();
                }
            }
        };
    }

    public static VisibleAreaListener getVisibleAreaListener(IDETrackerInfo info, XMLDocumentHandler xmldoc){
        return new VisibleAreaListener() {
            @Override
            public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
                if (!info.isTracking()) return;
                if (e.getEditor().getEditorKind() == EditorKind.MAIN_EDITOR) {
                    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());
                    Element visibleAreaElement =
                    xmldoc.createElementTimestamp("visible_area", "visible_areas",
                            Map.of( "event", "visibleAreaChanged",
                                    "path", virtualFile != null ?
                                            RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : "",
                                    "x", String.valueOf(e.getEditor().getScrollingModel().getHorizontalScrollOffset()),
                                    "y", String.valueOf(e.getEditor().getScrollingModel().getVerticalScrollOffset()),
                                    "width", String.valueOf(e.getEditor().getScrollingModel().getVisibleArea().width),
                                    "height", String.valueOf(e.getEditor().getScrollingModel().getVisibleArea().height)));
                    info.handleElement(visibleAreaElement);
                }
            };

        };
    }

    public static CaretListener getCaretListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent e) {
                if (!info.isTracking()) return;
                VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());

                Element caretElement = xmldoc.createElementTimestamp("caret", "carets",
                        Map.of( "event", "caretPositionChanged",
                                "path", virtualFile != null ?
                        RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : "",
                                "line", String.valueOf(e.getNewPosition().line),
                                "column", String.valueOf(e.getNewPosition().column)));
                info.handleElement(caretElement);
            }
        };
    }
    public static SelectionListener getSelectionListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                if (!info.isTracking()) return;
                VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(e.getEditor().getDocument());
                LogicalPosition startLogicalPos = e.getEditor().offsetToLogicalPosition(e.getNewRange().getStartOffset());
                LogicalPosition endLogicalPos = e.getEditor().offsetToLogicalPosition(e.getNewRange().getEndOffset());
                String selectedText = e.getEditor().getSelectionModel().getSelectedText();
                Element selectionElement = xmldoc.createElementTimestamp("selection", "selections",
                        Map.of("event", "selectionChanged",
                                "path", virtualFile != null ?
                                        RelativePathGetter.getRelativePath(virtualFile.getPath(), info.projectPath) : "",
                                "start_position", startLogicalPos.line + ":" + startLogicalPos.column,
                                "end_position", endLogicalPos.line + ":" + endLogicalPos.column,
                            "selected_text", selectedText != null ? selectedText : ""));

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
