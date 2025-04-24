package trackers;
import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.codeInsight.daemon.impl.EditorTrackerListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import entity.AOIBounds;
import entity.EyeEnum;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import entity.XMLDocumentHandler;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import trackers.TrackerInfo.IDETrackerInfo;
import utils.RelativePathGetter;
import utils.XMLWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class is the eye tracker.
 */
public class EyeTracker implements Disposable {
    private IDETrackerInfo info;

    /**
     * This variable indicates the sample frequency of the eye tracker.
     */
    double sampleFrequency;
    PsiDocumentManager psiDocumentManager;
    public Editor editor;
    /**
     * This variable is the XML document for storing the eye tracking data.
     */
    private final XMLDocumentHandler xmldoc = new XMLDocumentHandler("eye_tracking");
    private final List<String> ELEMENTS = List.of("setting", "gazes");
    /**
     * This variable indicates whether the tracking is started.
     */
    double screenWidth, screenHeight;
    String projectPath = "", filePath = "";
    PsiElement lastElement = null;
    Rectangle visibleArea = null;
    Process pythonProcess;
    Thread pythonOutputThread;
    String pythonInterpreter = "";
    String pythonScriptTobii;
    String pythonScriptMouse;
    int deviceIndex = 0;
    // This enum determines which eye is dominant, which affects the x,y calculation
    EyeEnum dominantEye;

    private static final Logger LOG = Logger.getInstance(EyeTracker.class);

    /**
     * This variable is the handler for eye tracking data.
     */
    private Consumer<Element> eyeTrackerDataHandler;

    /**
     * This is the default constructor.
     */
    public EyeTracker(IDETrackerInfo info) throws ParserConfigurationException {
        this.info = info;
        for(String element : ELEMENTS) {
            xmldoc.initializeElementAtRoot(element);
        }


        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        screenWidth = size.getWidth();
        screenHeight = size.getHeight();

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                // FIXME: if you want to add split screen ability, here would be the place
                LOG.info("file was opened.");
                editor = source.getSelectedTextEditor();
                if (editor != null) {
                    editor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
                }
                filePath = file.getPath();
                visibleArea = editor.getScrollingModel().getVisibleArea();
            }

            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                editor = event.getManager().getSelectedTextEditor() != null ? event.getManager().getSelectedTextEditor() : editor;
                if (event.getNewFile() != null) {
                    if (editor != null) {
                        editor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
                    }
                    filePath = event.getNewFile().getPath();
                    visibleArea = editor.getScrollingModel().getVisibleArea();
                }
            }
        });
    }

    /**
     * This is the constructor for the eye tracker.
     *
     * @param pythonInterpreter The path of the Python interpreter.
     * @param sampleFrequency   The sample frequency of the eye tracker.
     * @param isUsingMouse      Whether the mouse is used as the eye tracker.
     */
