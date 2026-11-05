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
| **Kody serwisowe** | `*#06#` pokazuje IMEI, `*#07#` prowadzi do wartości SAR, a `*#*#…#*#*` trafia do systemu – zaraz po wpisaniu, bez dzwonienia. |
| **Numery alarmowe** | 112, 997 i inne numery znane systemowi dzwonią od razu: bez potwierdzenia, bez wyboru karty, także na prośbę innych aplikacji. Przez 30 minut po takim połączeniu filtry i asystent AI nie zatrzymają oddzwaniających służb. |
| **Potwierdzanie** | Opcjonalne okno „Zadzwonić?” chroni przed przypadkowym dotknięciem. |

### Ekran rozmowy
- Wyciszanie, głośnik, zestaw Bluetooth i słuchawki przewodowe, zawieszanie, klawiatura DTMF w trakcie rozmowy.
- Drugie połączenie: zamiana, zakończenie i odebranie, zawieszenie i odebranie.
- **Konferencje** – scalanie rozmów, lista uczestników, rozmowa osobno z wybraną osobą, rozłączenie jednego uczestnika.
- Odbieranie przesunięciem gałki (chroni przed odebraniem w kieszeni) albo zwykłymi przyciskami.
- Odrzucenie z wiadomością SMS z listy szybkich odpowiedzi (edytowalnej w ustawieniach).
- Wygaszanie ekranu przy uchu (czujnik zbliżeniowy), wibracja przy odebraniu i przy zakończeniu, pokazywanie ekranu na zablokowanym telefonie.
- Powiadomienie o trwającej rozmowie z licznikiem czasu i powrotem do ekranu rozmowy.
- **RTT** – rozmowa tekstowa w czasie rzeczywistym (gdy wspiera ją sieć), litery idą do rozmówcy już w trakcie pisania; tryb TTY w ustawieniach ułatwień dostępu w połączeniach.
- Etykiety **Wi-Fi** i **HD**, gdy połączenie idzie przez Wi-Fi Calling albo ma dźwięk HD (VoLTE / VoWiFi).
- Pełna obsługa TalkBacka i Switch Access: każdy przycisk ma nazwę i akcję, przy czytniku ekranu odbiera się przyciskami zamiast gałki.

### Historia połączeń
- Przychodzące, wychodzące, nieodebrane, odrzucone, zablokowane i poczta głosowa – z filtrami i wyszukiwaniem.
- **Grupowanie** kolejnych połączeń z tym samym numerem tego samego dnia w jeden wiersz.
- Szczegóły wpisu: wszystkie połączenia z numerem, czas trwania, karta SIM, lokalizacja numeru.
- Dotknięcie wpisu konfigurowalne: rozwinięcie, połączenie albo szczegóły.
- **Poczta głosowa** – odsłuch nagrań prosto z listy, gdy operator dostarcza je do telefonu (wizualna poczta głosowa).
- Usuwanie pojedynczych wpisów, całej historii numeru lub całego rejestru; eksport do CSV (średnik i BOM – otwiera się w Excelu i Arkuszach bez kreatora importu).
- Powiadomienia o nieodebranych z oddzwonieniem i SMS-em jednym dotknięciem.

### Kontakty
- Lista z indeksem, wyszukiwaniem (nazwa, numer, T9) i sekcją **„Często”** – najczęściej wybierani rozmówcy.
- Ulubione z szybkim dzwonieniem i SMS-em – zakładkę można całkowicie usunąć z dolnego paska i otwierać listę z ustawień.
- **Edytor kontaktu**: imię, drugie imię, nazwisko, tytuł, przyrostek, pseudonim, firma, stanowisko, wiele numerów i e-maili z rodzajami, adresy, strona WWW, urodziny, notatka, zdjęcie, wybór konta zapisu.
- **Kontakty na karcie SIM** (Android 12+): zapis, edycja i usuwanie nazwy i numeru na samej karcie przez `SimPhonebookContract`, z licznikami miejsca (polskie znaki zajmują więcej) i odczytem kontrolnym po zapisie. Pomysł zapisu z weryfikacją na karcie podsunął Ami982, autor aplikacji Kontakty SIM.
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
- Motyw jasny, ciemny albo według systemu (Material 3); ekran rozmowy zawsze ciemny. Ikona adaptacyjna i monochromatyczna (motyw ikon w Androidzie 13+).
- **Skróty i widżet** – przytrzymanie ikony aplikacji daje „Nowy kontakt” i ulubionych, widżet „Ulubione” dzwoni jednym dotknięciem.
- Zakładki bez wielkich tytułów: w nagłówku „Ostatnich” i „Kontaktów” stoi pole wyszukiwania, więc listy zaczynają się wyżej.

### Asystent AI – „Kto dzwoni naprawdę”
Nieznany numer nie dzwoni. Odbiera AI, pyta, kto dzwoni i po co, a ważne rozmowy przełącza do Ciebie z jednym zdaniem streszczenia. Tryb jest domyślnie wyłączony (Ustawienia → Asystent AI).

**Jak to działa.** Android nie daje zwykłej aplikacji dostępu do dźwięku rozmowy GSM: przechwytywanie wymaga uprawnień aplikacji systemowych. Dlatego rozmawia agent głosowy w chmurze, na Twoim koncie:

