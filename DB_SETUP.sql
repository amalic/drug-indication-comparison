drop table ids_struct2hypothesis_annotation_tag;

drop table ids_struct2hypothesis_annotation;

drop table ids_struct2hypothesis;



create table ids_struct2hypothesis (
        dc_struct_id  integer
        , dc_atc_code text
        , dc_name text
        , dm_set_id   text
        , hypothesis_public_data text
        , hypothesis_group_data text
        , primary key (dc_struct_id, dc_atc_code)
);

alter table ids_struct2hypothesis 
add constraint s2h_2_s
foreign key (dc_struct_id)
references structures(id);

alter table ids_struct2hypothesis 
add constraint s2h_2_a
foreign key (dc_atc_code)
references atc(code);



create table ids_struct2hypothesis_annotation (
        dc_struct_id  integer
        , dc_atc_code text
        , discriminator text
        , id text
        , groupId text
        , selectedText text
        , userId text
        , tags text 
        , primary key (dc_struct_id, dc_atc_code, discriminator, id)
);

alter table ids_struct2hypothesis_annotation 
add constraint s2ha_2_s
foreign key (dc_struct_id)
references structures(id);

alter table ids_struct2hypothesis_annotation 
add constraint s2ha_2_a
foreign key (dc_atc_code)
references atc(code);



create table ids_struct2hypothesis_annotation_tag (
        annotation_id text
        , "key" text
        , "value" text
        , original text
);

--alter table ids_struct2hypothesis_annotation_tag 
--add constraint s2hat_2_s2ha
--foreign key (annotation_id)
--references ids_struct2hypothesis_annotation(id);



create or replace view 
        ids_indication_comparison 
as
        select
             s2h.dc_name as drug_name
             , s2h.dc_atc_code as atc_code
             , s2ha.selectedText
             , s2ha.userid as annotated_by
             , s2ha.groupid as "group"
             , role.value as role
             , indication.key as is_indication
             , contraindication.key as is_contraindication
             , label.key as is_label
             , symptomatic.key as is_symptomatic
             , adjunctive_therapy.key as is_adjunctive_therapy
             , disease_modifying.key as is_disease_modifying
             , confirmed.key as is_confirmed
             , rejected.key as is_rejected
             , umls_cui.value as umls_cui
             , doid.value as doid
             , db_id.value as db_id
             , target_umls_cui.value as target_umls_cui
             , target_doid.value as target_doid
             , 'https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=' || s2h.dm_set_id as dm_url
        from
             ids_struct2hypothesis s2h
             join ids_struct2hypothesis_annotation s2ha on
                s2ha.dc_struct_id = s2h.dc_struct_id
                and s2ha.dc_atc_code = s2h.dc_atc_code
             left outer join ids_struct2hypothesis_annotation_tag indication on
                indication.annotation_id = s2ha.id
                and indication.key = 'indication'
             left outer join ids_struct2hypothesis_annotation_tag label on
                label.annotation_id = s2ha.id
                and label.key = 'label'
             left outer join ids_struct2hypothesis_annotation_tag symptomatic on
                symptomatic.annotation_id = s2ha.id
                and symptomatic.key = 'symptomatic'
             left outer join ids_struct2hypothesis_annotation_tag adjunctive_therapy on
                adjunctive_therapy.annotation_id = s2ha.id
                and adjunctive_therapy.key = 'adjunctive therapy'
             left outer join ids_struct2hypothesis_annotation_tag disease_modifying on
                disease_modifying.annotation_id = s2ha.id
                and disease_modifying.key = 'disease modifying'
             left outer join ids_struct2hypothesis_annotation_tag confirmed on
                confirmed.annotation_id = s2ha.id
                and confirmed.key = 'confirmed' 
             left outer join ids_struct2hypothesis_annotation_tag rejected on
                rejected.annotation_id = s2ha.id
                and rejected.key = 'rejected'
             left outer join ids_struct2hypothesis_annotation_tag contraindication on
                contraindication.annotation_id = s2ha.id
                and contraindication.key = 'contraindication'
             left outer join ids_struct2hypothesis_annotation_tag umls_cui on
                umls_cui.annotation_id = s2ha.id
                and umls_cui.key = 'umls_cui'
             left outer join ids_struct2hypothesis_annotation_tag doid on
                doid.annotation_id = s2ha.id
                and doid.key = 'doid'
             left outer join ids_struct2hypothesis_annotation_tag db_id on
                db_id.annotation_id = s2ha.id
                and db_id.key = 'db_id'
             left outer join ids_struct2hypothesis_annotation_tag target_doid on
                target_doid.annotation_id = s2ha.id
                and target_doid.key = 'target_doid'
             left outer join ids_struct2hypothesis_annotation_tag role on
                role.annotation_id = s2ha.id
                and role.key = 'role'
             left outer join ids_struct2hypothesis_annotation_tag target_umls_cui on
                target_umls_cui.annotation_id = s2ha.id
                and target_umls_cui.key = 'target_umls_cui';