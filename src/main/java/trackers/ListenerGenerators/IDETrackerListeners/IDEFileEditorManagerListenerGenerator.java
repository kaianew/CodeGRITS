package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
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

            public EditorWindow getEditorWindow(FileEditorManagerImpl fileEditorManager, Editor editor, VirtualFile file) {
                EditorWindow[] windows = fileEditorManager.getWindows();
                for (EditorWindow window: windows) {
                    EditorWithProviderComposite composite = window.getSelectedEditor();
                    FileEditor fileEditor = composite.getSelectedEditor();;
                    if (fileEditor instanceof TextEditor) {
                        Editor comparEditor = ((TextEditor) fileEditor).getEditor();
                        if (composite != null && comparEditor == editor) {
                            return window;
                        }
                    }
                }
                return null;
            }

            // TODO: eventually manage state of filepath, visiblearea, and editors (in IDETrackerInfo) with this
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                handleFile(source, file, "fileOpened");
                Editor editor = source.getSelectedTextEditor();
                IDETrackerInfo.EditorTrackingInfo val = new IDETrackerInfo.EditorTrackingInfo();
                assert editor != null;
                Component editorComponent = editor.getComponent();
                Point location = editorComponent.getLocationOnScreen();
                Dimension bounds = editorComponent.getSize();
                AOIBounds loc = new AOIBounds(location.x, location.y, bounds.width, bounds.height, "Editor");
                String filePath = file.getPath();
                val.filePath = filePath;
                val.editor = editor;
                val.bounds = loc;
                EditorWindow editorWindow = getEditorWindow((FileEditorManagerImpl) source, editor, file);
                // add to file --> window map
                java.util.List<EditorWindow> editorWindowList = new ArrayList<>();
                editorWindowList.add(editorWindow);
                if (info.fileEditorWindows.containsKey(filePath)) {
                    // add to list -- shame on you for opening the same file in a different window
                    editorWindowList= info.fileEditorWindows.get(filePath);
                    editorWindowList.add(editorWindow);
                }
                info.fileEditorWindows.put(filePath, editorWindowList);
                info.visibleEditors.put(editorWindow, val); // I think this might resize a bunch so it might have different bounds
                System.out.println("we put an editor into our editor map");
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                handleFile(source, file, "fileClosed");
                System.out.println("Editor closed: " + file.getPath());
                // if editor window no longer visible, delete from map. need to look through
                // lookup in file --> editorwindow map
                java.util.List<EditorWindow> editorWindowList = info.fileEditorWindows.get(file.getPath());
                // collect no longer visible editorwindows
                java.util.List<EditorWindow> closedWindows = new ArrayList<>();
                for (EditorWindow window : editorWindowList) {
                    // if window is not visible, put in closedWindows list
//                    if (window.getComponent().isShowing()) {
//
//                    }
                }
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