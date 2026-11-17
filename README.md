# Plateforme de covoiturage multi-etapes

API REST permettant aux conducteurs de publier des trajets avec plusieurs
points d'arret et aux passagers de reserver des places sur une partie
ou la totalite d'un trajet.

## Technologies

- Java 17
- Spring Boot et Maven
- Spring Data JPA / Hibernate
- PostgreSQL 17 avec Docker Compose
- Bean Validation
- MapStruct et Lombok
- JUnit 5, Mockito et AssertJ
- Postman

## Etat actuel

- Diagramme de classes disponible dans `docs/conception/`.
- PostgreSQL configure avec Docker Compose.
- Connexion Spring Boot / PostgreSQL verifiee.
- Configuration MapStruct et Lombok ajoutee.
- Packages de l'architecture prepares.
- Gestion centralisee des erreurs avec ProblemDetail.
- Endpoints techniques de verification disponibles avec le profil `test-errors`.
- Creation d'un conducteur avec validation des donnees et controle d'unicite.
- Consultation, liste, recherche par nom ou email et modification du profil conducteur.
- Creation, consultation, liste, recherche par email et modification des passagers.
- Tests unitaires du service conducteur et du traitement des contraintes SQL.

Les fonctionnalites metier seront ajoutees progressivement.

## Prerequis

- JDK 17
- Docker Desktop demarre
- Git
- Postman pour les tests HTTP

Les commandes ci-dessous sont prevues pour PowerShell sur Windows.
Maven est fourni par le Maven Wrapper du projet.
Les ports 5433 (PostgreSQL) et 8080 (API) doivent etre disponibles.

## Recuperer le projet

```powershell
git clone https://github.com/yassinesekkah/Plateforme-de-covoiturage-multi--tapes.git
cd Plateforme-de-covoiturage-multi--tapes
```

Pour recuperer les fonctionnalites integrees :

```powershell
git switch develop
```

Les nouvelles fonctionnalites sont developpees dans des branches `feature/COV-...`,
puis fusionnees dans `develop` apres verification.

## Demarrer PostgreSQL

Depuis la racine du projet :

```powershell
docker compose up -d
docker compose ps
```

Configuration locale :

| Parametre | Valeur |
|---|---|
| Host | localhost |
| Port depuis le PC | 5433 |
| Base | covoiturage |
| Utilisateur | covoiturage |
| Mot de passe local | covoiturage |

Ces identifiants sont reserves au developpement local.

Pour verifier la base :

```powershell
docker compose exec postgres psql -U covoiturage -d covoiturage -c "SELECT current_database(), current_user;"
```

Les donnees sont conservees dans un volume Docker.

## Demarrer l'API

PostgreSQL doit etre demarre avant l'API.

```powershell
.\mvnw.cmd spring-boot:run
```

Adresse de base : `http://localhost:8080`.
Cette adresse ne correspond pas a une page d'accueil : utiliser les endpoints disponibles.

Utiliser `Ctrl + C` pour arreter l'application.

Hibernate cree ou met a jour les tables a partir des entites avec
`spring.jpa.hibernate.ddl-auto=update`, pour le developpement local.
Sans entites metier, aucune table metier n'est creee.

## Lancer les tests Java

Avec PostgreSQL demarre :

```powershell
.\mvnw.cmd clean test
```

Le test de chargement du contexte utilise la configuration PostgreSQL locale.
Les tests HTTP ci-dessous sont actuellement executes manuellement avec Postman.

Pour lancer uniquement les tests unitaires sans PostgreSQL :

```powershell
.\mvnw.cmd "-Dtest=ConducteurServiceImplTest,PassagerServiceImplTest,GlobalExceptionHandlerTest" test
```

Les 13 tests de ConducteurServiceImpl couvrent la creation et les doublons,
la liste avec ou sans resultats, la recherche et le mot-cle vide,
ainsi que la modification, le proprietaire, le profil absent et les doublons.
Les 2 tests de GlobalExceptionHandler simulent les erreurs SQL :
23505 retourne 409, une autre erreur de contrainte retourne 500.
Ces simulations ne constituent pas un test de concurrence reelle en base.
Le log ERROR affiche par le test du cas 500 est volontaire.

