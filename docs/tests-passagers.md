# COV-10 — Tests manuels Postman

Lancer PostgreSQL et l'application avec la configuration normale.
Dans la collection Covoiturage, creer le dossier Passagers.
POST et PUT : Body > raw > JSON, Content-Type: application/json.
GET : Body > none.

Variables de collection a renseigner :

| Variable | Valeur |
|---|---|
| baseUrl | http://localhost:8080 |
| passagerId | id retourne lors de la creation de Salma |
| autrePassagerId | id retourne lors de la seconde creation |
| absentId | identifiant confirme absent, par exemple 999999 |

## A. Creation

POST `{{baseUrl}}/api/passagers`, corps A :

```json
{
  "lastName": "Bennani",
  "firstName": "Salma",
  "email": "salma@example.com",
  "phone": "0612345678"
}
```

201 : les quatre champs sont retournes avec un id genere. Copier cet id dans passagerId.
Repeter exactement le POST : 409, Cet email est deja utilise par un passager.
Pour creer un second profil, changer firstName en Sara et email en
salma.autre@example.com : 201. Copier le second id dans autrePassagerId.
Si ces emails existent deja, choisir deux nouvelles adresses distinctes pour
les creations valides et adapter les recherches et modifications ci-dessous.

Dupliquer la requete pour chaque cas suivant, en repartant du corps A :

| Cas | Modification du corps A | Attendu |
|---|---|---|
| Nom vide | lastName = "" | 400, errors contient Le nom est obligatoire |
| Prenom blanc | firstName = "   " | 400, errors contient Le prenom est obligatoire |
| Email invalide | email = "salma" | 400, errors contient L'email est invalide |
| Telephone absent | supprimer la propriete phone | 400, errors contient Le telephone est obligatoire |
| Email null | email = null | 400, errors contient L'email est obligatoire |
| Tous les champs absents | envoyer {} | 400, erreurs des quatre champs |
| JSON malforme | envoyer {"lastName": | 400, Requete invalide |
| Corps absent | Body > none, conserver Content-Type application/json | 400, Requete invalide |

Pour verifier Size, dans Scripts > Pre-request d'une requete de test :

```javascript
pm.variables.set("longText", "a".repeat(256));
```

Dans une copie du corps A, remplacer lastName par "{{longText}}" : 400.
Repeter pour firstName, phone et email (pour email, plusieurs validations peuvent echouer).
Retirer ce script des autres requetes. Aucune requete invalide ne doit ajouter de ligne.

## B. Consultation et liste

| Methode | URL | Attendu |
|---|---|---|
| GET | {{baseUrl}}/api/passagers/{{passagerId}} | 200, profil cree |
| GET | {{baseUrl}}/api/passagers/{{absentId}} | 404, Passager introuvable avec l'identifiant ... |
| GET | {{baseUrl}}/api/passagers/abc | 400, parametre id invalide |
| GET | {{baseUrl}}/api/passagers | 200, tableau contenant les profils crees |

La liste vide retourne 200 et []. Ce cas est couvert par le test unitaire ;
ne pas supprimer les donnees existantes pour le rejouer manuellement.

## C. Recherche

| Methode | URL | Attendu |
|---|---|---|
| GET | {{baseUrl}}/api/passagers/search?email=salma@example.com | 200, profils avec cet email |
| GET | {{baseUrl}}/api/passagers/search?email=salma | 200, correspondances partielles |
| GET | {{baseUrl}}/api/passagers/search?email=SALMA | 200, memes correspondances |
| GET | {{baseUrl}}/api/passagers/search?email=%20salma%20 | 200, espaces retires |
| GET | {{baseUrl}}/api/passagers/search?email=zzzintrouvable999 | 200, [] si aucune correspondance |
| GET | {{baseUrl}}/api/passagers/search?email= | 400, L'email de recherche est obligatoire |
| GET | {{baseUrl}}/api/passagers/search?email=%20%20%20 | 400, meme message |
| GET | {{baseUrl}}/api/passagers/search | 400, parametre obligatoire manquant |

## D. Modification

PUT `{{baseUrl}}/api/passagers/{{passagerId}}`

Header `X-Passager-Id: {{passagerId}}`. Corps B :

```json
{
  "lastName": "Bennani",
  "firstName": "Salma",
  "email": "salma@example.com",
  "phone": "0699999999"
}
```

200 : meme id et telephone actualise. Confirmer avec GET et en base.
Le nombre de lignes doit rester identique. Garder son propre email est autorise.
Remplacer email par celui du second passager : 409,
Cet email est deja utilise par un autre passager. Le profil doit rester inchange.

Repartir de cette requete valide pour chaque refus :

| Cas | Changement | Attendu |
|---|---|---|
| Autre proprietaire | X-Passager-Id = {{autrePassagerId}} | 403, Vous ne pouvez modifier que votre propre profil |
| Profil absent | URL finit par {{absentId}}, header = {{absentId}} | 404 |
| Header absent | supprimer X-Passager-Id | 400 |
| Header invalide | X-Passager-Id = abc | 400 |
| Id invalide | URL finit par abc, header valide | 400 |
| Nom vide | lastName = "" dans corps B | 400 |
| Email invalide | email = "salma" dans corps B | 400 |
| Email d'un autre passager | email = "salma.autre@example.com" dans corps B | 409 |
| PUT incomplet | envoyer seulement {"phone":"0699999999"} | 400, autres champs obligatoires |
| JSON malforme | envoyer {"phone": | 400 |

Les validations NotBlank et Size sont les memes que pour POST : rejouer aussi
les champs blancs, nuls et trop longs sur PUT si besoin. Apres chaque refus,
un GET doit confirmer que le profil n'a pas change.

## Verifications communes

- Le status HTTP correspond au champ status du JSON d'erreur.
- instance correspond au chemin de la requete et timestamp est present.
- Les creations et modifications valides produisent un log INFO.
- Les validations et refus de propriete produisent un log WARN.
- Aucune erreur SQL ou stack trace n'est exposee dans le JSON.

SQL pour verifier les lignes sans les modifier :

```sql
SELECT id, last_name, first_name, email, phone
FROM passagers
ORDER BY id;
```

Ces tests HTTP sont a executer manuellement. Les tests unitaires utilisent des
mocks et ne prouvent pas seuls la persistance, IgnoreCase ou la validation HTTP.
