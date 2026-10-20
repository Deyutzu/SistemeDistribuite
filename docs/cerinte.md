# Cerințe și arhitectură - Sistem de stocare distribuită de fișiere

Acest document descrie specificațiile funcționale, modelul de date, protocolul de comunicație și scenariul de testare pentru sistemul de stocare distribuită de fișiere implementat în Java.

## 1. Operațiile sistemului

Fiecare operație expusă de sistem este descrisă mai jos prin comanda rulată, pașii parcurși între componente, rezultatul întors utilizatorului și situațiile de eroare tratate.

### Încărcarea unui fișier (`put`)

| Element | Descriere |
| :--- | :--- |
| Comanda | `put raport.pdf` |
| Pașii prin sistem | 1. Clientul verifică existența și dimensiunea fișierului local, calculează checksum-ul SHA-256 și cere coordonatorului două noduri active și distincte.<br>2. Coordonatorul verifică să nu existe deja numele în catalog, generează un UUID pentru fișier și întoarce adresele a două noduri active.<br>3. Clientul trimite conținutul fișierului către primul nod.<br>4. Primul nod salvează fișierul pe disc și îl copiază mai departe pe al doilea nod.<br>5. După ce ambele noduri confirmă scrierea pe disc, coordonatorul înregistrează metadatele fișierului în catalog. |
| Rezultatul | Se afișează ID-ul (UUID) alocat fișierului și ID-urile celor două noduri pe care au fost salvate copiile. |
| Erorile tratate | - Mai puțin de 2 noduri active în sistem: coordonatorul refuză cererea, nu se stochează nimic.<br>- Fișierul local nu există sau nu poate fi citit: clientul oprește comanda local.<br>- O copie eșuează în timpul transferului: se cere coordonatorului un alt nod activ și se reia copierea pe nodul nou. |

### Descărcarea unui fișier (`get`)

| Element | Descriere |
| :--- | :--- |
| Comanda | `get raport.pdf ./out/` |
| Pașii prin sistem | 1. Clientul cere coordonatorului metadatele și locațiile copiilor pentru `raport.pdf`.<br>2. Coordonatorul caută numele în catalog și returnează UUID-ul, dimensiunea, checksum-ul SHA-256 și lista nodurilor care dețin copiile.<br>3. Clientul descarcă fișierul de la primul nod din listă.<br>4. Clientul calculează checksum-ul SHA-256 al datelor primite și îl compară cu cel din metadate, apoi salvează fișierul în `./out/raport.pdf`. |
| Rezultatul | Fișierul este salvat local în directorul `./out/` cu checksum-ul verificat. |
| Erorile tratate | - Fișier necunoscut: coordonatorul răspunde că numele nu există în catalog.<br>- Primul nod nu răspunde sau checksum-ul calculat este greșit: clientul șterge datele parțiale și încearcă automat descărcarea de la a doua copie.<br>- Nicio copie nu este accesibilă: clientul afișează o eroare explicită și nu lasă fișier corupt pe disc. |

### Listarea fișierelor (`ls`)

| Element | Descriere |
| :--- | :--- |
| Comanda | `ls` |
| Pașii prin sistem | 1. Clientul trimite coordonatorului o cerere de listare.<br>2. Coordonatorul citește catalogul de metadate și trimite lista fișierelor înregistrate. |
| Rezultatul | Se afișează numele fiecărui fișier, UUID-ul, dimensiunea în octeți, checksum-ul SHA-256 și lista nodurilor care păstrează copiile (sau un mesaj că nu există fișiere stocate). |
| Erorile tratate | - Coordonatorul nu răspunde: clientul raportează eroarea de conexiune. |

### Ștergerea unui fișier (`rm`)

| Element | Descriere |
| :--- | :--- |
| Comanda | `rm raport.pdf` |
| Pașii prin sistem | 1. Clientul trimite coordonatorului cererea de ștergere pentru `raport.pdf`.<br>2. Coordonatorul caută fișierul în catalog, îl elimină din metadate și trimite comenzi de ștergere către nodurile active care dețin copiile.<br>3. Nodurile șterg fișierul asociat UUID-ului din directorul lor local și confirmă operația. |
| Rezultatul | Confirmare că fișierul a fost eliminat din catalog și de pe nodurile active. |
| Erorile tratate | - Fișierul nu există în catalog: coordonatorul întoarce eroare că numele nu a fost găsit.<br>- Un nod care deține o copie este căzut în momentul ștergerii: ștergerea reușește pe nodul activ și în catalog, iar copia rămasă pe nodul căzut este tratată la reconectare. |

