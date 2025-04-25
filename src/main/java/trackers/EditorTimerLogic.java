package trackers;

import com.intellij.openapi.application.ApplicationManager;
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

public final class EditorTimerLogic {
    private final Set<EditorWindow> knownWindows = ConcurrentHashMap.newKeySet();
    private final Timer debounceTimer = new Timer("EditorDebounceTimer", true);
    private final Map<String, TimerTask> resizeDebounceTasks = new HashMap<>();
    private TimerTask pendingDiffCheck = null;

    public void attachResizeMoveListener(FileEditor editor, String key) {
        // This might trigger more than once for the same editor.
        if (editor instanceof TextEditor) {
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    JComponent component = editor.getComponent();
                    component.addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentResized(ComponentEvent e) {
                            debounceResizeEvent(key, "resized");
                        }
                    });
                }
            };
            // Schedule adding a listener, because otherwise it will trigger a bunch of resizes
            debounceTimer.schedule(task, 250);
        }
    }

    private synchronized void debounceResizeEvent(String key, String eventType) {
        // fixme: combine with IDEFileEditorManagerListenerGenerator
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

    public synchronized void scheduleWindowDiffCheck(Project project) {
        if (pendingDiffCheck != null) {
            pendingDiffCheck.cancel();
        }

        pendingDiffCheck = new TimerTask() {
            @Override
            public void run() {
                // this was called twice
                updateKnownEditorWindows(project);
            }
        };

        debounceTimer.schedule(pendingDiffCheck, 300); // 300ms debounce delay
    }

    private void updateKnownEditorWindows(Project project) {
        FileEditorManagerImpl manager = (FileEditorManagerImpl) FileEditorManagerEx.getInstanceEx(project);
        Set<EditorWindow> currentWindows = new HashSet<>(Set.of(manager.getWindows()));

        boolean changed = false;

        for (EditorWindow window : currentWindows) {
            // recursive??
            // like, assume two new windows are created
            // indeed, split screen case: old window is deleted, and then two windows are added
            // that should be fine because we'll delete from and then add to map. but it was an odd log
            if (knownWindows.add(window)) { // should be editormap.put ya
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
            System.out.println("Editor windows unchanged." + currentWindows);
            for (EditorWindow window : currentWindows) {
                System.out.println("Window: " + window + " Selected file: " + window.getSelectedFile());
            }
        }
    }



    public static EditorTimerLogic getInstance() {
        return new EditorTimerLogic();
    }
}
