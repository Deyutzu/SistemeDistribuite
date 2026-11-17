# Sistem de stocare distribuită de fișiere

Proiect la disciplina Sisteme Distribuite: sistem de stocare distribuită de fișiere în Java, cu replicare pe două noduri, verificare de integritate prin SHA-256, detectare a căderilor și realegere a coordonatorului.

## Echipa

- Student: `[Popenciuc Andrei]`


## Cerințe preliminare

- JDK 17 sau mai nou
- Apache Maven 3.8+
- Git

## Structura proiectului

- `docs/cerinte.md` - specificațiile operațiilor, modelul de date, cerințele nefuncționale, scenariul demonstrației și rezultatele măsurătorilor
- `docs/protocol.md` - specificația protocolului TCP și UDP multicast
- `src/main/java/dfs/` - nodul de stocare (`StorageNode`), descoperirea multicast (`MulticastDiscovery`, `Membru`) și clientul (`Client`)
- `src/main/java/dfs/common/Protocol.java` - antete TCP, calcul SHA-256 și transfer pe bucăți
- `scripts/start-local.sh`, `scripts/start-local.bat` - scripturi pentru pornirea locală a 3 noduri
- `scripts/test-integritate.sh`, `scripts/test-integritate.bat` - test de integritate pentru 100 de fișiere aleatorii

## Compilare

Din rădăcina proiectului se rulează:

```bash
mvn -q package
```

## Pornirea nodurilor

Fiecare nod rulează într-un proces separat și primește identificatorul (`--id`), portul TCP (`--port`), directorul local (`--dir`), numărul echipei (`--echipa <k>`, folosit pentru grupul multicast `239.255.0.<k>:4446`) și opțional flag-ul `--coordinator`.

Pornirea a 3 noduri pentru echipa 1, în terminale separate:

```bash
java -cp target/classes dfs.StorageNode --id n1 --port 7001 --dir data/n1 --echipa 1 --coordinator
```

```bash
java -cp target/classes dfs.StorageNode --id n2 --port 7002 --dir data/n2 --echipa 1
```

```bash
java -cp target/classes dfs.StorageNode --id n3 --port 7003 --dir data/n3 --echipa 1
```

Output așteptat la pornirea unui nod și la descoperirea celorlalte noduri:

```text
[n1] pornit: port 7001, director data/n1, rol: coordonator
[n1] grup multicast: 239.255.0.1:4446
11:12:30.145 [n1] nod descoperit: n2@172.30.16.1:7002 (cunosc acum 2)
11:12:33.213 [n1] nod descoperit: n3@172.30.16.1:7003 (cunosc acum 3)
```

Alternativ, cele 3 noduri pot fi pornite cu scriptul dedicat (argumentul opțional este numărul echipei, implicit 1):

```bash
./scripts/start-local.sh 1
```

Pe Windows:

```bat
scripts\start-local.bat 1
```

## Pornirea și comenzile clientului

Clientul primește `--echipa <k>` (și opțional `--coordinator <host:port>`):

```bash
java -cp target/classes dfs.Client --echipa 1 --coordinator localhost:7001
```

Comenzi disponibile în Etapa 2:

- `nodes` - ascultă grupul multicast timp de 3 secunde și afișează nodurile active (`id@adresa:port`)
- `put <idNod> <fișier>` - trimite fișierul către nodul specificat cu verificare SHA-256
- `get <idNod> <nume> <destinație>` - descarcă fișierul în `<destinație>/<nume>` și verifică checksum-ul
- `exit` - închide clientul

Exemplu de sesiune cu 3 noduri pornite:

```text
[client] coordonator configurat: localhost:7001, echipa 1 (239.255.0.1:4446); comenzi: put, get, ls, rm, nodes, exit
nodes
n1@172.30.16.1:7001
n2@172.30.16.1:7002
n3@172.30.16.1:7003
put n2 raport.pdf
STORED ef30595d55510d743f7d1629a9f0e866a2d04c4064321d9a256f46718c8862ee
get n2 raport.pdf ./out
descărcat out\raport.pdf, checksum OK
get n3 raport.pdf ./out
NOT_FOUND
exit
```

## Verificarea integrității

Testul automat încarcă 100 de fișiere aleatorii de dimensiuni diferite pe cele 3 noduri, le descarcă și le compară cu originalele:

```bash
./scripts/test-integritate.sh 1
```

Pe Windows:

```bat
scripts\test-integritate.bat 1
```
