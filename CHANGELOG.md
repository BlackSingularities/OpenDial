# Zmiany

## 0.4.5

- Bez żadnych wzmianek o GitHubie w aplikacji: nowy adres serwera aktualizacji, komunikaty błędów bez nazw zewnętrznych serwerów.

## 0.4.4

- Komunikat o nowej wersji bez listy zmian, linków i informacji, skąd pobierana jest aktualizacja.

## 0.4.3

- Szczypanie w kontaktach działa jak w Poweramp: rozsuwanie palców powiększa listę, aż zamieni się w coraz większe kafelki, a zsuwanie zmniejsza je i zagęszcza listę.

## 0.4.2

- Czyszczenie historii połączeń przeniesione z górnego paska do Ustawień (Historia i kontakty), dzięki czemu filtry mają więcej miejsca.

## 0.4.1

- Filtry historii (wszystkie, nieodebrane i inne) są teraz w górnym pasku, bez dodatkowego rzędu pod nim, jak w OpenSMS.
- Wyszukiwanie to przycisk z lupą: rozwija się po dotknięciu, a lista zmienia się dopiero, gdy coś wpiszesz. Tak samo w kontaktach.
- Powiadomienie o aktualizacji nie pokazuje już listy zmian z GitHuba.

## 0.4.0

- **Tryb „Kto dzwoni naprawdę”.** Nieznany numer nie dzwoni: odbiera asystent AI, pyta, kto dzwoni i w jakiej sprawie, a ważne rozmowy przełącza do Ciebie z jednym zdaniem streszczenia. Fotowoltaika i „badania opinii” kończą się na asystencie. Tryb jest domyślnie wyłączony.
- Działa z **Vapi, Retell AI, ElevenLabs Agents** albo własnym serwerem, na Twoim koncie i Twoim kluczu API. Model AI i głos wybierasz w aplikacji (OpenAI, Claude, Gemini, Groq i inne, zależnie od platformy).
- Rozmowę prowadzi agent w chmurze, bo Android nie daje zwykłej aplikacji dostępu do dźwięku połączenia. Telefon odrzuca nieznany numer, a operator przekierowuje go na numer agenta; do wyboru jest też tryb cichy, w którym telefon dzwoni bez dźwięku i można odebrać.
- Pełna konfiguracja: ton, język, powitanie, co jest ważne, czego nie chcesz, własne wskazówki, limit czasu, godziny działania, numery ukryte, pomijanie w roamingu. Drugi telefon z rzędu i oddzwonienia na numery, do których dzwoniłeś, dzwonią normalnie.
- Po rozmowie: powiadomienie ze streszczeniem, „Oddzwoń” i „Zablokuj”, historia asystenta z transkrypcjami, opcjonalne blokowanie spamu. Gdy połączenie nie dotrze do agenta, dostajesz o tym powiadomienie.
- Klucz API jest szyfrowany w telefonie, a klucz i treści rozmów nie trafiają do kopii zapasowej. Agent zawsze mówi dzwoniącemu, że jest AI.

## 0.3.2

- Czerwona plakietka aktualizacji na przycisku ustawień nie jest już obcinana.
- Klawiatura w widoku „Telefon” chowa się i wysuwa płynnie, a historia połączeń rośnie razem z nią – bez chwilowego pustego miejsca.

## 0.3.1

- **Kadrowanie zdjęcia kontaktu naprawione:** wnętrze koła pokazuje teraz zdjęcie (wcześniej było czarne), przyciemniony jest tylko obszar poza kadrem.
- Klawiatura w widoku „Telefon” ma czarne tło jak dolny pasek i płynnie w niego przechodzi; zwija się też przeciągnięciem w dół.
- Filtry historii (wszystkie, nieodebrane, przychodzące, wychodzące, zablokowane) jako ikonki – nazwę ma tylko wybrany filtr.
- Dolny pasek w tym samym stylu: same ikony, a otwarta zakładka to pigułka z ikoną i nazwą.

## 0.3.0

- **Zegarek Wear OS.** Osobna aplikacja na zegarek: ekran połączenia przychodzącego (odbierz, odrzuć, wycisz dzwonek, odrzuć SMS-em), ekran rozmowy z licznikiem, mikrofonem, głośnikiem, zawieszeniem, klawiaturą DTMF oraz zamianą i scalaniem przy dwóch rozmowach, a poza rozmową lista ulubionych i ostatnich numerów do dzwonienia z nadgarstka.
- Zegarek sam budzi ekran powiadomieniem o dzwonieniu, nawet gdy aplikacja na nim nie jest otwarta; powiadomienie telefonu nie jest wtedy mostkowane, żeby się nie dublowało.
- Nowy przełącznik „Sterowanie z zegarka” w ustawieniach rozmowy; bez sparowanego zegarka z OpenDial cała ścieżka jest nieaktywna.
- Wydania zawierają drugi plik `OpenDial-Watch-x.y.z.apk`; aktualizacje telefonu dalej pobierają tylko swój APK.

## 0.2.0

