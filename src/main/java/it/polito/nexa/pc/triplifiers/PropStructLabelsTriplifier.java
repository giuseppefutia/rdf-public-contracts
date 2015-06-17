package it.polito.nexa.pc.triplifiers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.RDFS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PropStructLabelsTriplifier implements JSONTriplifier {

    //private static String BASE_URI = "http://localhost/id/"; Use it for your local graph
    private static String BASE_URI = "http://public-contracts.nexacenter.org/id/";

    public List<Statement> triplifyJSON(String inputJSON) {

        List<Statement> results = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readValue(inputJSON, JsonNode.class);
            for (JsonNode record : rootNode) {
                Resource subject = ResourceFactory.createResource(BASE_URI +
                                                                "businessEntities/" +
                                                                cleanString(getValue("vatId", record)));

                Literal label = ResourceFactory.createLangLiteral(getValue("name", record),"it");
                Statement proposingStructureLabel = ResourceFactory.createStatement(subject,
                                                                                    RDFS.label,
                                                                                    label);
                results.add(proposingStructureLabel);

                Statement vatID = ResourceFactory.createStatement(
                        subject,
                        ResourceFactory.createProperty("http://purl.org/goodrelations/v1#", "vatID"),
                        ResourceFactory.createPlainLiteral(cleanString(getValue("vatId", record))));
                results.add(vatID);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    public String cleanString(String s) {
        s = s.replaceAll("´", "'")
                .replaceAll("’", "")
                .replaceAll("'", "")
                .replaceAll("[“”]", "\"")
                .replaceAll("\"", "")
                .replaceAll("–", "-")
                .replaceAll("\t{2,}", "\t")
                .replaceAll(":", "")
                .replaceAll("°", "")
                .replaceAll("\\?", "")
                .replaceAll("[()]", "")
                .replaceAll("-", "")
                .replaceAll("\\.", "_")
                .replaceAll("\\[", "")
                .replaceAll("\\]","")
                .replaceAll(",", "")
                .replace(" ", "_")
                .replace("/", "_")
                .replaceAll("__", "_")
                .toLowerCase();
        return s;
    }

    private String getValue (String string, JsonNode record) {
        return record.get(string) != null ? record.get(string).asText() : "";
    }


}
