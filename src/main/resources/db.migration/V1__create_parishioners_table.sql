create table if not exists users (
                       id serial primary key,
                       chat_id integer,
                       username varchar(255),
                       name varchar(255),
                       age varchar(3),
                       male varchar(1)
);