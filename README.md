# Sistem de stocare distribuită de fișiere

Proiect la disciplina Sisteme Distribuite: sistem de stocare distribuită de fișiere în Java, cu replicare pe două noduri, verificare de integritate prin SHA-256, detectare a căderilor și realegere a coordonatorului.

## Echipa

- Student: `[Popenciuc Andrei]`


## Cerințe preliminare

- JDK 17 sau mai nou
- Apache Maven 3.8+
- Git

## Structura proiectului

- `docs/cerinte.md` - specificațiile operațiilor, modelul de date, cerințele nefuncționale, scenariul demonstrației și schița protocolului
- `src/main/java/dfs/` - codul sursă pentru noduri (`StorageNode`) și client (`Client`)
- `start-local.sh` - script pentru pornirea rapidă a 3 noduri pe mașina locală

## Compilare

Din rădăcina proiectului se rulează:

```bash
mvn -q package
```

## Pornirea nodurilor

Fiecare nod rulează într-un proces separat și primește identificatorul, portul, directorul local de stocare și opțional flag-ul `--coordinator`.

Pornirea nodului coordonator (`n1`):

```bash
java -cp target/classes dfs.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
```

Output așteptat:

```text
[n1] pornit: port 7001, director data/n1, rol: coordonator
```

Pornirea nodurilor de stocare (`n2` și `n3`) în terminale separate:

```bash
java -cp target/classes dfs.StorageNode --id n2 --port 7002 --dir data/n2
```

```text
[n2] pornit: port 7002, director data/n2, rol: nod de stocare
```

```bash
java -cp target/classes dfs.StorageNode --id n3 --port 7003 --dir data/n3
```

```text
[n3] pornit: port 7003, director data/n3, rol: nod de stocare
```

Alternativ, pe Linux/macOS/Git Bash se pot porni toate cele 3 noduri cu:

```bash
./start-local.sh
```

## Pornirea clientului

Clientul primește adresa și portul coordonatorului prin argumentul `--coordinator`:

```bash
java -cp target/classes dfs.Client --coordinator localhost:7001
```

Output la pornire:

```text
[client] coordonator configurat: localhost:7001; comenzi: put, get, ls, rm, nodes, exit
```
