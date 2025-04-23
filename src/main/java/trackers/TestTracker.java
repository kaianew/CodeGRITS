package trackers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class TestTracker implements Disposable {
    @Override
    public void dispose() {

    }

    public TestTracker() {
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        System.out.println("Editor opened: " + file.getPath());
                        FileEditor[] editors = source.getEditors(file);

                        for (FileEditor editor : editors) {
                            attachComponentListenerToEditor(editor, file.getName());
                        }
                    }

                    @Override
                    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                        System.out.println("Editor closed: " + file.getPath());
                    }
                }
        );
    }

    private void attachComponentListenerToEditor(FileEditor fileEditor, String key) {
        if (fileEditor instanceof TextEditor) {
            JComponent editorComponent = ((TextEditor) fileEditor).getComponent();
            editorComponent.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    System.out.println("Editor resized: " + key);
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                    System.out.println("Editor moved: " + key);
                }
            });
        }
    }

    public static TestTracker getInstance() {
        return new TestTracker();
    }

}
