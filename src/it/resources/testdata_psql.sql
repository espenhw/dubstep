create table customers (
  id serial primary key,
  name varchar(255) not null,
  created timestamp not null,
  updated timestamp not null
);

create table orders (
  id serial primary key,
  customer_id integer not null references customers(id),
  created timestamp not null
);

create table order_lines (
  id serial primary key,
  order_id integer not null references orders(id),
  quantity integer not null,
  description varchar(255) not null,
  item_code varchar(32),
  created timestamp not null,
  updated timestamp not null
);
