/**
 * This class tests the documentation of punned entities (a resource that is
 * e.g. an owl:Class and a skos:Concept at the same time) by analyzing the
 * html generated output.
 */
package widoco;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.*;

public class PunningTest {

    private static final String ONT_NS = "http://example.com/testCase/";
    /** Text shared by the rdfs:comment and the skos:definition of the punned entity */
    private static final String PUNNED_DEFINITION = "A punned vehicle definition text.";
    private static final String REGULAR_COMMENT = "A regular car comment text.";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Configuration c;

    public PunningTest() {
        c = new Configuration();
        c.setOverwriteAll(true);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(c.getTmpFile());
    }

    private static void copyResourceToFile(String resourcePath, File targetFile) throws Exception {
        try (InputStream in = PunningTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Get all the entity divs documenting a specific iri
     */
    private static Elements getEntities(Document doc, String iri) {
        Elements entities = new Elements();
        for (Element element : doc.getElementsByAttributeValue("class", "entity")) {
            if (iri.equals(element.id())) {
                entities.add(element);
            }
        }
        return entities;
    }

    /**
     * Assert that the entity description contains the expected text exactly once
     */
    private static void assertSingleComment(Element entity, String expectedText) {
        String type = entity.select("h3 > sup").attr("title");
        Elements comments = entity.select("div.comment");
        assertEquals("Description of " + entity.id() + " (" + type + ") should be rendered exactly once",
                1, comments.size());
        assertEquals("Description mismatch for " + entity.id() + " (" + type + ")",
                expectedText, comments.first().text());
    }

    @Test
    public void testPunnedEntityDefinitionIsNotDuplicated() throws Exception {
        File punning = folder.newFile("punning.ttl");
        copyResourceToFile("punning/punning.ttl", punning);
        File docFolder = folder.newFolder("doc");

        c.setFromFile(true);
        c.setOntologyPath(punning.getAbsolutePath());
        c.setDocumentationURI(docFolder.getAbsolutePath());
        c.setIncludeAllSectionsInOneDocument(true);
        WidocoUtils.loadModelToDocument(c);
        CreateResources.generateDocumentation(c.getDocumentationURI(), c, c.getTmpFile());

        File indexEn = new File(docFolder, "index-en.html");
        Document indexDoc = Jsoup.parse(indexEn, "UTF-8");

        // The punned entity is documented once as a class and once as a named individual
        Elements vehicleEntities = getEntities(indexDoc, ONT_NS + "Vehicle");
        assertEquals("Punned entity should be documented as a class and as a named individual",
                2, vehicleEntities.size());
        assertEquals("Punned entity is not documented as a class",
                1, vehicleEntities.select("h3 > sup.type-c").size());
        assertEquals("Punned entity is not documented as a named individual",
                1, vehicleEntities.select("h3 > sup.type-ni").size());

        // rdfs:comment and skos:definition have the same value, it has to be rendered only once per entity
        for (Element entity : vehicleEntities) {
            assertSingleComment(entity, PUNNED_DEFINITION);
        }

        // Regular entities are not affected
        Elements carEntities = getEntities(indexDoc, ONT_NS + "Car");
        assertEquals("Regular entity should be documented once", 1, carEntities.size());
        assertSingleComment(carEntities.first(), REGULAR_COMMENT);
    }
}
