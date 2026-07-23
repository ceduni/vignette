alter table community.accreditation_request
    add column IF not exists permission_type varchar (255);

alter table community.community_accreditation
    add column IF not exists permission_type varchar (255);

update community.accreditation_request
set permission_type = 'COMMUNITY_REVIEW'
where permission_type is null;

update community.community_accreditation
set permission_type = 'COMMUNITY_REVIEW'
where permission_type is null;

alter table community.accreditation_request
    alter column permission_type set not null;

alter table community.community_accreditation
    alter column permission_type set not null;

alter table community.accreditation_request
    add column IF not exists target_id varchar (255);

alter table community.community_accreditation
    add column IF not exists target_id varchar (255);

update community.accreditation_request
set target_id = cast(scenario_id as VARCHAR)
where scenario_id is not null
  and target_id is null;

update community.community_accreditation
set target_id = cast(scenario_id as VARCHAR)
where scenario_id is not null
  and target_id is null;

alter table community.accreditation_request
drop
column IF exists scenario_id;

alter table community.community_accreditation
drop
column IF exists scenario_id;