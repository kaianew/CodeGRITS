package trackers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Timer;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TestTracker {
    private static final Logger LOG = Logger.getInstance(TestTracker.class);
    private final Set<EditorWindow> knownWindows = ConcurrentHashMap.newKeySet();
    private final Timer debounceTimer = new Timer("EditorDebounceTimer", true);
    private final Map<String, TimerTask> resizeDebounceTasks = new HashMap<>();
    private TimerTask pendingDiffCheck = null;
    private TimerTask pendingOpenLog = null;
    private TimerTask pendingCloseLog = null;

    public TestTracker() {
        install();
    }

    private void install() {

        ApplicationManager.getApplication().getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                debounceFileEvent(file.getPath(), "opened", () -> {
                    for (FileEditor editor : source.getEditors(file)) {
                        // When file is opened, attach listener to the editor that it came from
                        // Are we attaching multiple listeners? let's see in the method below
                        // Verdict: you can be
                        attachResizeMoveListener(editor, file.getName());
                    }
                    // Also schedule a check
                    scheduleWindowDiffCheck(source.getProject());
                });
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                debounceFileEvent(file.getPath(), "closed", () -> {
                    scheduleWindowDiffCheck(source.getProject());
                });
            }
        });

     //   checkEditorWindowDiff(project);
    }

    private void debounceFileEvent(String path, String action, Runnable taskLogic) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("Editor " + action + ": " + path);
                SwingUtilities.invokeLater(taskLogic);
            }
        };

        if ("opened".equals(action)) {
            if (pendingOpenLog != null) pendingOpenLog.cancel();
            pendingOpenLog = task;
        } else {
            if (pendingCloseLog != null) pendingCloseLog.cancel();
            pendingCloseLog = task;
        }

        debounceTimer.schedule(task, 200);
    }

    private void attachResizeMoveListener(FileEditor editor, String key) {
        // This might trigger more than once for the same editor.
        if (editor instanceof TextEditor) {
            JComponent component = ((TextEditor) editor).getComponent();
            component.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    debounceResizeEvent(key, "resized");
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                    debounceResizeEvent(key, "moved");
                }
            });
        }
    }

    private synchronized void debounceResizeEvent(String key, String eventType) {
        if (resizeDebounceTasks.containsKey(key)) {
            resizeDebounceTasks.get(key).cancel();
        }

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
               System.out.println("Editor " + eventType + ": " + key);
                resizeDebounceTasks.remove(key);
            }
        };

        resizeDebounceTasks.put(key, task);
        debounceTimer.schedule(task, 200); // 200ms debounce delay
    }

    private synchronized void scheduleWindowDiffCheck(Project project) {
        if (pendingDiffCheck != null) {
            pendingDiffCheck.cancel();
        }

        pendingDiffCheck = new TimerTask() {
            @Override
            public void run() {
                // Invoke later and timer task
                SwingUtilities.invokeLater(() -> updateKnownEditorWindows(project));
            }
        };

        debounceTimer.schedule(pendingDiffCheck, 300); // 300ms debounce delay
    }

    private void updateKnownEditorWindows(Project project) {
        FileEditorManagerImpl manager = (FileEditorManagerImpl) FileEditorManagerEx.getInstanceEx(project);
        Set<EditorWindow> currentWindows = new HashSet<>(Set.of(manager.getWindows()));

        boolean changed = false;

        for (EditorWindow window : currentWindows) {
            if (knownWindows.add(window)) {
                System.out.println("Tracking new editor window (split or new tab): " + window);
                changed = true;
            }
        }

        Iterator<EditorWindow> iter = knownWindows.iterator();
        while (iter.hasNext()) {
            EditorWindow window = iter.next();
            if (!currentWindows.contains(window)) {
                System.out.println("Editor window closed or removed: " + window);
                iter.remove();
                changed = true;
            }
        }

        if (!changed) {
            System.out.println("Editor windows unchanged.");
        }
    }



    public static TestTracker getInstance() {
        return new TestTracker();
    }
}
