package it.polito.nexa.pc.businessEntities;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils; 

public class Company {
	private String id; 
	private boolean italian; 
	private HashMap<String, Integer> names = new HashMap<>(); 
	private HashMap<String, Integer> originalIds = new HashMap<>(); 
	
	public Company(String id, String name,  boolean isItalian) {
		super();
		this.id = id;
		this.italian = isItalian;
		names.put(name.toUpperCase(), 1); 
	}
	
	public boolean isItalian() {
		return italian;
	}

	public String getId() {
		return id;
	}
	
	public boolean hasOriginalId(){
		return !originalIds.isEmpty(); 
	}

	public Company( String id,String name, boolean isItalian, String originalId ) {
		super();
		this.id = id;
		this.italian = isItalian;
		names.put(name.toUpperCase(), 1);	
		originalIds.put(originalId, 1); 
	}
	
	public void addName(String name){
		if ((names.containsKey(name) && (name.length()>1))){
			names.put(name.toUpperCase(), names.get(name.toUpperCase())+1); 
		} 	
		else
		{
			if (name.length()>1)
				names.put(name.toUpperCase(), 1);
		}
	}
	
	public void addOriginalId(String originalId){
		if (originalIds.containsKey(originalId)){
			originalIds.put(originalId, originalIds.get(originalId)+1); 
		} 
		else 
			originalIds.put(originalId, 1);	
	}
	
	public HashMap<String, Integer> getNames(){
		return names; 
	}
	
	public Set<String> getOriginalIds(){
		return originalIds.keySet(); 
	}
	
	public String toString(){
		return  
		names.entrySet()
			.stream()
			.sorted(Entry.comparingByValue())
			.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new))
			.toString();   		
	}
	
	/*
	 * returns the most likely label, based on 
	 * label frequency and Levensthein distances
	 * */
	public String getBestLabel(){
		double similarity; 
		HashMap<String, Double> comparedNames = new HashMap<>(); 
		for (Entry e : names.entrySet()){
			similarity=0; 
			for (Entry f : names.entrySet()){
				if (!f.getKey().equals(e.getKey()))	{
					similarity +=StringUtils.getLevenshteinDistance((String)e.getKey(), (String) f.getKey())*(Integer)e.getValue();
				}
			}
			comparedNames.put((String)e.getKey(), similarity); 	
		}
		return comparedNames.entrySet()
				.stream()
				.max(Comparator.comparing(e -> e.getValue()))
				.get()
				.getKey(); 		
	}
	
	/*
	 * For debig purposes: returns all labels with relative score achieved 
	 * by comparing algorythm
	 * */
	public HashMap<String, Double> getAllLabels(){
		double similarity; 
		HashMap<String, Double> comparedNames = new HashMap<>(); 
		for (Entry e : names.entrySet()){
			similarity=0; 
			for (Entry f : names.entrySet()){
				if (!f.getKey().equals(e.getKey()))	{
					similarity +=StringUtils.getLevenshteinDistance((String)e.getKey(), (String) f.getKey())*(Integer)e.getValue();
				}
			}
			comparedNames.put((String)e.getKey(), similarity); 	
		}
		return comparedNames; 
	}
	
	
}
