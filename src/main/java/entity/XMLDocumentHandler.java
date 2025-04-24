package entity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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

    public Element createElementAtNamedParent(String elementName, String parentName) {
        Element element = iDETracking.createElement(elementName);
        Element parent = parentMap.get(parentName);
        parent.appendChild(element);
        return element;
    }
    public Element getElement(String elementName) {
        return parentMap.get(elementName);
    }
}
