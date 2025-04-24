package entity;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

public class IDEListenerGenerators {


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
