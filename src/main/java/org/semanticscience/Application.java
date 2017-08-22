package org.semanticscience;

import org.semanticscience.model.DrugInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

@SpringBootApplication
public class Application implements CommandLineRunner {
	private static final Logger log = LoggerFactory.getLogger(Application.class);
	private static final String NA_STRING = "N/A";
	
	@Value("${hypothesis.token}") 
	String hypothesisToken;
	@Value("${hypothesis.groupid}") 
	String hypothesisGroupId;
	
	@Autowired 
	private JdbcTemplate jdbcTemplate;
	@Autowired 
	private RestTemplate restTemplate;
	
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}

	@Override
	public void run(String... args) {
		// online data
		jdbcTemplate.query(
			"select s.id, s2a.atc_code, s.name"
				+ " from struct2atc s2a"
				+ " join structures s on s.id = s2a.struct_id"
//				+ " where s2a.atc_code like 'C%' or s2a.atc_code like 'L01%'"
			, new Object[] {}
			, (rs, rowNum) -> { 
				DrugInfo drugInfo = new DrugInfo(rs.getLong("id"), rs.getString("atc_code"), rs.getString("name"));
				processDrugInfo(drugInfo);
				log.info("Processed row " + rowNum + " -> " + drugInfo);
				return null;
			}
		);
	}
	
	@Transactional(isolation=Isolation.READ_UNCOMMITTED, propagation=Propagation.NESTED)
	private void processDrugInfo(DrugInfo drugInfo) {
		insertOrUpdateDrug(drugInfo);
		insertDailyMedSetIdIfNotPresent(drugInfo);
		insertHypothesisInfoIfNotPresent(drugInfo);
		extractAnnotations(drugInfo.getStructId(), drugInfo.getAtcCode(), "public", drugInfo.getHypothesisDataPublic());
		extractAnnotations(drugInfo.getStructId(), drugInfo.getAtcCode(), "group", drugInfo.getHypothesisDataGroup());
	}
	
	private void insertOrUpdateDrug(DrugInfo drugInfo) {
		jdbcTemplate.update(
			"update ids_struct2hypothesis set dc_name=? where dc_struct_id = ? and dc_atc_code=?;"
			, drugInfo.getDrugName(), drugInfo.getStructId(), drugInfo.getAtcCode());
		
		jdbcTemplate.update(
			"insert into ids_struct2hypothesis (dc_struct_id, dc_atc_code, dc_name) SELECT ?, ?, ?"
				+ " where not exists (select 1 from ids_struct2hypothesis where dc_struct_id = ? and dc_atc_code=?);"
			, drugInfo.getStructId(), drugInfo.getAtcCode(), drugInfo.getDrugName()
			, drugInfo.getStructId(), drugInfo.getAtcCode());
	}
	
	private void insertDailyMedSetIdIfNotPresent(DrugInfo drugInfo) {
		String setId = jdbcTemplate.queryForObject(
				"select dm_set_id from ids_struct2hypothesis where dc_struct_id = ? and dc_atc_code = ?"
				, String.class 
				, drugInfo.getStructId(), drugInfo.getAtcCode());
		if(setId == null || setId.length()==0) {
			String json = restTemplate.getForObject(
				"https://dailymed.nlm.nih.gov/dailymed/services/v2/spls.json?pagesize=1&page=1&drug_name={drugName}"
				, String.class
				, drugInfo.getDrugName());
			
			DocumentContext document = JsonPath.parse(json);
			if((Integer)document.read("$.data.length()") > 0)
				setId = document.read("$.data[0].setid").toString();
			else 
				setId = NA_STRING; 
			
			jdbcTemplate.update(
				"update ids_struct2hypothesis set dm_set_id = ?"
					+ " where dc_struct_id = ? and dc_atc_code = ?"
			, setId
			, drugInfo.getStructId(), drugInfo.getAtcCode());
			
		}
		drugInfo.setDailyMedSetId(setId);
	}
	
	private void insertHypothesisInfoIfNotPresent(DrugInfo drugInfo) {
		if(drugInfo.getDailyMedSetId()!=null && !NA_STRING.equals(drugInfo.getDailyMedSetId())) {
			String publicResponse = restTemplate.postForObject(
				"https://hypothes.is/api/search?uri={uri}"
				, new HttpHeaders()
				, String.class
				, "https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=" + drugInfo.getDailyMedSetId());
			drugInfo.setHypothesisDataPublic(publicResponse);
			
			String groupResponse = restTemplate.postForObject(
				"https://hypothes.is/api/search?group={groupId}&uri={uri}"
				, getRequestHeaders()
				, String.class
				, hypothesisGroupId
				, "https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=" + drugInfo.getDailyMedSetId());
			drugInfo.setHypothesisDataGroup(groupResponse);

			jdbcTemplate.update(
				"update ids_struct2hypothesis set hypothesis_public_data = ?, hypothesis_group_data = ?"
					+ " where dc_struct_id = ? and dc_atc_code = ?"
				, drugInfo.getHypothesisDataPublic(), drugInfo.getHypothesisDataGroup()
				, drugInfo.getStructId(), drugInfo.getAtcCode());
		}
	}
	
	private HttpEntity<HttpHeaders> getRequestHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + hypothesisToken);
		return new HttpEntity<>(headers);
	}
	
	private void extractAnnotations(Long structId, String atcCode, String discriminator, String json) {
		jdbcTemplate.update(
			"delete from ids_struct2hypothesis_annotation where dc_struct_id=? and dc_atc_code=? and discriminator=?"
			, new Object[]{structId, atcCode, discriminator}
		);
		
		if(json!=null) {
			DocumentContext document = JsonPath.parse(json);
			int total = Integer.parseInt(document.read("$.total").toString());
			for(int i=0; i<total; i++) {
				String id = document.read("$.rows[" + i + "].id").toString();
				String group = document.read("$.rows[" + i + "].group").toString();
				// somehow this query returns a string with cornered brackets and quotes around it
				String selectedText = null;
				try {
					selectedText = document.read("$.rows[" + i + "].target[0].selector[-1:].exact").toString().replace("[\"", "").replace("\"]", "");
				} catch (Exception e) {}
				String user = document.read("$.rows[" + i + "].user").toString().split(":")[1];
				String tags = document.read("$.rows[" + i + "].tags").toString();
				jdbcTemplate.update(
					"insert into ids_struct2hypothesis_annotation"
						+ " (dc_struct_id, dc_atc_code, discriminator, id, groupId, selectedText, userId, tags)"
						+ " values (?, ?, ?, ?, ?, ?, ?, ?)"
					, new Object[]{structId, atcCode, discriminator, id, group, selectedText, user, tags}
				);
				extractTags(id, tags.substring(1, tags.length()-1));
			}
		}
	}
	
	private void extractTags(String annotationId, String tags) {
		jdbcTemplate.update("delete from ids_struct2hypothesis_annotation_tag where annotation_id=?", annotationId);
		for(String tag : tags.split(",")) {
			tag = tag.substring(1, tag.length()-1);
			if (tag.toLowerCase().startsWith("umls_cui"))
				createTag(annotationId, "umls_cui", tag.substring(9), tag);
			else if (tag.toLowerCase().startsWith("doid")) 
				createTag(annotationId, "doid", tag.substring(5), tag);
			else if (tag.toLowerCase().startsWith("target:umls_cui"))
				createTag(annotationId, "target_umls_cui", tag.substring(16), tag);
			else if (tag.toLowerCase().startsWith("target:doid")) 
				createTag(annotationId, "target_doid", tag.substring(12), tag);
			else if (tag.toLowerCase().equals("at")) 
				createTag(annotationId, "adjunctive therapy", null, tag);
			else if (tag.toLowerCase().equals("dm")) 
				createTag(annotationId, "disease modifying", null, tag);
			else if (tag.toLowerCase().equals("sym")) 
				createTag(annotationId, "symptomatic", null, tag);
			else if (tag.toLowerCase().startsWith("role"))
				createTag(annotationId, "role", tag.substring(5), tag);
			else if (tag.toLowerCase().equals("indication"))
				createTag(annotationId, "indication", null, tag);
			else if (tag.toLowerCase().equals("label") || tag.toLowerCase().equals("lable")) // LOL
				createTag(annotationId, "label", null, tag);
			else if (tag.toLowerCase().equals("confirmed"))
				createTag(annotationId, "confirmed", null, tag);
			else if (tag.toLowerCase().equals("rejected"))
				createTag(annotationId, "rejected", null, tag);
			else if (tag.toLowerCase().equals("contraindication"))
				createTag(annotationId, "contraindication", null, tag);
			else if (tag.startsWith("DB")) 
				createTag(annotationId, "db_id", tag, tag);
			else createTag(annotationId, null, null, tag);
		}
	}
	
	private void createTag(String annotationId, String key, String value, String original) {
		jdbcTemplate.update(
				"insert into ids_struct2hypothesis_annotation_tag (annotation_id, key, value, original)"
					+ " values (?, ?, ?, ?)"
				, annotationId, key, value, original);
	}
	
}