- **Nowa nazwa: OpenDial.** Repozytorium przeniesione na BlackSingularities/OpenDial; zainstalowana aplikacja aktualizuje się dalej bez reinstalacji.
- Historia połączeń i klawiatura w jednym widoku „Telefon”, jak w Samsungu czy Huaweiu: klawiatura wysuwa się nad listą, przewinięcie historii ją zwija, a wpisane cyfry od razu pokazują pasujące kontakty (z podświetleniem dopasowania). Dolny pasek ma już tylko trzy zakładki.
- Nowy ekran rozmowy: poświata w kolorze stanu (dzwoni, rozmowa, zawieszone, koniec), rozchodzące się kręgi wokół awatara przy dzwonieniu i łączeniu, licznik czasu z migającą kropką i szeroki przycisk rozłączania.
- Nowy ekran połączenia przychodzącego: „Wycisz dzwonek” bez odrzucania, szybka wiadomość i suwak z płynącymi strzałkami.
- Kadrowanie zdjęcia kontaktu: przesuwanie, przybliżanie dwoma palcami, dwuklik i obrót o 90°.
- Kontakty jako lista albo kafelki (4, 3 lub 2 w rzędzie) – zmiana płynnym szczypaniem dwoma palcami; wybrany układ jest zapamiętywany.

## 0.1.6

- Dopasowane kolorystycznie tła ikon aplikacji i spójne zasoby graficzne.
- Nowa ikona aplikacji, warianty systemowe i odświeżona grafika na stronie projektu.

## 0.1.3

- Nowa strona aplikacji z opisem funkcji.
- Jednolita informacja o autorze (Adam Rędzikowski · redzikowski.com) i przycisk „Postaw kawę”.
- Usunięte odnośniki do repozytorium z interfejsu.

## 0.1.2

- Nowa ikona: sama czerwona słuchawka, bez białej karty pod nią – także na ekranie powitalnym i w wersji monochromatycznej.
- Żadna zakładka nie ma już wielkiego tytułu. „Kontakty” dostały w to miejsce pole wyszukiwania (obok dodawania kontaktu i ustawień), a „Ulubione” i „Klawiatura” – sam rząd przycisków. Listy zaczynają się wyżej.
- Z ustawień zniknął odnośnik do repozytorium; numer wersji został w stopce.

## 0.1.1

- Zakładka „Ostatnie” bez tytułu: pole wyszukiwania stoi teraz w nagłówku obok czyszczenia historii i ustawień, a filtry przeszły pod nie – lista zaczyna się wyżej.
- Zakładkę „Ulubione” można całkowicie usunąć z dolnego paska (Ustawienia → Historia i kontakty). Lista ulubionych jest wtedy dostępna z ustawień, a „Ekran startowy” przestaje ją proponować.
- Usunięty przycisk WhatsApp ze szczegółów kontaktu – zostają połączenie, SMS, e-mail i udostępnianie.

## 0.1.0

Pierwsze wydanie (wtedy jeszcze jako OpenCall) – otwartej aplikacji do połączeń i kontaktów.

- **Klawiatura** z wyszukiwaniem T9 (`5-2-6` → *Jan*, bez względu na polskie znaki), szybkim wybieraniem pod cyframi 2–9, tonami DTMF, wibracją klawiszy, wyborem karty SIM, kodami MMI i opcjonalnym potwierdzaniem połączeń.
- **Ekran rozmowy**: wyciszanie, głośnik, Bluetooth i słuchawki, zawieszanie, klawiatura DTMF, drugie połączenie (zamiana, zakończ i odbierz, zawieś i odbierz), konferencje z listą uczestników, rozmową osobno i rozłączaniem jednej osoby. Odbieranie przesunięciem gałki, odrzucanie z SMS-em, czujnik zbliżeniowy, wibracja przy odebraniu i zakończeniu, powiadomienie o trwającej rozmowie z licznikiem.
- **Historia połączeń** z filtrami, wyszukiwaniem, grupowaniem połączeń z tego samego dnia, szczegółami numeru, usuwaniem wpisów i eksportem do CSV; powiadomienia o nieodebranych z oddzwonieniem i SMS-em.
- **Kontakty**: lista z indeksem liter, sekcja „Często”, ulubione, pełny edytor (nazwa w sześciu polach, wiele numerów i e-maili, adresy, strona, urodziny, notatka, zdjęcie, konto zapisu), dzwonek na kontakt, udostępnianie jako vCard, adres na mapie, zaznaczanie wielu kontaktów.
- **Duplikaty** – wykrywanie kontaktów o tym samym numerze lub nazwie i scalanie ich.
- **Kopia zapasowa** – eksport i import `.vcf`, z pomijaniem kontaktów już obecnych w telefonie.
- **Blokowanie**: systemowa lista blokad, reguły prefiksów z gwiazdką, numery ukryte, odrzucanie lub wyciszanie numerów spoza kontaktów, zapisywanie i zgłaszanie zablokowanych połączeń.
- **Statystyki**: liczba i czas rozmów, średnia i najdłuższa rozmowa, najczęstsi rozmówcy, rozkład po dniach tygodnia i porach dnia, za 7 dni, 30 dni, 3 miesiące albo cały rejestr.
- **Szybkie odpowiedzi** – edytowalne wiadomości do odrzucania połączeń.
- **Ustawienia** w siedmiu sekcjach: połączenia, w trakcie rozmowy, klawiatura, historia i kontakty, blokowanie i narzędzia, ogólne, aktualizacje.
- **Interfejs** w stylu „glass”, po polsku i angielsku (język systemu albo wybrany), ikona adaptacyjna i monochromatyczna.
- **Aktualizacje z GitHub Releases** – sprawdzanie przy uruchomieniu (najwyżej raz na 6 godzin), pobieranie z postępem i instalacja w aplikacji.
- Bez reklam, analityki i kont; dane nie opuszczają telefonu.
