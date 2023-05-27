package org.sahli.asciidoc.confluence.publisher.converter;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;


public class ImageSizeCloseTo extends TypeSafeMatcher<String> {
    private final int delta;
    private final AsciidocImageDimensions exptectedDimensions;

    public ImageSizeCloseTo(String expectedImageTag, int delta) {
        this.exptectedDimensions = new AsciidocImageDimensions(expectedImageTag);
        this.delta = delta;
    }

    @Override
    public boolean matchesSafely(String imageTag) {
        AsciidocImageDimensions actualDimensions = new AsciidocImageDimensions(imageTag);
        return this.exptectedDimensions.hasDimensionsWithin(actualDimensions, delta);
    }

    @Override
    public void describeMismatchSafely(String actualImageTag, Description mismatchDescription) {
        AsciidocImageDimensions actualDimensions = new AsciidocImageDimensions(actualImageTag);
        mismatchDescription.appendText(actualDimensions.toString())
                .appendText(" deviates too much from expected: ")
                .appendText(exptectedDimensions.toString());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("the expected image dimensions of ")
                .appendText(exptectedDimensions.toString())
                .appendText(" may only be deviated by ")
                .appendValue(delta);
    }

    public static Matcher<String> hasImageSizeCloseTo(String expectedImageTag, int error) {
        return new ImageSizeCloseTo(expectedImageTag, error);
    }

    private static class AsciidocImageDimensions {

        private final int width;
        private final int height;

        public AsciidocImageDimensions(String imageTag) {
            Document doc = convertStringToXMLDocument(imageTag);
            Node acImageTag = doc.getFirstChild();

            if(!"ac:image".equalsIgnoreCase(acImageTag.getNodeName())) {
                throw new IllegalArgumentException("Provided ASCIIDOC image tag is invalid: " + imageTag);
            }

            NamedNodeMap acImageAttrs = acImageTag.getAttributes();
            Node acWidth = acImageAttrs.getNamedItem("ac:width");
            Node acHeight = acImageAttrs.getNamedItem("ac:height");

            this.width = Integer.parseInt(acWidth.getNodeValue());
            this.height = Integer.parseInt(acHeight.getNodeValue());
        }

        private Document convertStringToXMLDocument(String xmlString) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                return builder.parse(new InputSource(new StringReader(xmlString)));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public boolean hasDimensionsWithin(AsciidocImageDimensions actualDimensions, int delta) {
            return Math.abs(actualDimensions.width - this.width) <= delta
                    && Math.abs(actualDimensions.height - this.height) <= delta;
        }

        @Override
        public String toString() {
            return "(w:" + this.width + ",h:" + this.height + ")";
        }

    }
}
