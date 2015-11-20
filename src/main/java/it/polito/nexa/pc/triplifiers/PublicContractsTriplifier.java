package it.polito.nexa.pc.triplifiers;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.datatypes.xsd.impl.XSDDateType;
import com.hp.hpl.jena.rdf.model.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.hpl.jena.sparql.vocabulary.FOAF;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

import java.util.ArrayList;
import java.util.List;

// This class creates triples of Italian public contracts

public class PublicContractsTriplifier implements JSONTriplifier {

    private static String BASE_URI = "http://public-contracts.nexacenter.org/id/";

    /**
     * Create a general list of Jena Statements from a JSON
     * @param inputJSON
     * @return A list of Jena Statements
     *
     */
    public List<Statement> triplifyJSON(String inputJSON, String pathJSON) {
        List<Statement> results = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readValue(inputJSON, JsonNode.class);
            results.addAll(createStatements(rootNode));
        } catch (Exception e){
            System.out.println(pathJSON);
            e.printStackTrace();
        }
        results.addAll(createProcedureTypeStatements());
        return results;
    }

    /**
     * Create general statements from JSON produced using XML files of Italian public contracts.
     * @param record The input JSON for creating CIG statements.
     *
     * @return A list of Jena Statements
     *
     */
    private List createStatements(JsonNode record){
        List<Statement> results = new ArrayList<>();

        String cig = "";
        String cigURI = "";
        String year = getValue("annoRiferimento", record);
        String processingDate = getValue("dataDiElaborazione", record);
        JsonNode errors = record.get("errors");

        if(getValue("cig", record) != "")
            cig = getValue("cig", record); //XXX Use a hash if the value is undefined
        else cig = "Missing cig";

        if(getValue("cigValid", record).equals("true"))
            cigURI = cig;
        else cigURI = getValue("cigHash", record);

        // Create resources for contract information
        Resource subject = ResourceFactory.createResource(BASE_URI + "public_contracts/" + cigURI);

        // Create errors field for RDF
        int errorIndex = 0;

        while(errors.get(errorIndex) != null){
            Statement errorStatement = ResourceFactory.createStatement(
                    subject,
                    ResourceFactory.createProperty(BASE_URI + "properties/error"),
                    ResourceFactory.createPlainLiteral(errors.get(errorIndex).toString())
            );
            results.add(errorStatement);
            errorIndex++;
        }

        if (processingDate != "") {
            Statement pd = ResourceFactory.createStatement(
                    subject,
                    ResourceFactory.createProperty(BASE_URI + "properties/processingDate"),
                    ResourceFactory.createPlainLiteral(processingDate)
            );
            results.add(pd);
        }

        Statement cigLabel = ResourceFactory.createStatement(
                subject,
                RDFS.label,
                ResourceFactory.createLangLiteral(getValue("oggetto", record), "it"));
                //ResourceFactory.createLangLiteral(getValue("oggetto", record) + " - di " + getValue("denominazione", value), "it"));

        results.add(cigLabel);

        Statement url = ResourceFactory.createStatement(
                subject,
                DCTerms.source,
                ResourceFactory.createPlainLiteral(getValue("urlFile", record)));

        results.add(url);

        Statement cigClass = ResourceFactory.createStatement(
                subject,
                RDF.type,
                ResourceFactory.createResource("http://purl.org/procurement/public-contracts#Contract"));

        results.add(cigClass);

        Statement dcId;

        if(getValue("cigValid", record).equals("true")) {
            dcId = ResourceFactory.createStatement(
                    subject,
                    DCTerms.identifier,
                    ResourceFactory.createPlainLiteral(cig));
        } else {
            dcId = ResourceFactory.createStatement(
                    subject,
                    DCTerms.identifier,
                    ResourceFactory.createPlainLiteral(cig + " (not valid)"));
        }

        results.add(dcId);

        Statement description = ResourceFactory.createStatement(
                subject,
                DCTerms.description,
                ResourceFactory.createLangLiteral(getValue("oggetto", record), "it"));

        results.add(description);

        RDFDatatype priceType = XSDDatatype.XSDfloat;

        Statement price = ResourceFactory.createStatement(
                subject,
                ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "agreedPrice"),
                ResourceFactory.createTypedLiteral(getValue("importoAggiudicazione", record), priceType));

        results.add(price);

        if(getValue("importoAggiudicazioneOriginale", record) != null) {
            Statement originalPrice = ResourceFactory.createStatement(
                    subject,
                    ResourceFactory.createProperty(BASE_URI + "properties/originalAgreedPrice"),
                    ResourceFactory.createPlainLiteral(getValue("importoAggiudicazioneOriginale", record))
            );

            results.add(originalPrice);
        }

        if(getValue("sceltaContraente", record) != "" ) {
            String procedureType = getValue("sceltaContraente", record);
            Property ptProperty = ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "procedureType");
            Resource pt = ResourceFactory.createResource(BASE_URI + "procedureTypes/" + cleanString(procedureType));
            Statement procedure= ResourceFactory.createStatement(subject, ptProperty, pt);
            results.add(procedure);
        }

        if(getValue("sceltaContraenteOriginal", record) != "") { // This property tracks errors in the procedure type values
            String pte = getValue("sceltaContraenteOriginal", record);
            Property pteProp = ResourceFactory.createProperty(BASE_URI + "properties/procedureTypeError");
            Statement procedureTypeError = ResourceFactory.createStatement(subject, pteProp, ResourceFactory.createPlainLiteral(pte));
            results.add(procedureTypeError);

            Statement pteLabel = ResourceFactory.createStatement(pteProp,
                    RDFS.label,
                    ResourceFactory.createLangLiteral("Valore originale del campo sceltaContraente", "it"));
            results.add(pteLabel);
        }

        if(getValue("importoSommeLiquidate", record) != "") {
            Resource paymentType = ResourceFactory.createResource("http://reference.data.gov.uk/def/payment#Payment");
            Resource payment = ResourceFactory.createResource(BASE_URI + "payments/" + cleanString(cigURI) + "_" + year);

            Statement hasPayment = ResourceFactory.createStatement(
                    subject,
                    ResourceFactory.createProperty("http://reference.data.gov.uk/def/payment#payment"),
                    payment
            );

            results.add(hasPayment);

            Statement netAmount = ResourceFactory.createStatement(
                    payment,
                    ResourceFactory.createProperty("http://reference.data.gov.uk/def/payment#", "netAmount"),
                    ResourceFactory.createTypedLiteral(getValue("importoSommeLiquidate", record), priceType));

            results.add(netAmount);

            RDFDatatype intType = XSDDatatype.XSDint;

            Statement paymentYear = ResourceFactory.createStatement(
                    payment,
                    ResourceFactory.createProperty("http://www.w3.org/2006/time#", "year"),
                    ResourceFactory.createTypedLiteral(year, intType));

            results.add(paymentYear);

            Statement paymentLabel = ResourceFactory.createStatement(
                    payment,
                    RDFS.label,
                    ResourceFactory.createTypedLiteral(getValue("importoSommeLiquidate", record), priceType));

            results.add(paymentLabel);

            Statement cigYear = ResourceFactory.createStatement(
                    subject,
                    ResourceFactory.createProperty("http://www.w3.org/2006/time#", "year"),
                    ResourceFactory.createTypedLiteral(year, intType));

            results.add(cigYear);

            Statement pt = ResourceFactory.createStatement(
                    payment,
                    RDF.type,
                    paymentType);

            results.add(pt);
        }

        if(getValue("importoSommeLiquidateOriginal", record) != "") {
            Statement originalPayment = ResourceFactory.createStatement(
                    subject,
                    ResourceFactory.createProperty(BASE_URI + "properties/originalAgreedPrice"),
                    ResourceFactory.createPlainLiteral(getValue("importoSommeLiquidateOriginal", record))
                    );
            results.add(originalPayment);
        }

        if(record.get("tempiCompletamento") != null){
            JsonNode times = record.get("tempiCompletamento");
            RDFDatatype dateType = XSDDateType.XSDdate;
            if(times.get("dataInizio") != null) {
                Statement startDate = ResourceFactory.createStatement(
                        subject,
                        ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "startDate"),
                        ResourceFactory.createTypedLiteral(getValue("dataInizio", times), dateType)
                );
                results.add(startDate);
            }

            if(times.get("dataUltimazione") != null) {
                Statement endDate = ResourceFactory.createStatement(
                        subject,
                        ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "estimatedEndDate"),
                        ResourceFactory.createTypedLiteral(getValue("dataUltimazione", times), dateType)
                );
                results.add(endDate);
            }

            if(times.get("dataInizioOriginale") != null) {
                Statement originalStartDate = ResourceFactory.createStatement(
                        subject,
                        ResourceFactory.createProperty(BASE_URI + "properties/originalStartDate"),
                        ResourceFactory.createPlainLiteral(getValue("dataInizioOriginale", times))
                );
                results.add(originalStartDate);
            }

            if(times.get("dataUltimazioneOriginale") != null) {
                Statement originalEndDate = ResourceFactory.createStatement(
                        subject,
                        ResourceFactory.createProperty(BASE_URI + "properties/originalEstimatedEndDate"),
                        ResourceFactory.createPlainLiteral(getValue("dataUltimazioneOriginale", times))
                );
                results.add(originalEndDate);
            }
        }

        if(getValue("aggiudicatari",record) != null) {
            JsonNode winners = record.get("aggiudicatari");
            if (winners != null)
                results.addAll(createGeneralWinners(winners, subject, cig, cigURI));
        }

        if(getValue("partecipanti",record) != null) {
            JsonNode participants = record.get("partecipanti");
            if (participants != null)
                results.addAll(createGeneralParticipants(participants, subject, cig, cigURI));
        }

        // Create resources for contracting authorities

        String contractingAuthorityId = cleanString(getValue("cfStrutturaProponenteANAC", record));
        Resource contractingAuthorityResource = ResourceFactory.createResource(BASE_URI + "businessEntities/" + contractingAuthorityId);

        Statement businessEntity = ResourceFactory.createStatement(
                contractingAuthorityResource,
                RDF.type,
                ResourceFactory.createResource("http://purl.org/goodrelations/v1#BusinessEntity"));

        results.add(businessEntity);

        Statement contractingAuthority = ResourceFactory.createStatement(
                subject,
                ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "contractingAutority"),
                contractingAuthorityResource);
        results.add(contractingAuthority);

        return results;
    }

    /**
     * Create general participants statements from JSON produced using XML files of Italian public contracts.
     * @param record The input JSON for creating general participants statements. The root node is "lotto": [{},{}]
     * @param publicContract The resource that identifies the public contract. A sample URI is:
     *                       http:​/​/​localhost/​id/​public_​contracts/​5128833EDE
     * @return A list of Jena Statements
     *
     */
    private List createGeneralParticipants(JsonNode record, Resource publicContract, String cig, String cigURI) {
        List<Statement> results = new ArrayList<>();

        int i = 0;
        while(record.get(i) != null){
            JsonNode value = record.get(i);
            if(getValue("type", value).equals("partecipante")) {
                results.addAll(createParticipantStatements(record, value, publicContract, cig, cigURI, false));
            }
            if(value.get("raggruppamento") != null){
                String groupID = getValue("groupHash", value);
                results.addAll(createGroupStatements(value.get("raggruppamento"), publicContract, groupID, cig, cigURI, false));
            }
            i++;
        }
        return results;
    }

    /**
     * Create general winners statements from JSON produced using XML files of Italian public contracts.
     * @param record The input JSON for creating general winners statements. The root node is "lotto": [{},{}]
     * @param publicContract The resource that identifies the public contract. A sample URI is:
     *                       http:​/​/​localhost/​id/​public_​contracts/​5128833EDE
     * @return A list of Jena Statements
     *
     */

    // TODO Probably this method could be removed

    private List createGeneralWinners(JsonNode record, Resource publicContract, String cig, String cigURI) {
        List<Statement> results = new ArrayList<>();

        int i = 0;

        while(record.get(i) != null){
            JsonNode value = record.get(i);
            if(getValue("type", value).equals("partecipante")) {
                results.addAll(createParticipantStatements(record, value, publicContract, cig, cigURI, true));
            }
            if(value.get("raggruppamento") != null){
                String groupID = getValue("groupHash", value);
                results.addAll(createGroupStatements(value.get("raggruppamento"), publicContract, groupID, cig, cigURI, false));
            }
            i++;
        }
        return results;
    }

    /**
     * Create participants statements from JSON produced using XML files of Italian public contracts.
     * @param record TODO
     * @param value The input JSON for creating participants statements. The root node is "partecipanti": [{},{}]
     * @param publicContract The resource that identifies the public contract. A sample URI is:
     *                       http:​/​/​localhost/​id/​public_​contracts/​5128833EDE
     * @param cig The CIG identifier
     * @param cigURI TODO
     * @param isWinner Flag for identifying winner tenders
     * @return A list of Jena Statements
     *
     */
    private List createParticipantStatements(JsonNode record,JsonNode value, Resource publicContract, String cig, String cigURI, Boolean isWinner) {
        List<Statement> results = new ArrayList<>();

        String idParticipant = "";
        Boolean isItalian = false;
        Boolean hasNationality = true;

        if(getValue("companyHash", value) != "") {
            idParticipant = getValue("companyHash", value);
            if(getValue("codiceFiscale", value) == "" && getValue("identificativoFiscaleEstero", value) == "") {
                hasNationality = false;
                isItalian = false;
            }
        } else if(getValue("codiceFiscale", value) != "") {
            idParticipant = getValue("codiceFiscale", value);
            isItalian = true;
        } else {
            idParticipant = getValue("identificativoFiscaleEstero", value);
        }

        results.addAll(createSingleParticipant(value, hasNationality, isItalian, idParticipant));

        Statement tender = ResourceFactory.createStatement(
                ResourceFactory.createResource(BASE_URI + "tenders/" +
                        cleanString(cigURI + "_" + idParticipant)),
                RDFS.label,
                ResourceFactory.createLangLiteral("CIG: " + cig + " - Identificativo offerente: " + idParticipant, "it"));

        results.add(tender);

        if(isWinner) {
            Statement tenderWinner = ResourceFactory.createStatement(
                    ResourceFactory.createResource(BASE_URI + "tenders/" +
                            cleanString(cigURI + "_" + idParticipant)),
                    RDFS.label,
                    ResourceFactory.createLangLiteral("CIG: " + cig + " - Identificativo aggiudicatario:" + idParticipant, "it"));

            results.add(tenderWinner);

            Statement awardedTender = ResourceFactory.createStatement(
                    publicContract,
                    ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "awardedTender"),
                    ResourceFactory.createResource(BASE_URI + "tenders/" +
                            cleanString(cigURI + "_" + idParticipant)));

            results.add(awardedTender);
        }

        Statement tenderClass = ResourceFactory.createStatement(
                ResourceFactory.createResource(BASE_URI + "tenders/" +
                        cleanString(cigURI + "_" + idParticipant)),
                RDF.type,
                ResourceFactory.createResource("http://purl.org/procurement/public-contracts#Tender"));

        results.add(tenderClass);

        Statement hasTender = ResourceFactory.createStatement(
                publicContract,
                ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "tender"),
                ResourceFactory.createResource(BASE_URI + "tenders/" +
                        cleanString(cigURI + "_" + idParticipant)));

        results.add(hasTender);

        Statement bidder = ResourceFactory.createStatement(
                ResourceFactory.createResource(BASE_URI + "tenders/" +
                        cleanString(cigURI + "_" + idParticipant)),
                ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "bidder"),
                ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                        cleanString(idParticipant)));

        results.add(bidder);

        return results;
    }

    /**
     * Create statements for a single participant
     * @param value The input JSON for creating statements. An example object of a single participant is
     *
     *              {
     *               "ragioneSociale": "Gruppo Biesse Sistemi S.r.l.",
     *               "codiceFiscale": "01015600057",
     *               "type": "partecipante"
     *               }
     *
     *
     * @return A list of Jena Statements
     *
     */
    private List<Statement> createSingleParticipant(JsonNode value, Boolean hasNationality, Boolean isItalian, String idParticipant){
        List<Statement> results = new ArrayList<>();

        // It is created with another script

        /*if (getValue("ragioneSociale", value) != ""){
            Statement participant = ResourceFactory.createStatement(
                    ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                            cleanString(idParticipant)),
                    ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#", "label"),
                    ResourceFactory.createLangLiteral(getValue("ragioneSociale", value),"it"));

            results.add(participant);
        }*/

        if(isItalian) {
            Statement nationality = ResourceFactory.createStatement(
                    ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                            cleanString(idParticipant)),
                    ResourceFactory.createProperty("http://dbpedia.org/ontology/country"),
                    ResourceFactory.createResource("http://dbpedia.org/resource/Italy"));
            results.add(nationality);
        }
        else if (!isItalian && hasNationality){ // It creates problems for wrong data
            /*Statement nationality = ResourceFactory.createStatement(
                    ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                            cleanString(idParticipant)),
                    ResourceFactory.createProperty(BASE_URI + "properties/isItalian"),
                    ResourceFactory.createLangLiteral("false", "en"));
            results.add(nationality);*/
        }

        if(getValue("companyHash", value) != "") {
            Statement notValidLabel = ResourceFactory.createStatement(ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                    cleanString(idParticipant)),
                    RDFS.label,
                    ResourceFactory.createLangLiteral("Codice fiscale assente o non valido", "it"));
            results.add(notValidLabel);
        } 
        else {
            if (getValue("codiceFiscale", value) != "") {
                Statement vatID = ResourceFactory.createStatement(
                        ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                                cleanString(idParticipant)),
                        ResourceFactory.createProperty("http://purl.org/goodrelations/v1#", "vatID"),
                        ResourceFactory.createPlainLiteral(getValue("codiceFiscale", value)));
                results.add(vatID);
            } else if (getValue("identificativoFiscaleEstero", value) != "") {
                Statement vatID = ResourceFactory.createStatement(
                        ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                                cleanString(idParticipant)),
                        ResourceFactory.createProperty("http://purl.org/goodrelations/v1#", "vatID"),
                        ResourceFactory.createPlainLiteral(getValue("identificativoFiscaleEstero", value)));
                results.add(vatID);
            }
        }

        Statement grBusinessEntity = ResourceFactory.createStatement(
                ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                        cleanString(idParticipant)),
                RDF.type,
                ResourceFactory.createResource("http://purl.org/goodrelations/v1#BusinessEntity"));

        results.add(grBusinessEntity);

        return results;
    }

    /**
     * Create statements for a group of participants
     * @param record The input JSON for creating statements. An example object of a group of participants is
     *
     *              {
     *                  "type": "raggruppamento",
     *                  "raggruppamento": [
     *                      {
     *                          "ragioneSociale": "TECNONET S.p.A. ",
     *                          "codiceFiscale": "04187501004",
     *                          "ruolo": "02-MANDATARIA"
     *                      },
     *                      {
     *                          "ragioneSociale": "Gruppo Easy Telecomuinicazioni S.r.l.",
     *                          "codiceFiscale": "10328750012",
     *                          "ruolo": "01-MANDANTE"
     *                      }
     *                  ]
     *              }
     * @param publicContract The resource that identifies the public contract. A sample URI is:
     *                       http:​/​/​localhost/​id/​public_​contracts/​5128833EDE
     * @param groupID An incremental id for distinguishing groups of participant (this value is combine with the
     *                cig in order to create an identifier for the group)
     * @param cig The CIG identifier
     * @param cigURI TODO
     * @param isWinner Flag for identifying the winner tender
     * @return A list of Jena Statements
     *
     */
    private List<Statement> createGroupStatements(JsonNode record,
                                                  Resource publicContract,
                                                  String groupID,
                                                  String cig,
                                                  String cigURI,
                                                  Boolean isWinner){

        List<Statement> results = new ArrayList<>();

        Resource gr =   ResourceFactory.createResource(BASE_URI + "groups/" + groupID);

        Resource td = ResourceFactory.createResource(BASE_URI + "tenders/" + cleanString(cigURI) + "_group_" + groupID);

        // Get head of the group to clarify the label of the group
        String groupHead = "indefinito";
        int i = 0;

        while (record.get(i) != null) {
            JsonNode value = record.get(i);
            if(getValue("ruolo", value).equals("02-MANDATARIA")) {
                String idParticipant = "";
                if(getValue("companyHash", value) != "") {
                    idParticipant = getValue("companyHash", value);
                } else if(getValue("codiceFiscale", value) != "") {
                    idParticipant = getValue("codiceFiscale", value);
                } else {
                    idParticipant = getValue("identificativoFiscaleEstero", value);
                }
                groupHead = idParticipant;
            } else if(getValue("ruolo", value).equals("04-CAPOGRUPPO")) {
                String idParticipant = "";
                if(getValue("companyHash", value) != "") {
                    idParticipant = getValue("companyHash", value);
                } else if(getValue("codiceFiscale", value) != "") {
                    idParticipant = getValue("codiceFiscale", value);
                } else {
                    idParticipant = getValue("identificativoFiscaleEstero", value);
                }
                groupHead = idParticipant;
            }
            i++;
        }

        Statement  group = ResourceFactory.createStatement(
                gr,
                RDFS.label,
                ResourceFactory.createLangLiteral("Raggruppamento con capogruppo/mandataria con identificavo " + groupHead, "it"));

        results.add(group);

        Statement bidder = ResourceFactory.createStatement(
                td,
                ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "bidder"),
                gr);

        results.add(bidder);

        Statement isTender = ResourceFactory.createStatement(
                td,
                RDF.type,
                ResourceFactory.createResource("http://purl.org/procurement/public-contracts#Tender"));

        results.add(isTender);

        if(isWinner) {
            Statement tenderWinner = ResourceFactory.createStatement(
                    td,
                    RDFS.label,
                    ResourceFactory.createLangLiteral("Raggruppamento aggiudicatario: capogruppo/mandataria con identificativo " + groupHead, "it"));

            results.add(tenderWinner);

            Statement awardedTender = ResourceFactory.createStatement(
                    publicContract,
                    ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "awardedTender"),
                    td);

            results.add(awardedTender);
        }

        Statement tenderLabel = ResourceFactory.createStatement(
                td,
                RDFS.label,
                ResourceFactory.createLangLiteral("Raggruppamento partecipante: capogruppo/mandataria con identificatico" + groupHead, "it"));

        results.add(tenderLabel);

        Statement hasTender = ResourceFactory.createStatement(
                publicContract,
                ResourceFactory.createProperty("http://purl.org/procurement/public-contracts#", "tender"),
                td);

        results.add(hasTender);

        Statement foafGroup = ResourceFactory.createStatement(
                gr,
                RDF.type,
                FOAF.Group);

        results.add(foafGroup);

        int a = 0;
        while (record.get(a) != null) {

            JsonNode value = record.get(a);

            String idParticipant = "";
            Boolean isItalian = false;
            Boolean hasNationality = true;
            if(getValue("companyHash", value) != "") {
                idParticipant = getValue("companyHash", value);
                if(getValue("codiceFiscale", value) == "" && getValue("identificativoFiscaleEstero", value) == "") {
                    hasNationality = false;
                    isItalian = false;
                }
            } else if(getValue("codiceFiscale", value) != "") {
                idParticipant = getValue("codiceFiscale", value);
                isItalian = true;
            } else {
                idParticipant = getValue("identificativoFiscaleEstero", value);
            }

            Resource pt = ResourceFactory.createResource(BASE_URI + "businessEntities/" +
                    cleanString(idParticipant));

            Property rl;
            Statement role;
            if(getValue("ruolo", value) != "") {
                rl = ResourceFactory.createProperty(BASE_URI + "propertiesRole/" + getValue("ruolo", value));
                role = ResourceFactory.createStatement(gr, rl, pt);
                Literal rlabel = ResourceFactory.createLangLiteral(getValue("ruolo", value), "it");
                Statement roleLabel = ResourceFactory.createStatement(rl, RDFS.label, rlabel);
                results.add(roleLabel);

                Statement isRoleSubproperty = ResourceFactory.createStatement(
                        rl,
                        RDFS.subPropertyOf,
                        ResourceFactory.createProperty("http://www.w3.org/ns/org#", "role"));

                results.add(isRoleSubproperty);

            } else {
                role = ResourceFactory.createStatement(gr, FOAF.member, pt);
            }

            results.add(role);

            if(getValue("ruoloOriginale", value) != "") { // This property tracks errors in the role values
                Statement originalRole = ResourceFactory.createStatement(
                        pt,
                        ResourceFactory.createProperty(BASE_URI + "properties/originalRole"),
                        ResourceFactory.createPlainLiteral(getValue("ruoloOriginal", value))
                );
                results.add(originalRole);
            }

            results.addAll(createSingleParticipant(value, hasNationality, isItalian, idParticipant));
            a++;
        }

        return results;
    }

    /**
     * Create procedure type statements
     *
     * @return A list of Jena Statements
     *
     */
    private List<Statement> createProcedureTypeStatements(){
        List<Statement> results = new ArrayList<>();

        Property skosNarrower = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#", "narrower");
        Property dp = ResourceFactory.createProperty("http://dbpedia.org/ontology", "property");

        List<String []> procedures = new ArrayList<>();
        procedures.add(new String[] {"01-PROCEDURA APERTA", "http://purl.org/procurement/public-contracts-procedure-types#Open"});
        procedures.add(new String[] {"02-PROCEDURA RISTRETTA", "http://purl.org/procurement/public-contracts-procedure-types#Restricted"});
        procedures.add(new String[] {"21-PROCEDURA RISTRETTA DERIVANTE DA AVVISI CON CUI SI INDICE LA GARA", "http://purl.org/procurement/public-contracts-procedure-types#Restricted"});
        procedures.add(new String[] {"22-PROCEDURA NEGOZIATA DERIVANTE DA AVVISI CON CUI SI INDICE LA GARA", "http://purl.org/procurement/public-contracts-procedure-types#Restricted"});
        procedures.add(new String[] {"07-SISTEMA DINAMICO DI ACQUISIZIONE", "http://purl.org/procurement/public-contracts-procedure-types#Restricted"});
        procedures.add(new String[] {"03-PROCEDURA NEGOZIATA PREVIA PUBBLICAZIONE DEL BANDO", "http://purl.org/procurement/public-contracts-procedure-types#Negotiated"});
        procedures.add(new String[] {"04-PROCEDURA NEGOZIATA SENZA PREVIA PUBBLICAZIONE DEL BANDO", "http://purl.org/procurement/public-contracts-procedure-types#Negotiated"});
        procedures.add(new String[] {"05-DIALOGO COMPETITIVO", "http://purl.org/procurement/public-contracts-procedure-types#CompetitiveDialogue"});
        procedures.add(new String[] {"27-CONFRONTO COMPETITIVO IN ADESIONE AD ACCORDO QUADRO/CONVENZIONE", "http://purl.org/procurement/public-contracts-procedure-types#CompetitiveDialogue"});
        procedures.add(new String[] {"06-PROCEDURA NEGOZIATA SENZA PREVIA INDIZIONE DI  GARA ART. 221 D.LGS. 163/2006", "http://purl.org/procurement/public-contracts-procedure-types#NegotiatedWithoutCompetition"});
        procedures.add(new String[] {"08-AFFIDAMENTO IN ECONOMIA - COTTIMO FIDUCIARIO", "http://purl.org/procurement/public-contracts-procedure-types#AwardWithoutPriorPublication"});
        procedures.add(new String[] {"23-AFFIDAMENTO IN ECONOMIA - AFFIDAMENTO DIRETTO", "http://purl.org/procurement/public-contracts-procedure-types#AwardWithoutPriorPublication"});
        procedures.add(new String[] {"24-AFFIDAMENTO DIRETTO A SOCIETA' IN HOUSE", "http://purl.org/procurement/public-contracts-procedure-types#AwardWithoutPriorPublication"});
        procedures.add(new String[] {"25-AFFIDAMENTO DIRETTO A SOCIETA' RAGGRUPPATE/CONSORZIATE O CONTROLLATE NELLE CONCESSIONI DI LL.PP", "http://purl.org/procurement/public-contracts-procedure-types#AwardWithoutPriorPublication"});
        procedures.add(new String[] {"26-AFFIDAMENTO DIRETTO IN ADESIONE AD ACCORDO QUADRO/CONVENZIONE", "http://purl.org/procurement/public-contracts-procedure-types#AwardWithoutPriorPublication"});
        procedures.add(new String[] {"17-AFFIDAMENTO DIRETTO EX ART. 5 DELLA LEGGE N.381/91", "http://purl.org/procurement/public-contracts-procedure-types#AwardWithoutPriorPublication"});
        procedures.add(new String[] {"14-PROCEDURA SELETTIVA EX ART 238 C.7, D.LGS. 163/2006", BASE_URI + "public-contracts-procedure-types#Special"});
        procedures.add(new String[] {"28-PROCEDURA AI SENSI DEI REGOLAMENTI DEGLI ORGANI COSTITUZIONALI", BASE_URI + "public-contracts-procedure-types#Special"});

        for(String[] procedure: procedures) {

            if(!procedure[1].equals("")){
                Statement procedureSkos = ResourceFactory.createStatement(
                        ResourceFactory.createResource(BASE_URI + "procedureTypes/" + cleanString(procedure[0])),
                        skosNarrower,
                        ResourceFactory.createResource(procedure[1]));

                results.add(procedureSkos);
            }

            Statement procedureDBpedia = ResourceFactory.createStatement(
                    ResourceFactory.createResource(BASE_URI + "procedureTypes/" + cleanString(procedure[0])),
                    skosNarrower,
                    dp);
            results.add(procedureDBpedia);

            Statement procedureLabel = ResourceFactory.createStatement(
                    ResourceFactory.createResource(BASE_URI + "procedureTypes/" + cleanString(procedure[0])),
                    RDFS.label,
                    ResourceFactory.createLangLiteral(procedure[0], "it"));

            results.add(procedureLabel);
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
                .replaceAll("\\]", "")
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
