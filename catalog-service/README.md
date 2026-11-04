# Systeme de gestion de bibliotheque - Microservices Spring Boot

Ce projet contient 3 microservices independants :
- `catalog-service`(livres + stock)
- `user-service`(utilisateurs)
- `loan-service`(emprunts)

Chaque service est autonome avec:
- son code Spring Boot
- son `Dockerfile`
- son `docker-compose.yml`
- son `.gitlab-ci.yml`
- sa base de donnees dediee (moteur SQL + phpMyAdmin pour l'administration)

# Stack
- Java 21
- Spring Boot 3.x
- Spring Web, JPA, Validation, Cache, Actuator, AOP
- Redis (catalog + user)
- Kafka : un seul broker dans le stack `catalog-service` ; `loan-service` s’y connecte via `host.docker.internal:9093`
- OpenFeign (loan -> user + catalog)
- MapStruct
- Lombok
- Log4j2
- Donnees relationnelles : moteur MySQL 8 (service Docker `db`) + phpMyAdmin (interface web ; ne remplace pas le serveur SQL)

# Ports utilises
- `catalog-service`: app `8081`, phpMyAdmin `8085`, redis `6380`, Kafka (broker unique) `9093` (écoute externe pour les autres stacks)
- `user-service`: app `8082`, phpMyAdmin `8086`, redis `6381`
- `loan-service`: app `8083`, phpMyAdmin `8087` (pas de Kafka dédié : utilise le broker du catalog)

# Base de donnees et phpMyAdmin
**phpMyAdmin** est une interface web pour gerer MySQL ; il ne stocke pas les donnees. Chaque stack Docker inclut :

- `db` : conteneur MySQL 8 (utilise par Spring via `jdbc:mysql://db:3306/...` sur le reseau interne)
- `phpmyadmin` : connexion au meme `db` (`PMA_HOST=db`)

Connexion dans le navigateur (apres `docker compose up`) :
| Service | URL phpMyAdmin | Utilisateur / mot de passe |
|------|----------------------|----------------------|
| catalog | `http://localhost:8085`  | `root` / valeur de `MYSQL_ROOT_PASSWORD` dans `.env`
| user | `http://localhost:8086` |`root` / valeur de `MYSQL_ROOT_PASSWORD` dans `.env`
| loan | `http://localhost:8087` |`root` / valeur de `MYSQL_ROOT_PASSWORD` dans `.env`

# APIs
## catalog-service
- `GET /books`
- `GET /books/paginated?page=0&size=1`
- `GET /books/{id}`
- `POST /books`
- `PUT /books/{id}`
- `DELETE /books/{id}`

Kafka consumer:
- topic `loan-created` -> decrement stock
- topic `loan-returned` -> increment stock

## user-service
- `POST /users`
- `GET /users/{id}`
- `GET /users`
- `GET /users/paginated?page=0&size=10`
- `DELETE /users/{id}`

## loan-service
- `POST /loans`
- `PUT /loans/{id}/return`
- `GET /loans/user/{userId}`

# Communication entre services
- **Synchrone (Feign)** dans `loan-service`:
    - `UserClient` -> `user-service`
    - `BookClient` -> `catalog-service`

Flux `POST /loans`:

1. Verification utilisateur via `user-service`
2. Verification livre via `catalog-service`
3. Verification `availableStock > 0`
4. Creation de l'emprunt
5. Emission Kafka `loan-created`

- **Asynchrone (Kafka)**:
    - `loan-service` publie loan-created et `loan-returned`
    - `catalog-service` consomme et met a jour le stock

**Kafka unique (obligatoire pour le stock)**

Un seul cluster Kafka tourne dans `catalog-service` (Zookeeper + Kafka). Le broker expose :

- `catalog-kafka:29092` : accès interne (conteneurs du même `docker compose` catalog : app + consumer)
- `host.docker.internal:9093` : accès depuis `loan-service` (autre stack Docker) vers le port publié sur la machine hôte

Le `loan-service`  ne démarre plus Zookeeper/Kafka : dans son `.env`, `KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093`.

**Ordre de démarrage recommandé** : `catalog-service` (pour Kafka + consumer) → `user-service` → `loan-service`. Sans catalog déjà up, les événements d’emprunt n’atteignent pas le consumer et le **stock ne bouge pas**.

# Cache Redis

- `catalog-service`: `GET /books/{id}` via `@Cacheable`, maj via `@CachePut`, suppression via `@CacheEvict`
- `user-service`: `GET /users/{id}` via `@Cacheable`, maj via `@CachePut`, suppression via `@CacheEvict`
- Les valeurs en cache sont sérialisées en **JSON (Jackson)** dans `RedisConfig` pour ne pas exiger `Serializable` sur les DTO.
- Après un changement de format de cache, si tu vois encore des 500 sur `GET /users/{id}` ou `GET /books/{id}`, vide Redis (`FLUSHALL` dans le conteneur redis du service) ou relance les stacks : les noms de cache (`userDto`, `bookDto`) ont été renommés pour éviter les entrées JDK obsolètes.

# Gestion des erreurs
Chaque service a un `@ControllerAdvice` global:

    {
    "message": "....",
    "status": 400,
    "timestamp": "2026-03-21T11:00:00"
    }

Exceptions gerees:

- `EntityNotFoundException` -> 404
- `RequestException` -> 409
- erreurs de validation -> 400

# Logging (AOP + Log4j2)

- Log d'entree methode
- Log de sortie
- Log d'erreur
- Output console + fichier `logs/logs.log`

# Lancement local avec Docker Compose
## 1) Catalog
    cd catalog-service
    docker compose up --build

## 2) User
    cd user-service
    docker compose up --build

## 1) Loan
    cd loan-service
    docker compose up --build

# Variables d'environnement

Chaque service dispose de son `.env`. Ajuster selon l'environnement:

- URL JDBC (`DB_URL` vers le service `db`), utilisateur / mot de passe
- `PMA_PORT` (port phpMyAdmin)
- host/port Redis
- Kafka : `loan-service` → `KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9093` (broker du catalog) ; `catalog-service` → `KAFKA_BOOTSTRAP_SERVERS=catalog-kafka:29092`
- URLs des services pour Feign (`loan-service`)

# Pipelines GitLab CI/CD

Chaque microservice contient son `.gitlab-ci.yml` avec stages:

- `build`
- `test`
- `package`
- `docker`


Pipeline:

1. compilation Maven
2. tests
3. packaging jar
4. build et push image Docker

# Test avec Postman

**Collection Postman (import)**

Fichier dans catalog-service : `postman/Library-Microservices.postman_collection.json`

- Postman → **Import** → coller le contenu du fichier ou choisir le fichier.
- Variables de collection (onglet **Variables**) : `catalogBaseUrl`, `userBaseUrl`, `loanBaseUrl` (par défaut `localhost:8081/8082/8083`), et `userId` / `bookId` / `loanId` pour les chemins paramétrés.

La collection inclut tous les endpoints REST + **actuator/health**, exemples de **pagination**, cas **validation** (400), **404**, et scénarios loan (retour, erreurs).

**Structure de la collection Postman** :
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_153238.png" alt="Vagrant status" width="600"/>
</div>


## Queslques résultats attendus
**Catalog** :
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142329.png" alt="Vagrant status" width="600"/>
</div>

---

<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142434.png" alt="Vagrant status" width="600"/>
</div>

---
**User** :
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142557.png" alt="Vagrant status" width="600"/>
</div>

---
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_154344.png" alt="Vagrant status" width="600"/>
</div>

---
**Loan** :
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_153745.png" alt="Vagrant status" width="600"/>
</div>

---

**Simulation d'un emprunt et retour** :
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142730.png" alt="Vagrant status" width="600"/>
</div>

---
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142815.png" alt="Vagrant status" width="600"/>
</div>

---
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142916.png" alt="Vagrant status" width="600"/>
</div>

---
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_142943.png" alt="Vagrant status" width="600"/>
</div>

---
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_143018.png" alt="Vagrant status" width="600"/>
</div>

---
<div >
  <img src="Captures/Capture_d_écran_2026-03-24_143037.png" alt="Vagrant status" width="600"/>
</div>