Les 14 tests de PassagerServiceImpl couvrent creation, detail existant ou absent,
liste et recherche avec ou sans resultats, nettoyage et refus du terme de recherche,
modification du profil et refus de modification pour absence ou mauvais proprietaire,
ainsi que les emails deja utilises en creation et modification.
Ils utilisent des mocks : les validations HTTP, la recherche SQL et la persistance
reelle se verifient avec Postman et PostgreSQL.

## Creer un conducteur

Cet endpoint fonctionne sans le profil `test-errors` :

```text
POST http://localhost:8080/api/conducteurs
Content-Type: application/json
```

Corps de la requete :

```json
{
  "lastName": "Alami",
  "firstName": "Hamid",
  "email": "hamid@example.com",
  "phone": "0612345678",
  "licenseNumber": "AB123456"
}
```

Tous les champs sont obligatoires, ne doivent pas etre vides et sont limites
a 255 caracteres. L'email doit avoir un format valide.
L'email et le numero de permis doivent etre uniques parmi les conducteurs.
Le client ne fournit pas d'identifiant : PostgreSQL le genere.

Une creation valide retourne 201 Created :

```json
{
  "id": 1,
  "lastName": "Alami",
  "firstName": "Hamid",
  "email": "hamid@example.com",
  "phone": "0612345678",
  "licenseNumber": "AB123456"
}
```

L'identifiant ci-dessus est un exemple. La ligne est enregistree dans la table
`conducteurs`, avec la colonne `license_number` pour le numero de permis.
La creation est journalisee au niveau INFO.

Dans le dossier Conducteurs de Postman, conserver les cas suivants :

| Cas | Status attendu | Resultat |
|---|---|---|
| Donnees valides et uniques | 201 | Conducteur cree avec son identifiant |
| Email existant, permis nouveau | 409 | Cet email est deja utilise par un conducteur |
| Permis existant, email nouveau | 409 | Ce numero de permis est deja utilise |
| Nom vide | 400 | Liste errors contenant Le nom est obligatoire |
| Email invalide | 400 | Liste errors contenant L'email est invalide |
| Corps vide `{}` | 400 | Erreurs des champs obligatoires |

Les requetes refusees ne doivent pas ajouter de conducteur.
Les controles du service fournissent des messages precis sur les doublons.
Les contraintes uniques PostgreSQL restent la protection finale : si un doublon
atteint la base (SQLState 23505), le gestionnaire retourne 409 avec le message
Une valeur devant etre unique est deja utilisee.

La collection reste sauvegardee dans Postman. Son export dans le depot est prevu
apres l'ajout des autres fonctionnalites.

## Consulter et rechercher les conducteurs

Ces endpoints fonctionnent sans le profil `test-errors` :

| Methode | Chemin | Resultat |
|---|---|---|
| GET | /api/conducteurs | 200 et liste des conducteurs |
| GET | /api/conducteurs/{id} | 200 et profil, ou 404 si absent |
| GET | /api/conducteurs/search?keyword=Alami | 200 et liste des correspondances |

La recherche porte sur `lastName` ou `email`. Elle accepte une partie du texte,
ignore la casse et retire les espaces au debut et a la fin du mot-cle.
Un mot-cle absent, vide ou compose uniquement d'espaces retourne 400.
Une liste ou une recherche sans resultat retourne 200 avec `[]`.
Un identifiant non numerique retourne 400. Aucun tri particulier n'est garanti.

## Modifier un profil conducteur

```text
PUT http://localhost:8080/api/conducteurs/1
Content-Type: application/json
X-Conducteur-Id: 1
```

Remplacer `1` par l'identifiant du conducteur. Le header `X-Conducteur-Id`
represente l'acteur pour simuler le controle de propriete dans ce projet.
Il est fourni par le client et ne constitue pas une authentification securisee.

Le PUT remplace les cinq informations du profil : tous les champs sont requis,
avec les memes validations que pour la creation.

```json
{
  "lastName": "Alami",
  "firstName": "Hamid",
  "email": "hamid@example.com",
  "phone": "0699999999",
  "licenseNumber": "AB123456"
}
```