#### Comportamentul la `rm` când un nod cu o copie este căzut

Dacă în momentul rulării comenzii `rm raport.pdf` unul dintre cele două noduri care păstrează copiile este căzut, coordonatorul nu blochează operația. El șterge imediat intrarea pentru `raport.pdf` din catalogul de metadate și trimite comanda de ștergere către nodul rămas activ. Din acel moment, fișierul nu mai apare la comanda `ls` și nu mai poate fi cerut prin `get`.

Dacă nodul căzut este repornit mai târziu, la reintrarea în cluster acesta trimite coordonatorului lista de UUID-uri pe care le are în directorul său de date. Coordonatorul compară lista primită cu catalogul curent, observă că UUID-ul fișierului șters nu mai există în metadate și îi trimite nodului comandă să șteargă fișierul orfan de pe disc.

### Afișarea stării nodurilor (`nodes`)

| Element | Descriere |
| :--- | :--- |
| Comanda | `nodes` |
| Pașii prin sistem | 1. Clientul cere coordonatorului situația la zi a nodurilor din cluster.<br>2. Coordonatorul citește registrul intern de noduri și returnează lista completă. |
| Rezultatul | Se afișează pentru fiecare nod ID-ul, adresa și portul, starea curentă (`ACTIV`, `SUSPECT` sau `CAZUT`) și timpul scurs de la ultimul semn de viață. |
| Erorile tratate | - Coordonatorul nu poate fi contactat: clientul afișează eroare de comunicare. |

### Adăugarea unui nod nou la rulare

| Element | Descriere |
| :--- | :--- |
| Comanda | `java -cp target/classes dfs.StorageNode --id n4 --port 7004 --dir data/n4` |
| Pașii prin sistem | 1. Procesul noului nod pornește, își creează directorul local `data/n4` și începe să asculte pe portul 7004.<br>2. Nodul își anunță prezența în rețea și începe trimiterea periodică de semne de viață.<br>3. Coordonatorul îl detectează, îl înregistrează în lista de noduri cu starea `ACTIV` și îl ia în calcul pentru următoarele comenzi `put`. |
| Rezultatul | Nodul `n4` devine vizibil în comanda `nodes` ca `ACTIV` în cel mult 3 secunde, fără oprirea sau repornirea celorlalte noduri. |
| Erorile tratate | - Portul este deja ocupat sau ID-ul este duplicat: procesul se oprește cu mesaj de eroare.<br>- Directorul local nu poate fi creat din lipsă de drepturi: nodul afișează eroarea și se închide. |

## 2. Decizii de proiectare pentru cerințele nespecificate

1. Identificarea fișierelor: utilizatorul lucrează cu numele fișierului (de exemplu `raport.pdf`), însă intern coordonatorul asociază fiecărui fișier un `UUID` unic. Pe discul nodurilor de stocare fișierele sunt salvate folosind acest `UUID` ca nume de fișier, pentru a evita conflictele de căi sau caractere speciale.
2. Nume duplicate la încărcare: dacă utilizatorul rulează `put` pentru un nume de fișier care există deja în catalogul coordonatorului, cererea este refuzată direct cu un mesaj clar de eroare. Nu se face suprascriere automată; dacă utilizatorul vrea să înlocuiască fișierul, trebuie să ruleze mai întâi `rm` și apoi `put`.
3. Dimensiunea maximă a unui fișier: sistemul acceptă fișiere de cel mult 500 MB (524288000 octeți). Clientul verifică dimensiunea local înainte de a cere alocarea nodurilor și refuză transferul dacă limita este depășită.

## 3. Modelul de date

Coordonatorul menține în memorie două structuri principale: catalogul de metadate al fișierelor și registrul nodurilor din cluster.

### Metadatele fișierului (`FileMetadata`)

