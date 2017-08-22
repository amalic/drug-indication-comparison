package org.semanticscience.model;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class DrugInfo {
	private Long structId;
	private String atcCode;
	private String drugName;
	private String dailyMedSetId;
	private String hypothesisDataPublic;
	private String hypothesisDataGroup;
	
	public DrugInfo(Long structId, String atcCode, String drugName) {
		this.structId = structId;
		this.atcCode = atcCode;
		this.drugName = drugName;
	}
	
	public Long getStructId() {
		return structId;
	}
	public void setStructId(Long structId) {
		this.structId = structId;
	}
	
	public String getAtcCode() {
		return atcCode;
	}
	public void setAtcCode(String atcCode) {
		this.atcCode = atcCode;
	}
	
	public String getDrugName() {
		return drugName;
	}
	public void setDrugName(String drugName) {
		this.drugName = drugName;
	}
	
	public String getDailyMedSetId() {
		return dailyMedSetId;
	}
	public void setDailyMedSetId(String dailyMedSetId) {
		this.dailyMedSetId = dailyMedSetId;
	}
	
	public String getHypothesisDataPublic() {
		return hypothesisDataPublic;
	}
	public void setHypothesisDataPublic(String hypothesisDataPublic) {
		this.hypothesisDataPublic = hypothesisDataPublic;
	}
	
	public String getHypothesisDataGroup() {
		return hypothesisDataGroup;
	}
	public void setHypothesisDataGroup(String hypothesisDataGroup) {
		this.hypothesisDataGroup = hypothesisDataGroup;
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

}
