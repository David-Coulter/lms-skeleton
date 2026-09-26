-- Dev-only seed data. Ignored under the prod profile.
INSERT INTO items (name, description, owner_id) VALUES ('Sample item', 'Loaded by dev profile', 1);