| Câmp | Tip | Descriere |
| :--- | :--- | :--- |
| `fileId` | `UUID` | Identificatorul intern unic al fișierului, folosit ca nume pe discul nodurilor. |
| `fileName` | `String` | Numele original al fișierului folosit în comenzile clientului (ex. `raport.pdf`). |
| `size` | `long` | Dimensiunea exactă a fișierului în octeți. |
| `sha256Checksum` | `String` | Valoarea hash SHA-256 în format hexazecimal (64 de caractere), calculată la încărcare. |
| `replicaNodeIds` | `List<String>` | Lista cu ID-urile nodurilor pe care se află copiile fișierului (ex. `["n2", "n3"]`). |

### Informațiile despre un nod (`NodeInfo`)

| Câmp | Tip | Descriere |
| :--- | :--- | :--- |
| `id` | `String` | Identificatorul unic al nodului (ex. `n1`, `n2`). |
| `address` | `String` | Adresa IP sau hostname-ul la care ascultă nodul. |
| `port` | `int` | Portul TCP pe care nodul primește conexiuni. |
| `state` | `NodeState` | Starea curentă a nodului: `ACTIV`, `SUSPECT` sau `CAZUT`. |
| `lastHeartbeatMs` | `long` | Momentul ultimului semn de viață primit, exprimat în milisecunde (`System.currentTimeMillis()`). |

### Reconstrucția metadatelor (pentru Etapa 6)

Catalogul de metadate ținut de coordonator poate fi reconstruit integral din rapoartele nodurilor de stocare. Fiecare nod salvează lângă fișierul efectiv și metadatele de bază ale acestuia (`UUID`, nume, dimensiune, checksum SHA-256). Dacă procesul coordonator cade și un alt nod este ales ca nou coordonator (Etapa 6), fiecare nod activ își trimite lista locală de fișiere către noul coordonator, care reface astfel catalogul și harta copiilor fără pierdere de date.

## 4. Cerințe nefuncționale

| Cerință | Obiectiv | Cum se măsoară | Etapa |
| :--- | :--- | :--- | :--- |
| Integritate | 0 checksum-uri diferite din 100 de descărcări | Se încarcă un fișier, se descarcă de 100 de ori și se compară SHA-256 al fiecărei copii descărcate cu fișierul inițial. | Etapa 2 |
| Scalabilitate | Debit total mai mare cu 3 noduri față de 1 nod la transferul a 10 fișiere de 100 MB | Se măsoară timpul total de transfer pentru 10 fișiere de 100 MB pe un singur nod și apoi pe 3 noduri în paralel, calculând debitul în MB/s. | Etapa 3 |
| Descoperire | Nod nou vizibil în maximum 3 secunde | Se pornește un nod nou în timp ce sistemul rulează și se cronometrează timpul până când apare cu starea `ACTIV` în comanda `nodes`. | Etapa 4 |
| Detectarea defectelor | Nod oprit cu `kill -9` marcat `CAZUT` în maximum 5 secunde | Se oprește forțat procesul unui nod și se măsoară durata până când coordonatorul îi schimbă starea în `CAZUT`. | Etapa 4 |
| Replicare | Exact 2 copii păstrate pe noduri distincte | După fiecare `put` și după căderea unui nod, se verifică în `ls` și în directoarele `data/` că fișierul există pe două noduri diferite. | Etapa 5 |
| Disponibilitate | 100% din fișiere rămân descărcabile după oprirea oricărui nod | Se încarcă mai multe fișiere, se oprește oricare dintre nodurile cu copii și se rulează `get` pentru toate fișierele din sistem. | Etapa 5 |
| Coordonare | Coordonator nou ales și funcțional în maximum 10 secunde | Se oprește coordonatorul curent cu `kill -9` și se măsoară timpul până când un alt nod preia rolul și răspunde la `ls` și `get`. | Etapa 6 |

## 5. Ipoteze de lucru

- Sistemul rulează într-o rețea locală (LAN sau pe aceeași mașină în procese separate), cu latență mică și lărgime de bandă suficientă pentru transferul fișierelor.
- La un moment dat poate cădea cel mult un nod înainte ca sistemul să detecteze defectul și să refacă numărul de copii sau să aleagă un nou coordonator.
- Defectele nodurilor sunt de tip crash (procesul se oprește brusc sau nu mai răspunde la rețea), nu defecte bizantine (nodurile nu trimit mesaje falsificate intenționat și nu alterează protocolul).
