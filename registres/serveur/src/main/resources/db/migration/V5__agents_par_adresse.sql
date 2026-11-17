-- Les agents se connectent désormais avec leur adresse électronique : leur identifiant de
-- connexion devient cette adresse. Les comptes de démonstration « agent.<code> » suivent.
UPDATE agent_affectation SET identifiant = identifiant || '@sira.example'
 WHERE identifiant ~ '^agent\.[a-z-]+$';
