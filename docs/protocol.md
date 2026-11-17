# Protocolul de comunicație (Etapa 2)

Sistemul folosește două canale de comunicație:
1. TCP pentru transferul fișierelor și comenzile de citire/scriere către noduri.
2. UDP multicast (`239.255.0.<k>:4446`, unde `<k>` este numărul echipei) pentru anunțarea și descoperirea nodurilor.

## Regula de încadrare pe TCP

Deoarece TCP este un flux continuu de octeți, fiecare mesaj începe cu un antet text scris cu `DataOutputStream.writeUTF` și citit cu `DataInputStream.readUTF`. Astfel antetul are lungimea prefixată și nu se amestecă cu datele binare ale fișierului.

Atunci când un mesaj transportă conținutul unui fișier, dimensiunea în octeți (`<dimensiune>`) este trecută întotdeauna în antet, înaintea octeților propriu-ziși. Receptorul citește întâi antetul, extrage `<dimensiune>` și apoi citește din socket exact `<dimensiune>` octeți pe bucăți de 64 KB. Dacă fluxul se închide înainte de primirea tuturor octeților, transferul eșuează cu `EOFException`.

## Tabelul mesajelor

| Mesaj | Canal | Sens | Antet / format | Conținut după antet |
| :--- | :--- | :--- | :--- | :--- |
| `PUT` | TCP | client -> nod | `PUT <nume> <dimensiune> <sha256>` | Exact `<dimensiune>` octeți (conținutul fișierului) |
| `STORED` | TCP | nod -> client | `STORED <sha256>` | Fără conținut |
| `ERROR` | TCP | nod -> client | `ERROR <motiv>` | Fără conținut |
| `GET` | TCP | client -> nod | `GET <nume>` | Fără conținut |
| `FILE` | TCP | nod -> client | `FILE <dimensiune> <sha256>` | Exact `<dimensiune>` octeți (conținutul fișierului) |
| `NOT_FOUND` | TCP | nod -> client | `NOT_FOUND` | Fără conținut |
| `HELLO` | UDP multicast | nod -> grup | `HELLO;<id>;<portTCP>` | Fără conținut (adresa IP se ia din `packet.getAddress()`) |

## Reguli de validare

- Numele de fișier care conține `/`, `\` sau `..` este refuzat cu `ERROR nume invalid` înainte de citirea octeților.
- Fișierele cu dimensiune peste 500 MB (524288000 octeți) sunt refuzate cu `ERROR fișier prea mare` înainte de citirea octeților.
- La `PUT`, nodul scrie datele într-un fișier temporar `<nume>.part`, verifică SHA-256 și face redenumire atomică (`ATOMIC_MOVE`, `REPLACE_EXISTING`) doar dacă checksum-ul corespunde; altfel șterge `.part` și întoarce `ERROR checksum diferit`.
- La `GET`, fișierele `.part` sunt ignorate; dacă fișierul complet nu există, nodul întoarce `NOT_FOUND`.
- Antetul necunoscut sau cu număr greșit de câmpuri primește `ERROR comanda necunoscută`.
- La descoperirea UDP multicast, nodurile ignoră propriile anunțuri (`<id>` propriu) și datagramele care nu au exact 3 câmpuri separate prin `;` sau nu încep cu `HELLO`.
