package it.polito.nexa.pc;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import it.polito.nexa.pc.importers.DefaultJSONImporter;
import it.polito.nexa.pc.triplifiers.PropStructLabelsTriplifier;
import it.polito.nexa.pc.triplifiers.PublicContractsTriplifier;
import it.polito.nexa.pc.triplifiers.SPCDataTriplifier;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by giuseppe on 19/05/15.
 */
public class TriplesGenerator {

    public static void main(String[] args) throws FileNotFoundException {

        DefaultJSONImporter dji = new DefaultJSONImporter();

        // Generate labels of proposing structures
        String psJson = dji.getJSON("src/main/resources/proposingStructures.json", "FILE");
        PropStructLabelsTriplifier pslt = new PropStructLabelsTriplifier();
        RDFforProposingStructureLabels(pslt, psJson, createBaseModel(), "output/proposing-structures-labels.nt");

        // Generate labels of general business entities
        String beJson = dji.getJSON("src/main/resources/businessEntities.json", "FILE");
        PropStructLabelsTriplifier belt = new PropStructLabelsTriplifier();
        RDFforProposingStructureLabels(belt, beJson, createBaseModel(), "output/business-entities-labels.nt");

        // Generate sameas with SPCData
        SPCDataTriplifier st = new SPCDataTriplifier();
        RDFforSameas(st, psJson, createBaseModel());

        // Generate public contracts data
        PublicContractsTriplifier pcTriplifier = new PublicContractsTriplifier();
        File dir = new File("../../download");
        Collection files = FileUtils.listFiles(dir, new RegexFileFilter("([^\\s]+(\\.(?i)(json))$)"), DirectoryFileFilter.DIRECTORY);
        System.out.println(files.size());
        Iterator itr = files.iterator();

        long startTime = System.currentTimeMillis();
        long endTime = 0;

        int processedFiles = 0;

        Model pcModel = createBaseModel();

        while (itr.hasNext()) {
            String value = itr.next().toString();
            Path path = Paths.get(value);
            String fileName = path.getFileName().toString();
            if(!fileName.equals("stats.json") && !fileName.equals("proposingStructure.json")
                                              && !fileName.equals("downloadStats.json")
                                              && !fileName.contains("_index")){
                String pcJson = dji.getJSON(value, "FILE");
                List<Statement> pcStatements = pcTriplifier.triplifyJSON(pcJson);
                pcModel.add(pcStatements);
                processedFiles += 1;
                if (processedFiles %100 == 0) {
                    System.out.println("Processed " + processedFiles +" files");
                }
                if (processedFiles %20000 == 0) {
                    System.out.println("Publish RDF!");
                    publishRDF("output/rdf_" + processedFiles + ".nt", pcModel);
                    pcModel = ModelFactory.createDefaultModel();
                }
            }
        }
        publishRDF("output/rdf.nt", pcModel);
        endTime = System.currentTimeMillis();
        System.out.println("Time in minutes: "+ ((endTime-startTime)/1000)/60);

        /* Generate test data
        String testJson = dji.getJSON("src/main/resources/894c5d3ee240fd5c8163e1fceec4c92124fea5fe.json", "FILE");
        PublicContractsTriplifier pctest = new PublicContractsTriplifier();
        RDFforTestingData(pctest, testJson, createBaseModel());*/
    }

    private static Model createBaseModel(){
        Model result = ModelFactory.createDefaultModel();
        Map<String, String> prefixMap = new HashMap<String, String>();

        prefixMap.put("rdfs", RDFS.getURI());
        prefixMap.put("geo", "http://www.w3.org/2003/01/geo/wgs84_pos#");
        prefixMap.put("schema", "http://schema.org/");
        prefixMap.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        prefixMap.put("gn", "http://www.geonames.org/ontology#");
        prefixMap.put("rdf", RDF.getURI());
        prefixMap.put("dcterms", DCTerms.getURI());

        result.setNsPrefixes(prefixMap);

        return result;
    }

    private static void publishRDF(String filePath, Model model) throws FileNotFoundException {
        File file = new File(filePath.replaceAll("(.+)/[^/]+", "$1"));
        file.mkdirs();
        OutputStream outTurtle = new FileOutputStream(new File(filePath));
        RDFDataMgr.write(outTurtle, model, RDFFormat.NTRIPLES);
    }

    private static void RDFforProposingStructureLabels(PropStructLabelsTriplifier pslt, String inputJson, Model model, String output) throws FileNotFoundException {
        System.out.println("Generate data for businessEntities...");
        List<Statement> statements = pslt.triplifyJSON(inputJson);
        model.add(statements);
        publishRDF(output, model);
    }

    private static void RDFforTestingData(PublicContractsTriplifier pct, String inputJson, Model model) throws FileNotFoundException {
        List<Statement> statements = pct.triplifyJSON(inputJson);
        model.add(statements);
        publishRDF("output/test.nt", model);
    }

    private static void RDFforSameas(SPCDataTriplifier st, String inputJson, Model model) throws FileNotFoundException {
        System.out.println("Generate sameas data...");
        List<Statement> statements = st.triplifyJSON(inputJson);
        model.add(statements);
        publishRDF("output/sameas.nt", model);
    }
}
