# Mesures de novembre 2026

- `jvm/`, `native/` : démarrage (10 essais), cold start, mémoire (5 relevés), taille d'image.
- `ready-sondes-rapides.csv` : temps jusqu'à Ready avec des sondes identiques et rapides (période 1 s).
- `ab-regime-etabli/` : échantillons bruts du test A/B (5 runs de 1 000 requêtes par variante après 10 000 de chauffe).
- `ab-courbe-chauffe/` : une run de 6 000 requêtes sans chauffe (courbe de convergence de la JVM).
- `ab-campagne1-chauffe-200/` : première série (200 requêtes de chauffe). Pour `loan-service` et `library-gateway`, l'image JVM de l'époque datait d'avant la migration : ces deux services sont à ignorer dans ce dossier.
- `debit-wrk*.csv` : débit (wrk, 10 connexions). Seul `user-service` (fichier `debit-wrk-user.csv`) est exploitable ; les autres sont plafonnés par le quota CPU de MySQL.
