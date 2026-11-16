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
