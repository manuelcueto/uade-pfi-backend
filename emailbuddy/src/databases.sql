create table campaign (id int primary key auto_increment, name varchar(100), status varchar(50));
create table template (id int primary key auto_increment, name varchar(100), subject varchar(500), template varchar(2000));
create table user_base (id int primary key auto_increment, name varchar(100));
create table user (id int primary key auto_increment, name varchar(100), sex varchar(100), handedness varchar(100), email varchar(100), nationality varchar(100), age int, extraversion int, agreeableness int, conscientiousness int, neuroticism int, openness int);

create table campaign_base (base_id int, campaign_id int, foreign key (base_id) references user_base (id) on update cascade, foreign key (campaign_id) references campaign (id) on update cascade, primary key (base_id, campaign_id));
create table campaign_template (template_id int, campaign_id int, foreign key (template_id) references template (id) on update cascade, foreign key (campaign_id) references campaign (id) on update cascade, primary key (template_id, campaign_id));
create table user_user_base (base_id int, user_id int, foreign key (base_id) references user_base (id) on update cascade, foreign key (user_id) references user (id) on update cascade, primary key (base_id, user_id));

create table campaign_user (campaign_id int, user_id int, email_status varchar(50), foreign key (campaign_id) references campaign (id) on update cascade, foreign key (user_id) references user (id) on update cascade, primary key (campaign_id, user_id));

create table pfi.campaign_stats (campaign_id INT primary key, total_users INT , mails_opened INT,sites_opened INT,codes_used  INT,avg_time_to_open BIGINT,avg_time_to_site BIGINT,avg_time_to_use_code BIGINT )