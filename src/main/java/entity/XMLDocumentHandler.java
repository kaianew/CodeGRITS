package entity;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import utils.RelativePathGetter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class XMLDocumentHandler {
    private final Document iDETracking = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    private final Element root;

    private final Map<String, Element> parentMap = new HashMap<>();
    public XMLDocumentHandler(String rootName) throws ParserConfigurationException {
        root = iDETracking.createElement(rootName);
        iDETracking.appendChild(root);
        parentMap.put(rootName,root);
    }

    public Document getDocument() { return iDETracking; }
    public void initializeElementAtRoot(String elementName) {
        Element element = iDETracking.createElement(elementName);
        root.appendChild(element);
        parentMap.put(elementName,element);
    }

    public Element createElementAtRoot(String elementName) {
        return iDETracking.createElement(elementName);
    }

    public Element createElementAtNamedParent(String elementName, String parentName) {
        Element element = iDETracking.createElement(elementName);
        Element parent = parentMap.get(parentName);
        parent.appendChild(element);
        return element;
    }
    public Element getParentElement(String elementName) {
        return parentMap.get(elementName);
    }


    public Element createElementTimestamp(String elementName, String parentName, Map<String,String> attributes) {
        Element element = createElementAtNamedParent(elementName, parentName);
        element.setAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
        // FIXME: consider iterating and only setting attribute if not "" or null or something
        attributes.forEach(element::setAttribute);
        return element;
    }

    /**
     * This method archives the file. If the file is a code file, the file is copied to the archive folder.
     * FIXME: paths.  Frankly, let's open the paths as files to minimize the fuckups possible with all these strings
     * @param path      The path of the file.
     * @param timestamp The timestamp of the file.
     * @param remark    The remark of the file.
     * @param text      The text of the file.
     */
    public void archiveFile(String dataOutputPath, String projectPath, String path, String timestamp, String remark, String text) {
        File srcFile = new File(path);
        File destFile = new File(dataOutputPath + "/archives/" + timestamp + ".archive");
        String[] codeExtensions = {".java", ".cpp", ".c", ".py", ".rb", ".js", ".md", ".cs", ".html", ".htm", ".css", ".php", ".ts", ".swift", ".go", ".kt", ".kts", ".rs", ".pl", ".sh", ".bat", ".ps1", ".asp", ".aspx", ".jsp", ".lua"};
        try {
            if (path.equals("unknown")) {
                FileUtils.writeStringToFile(destFile, text, "UTF-8", true);
            } else {
                if (Arrays.stream(codeExtensions).anyMatch(path::endsWith)) {
                    if (text == null) {
                        FileUtils.copyFile(srcFile, destFile);
                    } else {
                        FileUtils.writeStringToFile(destFile, text, "UTF-8", true);
                    }
                } else {
                    remark += " | NotCodeFile | Fail";
                }
            }
        } catch (IOException e) {
            remark += " | IOException | Fail";
        }

        Element archive = createElementAtNamedParent("archive", "archives");
        if (!path.equals("unknown")) {
            archive.setAttribute("id", "fileArchive");
        } else {
            archive.setAttribute("id", "consoleArchive");
        }
        archive.setAttribute("timestamp", timestamp);
        if (!path.equals("unknown")) {
            archive.setAttribute("path", RelativePathGetter.getRelativePath(path, projectPath));
            archive.setAttribute("remark", remark);
        }
    }
}
