# OpenDial

<img src="artwork/app-icon.png" alt="OpenDial" width="128" />

**Otwarta aplikacja do połączeń i kontaktów dla Androida** – pełny ekran rozmowy, klawiatura z wyszukiwaniem T9, historia połączeń, kontakty z edytorem i vCard, blokowanie numerów, konferencje i statystyki. Bez reklam, bez śledzenia, bez kont.

[![Build & Release](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

## Pobieranie

Najnowszy plik jest zawsze w **[Releases → Latest](../../releases/latest)**. Każdy wypchnięty tag `vX.Y.Z` buduje i publikuje nowe wydanie automatycznie, a aplikacja sama proponuje aktualizację.

1. Pobierz `OpenDial-x.y.z.apk` na telefon.
2. Otwórz plik i zezwól na instalację z tego źródła.
3. Wymagany Android 10 (API 29) lub nowszy.

Aby OpenDial obsługiwał ekran rozmowy, odbieranie, blokowanie i powiadomienia o nieodebranych, ustaw go jako **domyślną aplikację telefonu** – aplikacja proponuje to przy pierwszym uruchomieniu (Ustawienia → Aplikacje domyślne → Telefon).

## Funkcje

### Klawiatura
| Funkcja | Opis |
|---|---|
| **Wyszukiwanie T9** | Cyfry podpowiadają kontakty po nazwie: `5-2-6` → *Jan*. Dopasowanie do początku nazwy, początku kolejnego słowa i do inicjałów, bez względu na polskie znaki (`Łukasz` = `585279`). |
| **Szybkie wybieranie** | Przytrzymaj cyfrę 2–9 na pustej klawiaturze, aby zadzwonić; `1` to poczta głosowa, `0` wpisuje `+`. |
| **Tony i wibracja** | Tony DTMF i wibracja klawiszy – osobno przełączane. |
| **Dwie karty SIM** | Wybór karty przy dzwonieniu albo karta domyślna; na liście połączeń widać, z której karty było połączenie. |
| **Kody MMI** | Numery z `*` i `#` (np. `*100#`) przechodzą do systemu bez formatowania. |
| **Potwierdzanie** | Opcjonalne okno „Zadzwonić?” chroni przed przypadkowym dotknięciem. |

### Ekran rozmowy
- Wyciszanie, głośnik, zestaw Bluetooth i słuchawki przewodowe, zawieszanie, klawiatura DTMF w trakcie rozmowy.
- Drugie połączenie: zamiana, zakończenie i odebranie, zawieszenie i odebranie.
- **Konferencje** – scalanie rozmów, lista uczestników, rozmowa osobno z wybraną osobą, rozłączenie jednego uczestnika.
- Odbieranie przesunięciem gałki (chroni przed odebraniem w kieszeni) albo zwykłymi przyciskami.
- Odrzucenie z wiadomością SMS z listy szybkich odpowiedzi (edytowalnej w ustawieniach).
- Wygaszanie ekranu przy uchu (czujnik zbliżeniowy), wibracja przy odebraniu i przy zakończeniu, pokazywanie ekranu na zablokowanym telefonie.
- Powiadomienie o trwającej rozmowie z licznikiem czasu i powrotem do ekranu rozmowy.

### Historia połączeń
- Przychodzące, wychodzące, nieodebrane, odrzucone, zablokowane i poczta głosowa – z filtrami i wyszukiwaniem.
- **Grupowanie** kolejnych połączeń z tym samym numerem tego samego dnia w jeden wiersz.
- Szczegóły wpisu: wszystkie połączenia z numerem, czas trwania, karta SIM, lokalizacja numeru.
- Dotknięcie wpisu konfigurowalne: rozwinięcie, połączenie albo szczegóły.
- Usuwanie pojedynczych wpisów, całej historii numeru lub całego rejestru; eksport do CSV (średnik i BOM – otwiera się w Excelu i Arkuszach bez kreatora importu).
- Powiadomienia o nieodebranych z oddzwonieniem i SMS-em jednym dotknięciem.

### Kontakty
- Lista z indeksem, wyszukiwaniem (nazwa, numer, T9) i sekcją **„Często”** – najczęściej wybierani rozmówcy.
- Ulubione z szybkim dzwonieniem i SMS-em – zakładkę można całkowicie usunąć z dolnego paska i otwierać listę z ustawień.
- **Edytor kontaktu**: imię, drugie imię, nazwisko, tytuł, przyrostek, pseudonim, firma, stanowisko, wiele numerów i e-maili z rodzajami, adresy, strona WWW, urodziny, notatka, zdjęcie, wybór konta zapisu.
- **Dzwonek na kontakt**, blokowanie osoby, udostępnianie jako vCard, pokazanie adresu na mapie.
- **Duplikaty** – wyszukiwanie kontaktów o tym samym numerze lub nazwie i scalanie ich (bez usuwania danych).
- **Kopia zapasowa**: eksport i import `.vcf` (Google, iPhone, Outlook) z pominięciem kontaktów, które już są w telefonie.
- Zaznaczanie wielu kontaktów: usuwanie, udostępnianie, dodawanie do ulubionych.

### Blokowanie i filtry
| Filtr | Działanie |
|---|---|
| **Zablokowane numery** | Systemowa lista blokad – wspólna z resztą telefonu. |
| **Reguły prefiksów** | `0700` (linie płatne), `+48 22 30*` (telemarketing) – gwiazdka zastępuje dowolne cyfry. |
| **Numery ukryte** | Połączenia bez numeru („Numer prywatny”). |
| **Spoza kontaktów** | Odrzucanie albo tylko wyciszanie – nieznane numery dzwonią bez dźwięku i nadal można je odebrać. |

Zablokowane połączenia mogą być widoczne w „Ostatnich” i zgłaszane cichym powiadomieniem.

### Statystyki
Liczba połączeń, łączny i średni czas rozmów, najdłuższa rozmowa, najczęstsi rozmówcy, rozkład po dniach tygodnia i porach dnia – za 7 dni, 30 dni, 3 miesiące albo cały rejestr.

### Interfejs
- Styl „glass” jak w pozostałych aplikacjach z tej rodziny: ciemne przezierne panele, miękkie cienie, płynne przejścia między ekranami.
- Pełna obsługa polskiego i angielskiego – automatycznie według języka systemu albo wybrany w ustawieniach (również dla numerów i dat).
- Wibracja przycisków, wybór ekranu startowego, ukrywanie zakładki „Ulubione”, kolejność nazw (Imię Nazwisko / Nazwisko, Imię), ukrywanie kontaktów bez numeru.
- Własna ciemna paleta (Material 3), ikona adaptacyjna i monochromatyczna (motyw ikon w Androidzie 13+).
- Zakładki bez wielkich tytułów: w nagłówku „Ostatnich” i „Kontaktów” stoi pole wyszukiwania, więc listy zaczynają się wyżej.

### Aktualizacje
Aplikacja sprawdza **GitHub Releases** przy uruchomieniu (najwyżej raz na 6 godzin), pokazuje opis wydania, pobiera APK z pokazanym postępem i instaluje je – bez sklepu i bez konta. Sprawdzanie można wyłączyć i uruchamiać ręcznie w ustawieniach.

## Prywatność

Aplikacja nie ma reklam, analityki ani kont. Kontakty i historia połączeń nie opuszczają telefonu; jedyne połączenie z internetem to zapytanie do GitHuba o najnowsze wydanie i pobranie APK. Eksporty (vCard, CSV) zapisujesz sam, w wybranym miejscu.

## Architektura

```
app/src/main/java/com/blacksingularities/opencall/
  call/        CallService (InCallService), CallManager, CallActivity, ScreeningService,
               CallNotifications, Dialer, Receivers – telefonia i powiadomienia
  data/        ContactsRepo, CallLogRepo, BlockRepo, Settings, VCard, PhoneText, Models
  ui/          AppRoot (nawigacja), AppViewModel (stan aplikacji), InCallScreen, Keypad,
               Avatar, Format, UpdateBanner, components/, screens/, theme/ (styl „glass”)
  i18n/        I18n, En – polskie teksty w kodzie, angielskie w mapie
  update/      Updates (GitHub API, porównanie wersji), UpdateManager (pobieranie, instalacja)
```

Warstwa danych nie ma Androida tam, gdzie nie musi: `PhoneText`, `VCard`, `Updates` i `SettingsStore` są czystym Kotlinem i mają testy jednostkowe (`app/src/test`). Test `TranslationsTest` skanuje źródła i nie przepuszcza napisu bez angielskiego odpowiednika.

## Budowanie

```bash
./gradlew :app:testDebugUnitTest          # testy jednostkowe
./gradlew :app:assembleDebug              # APK do debugowania
./gradlew :app:assembleRelease -PversionName=0.1.0 -PversionCode=1
```

Potrzebny JDK 17+ i Android SDK (ścieżka w `local.properties` jako `sdk.dir`). Bez zmiennych `SIGNING_*` wydanie podpisuje się kluczem debug.

### Klucz podpisu

Wydania z CI podpisuje klucz PKCS12 przechowywany w sekretach repozytorium:

| Sekret | Zawartość |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | plik `.p12` zakodowany base64 |
| `SIGNING_STORE_PASSWORD` | hasło magazynu |
| `SIGNING_KEY_PASSWORD` | hasło klucza |
| `SIGNING_KEY_ALIAS` | alias klucza (`opencall`) |

Bez tych sekretów CI nadal zbuduje APK – podpisze je kluczem debug, ale wtedy aktualizacja „na wierzch” nie będzie możliwa.

## Licencja

MIT – patrz [LICENSE](LICENSE).