Une modification valide retourne 200 avec le profil actualise et le meme `id`.
Elle ne cree pas une nouvelle ligne. Le conducteur peut conserver son email
et son permis ; les controles de doublons excluent son propre identifiant.
La modification est transactionnelle et journalisee au niveau INFO.

| Cas | Status attendu |
|---|---|
| Modification de son profil avec des valeurs valides | 200 |
| Profil inexistant | 404 |
| Acteur different du proprietaire du profil existant | 403 |
| Email ou permis deja utilise par un autre conducteur | 409 |
| Champs invalides ou obligatoires absents | 400 |
| Header X-Conducteur-Id absent ou non numerique | 400 |

Conserver ces cas dans le dossier Conducteurs de Postman. Apres un PUT valide,
relire le profil avec GET et verifier la base. Apres un refus, les donnees doivent
rester identiques. Pour tester les doublons, utiliser un second conducteur avec
des valeurs distinctes. Les controles sont aussi proteges par les contraintes
uniques PostgreSQL et le traitement du SQLState 23505.

## Gerer les passagers

Ces endpoints fonctionnent sans le profil `test-errors` :

| Methode | Chemin | Resultat |
|---|---|---|
| POST | /api/passagers | 201 et profil avec identifiant genere |
| GET | /api/passagers/{id} | 200 et profil, ou 404 si absent |
| GET | /api/passagers | 200 et liste, ou `[]` |
| GET | /api/passagers/search?email=salma | 200 et liste des correspondances |
| PUT | /api/passagers/{id} | 200 et profil actualise |

Corps JSON pour POST et PUT :

```json
{
  "lastName": "Bennani",
  "firstName": "Salma",
  "email": "salma@example.com",
  "phone": "0612345678"
}
```

Les quatre champs sont obligatoires, non blancs et limites a 255 caracteres.
L'email doit avoir un format valide. Le client ne fournit pas d'identifiant.
La table `passagers` ne contient pas de permis. L'email est unique parmi les passagers,
comme choix de conception complementaire au brief. Creation avec email existant :
409 et Cet email est deja utilise par un passager.
L'unicite compare les valeurs exactes, comme pour les conducteurs ; la recherche
reste insensible a la casse.

La recherche accepte une partie de l'email, ignore la casse et retire les espaces
en debut et fin. Une recherche sans resultat retourne 200 avec `[]` ; un parametre
absent ou blanc retourne 400. Aucun tri particulier n'est garanti.

PUT demande les quatre champs et conserve l'identifiant. Le header obligatoire
`X-Passager-Id` represente l'acteur : il doit correspondre au profil a modifier.
C'est une simulation de propriete, pas une authentification securisee.
Un profil absent retourne 404, un autre proprietaire 403, des donnees invalides
ou un header absent/non numerique 400. Le passager peut conserver son propre email.
L'email d'un autre passager est refuse avec 409 et le message
Cet email est deja utilise par un autre passager. La contrainte unique PostgreSQL
protege aussi les acces concurrents ; une violation SQLState 23505 retourne 409.
Les creations et modifications sont transactionnelles et journalisees au niveau INFO.

Le gestionnaire d'erreurs commun est reutilise. L'historique des reservations
sera ajoute avec la fonctionnalite de reservation.

Le [guide des tests Postman passagers](docs/tests-passagers.md) fournit les requetes,
les corps, les headers et les resultats attendus. La collection reste dans Postman
jusqu'a l'export final prevu avec les autres fonctionnalites.

La [collection simple importable](postman/covoiturage-simple.postman_collection.json)
regroupe Conducteurs et Passagers, sans variables ni scripts. Les URLs sont completes
et les identifiants d'exemple sont ecrits directement (1, 2, 999999).
Adapter manuellement les ids et les emails aux profils de la base.
Un POST genere son propre id, qui n'est pas forcement 1. Les noms et descriptions
des requetes indiquent les resultats attendus. Executer la creation valide avant
le test de doublon correspondant, en conservant les memes valeurs pour ce dernier.

## Verifier les erreurs HTTP