1. Filtr połączeń rozpoznaje numer spoza kontaktów i odrzuca go (albo wycisza w trybie cichym).
2. Operator przekierowuje połączenie na numer agenta (`**67*numer#` przy „zajętym”, `**61*numer**czas#` przy braku odpowiedzi).
3. Agent rozmawia według Twoich zasad: przełącza ważne sprawy, kończy spam, od reszty przyjmuje wiadomość.
4. Przełączone połączenie przychodzi z numeru agenta; aplikacja przepuszcza je i pokazuje jako „Asystent AI”. Agent mówi Ci, kto dzwoni i po co, zanim połączy rozmówcę.
5. Po rozmowie aplikacja pobiera streszczenie i pokazuje powiadomienie z „Oddzwoń” i „Zablokuj”.

| Platforma | Co robi aplikacja | Co trzeba przygotować |
|---|---|---|
| **Vapi** | Tworzy agenta, przypisuje go do numeru, ustawia rozmowę, przełączanie i analizę. | Klucz API i numer. Streszczenie głosem przy przełączeniu wymaga numeru Twilio. |
| **Retell AI** | Ustawia rozmowę, przełączanie i analizę istniejącego agenta. | Klucz API, agent z Retell LLM przypisany do numeru i opublikowany. |
| **ElevenLabs Agents** | Ustawia rozmowę, przełączanie i analizę istniejącego agenta. | Klucz API, agent przypisany do numeru Twilio lub SIP. |
| **Własny serwer** | Wysyła konfigurację i odczytuje rozmowy. | Serwer HTTPS w formacie opisanym niżej. |

Platforma nalicza opłaty za minuty, a operator może doliczyć przekierowanie. Numer agenta najlepiej mieć w tym samym kraju.

**Zabezpieczenia.** Gdy czegoś brakuje, telefon po prostu dzwoni: bez dostępu do kontaktów, w roamingu i poza ustawionymi godzinami nic nie jest odrzucane. Drugi telefon z tego samego numeru w ciągu kilku minut i numery, do których dzwoniłeś, dzwonią normalnie. Jeśli połączenie nie dotrze do agenta, dostajesz powiadomienie. Agent traktuje słowa dzwoniącego jako informację, a nie polecenia, nie zdradza Twoich danych i niczego w Twoim imieniu nie akceptuje.

**Własny serwer.** Żądania niosą nagłówek `Authorization: Bearer <klucz>`. `POST <adres>` przyjmuje konfigurację: `systemPrompt`, `firstMessage`, `transferNumber`, `language`, `maxSeconds`, `summaryInstruction`. `GET <adres>?since=<ms>` zwraca:

```json
{"calls": [{"id": "1", "number": "+48500600700", "startedAt": 1793800000000, "endedAt": 1793800060000,
  "ended": true, "summary": "Kurier z paczką.", "verdict": "important", "caller": "DPD",
  "transcript": "…", "transferred": true}]}
```

`verdict` to `important`, `message` albo `spam`.

### Aktualizacje
Aplikacja sprawdza **GitHub Releases** przy uruchomieniu (najwyżej raz na 6 godzin), pokazuje opis wydania, pobiera APK z pokazanym postępem i instaluje je – bez sklepu i bez konta. Sprawdzanie można wyłączyć i uruchamiać ręcznie w ustawieniach.

## Prywatność

Aplikacja nie ma reklam, analityki ani kont. Kontakty i historia połączeń nie opuszczają telefonu; z internetem łączy się po najnowsze wydanie i APK z GitHuba, a jeśli włączysz asystenta AI, także z wybraną platformą. Wtedy rozmowy z nieznanymi numerami, ich transkrypcje i Twój numer trafiają na Twoje konto u tej platformy. Klucz API jest szyfrowany w Android Keystore, a klucz i historia asystenta nie wchodzą do kopii zapasowej. Eksporty (vCard, CSV) zapisujesz sam, w wybranym miejscu.

## Architektura

```
app/src/main/java/com/blacksingularities/opencall/
  ai/          AiConfig, AiPrompt, AiPayloads, AiModels (czysty Kotlin), AgentPlatform, AiSync,
               AiLog, SecretStore – asystent AI i platformy agentów głosowych
  call/        CallService (InCallService), CallManager, CallActivity, ScreeningService,
               CallNotifications, Dialer, Receivers – telefonia i powiadomienia
  data/        ContactsRepo, CallLogRepo, BlockRepo, Settings, VCard, PhoneText, Models
  ui/          AppRoot (nawigacja), AppViewModel (stan aplikacji), InCallScreen, Keypad,
               Avatar, Format, UpdateBanner, components/, screens/, theme/ (styl „glass”)
  i18n/        I18n, En – polskie teksty w kodzie, angielskie w mapie
  update/      Updates (GitHub API, porównanie wersji), UpdateManager (pobieranie, instalacja)
```

Warstwa danych nie ma Androida tam, gdzie nie musi: `PhoneText`, `VCard`, `Updates`, `SettingsStore` i rdzeń asystenta AI są czystym Kotlinem i mają testy jednostkowe (`app/src/test`). Test `TranslationsTest` skanuje źródła i nie przepuszcza napisu bez angielskiego odpowiednika.

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
