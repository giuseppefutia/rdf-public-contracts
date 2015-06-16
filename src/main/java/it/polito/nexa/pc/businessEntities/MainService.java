package it.polito.nexa.pc.businessEntities;

import it.polito.nexa.pc.importers.DefaultJSONImporter;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.stream.Collector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class MainService {
	private static HashMap<String, Company> companies = new HashMap<>(); 
	private HashMap <String, Object> result = new HashMap<>(); 
	private static String filesDirectory="download/"; 
	
	public static void main(String[] args) {
		
		// Read all json files in the download directory 
        long startTime = System.currentTimeMillis();
        long endTime = 0;
        System.out.println("Reading all files in directory..."); 
        File dir = new File(filesDirectory);
        Collection files = FileUtils.listFiles(dir, new RegexFileFilter("([^\\s]+(\\.(?i)(json))$)"), DirectoryFileFilter.DIRECTORY);
        System.out.println("Files found: "+files.size());
        Iterator itr = files.iterator();
        int processedFiles = 0;
        DefaultJSONImporter dji = new DefaultJSONImporter();
       
        //add all the companies found and relative labels to a list 
        while (itr.hasNext()) {
            String value = itr.next().toString();
            Path path = Paths.get(value);
            String fileName = path.getFileName().toString();
            if(!fileName.equals("businessEntities.json") &&!fileName.equals("stats.json") && !fileName.equals("proposingStructure.json") 
            		&& !fileName.equals("downloadInfo.json")&& !fileName.equals("downloadStats.json") && !fileName.contains("_index")){
                String pcJson = dji.getJSON(value, "FILE");
                companiesFromJson(pcJson); 
                processedFiles += 1;
                if (processedFiles %10000 == 0) {
                    System.out.println("Processed " + processedFiles +" files");
                }  
            }
        }
        endTime = System.currentTimeMillis();
        
        //remove from the list, all the public administrations present in proposingStructures.json
        removePA("download/proposingStructures.json");
        
        //
        LinkedList<HashMap<String, Object>> result =  companies.values()
        		.stream()
        		.collect(Collector.of(() -> new LinkedList<HashMap<String, Object>>(), (list, c) ->
	        		{
	                	Company company = (Company) c; 
	                	HashMap <String, Object> res = new HashMap<>(); 
	                	res.put("id", company.getId()); 
	                	if (company.hasOriginalId()) 
	                		res.put("originalId", company.getOriginalIds()); 
	                	res.put("name", company.getBestLabel()); 
	                	res.put("isItalian", company.isItalian());
	                	list.add(res);
	                 } ,  
                (j1, j2) ->
	                 { 
	                	 j1.addAll(j2); 
	                	 return j1; 
	                 }
	                 )
        				); 
       
        System.out.println(String.format("Found %d distinct business entities.", result.size())); 
        
        //write list of companies to a json file 
        ObjectMapper mapper =  new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true); 
       try {  
    	   mapper.writeValue(new File("download/businessEntities.json"), result); 
        
       } catch(Exception e ){
    	   System.out.println(e.getMessage()); 
       }		
        System.out.println("Time in minutes: "+ ((endTime-startTime)/1000)/60);       
	}
	
	
	private static String getValue (String string, JsonNode record) {
        return record.get(string) != null ? record.get(string).asText() : "";
	}
	
	/*
	 * this methos removes from the label list all the vatId which nelong to a public administration
	 * which are already present in the proposingStructure.json file 
	 */
		private static void removePA(String proposingStructuresFile){
		try {
			String id; 
			DefaultJSONImporter dji = new DefaultJSONImporter();
			String psJson = dji.getJSON(proposingStructuresFile, "FILE");
			ObjectMapper mapper = new ObjectMapper();
	        
            JsonNode rootNode = mapper.readValue(psJson, JsonNode.class);
            for (JsonNode pa : rootNode){
            	id= pa.get("vatId").textValue(); 
            	if (companies.containsKey(id)) {
            		companies.remove(id); 
            		//System.out.println("removed "+id); 
            	}
            }
		} catch (Exception e){
			System.out.println("Errore nella letutra delle strutture proponenti (PA)"); 
			System.out.println(e.getMessage()); 
		}
	}

	private static void   companiesFromJson(String inputJSON){
	    ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readValue(inputJSON, JsonNode.class);
            JsonNode data = rootNode.get("data").get("lotto");
            if (data !=null){
	            for (JsonNode record : data) {
	            	if(getValue("aggiudicatari",record) != null) {
	                    JsonNode winners = record.get("aggiudicatari");
	                    if (winners != null){ 
	                    	for (JsonNode winner:winners){
	                    		if(getValue("type", winner).equals("aggiudicatario")) {
	                    		addCompany(winner);               
	                    		}
	                    		if(getValue("type", winner).equals("aggiudicatarioRaggruppamento")) {
	                        		addGroup(winner);                         		
	                        	}     
	                    	}
	                    }
	            	}
	                if(getValue("partecipanti",record) != null) {
	                    JsonNode participants = record.get("partecipanti");
	                    if (participants != null){ 
	                    	for (JsonNode participant:participants){
	                    		if(getValue("type",  participant).equals("partecipante")) {
	                        		addCompany( participant); 
	                        	}
	                        		if(getValue("type",  participant).equals("raggruppamento")) {
	                            		addGroup( participant);                            		
	                            	}                   		
	                    	}                      
	                    }
	                }    	 
	            }
            }
        }catch (Exception e) {
        	e.printStackTrace();   
	        }         
	}
	
	private static void addCompany (JsonNode record){
		String id; 
		String name= getValue("ragioneSociale", record); 
		Boolean isItalian=false; 
		String originalId=""; 
		if(getValue("companyHash", record) != "") {
            id = getValue("companyHash", record);
            if(getValue("codiceFiscale", record) != "") {
            	originalId = getValue("codiceFiscale", record);
            	isItalian = true;
                }
            if(getValue("identificativoFiscaleEstero", record) != "") {
                originalId = getValue("identificativoFiscaleEstero", record);
                isItalian = false;
                }
        } 
		else {
        	if (getValue("codiceFiscale", record)!=""){
        		id = getValue("codiceFiscale", record);
        		isItalian=true; 
        		} 
        	else {
        		id = getValue("identificativoFiscaleEstero", record);
        		isItalian=false;
        		}
        }
		
		//add company to the list 
		if (originalId==""){
			if (companies.containsKey(id)) 
				companies.get(id).addName(name);
				else companies.put(id, new Company(id, name, isItalian)); 
		} 
		else{
			if (companies.containsKey(id)) {
				companies.get(id).addName(name);
				companies.get(id).addOriginalId(originalId);
				}
			else companies.put(id, new Company(id, name, isItalian, originalId));
		}
	}
	
	private static void addGroup(JsonNode record){
		JsonNode members ; 
		if (record.has("aggiudicatarioRaggruppamento")) 
			members =record.get("aggiudicatarioRaggruppamento"); 
		else  members =record.get("raggruppamento");
		if (members!=null)
			for (JsonNode member:members) addCompany(member); 
		
	}
}







