package org.lisasp.alphatiming.legacy.api;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class LegacyXmlReader {

    private static final String HEATS = "AlphaServer.Heat-array";
    private static final String HEAT = "AlphaServer.Heat";
    private static final String LANE = "AlphaServer.Lane";
    private static final String LANE_STATUS = "AlphaServer.LaneStatus";

    private LegacyXmlReader() {
    }

    static Heat[] read(InputStream in) throws IOException {
        Element root = parse(in).getDocumentElement();
        requireName(root, HEATS);
        List<Heat> heats = new ArrayList<>();
        for (Element element : children(root)) {
            heats.add(heat(element));
        }
        return heats.toArray(Heat[]::new);
    }

    private static Document parse(InputStream in) throws IOException {
        DocumentBuilder builder;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The JDK's XML parser lacks a required feature", e);
        }
        builder.setErrorHandler(new DefaultHandler());
        try {
            return builder.parse(in);
        } catch (SAXException e) {
            throw new InvalidLegacyXmlException("The heats are not well-formed XML", e);
        }
    }

    private static Heat heat(Element element) throws InvalidLegacyXmlException {
        requireName(element, HEAT);
        if (!element.hasAttribute("id")) {
            throw new InvalidLegacyXmlException("A heat has no id");
        }
        Heat heat = new Heat(element.getAttribute("id"), intAttribute(element, "event"), intAttribute(element, "heat"));
        for (Element lanes : children(element, "lanes")) {
            for (Element entry : children(lanes)) {
                addLane(heat, entry);
            }
        }
        return heat;
    }

    private static void addLane(Heat heat, Element entry) throws InvalidLegacyXmlException {
        requireName(entry, "entry");
        List<Element> parts = children(entry);
        if (parts.size() != 2) {
            throw new InvalidLegacyXmlException("A lane entry of heat " + heat.computeKey() + " has " + parts.size() + " parts");
        }
        int key = parseInt(text(parts.get(0), "int"));
        Element element = parts.get(1);
        requireName(element, LANE);
        int index = intAttribute(element, "laneindex");
        if (index < 0 || index != key) {
            throw new InvalidLegacyXmlException("Heat " + heat.computeKey() + " stores lane " + index + " under " + key);
        }
        if (heat.getLanes().containsKey(index)) {
            throw new InvalidLegacyXmlException("Heat " + heat.computeKey() + " has lane " + index + " twice");
        }
        heat.getLanes().put(index, lane(index, element));
    }

    private static Lane lane(int index, Element element) throws InvalidLegacyXmlException {
        List<Element> times = new ArrayList<>();
        List<Element> stati = new ArrayList<>();
        for (Element list : children(element, "times")) {
            times.addAll(children(list));
        }
        for (Element list : children(element, "stati")) {
            stati.addAll(children(list));
        }
        if (times.size() != stati.size()) {
            throw new InvalidLegacyXmlException("Lane " + index + " has " + times.size() + " times but " + stati.size() + " stati");
        }
        Lane lane = new Lane(index);
        for (int i = 0; i < times.size(); i++) {
            lane.store(parseLong(text(times.get(i), "long")), status(text(stati.get(i), LANE_STATUS)));
        }
        return lane;
    }

    private static LaneStatus status(String name) throws InvalidLegacyXmlException {
        try {
            return LaneStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new InvalidLegacyXmlException("Unknown lane status " + name, e);
        }
    }

    private static int intAttribute(Element element, String name) throws InvalidLegacyXmlException {
        if (!element.hasAttribute(name)) {
            throw new InvalidLegacyXmlException(element.getTagName() + " has no " + name);
        }
        return parseInt(element.getAttribute(name));
    }

    private static int parseInt(String text) throws InvalidLegacyXmlException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidLegacyXmlException("Not a number: " + text, e);
        }
    }

    private static long parseLong(String text) throws InvalidLegacyXmlException {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidLegacyXmlException("Not a time: " + text, e);
        }
    }

    private static String text(Element element, String name) throws InvalidLegacyXmlException {
        requireName(element, name);
        return element.getTextContent();
    }

    private static void requireName(Element element, String name) throws InvalidLegacyXmlException {
        if (!element.getTagName().equals(name)) {
            throw new InvalidLegacyXmlException("Expected " + name + " but found " + element.getTagName());
        }
    }

    private static List<Element> children(Element parent, String name) {
        return children(parent).stream().filter(child -> child.getTagName().equals(name)).toList();
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) nodes.item(i));
            }
        }
        return result;
    }
}
