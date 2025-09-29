package entity;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;

import javax.swing.*;
import java.util.List;

public class NannyCam {
    public enum TrackboxEnum {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        BACK,
        FORWARD,
        OK;

        @Override
        public String toString() {
            switch (this) {
                case OK:
                    return null;
                case BACK:
                    return "back";
                case FORWARD:
                    return "forward";
                case RIGHT:
                    return "right";
                case LEFT:
                    return "left";
                case UP:
                    return "up";
                case DOWN:
                    return "down";
            }
            return null;
        }
    }

    public TrackboxEnum currState = TrackboxEnum.OK;

    public JBPopup activePopup;

    public void checkOnNanny(List<Double> leftXYZ, List<Double> rightXYZ) {
        // In here, we update/set the new trackbox state and invoke or devoke the nanny
    }

    public void nannyActivate(FileEditorManager source) {
        Project project = source.getProject();

        SwingUtilities.invokeLater(() -> {
            JBPopup popup = JBPopupFactory.getInstance()
                    .createMessage("Please lean or move " + currState.toString() + ".");

            // Show popup centered in IDE window
            JFrame frame = com.intellij.openapi.wm.WindowManager.getInstance().getFrame(project);
            if (frame != null) {
                popup.showInCenterOf(frame);
            }
        });
    }

    public void nannyDeactivate() {
        activePopup.dispose();
        activePopup = null;
        currState = TrackboxEnum.OK;
    }
}
