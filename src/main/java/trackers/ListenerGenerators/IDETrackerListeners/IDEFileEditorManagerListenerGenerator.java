package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import entity.AOIBounds;
import org.bytedeco.javacpp.annotation.Virtual;
import trackers.TrackerInfo.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

import java.awt.*;
import java.util.*;
import java.util.List;

public class IDEFileEditorManagerListenerGenerator {

    public static FileEditorManagerListener getFileEditorManagerListener(IDETrackerInfo info, XMLDocumentHandler xmldoc) {
        return new FileEditorManagerListener() {
            private void handleFile(@NotNull FileEditorManager source, @NotNull VirtualFile file, String event) {
                if (info.isTracking()) {
                    Element fileElement = xmldoc.createElementTimestamp("file", "files",
                            Map.of("event", event,
                                    "path", RelativePathGetter.getRelativePath(file.getPath(), info.projectPath)));
                    xmldoc.archiveFile(info.dataOutputPath, info.projectPath, file.getPath(), String.valueOf(System.currentTimeMillis()), event, null);
                    info.handleElement(fileElement);
                }
            }

            private static void traverse(Component component) {
                System.out.println("Component: " + component.getClass().getName());

                if (component instanceof Container) {
                    for (Component child : ((Container) component).getComponents()) {
                        traverse(child);
                    }
                }
            }

            // TODO: eventually manage state of filepath, visiblearea, and editors (in IDETrackerInfo) with this
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                handleFile(source, file, "fileOpened");
//                Editor editor = source.getSelectedTextEditor();
                EditorsSplitters splitters = ((FileEditorManagerImpl) source).getSplitters();
                for (EditorWindow window : splitters.getWindows()) {
                    EditorWithProviderComposite composite = (EditorWithProviderComposite) window.getSelectedEditor();
                    int count = 0;
                    for (FileEditor fe : composite.getEditors()) {
                        count++;
                        if (fe instanceof TextEditor) {
                            Editor editor = ((TextEditor) fe).getEditor();
                            System.out.println("Unwrapped text editor: " + editor);
                            // make all the AOIBounds and check to see if a hypothetical gaze is in them
                            Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
                            Point editorLocation = editor.getContentComponent().getLocationOnScreen();
                            String filePath = editor.getVirtualFile().getPath();
                            String AOIString = "Editor" + count;
                        }
                    }
                }

            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                handleFile(source, file, "fileClosed");

            }

            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                if (info.isTracking()) {
                    Element fileElement = xmldoc.createElementTimestamp("file", "files", Map.of("event", "selectionChanged"));

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
        };
    }
}