package org.lisasp.alphatiming.lenex;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public final class LenexReader {

    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
    private static final Pattern SWIM_TIME = Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{2}");

    private LenexReader() {
    }

    public static LenexMeet read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    /**
     * Reads a {@code .lef} file, or a {@code .lxf} file: a zip holding one {@code .lef}. Entry names are decoded as ISO-8859-1,
     * which accepts any byte: zips without the UTF-8 flag name their entries in the creator's code page.
     */
    public static LenexMeet read(InputStream in) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        buffered.mark(ZIP_SIGNATURE.length);
        byte[] start = buffered.readNBytes(ZIP_SIGNATURE.length);
        buffered.reset();
        if (!Arrays.equals(start, ZIP_SIGNATURE)) {
            return readXml(buffered);
        }
        ZipInputStream zip = new ZipInputStream(buffered, StandardCharsets.ISO_8859_1);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".lef")) {
                return readXml(zip);
            }
        }
        throw new InvalidLenexException("The zipped file holds no .lef file");
    }

    private static LenexMeet readXml(InputStream in) throws IOException {
        Element meet = child(parse(in).getDocumentElement(), "MEETS").flatMap(meets -> child(meets, "MEET"))
                                                                    .orElseThrow(() -> new InvalidLenexException("The file holds no meet"));
        Map<String, List<LenexResult>> resultsByHeat = resultsByHeat(meet);
        List<LenexEvent> events = new ArrayList<>();
        for (Element session : children(meet, "SESSIONS", "SESSION")) {
            String course = attribute(session, "course").isEmpty() ? attribute(meet, "course") : attribute(session, "course");
            for (Element event : children(session, "EVENTS", "EVENT")) {
                event(event, course, resultsByHeat).ifPresent(events::add);
            }
        }
        return new LenexMeet(events);
    }

    private static Optional<LenexEvent> event(Element event, String course, Map<String, List<LenexResult>> resultsByHeat) {
        Optional<Element> style = child(event, "SWIMSTYLE");
        OptionalInt number = number(event, "number");
        OptionalInt distance = style.map(s -> number(s, "distance")).orElse(OptionalInt.empty());
        if (number.isEmpty() || distance.isEmpty()) {
            log.warn("Skipped event {} without a number or distance", attribute(event, "eventid"));
            return Optional.empty();
        }
        List<LenexHeat> heats = new ArrayList<>();
        for (Element heat : children(event, "HEATS", "HEAT")) {
            OptionalInt heatNumber = number(heat, "number");
            if (heatNumber.isEmpty()) {
                log.warn("Skipped heat {} of event {} without a number", attribute(heat, "heatid"), number.getAsInt());
                continue;
            }
            List<LenexResult> results = resultsByHeat.getOrDefault(attribute(heat, "heatid"), List.of());
            heats.add(new LenexHeat(heatNumber.getAsInt(), attribute(heat, "status"), results));
        }
        int relayCount = number(style.get(), "relaycount").orElse(1);
        return Optional.of(new LenexEvent(number.getAsInt(), attribute(event, "round"), course, distance.getAsInt(), relayCount, heats));
    }

    /**
     * By heat id alone: it is unique within a meet, while the event id of a result may name the event before the one that
     * holds its heat (extra heats and finals refer to their prelims through {@code preveventid}).
     */
    private static Map<String, List<LenexResult>> resultsByHeat(Element meet) {
        Map<String, List<LenexResult>> results = new HashMap<>();
        var elements = meet.getElementsByTagName("RESULT");
        for (int i = 0; i < elements.getLength(); i++) {
            Element result = (Element) elements.item(i);
            OptionalInt lane = number(result, "lane");
            if (attribute(result, "heatid").isEmpty() || lane.isEmpty()) {
                continue;
            }
            List<LenexSplit> splits = new ArrayList<>();
            for (Element split : children(result, "SPLITS", "SPLIT")) {
                OptionalInt distance = number(split, "distance");
                OptionalInt time = swimTime(attribute(split, "swimtime"));
                if (distance.isPresent() && time.isPresent()) {
                    splits.add(new LenexSplit(distance.getAsInt(), time.getAsInt()));
                }
            }
            results.computeIfAbsent(attribute(result, "heatid"), k -> new ArrayList<>())
                   .add(new LenexResult(lane.getAsInt(), swimTime(attribute(result, "swimtime")),
                                        attribute(result, "status"), splits));
        }
        return results;
    }

    /** A swim time is {@code HH:MM:SS.hh}; {@code NT} and a zero time mean that there is none. */
    private static OptionalInt swimTime(String value) {
        if (!SWIM_TIME.matcher(value).matches()) {
            return OptionalInt.empty();
        }
        String[] parts = value.split("[:.]");
        int millis = ((Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1])) * 60 + Integer.parseInt(parts[2])) * 1000
                + Integer.parseInt(parts[3]) * 10;
        return millis > 0 ? OptionalInt.of(millis) : OptionalInt.empty();
    }

    private static OptionalInt number(Element element, String name) {
        try {
            return OptionalInt.of(Integer.parseInt(attribute(element, name)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
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
            throw new InvalidLenexException("The file is not well-formed XML", e);
        }
    }

    private static String attribute(Element element, String name) {
        return element.getAttribute(name);
    }

    private static Optional<Element> child(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private static List<Element> children(Element parent, String collection, String name) {
        List<Element> elements = new ArrayList<>();
        child(parent, collection).ifPresent(c -> {
            for (Node node = c.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element && element.getTagName().equals(name)) {
                    elements.add(element);
                }
            }
        });
        return elements;
    }
}
