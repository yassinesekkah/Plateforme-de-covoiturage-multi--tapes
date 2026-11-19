# Plateforme de covoiturage multi-etapes

API REST permettant aux conducteurs de publier des trajets avec plusieurs points d'arret et aux passagers de reserver des places sur tout ou partie du parcours.

## Technologies

Java 17, Spring Boot, Maven, PostgreSQL 17, Spring Data JPA, Bean Validation, MapStruct et Lombok. Tests avec JUnit 5, Mockito, AssertJ et Postman.

Le projet suit une architecture en couches : Controller, Service, Repository, Entity, DTO et Mapper.

## Fonctionnalites

- Gestion des conducteurs, passagers et vehicules.
- Publication de trajets avec points d'arret ordonnes.
- Recherche entre deux points du parcours, avec pagination et filtre de prix.
- Reservation, calcul du montant et des places restantes.
- Consultation des reservations et de l'historique.
- Annulation passager jusqu'a 24 heures avant le depart.
- Annulation d'un trajet et de ses reservations, ou terminaison apres le depart.
- Gestion centralisee des erreurs avec ProblemDetail.

## Prerequis

- JDK 17.
- Docker Desktop demarre.
- Git et Postman.
- Ports 5433 (PostgreSQL) et 8080 (API) disponibles.

Les commandes suivantes s'executent dans PowerShell, a la racine du projet. Maven est fourni par le wrapper.

## Recuperer le projet

```powershell
git clone https://github.com/yassinesekkah/Plateforme-de-covoiturage-multi--tapes.git
cd Plateforme-de-covoiturage-multi--tapes
git switch develop
```

`develop` contient les fonctionnalites integrees. `main` recevra la version de livraison finale.

## Demarrer PostgreSQL

```powershell
docker compose up -d
docker compose ps
```

| Parametre | Valeur locale |
| --- | --- |
| Host | localhost |
| Port | 5433 |
| Base | covoiturage |
| Utilisateur | covoiturage |
| Mot de passe | covoiturage |

## Demarrer l'API

Avec PostgreSQL demarre :

```powershell
.\mvnw.cmd spring-boot:run
```

Adresse : `http://localhost:8080/api`. Utiliser Postman pour appeler les endpoints.
Hibernate cree ou met a jour les tables au demarrage. Les donnees ne sont pas inserees automatiquement.

Si le wrapper affiche que `powershell` est introuvable, executer puis relancer la commande :

```powershell
$env:Path += ';C:\Windows\System32\WindowsPowerShell\v1.0'
```

## Lancer les tests

Ouvrir un terminal a la racine du projet :

```powershell
.\mvnw.cmd clean test
```

La suite contient 20 tests unitaires avec JUnit 5, Mockito et AssertJ :

- `TrajetServiceTest` : 9 tests de publication, recherche, places, annulation et terminaison.
- `ReservationServiceTest` : 11 tests de prix, places, parcours, proprietaire et delai d'annulation.

Ils couvrent les cas valides et les principaux refus du brief.
PostgreSQL n'est pas necessaire pour ces tests, car les repositories sont simules.
Le resultat attendu est `BUILD SUCCESS`, sans echec ni erreur.

## Tester avec Postman

Importer la [collection complete](postman/covoiturage-simple.postman_collection.json).
Les URL, ids et dates sont ecrits directement : adapter les ids aux reponses des creations et choisir des dates futures.

Pour la base locale preparee pour la demonstration, les vrais ids sont deja renseignes.
Voir les [donnees demo et l'ordre des essais](docs/demo/README.md).

Les headers `X-Conducteur-Id` et `X-Passager-Id` simulent l'acteur pour les controles de propriete ; ce n'est pas une authentification securisee.

Le dossier **Validation brief - 11 scenarios COV-20** se joue de 00 a 11. Il verifie notamment les montants 50 DH et 160 DH, les places, les annulations et les refus.

- [Collection des 11 scenarios seule](postman/scenarios-brief.postman_collection.json)
- [Preparation et resultats attendus](docs/scenarios-validation-brief.md)

## Diagramme UML

- [Image SVG](docs/conception/diagramme-classes.svg)
- [Fichier modifiable draw.io](docs/conception/diagramme-classes.drawio)
- [Explication des relations](docs/conception/README.md)

## Arreter le projet

Arreter l'API avec `Ctrl + C`, puis PostgreSQL :

```powershell
docker compose stop
```

Les donnees PostgreSQL sont conservees dans le volume Docker.