Pour activer les endpoints techniques :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test-errors"
```

Ces endpoints sont des simulations pour verifier le gestionnaire d'erreurs.
Ils ne verifient pas encore les regles des fonctionnalites metier.

Dans IntelliJ, il est aussi possible de dupliquer la configuration Spring Boot
dans Run > Edit Configurations et de renseigner `test-errors` dans Active profiles.
Si ce champ n'est pas disponible, ajouter `--spring.profiles.active=test-errors`
dans Program arguments. Garder une configuration normale sans ce profil.

Dans Postman, regrouper les requetes dans le dossier
`Tests techniques - Gestion des erreurs` de la collection `Covoiturage`.

| Methode | Chemin | Corps | Status attendu |
|---|---|---|---|
| GET | /test-errors/not-found | Aucun | 404 |
| GET | /test-errors/forbidden | Aucun | 403 |
| GET | /test-errors/conflict | Aucun | 409 |
| GET | /test-errors/bad-request | Aucun | 400 |
| POST | /test-errors/validation | Nom vide | 400 |
| POST | /test-errors/validation | JSON malforme | 400 |
| GET | /test-errors/number/abc | Aucun | 400 |
| GET | /test-errors/number/12 | Aucun | 200, reponse vide |
| POST | /test-errors/validation | Nom valide | 200, reponse vide |
| POST | /test-errors/not-found | Aucun | 405, header Allow contenant GET |
| GET | /test-errors/internal | Aucun | 500 |

Pour les requetes POST de validation, utiliser
`Content-Type: application/json` (Body > raw > JSON dans Postman).

Corps valide :

```json
{
  "nom": "Salma"
}
```

Corps invalide :

```json
{
  "nom": ""
}
```

JSON malforme (accolade de fermeture volontairement absente) :

```text
{"nom": "Salma"
```

Verifier que le status HTTP correspond au champ `status` du JSON,
que `instance` contient le chemin et que `timestamp` est present.
Le test 500 doit retourner un message general au client et ecrire
les details techniques dans les logs, sans arreter l'application.

Les endpoints `/test-errors` sont desactives sans le profil `test-errors`.

## Format des erreurs

Les erreurs sont gerees dans `GlobalExceptionHandler`.

Les reponses utilisent ProblemDetail avec :

- `status` : code HTTP ;
- `title` : titre de l'erreur ;
- `detail` : message explicatif ;
- `instance` : chemin de la requete ;
- `timestamp` : date et heure de l'erreur ;
- `errors` : liste des erreurs de validation, si applicable.

Le champ standard `type` utilise la valeur par defaut `about:blank`
lorsqu'aucun type particulier n'est defini ; sa presence dans le JSON
depend de la serialisation.

Les refus metier, les acces interdits, les donnees refusees et les erreurs
de validation sont journalises au niveau WARN.
Les erreurs inattendues sont journalisees au niveau ERROR,
sans exposer leurs details techniques au client.
Les erreurs Spring implementant ErrorResponse conservent leur status et leurs headers.

## Architecture

```text
ma.youcode.covoiturage
|-- controller
|-- service
|   `-- impl
|-- repository
|-- entity
|-- dto
|-- mapper
`-- exception
```

Chaque fonctionnalite est developpee avec ses couches et ses tests
avant de passer a la suivante.

## Conception

- [Diagramme SVG](docs/conception/diagramme-classes.svg)
- [Diagramme modifiable draw.io](docs/conception/diagramme-classes.drawio)
- [Source PlantUML](docs/conception/diagramme-classes.puml)
- [Explications des relations](docs/conception/README.md)

## Workflow Git

- `main` : branche de livraison.
- `develop` : integration des fonctionnalites terminees.
- `feature/COV-...` : travail sur une fonctionnalite.

Chaque changement coherent possede son commit.
Les branches feature sont fusionnees dans develop apres verification,
sans squash pour conserver les commits.
Develop sera fusionnee dans main pour la livraison.

## Arreter PostgreSQL

```powershell
docker compose stop
```

## Probleme connu sous Windows

Si Maven Wrapper affiche que `powershell` est introuvable,
ajouter son dossier au PATH du terminal courant :

```powershell
$env:Path += ";C:\Windows\System32\WindowsPowerShell\v1.0"
```

Puis relancer la commande Maven.
