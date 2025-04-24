package trackers.ListenerGenerators.IDETrackerListeners;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import entity.IDETrackerInfo;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

import java.util.Map;

public class IDEFileEditorManagerListenerGenerator {

    // Listener for the "Search Everywhere" window

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

            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                handleFile(source, file, "fileOpened");
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