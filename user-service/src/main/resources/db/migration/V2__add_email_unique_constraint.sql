-- Supprimer les doublons d'email en ne gardant que le premier enregistrement (id le plus petit)
DELETE u1 FROM users u1
INNER JOIN users u2 ON u1.email = u2.email
WHERE u1.id > u2.id;

-- Ajouter NOT NULL et UNIQUE sur email
ALTER TABLE users
    MODIFY email VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_users_email UNIQUE (email);