//    public EyeTracker(String pythonInterpreter, double sampleFrequency, boolean isUsingMouse) throws ParserConfigurationException {
//        // designed specifically for the real-time data API
//        this(); // call default constructor
//        if (isUsingMouse) {
//            deviceIndex = 0;
//        } else {
//            deviceIndex = 1;
//        }
//        this.pythonInterpreter = pythonInterpreter;
//        this.sampleFrequency = sampleFrequency;
//        setPythonScriptMouse();
//        setPythonScriptTobii();
//    }

    /**
     * The listener for the visible area used for filtering the eye tracking data.
     */
    VisibleAreaListener visibleAreaListener = e -> visibleArea = e.getNewRectangle();

    /**
     * This method starts the eye tracking.
     *
     * @param project The project.
     * @throws IOException The exception.
     */
    public void startTracking(Project project) throws IOException {
        info.startTracking();
        psiDocumentManager = PsiDocumentManager.getInstance(project);
        editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
            editor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
            visibleArea = editor.getScrollingModel().getVisibleArea();
        }
        VirtualFile[] virtualFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (virtualFiles.length > 0) {
            filePath = virtualFiles[0].getPath();
        }
        Element setting = xmldoc.getParentElement("setting");
        if (deviceIndex == 0) {
            setting.setAttribute("eye_tracker", "Mouse");
        } else {
            setting.setAttribute("eye_tracker", "Tobii Pro Fusion");
        }
        setting.setAttribute("sample_frequency", String.valueOf(sampleFrequency));
        // Records the dominant eye in the eye_tracking.xml file.
        setting.setAttribute("dominant_eye", dominantEye.toString());
        track();
    }

    /**
     * This method stops the eye tracking.
     *
     * @throws TransformerException The exception.
     */
    public void stopTracking() throws TransformerException {
        info.stopTracking(); // CLG FIXME: I don't love this indirection but haven't come up with a better way to do it
        pythonOutputThread.interrupt();
        pythonProcess.destroy();
        XMLWriter.writeToXML(xmldoc.getDocument(), info.dataOutputPath + "/eye_tracking.xml");
    }

    /**
     * This method pauses the eye tracking. The {@code isTracking} variable will be set to {@code false}.
     */
    public void pauseTracking() {
        info.stopTracking();
    }

    /**
     * This method resumes the eye tracking. The {@code isTracking} variable will be set to {@code true}.
     */
    public void resumeTracking() {
        info.startTracking();
    }

    private record EyeGazePoint(int eyeX, int eyeY) { } ;


    private EyeGazePoint createPointFromMessage(String message, Element gaze) {
        String leftInfo = message.split("; ")[1];
        String leftGazePointX = leftInfo.split(", ")[0];
        String leftGazePointY = leftInfo.split(", ")[1];

        String rightInfo = message.split("; ")[2];
        String rightGazePointX = rightInfo.split(", ")[0];
        String rightGazePointY = rightInfo.split(", ")[1];

        if (leftGazePointX.equals("nan") || leftGazePointY.equals("nan") || rightGazePointX.equals("nan") || rightGazePointY.equals("nan")) {
            return null;
        }
        int eyeX;
        // Kaia 01_28_25: Use the X value from the dominant eye and the average of the Y values.
        switch(dominantEye) {
            case LEFT:
                eyeX = (int) (Double.parseDouble(leftGazePointX) * screenWidth);
                break;
            case RIGHT:
                eyeX = (int) (Double.parseDouble(rightGazePointX) * screenWidth);
                break;
            default:
                eyeX = 0;
                return null;
        }
        int eyeY = (int) ((Double.parseDouble(leftGazePointY) + Double.parseDouble(rightGazePointY)) / 2 * screenHeight);
        return new EyeGazePoint(eyeX, eyeY);
    }

    private boolean inBounds(AOIBounds bounds, EyeGazePoint gazePoint) {
        return bounds.x() <= gazePoint.eyeX && gazePoint.eyeX <= (bounds.x() + bounds.width()) &&
                bounds.y() <= gazePoint.eyeY && gazePoint.eyeY <= (bounds.y() + bounds.height());
    }
    /**
     * This method processes the raw data message from the eye tracker. It will filter the data, map the data to the specific source code element, and perform the upward traversal in the AST.
     *
     * @param message The raw data.
     */
    public void processRawData(String message) {
        if (!info.isTracking()) return;
        Element gaze = getRawGazeElement(message);
        EyeGazePoint gazePoint = createPointFromMessage(message, gaze);
        if(gazePoint == null) { // CLG note: Java is smart enough that this null check means it won't
                                // complain that gazePoint might be null after this point.
            gaze.setAttribute("remark", "Fail | Invalid Gaze Point");
            return;
        }

        // First, check to see if in the SearchEverywhere popup, which will overlay everything if it exists
        AOIBounds popup = info.AOIMap.get("SearchEverywhere");
        if ((popup != null) && inBounds(popup, gazePoint)) {
            gaze.setAttribute("AOI", "SearchEverywhere");
            return;
        }

        try {
            Point editorLocation = editor.getContentComponent().getLocationOnScreen();
            int relativeX = gazePoint.eyeX - editorLocation.x;
            int relativeY = gazePoint.eyeY - editorLocation.y;
            if ((relativeX - visibleArea.x) >= 0 && (relativeY - visibleArea.y) >= 0
                    && (relativeX - visibleArea.x) <= visibleArea.width && (relativeY - visibleArea.y) <= visibleArea.height) {
                // FIXME KAIA: claire pleads that you check this; I inverted the if condition
                // to make it so that this SHOULD be true if the AOI IS the editor
                // (previously it was a check if the AOI was NOT the editor).
                // I'd be happier if there were a way to use the "inBounds" helper function I made, above
                // but the types don't match up and I'm a little bit too lazy to figure out how to make them match up.
                gaze.setAttribute("AOI", "Editor");
                Point relativePoint = new Point(relativeX, relativeY);

                EventQueue.invokeLater(new Thread(() -> {
                    PsiFile psiFile = psiDocumentManager.getPsiFile(editor.getDocument());
                    LogicalPosition logicalPosition = editor.xyToLogicalPosition(relativePoint);
                    if (psiFile != null) {
                        int offset = editor.logicalPositionToOffset(logicalPosition);
                        PsiElement psiElement = psiFile.findElementAt(offset);
                        Element location = xmldoc.createElementAtRoot("location");
                        location.setAttribute("x", String.valueOf(gazePoint.eyeX));
                        location.setAttribute("y", String.valueOf(gazePoint.eyeY));
                        location.setAttribute("line", String.valueOf(logicalPosition.line));
                        location.setAttribute("column", String.valueOf(logicalPosition.column));
                        location.setAttribute("path", RelativePathGetter.getRelativePath(filePath, projectPath));
                        gaze.appendChild(location);
                        Element aSTStructure = getASTStructureElement(psiElement);
                        gaze.appendChild(aSTStructure);
                        lastElement = psiElement;
//                System.out.println(gaze.getAttribute("timestamp") + " " + System.currentTimeMillis());
                        handleElement(gaze);
                    }
                }));
                return;
            }
        } catch (IllegalComponentStateException | NullPointerException e) {
            gaze.setAttribute("remark", "Fail | No Editor");
        }
        // the use of exception handling as control flow is kind of a bad practice
        // but CLG is struggling to come up with a better way to do it.
        // anyhoozles.  Execution would get here for one of two reasons: either the catch block immediately above
        // these comments triggered, because there is no editor
        // OR we didn't return on line 319, where we would have if the relative gaze is within the
        // active visible area. In that case, check the AOI map in case they're looking somewhere else.

        // CLG can't help herself, demonstrating some fun with Streams...
        info.AOIMap.entrySet().stream()
                .filter(e -> inBounds(e.getValue(), gazePoint))
                .findFirst()
                .ifPresentOrElse(
                        e -> gaze.setAttribute("AOI", e.getKey()),
                        () -> gaze.setAttribute("AOI", "OOB")
                );
    }

    /**
     * This method builds the Python process and redirects the output to the {@code pythonOutputThread} to process.
     */
    public void track() {
        try {
            ProcessBuilder processBuilder;
            if (deviceIndex == 0) {
                processBuilder = new ProcessBuilder(pythonInterpreter, "-c", pythonScriptMouse);
            } else {
                processBuilder = new ProcessBuilder(pythonInterpreter, "-c", pythonScriptTobii);
            }
            processBuilder.redirectErrorStream(true);
            pythonProcess = processBuilder.start();

            pythonOutputThread = new Thread(() -> {
                try (InputStream inputStream = pythonProcess.getInputStream();
                     InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                     BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        processRawData(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            pythonOutputThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
    }

    /**
     * This method gets the raw gaze xml element from the raw gaze data.
     *
     * @param message The raw gaze data.
     * @return The raw gaze element.
     */
    public Element getRawGazeElement(String message) {
        String timestamp = message.split("; ")[0];

        String leftInfo = message.split("; ")[1];
        String leftGazePointX = leftInfo.split(", ")[0];
        String leftGazePointY = leftInfo.split(", ")[1];
        String leftGazeValidity = leftInfo.split(", ")[2];
        String leftPupilDiameter = leftInfo.split(", ")[3];
        String leftPupilValidity = leftInfo.split(", ")[4];

        String rightInfo = message.split("; ")[2];
        String rightGazePointX = rightInfo.split(", ")[0];
        String rightGazePointY = rightInfo.split(", ")[1];
        String rightGazeValidity = rightInfo.split(", ")[2];
        String rightPupilDiameter = rightInfo.split(", ")[3];
        String rightPupilValidity = rightInfo.split(", ")[4];
        String leftTrackbox = rightInfo.split(", ")[5];
        String rightTrackbox = rightInfo.split(", ")[6];

        Element rawGaze = xmldoc.createElementAtNamedParent("gaze", "gazes");
        Element leftEye = xmldoc.createElementAtRoot("left_eye");
        Element rightEye = xmldoc.createElementAtRoot("right_eye");

        rawGaze.appendChild(leftEye);
        rawGaze.appendChild(rightEye);

        rawGaze.setAttribute("timestamp", timestamp);

        leftEye.setAttribute("gaze_point_x", leftGazePointX);
        leftEye.setAttribute("gaze_point_y", leftGazePointY);
        leftEye.setAttribute("gaze_validity", leftGazeValidity);
        leftEye.setAttribute("pupil_diameter", leftPupilDiameter);
        leftEye.setAttribute("pupil_validity", leftPupilValidity);
        leftEye.setAttribute("gaze_point_z", leftTrackbox);

        rightEye.setAttribute("gaze_point_x", rightGazePointX);
        rightEye.setAttribute("gaze_point_y", rightGazePointY);
        rightEye.setAttribute("gaze_validity", rightGazeValidity);
        rightEye.setAttribute("pupil_diameter", rightPupilDiameter);
        rightEye.setAttribute("pupil_validity", rightPupilValidity);
        rightEye.setAttribute("gaze_point_z", rightTrackbox);

        return rawGaze;
    }

    /**
     * This method gets the AST structure element from the PSI element. It performs the upward traversal in the AST.
     *
     * @param psiElement The PSI element.
     * @return The AST structure element.
     */
    public Element getASTStructureElement(PsiElement psiElement) {
        String token = "", type = "";
        Element aSTStructure = xmldoc.createElementAtRoot("ast_structure");
        if (psiElement != null && psiElement.getTextLength() > 0) {
            token = psiElement.getText();
            type = psiElement.getNode().getElementType().toString();
        }
        aSTStructure.setAttribute("token", token);
        aSTStructure.setAttribute("type", type);
        if (psiElement != null && psiElement.equals(lastElement)) {
            aSTStructure.setAttribute("remark", "Same (Last Successful AST)");
            return aSTStructure;
        }
        PsiElement parent = psiElement;
        while (parent != null) {
            if (parent instanceof PsiFile) {
                break;
            }
            Element level = xmldoc.createElementAtRoot("level");
            aSTStructure.appendChild(level);
            level.setAttribute("tag", String.valueOf(parent));
            LogicalPosition startLogicalPosition = editor.offsetToLogicalPosition(parent.getTextRange().getStartOffset());
            LogicalPosition endLogicalPosition = editor.offsetToLogicalPosition(parent.getTextRange().getEndOffset());
            level.setAttribute("start", startLogicalPosition.line + ":" + startLogicalPosition.column);
            level.setAttribute("end", endLogicalPosition.line + ":" + endLogicalPosition.column);
            parent = parent.getParent();
        }
        return aSTStructure;
    }

    /**
     * This method handles the element.
     *
     * @param element The element.
     */
    private void handleElement(Element element) {
        if (eyeTrackerDataHandler != null && IDETrackerInfo.isRealTimeDataTransmitting) {
            eyeTrackerDataHandler.accept(element);
        } else if (eyeTrackerDataHandler == null) {
//            throw new RuntimeException("eyeTrackerDataHandler is null");
        }
    }

    public void setIsRealTimeDataTransmitting(boolean b) {
        IDETrackerInfo.isRealTimeDataTransmitting = b;
    }

    public void setEyeTrackerDataHandler(Consumer<Element> eyeTrackerDataHandler) {
        this.eyeTrackerDataHandler = eyeTrackerDataHandler;
    }

    public void setPythonInterpreter(String pythonInterpreter) {
        this.pythonInterpreter = pythonInterpreter;
    }

    public void setSampleFrequency(double sampleFrequency) {
        this.sampleFrequency = sampleFrequency;
    }
    public void setDominantEye(EyeEnum dominantEye) {this.dominantEye = dominantEye;}

    /**
     * This method sets the Python script for the Tobii eye tracker.
     */
    public void setPythonScriptTobii() {
        pythonScriptTobii = "freq = " + sampleFrequency + "\n" + """
                import tobii_research as tr
                import time
                import sys
                import math
                            
                            
                def gaze_data_callback(gaze_data):
                    message = '{}; {}, {}, {}, {}, {}; {}, {}, {}, {}, {}, {}, {}'.format(
                        round(time.time() * 1000),
                        gaze_data['left_gaze_point_on_display_area'][0],
                        gaze_data['left_gaze_point_on_display_area'][1],
                        gaze_data['left_gaze_point_validity'],
                        gaze_data['left_pupil_diameter'],
                        gaze_data['left_pupil_validity'],
                        gaze_data['right_gaze_point_on_display_area'][0],
                        gaze_data['right_gaze_point_on_display_area'][1],
                        gaze_data['right_gaze_point_validity'],
                        gaze_data['right_pupil_diameter'],
                        gaze_data['right_pupil_validity'],
                        gaze_data['left_gaze_origin_in_trackbox_coordinate_system'][2],
                        gaze_data['right_gaze_origin_in_trackbox_coordinate_system'][2]
                    )
                    print(message)
                    sys.stdout.flush()
                            
                found_eyetrackers = tr.find_all_eyetrackers()
                my_eyetracker = found_eyetrackers[0]
                my_eyetracker.set_gaze_output_frequency(freq)
                my_eyetracker.subscribe_to(tr.EYETRACKER_GAZE_DATA, gaze_data_callback, as_dictionary=True)
                start_time = time.time()
                while time.time() - start_time <= math.inf:
                    continue
                """;
    }

    /**
     * This method sets the Python script for the mouse eye tracker.
     */
    public void setPythonScriptMouse() {
        pythonScriptMouse = "freq = " + sampleFrequency + "\n" + """
                import pyautogui
                from screeninfo import get_monitors
                import time
                import sys
                import math
                            
                width, height = get_monitors()[0].width, get_monitors()[0].height
                start_time = time.time()
                last_time = start_time
                            
                while time.time() - start_time <= math.inf:
                    current_time = time.time()
                    if current_time - last_time > 1 / freq:
                        message = f'{round(current_time * 1000)}; ' \\
                                  f'{pyautogui.position().x / width}, {pyautogui.position().y / height}, 1.0, 0, 0.0; ' \\
                                  f'{pyautogui.position().x / width}, {pyautogui.position().y / height}, 1.0, 0, 0.0'
                        print(message)
                        last_time = current_time
                        sys.stdout.flush()
                """;
    }

    /**
     * This method sets the device index.
     *
     * @param deviceIndex The device index.
     */
    public void setDeviceIndex(int deviceIndex) {
        this.deviceIndex = deviceIndex;
    }
}