package org.lisasp.alphatiming.lenex;

import org.lisasp.alphatiming.lenex.api.XmlNotWritableException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** The form in which a recording keeps a version of a LENEX file. */
public final class LenexDocuments {

    private LenexDocuments() {
    }

    /**
     * The file ({@code .lef} or {@code .lxf}) as XML in one line: written again without the line breaks and indentation
     * between its elements. LENEX keeps its data in attributes, so nothing of it is lost.
     */
    public static byte[] oneLine(byte[] file) throws IOException {
        Document document = LenexReader.parse(LenexReader.unzipped(new ByteArrayInputStream(file)));
        dropFormatting(document.getDocumentElement());
        return write(document);
    }

    /** Whether two versions in one line differ at most in the time of their export ({@code LENEX created}). */
    public static boolean sameExceptExportTime(byte[] oneLine, byte[] otherOneLine) throws IOException {
        Element first = LenexReader.parse(new ByteArrayInputStream(oneLine)).getDocumentElement();
        Element second = LenexReader.parse(new ByteArrayInputStream(otherOneLine)).getDocumentElement();
        first.removeAttribute("created");
        second.removeAttribute("created");
        return first.isEqualNode(second);
    }

    private static void dropFormatting(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                element.removeChild(child);
            } else if (child instanceof Element nested) {
                dropFormatting(nested);
            }
            child = next;
        }
    }

    private static byte[] write(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(bytes));
            return bytes.toByteArray();
        } catch (TransformerException e) {
            throw new XmlNotWritableException(e);
        }
    }
}
