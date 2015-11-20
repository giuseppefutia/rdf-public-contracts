package it.polito.nexa.pc.triplifiers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.OWL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// This class creates "sameAs" triples with SPCData repository

public class SPCDataTriplifier implements JSONTriplifier {

    //private static String BASE_URI = "http://localhost/id/";
    private static String BASE_URI = "http://public-contracts.nexacenter.org/id/";

    public List<Statement> triplifyJSON(String inputJSON, String pathJSON) {
        List<Statement> results = new ArrayList<>();
        String SPCDATAEndpoint = "http://spcdata.digitpa.gov.it:8899/sparql";
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readValue(inputJSON, JsonNode.class);
            for (JsonNode record : rootNode) {
                String SPCDATAQuery = "select distinct ?entity " +  "where {?entity <http://www.w3.org/ns/org#identifier> '" + getValue("vatId", record) + "'}";
                Resource subject = ResourceFactory.createResource(BASE_URI + "businessEntities/" + cleanString(getValue("vatId", record)));
                results.addAll(semanticAlignment(subject, SPCDATAEndpoint, SPCDATAQuery));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    public List<Statement> semanticAlignment (Resource subject, String endpoint, String query) {
        List<Statement> results = new ArrayList<>();
        System.out.println(query);
        QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, query);
        ResultSet rs = qe.execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            if(qs.get("entity") != null) {
                Statement owlSameAs = ResourceFactory.createStatement(subject,
                        OWL.sameAs,
                        ResourceFactory.createResource(qs.get("entity").asNode().toString()));
                results.add(owlSameAs);
                System.out.println(owlSameAs);
            }else {
                System.out.println("SameAs not found");
            }
        }
        return results;
    }

    private String getValue (String string, JsonNode record) {
        return record.get(string) != null ? record.get(string).asText() : "";
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
}
