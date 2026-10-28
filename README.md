# Property Search (Android)

**Author:** Steve Yang

A native Android app, written in Kotlin with Jetpack Compose, for searching property listings
from the reference listings API in `server/`.

You can search by city, keyword, price range, minimum bedrooms and an optional target budget.
Results show price, address, beds, baths and square footage, and the next page loads as you
scroll. The app shows a loading state while a request is in flight, an error state with retry
when it fails, and a message when nothing matches. Copies of the same property listed by more
than one MLS source are merged into one card by default.

## Requirements

- **Python 3**, for the server. It only uses the standard library, so there's nothing to install.
- **JDK 17 or newer.** The JDK bundled with Android Studio works.
- **Android SDK** with platform 36. Installing Android Studio sets this up. The scripts find the
  SDK through `ANDROID_HOME`, `local.properties`, or the default location
  (`~/Library/Android/sdk` on macOS).
- **An Android emulator (AVD), or a phone** with USB debugging enabled.

## Quick start

From the project root:

```bash
scripts/run.sh
```

This one command:

1. Starts the listings server on port 8000, unless one is already running.
2. Starts your first emulator if no device is connected, and waits for it to boot.
3. Builds and installs the app, pointing it at the right server address.
4. Launches the app.

The server keeps running in the background after the script exits. Stop it with
`scripts/server.sh stop`.

## Running the server manually

To run it in the background with the helper script:

```bash
scripts/server.sh start               # or: scripts/server.sh start --port 9000
scripts/server.sh status
scripts/server.sh stop
```

Or run it in the foreground yourself, and stop it with Ctrl+C:

```bash
cd server
python3 server.py          # listens on http://0.0.0.0:8000
python3 server.py 9000     # or on another port
```

Run it from inside `server/`, because `server.py` loads `data.json` from the current directory.
To check that it's up:

```bash
curl "http://localhost:8000/listings/search?city=Reston"
```

## Running the app

### Emulator

The emulator can't reach your Mac through `localhost`. It uses the special address `10.0.2.2`
instead, and the app is built with `http://10.0.2.2:8000` by default.

```bash
scripts/run.sh
```

Or open the project in Android Studio and press **Run**.

### Physical device

The phone and your computer must be on the same Wi-Fi network. Plug the phone in with USB
debugging enabled, then run:

```bash
scripts/run.sh                          # uses this machine's LAN IP automatically
scripts/run.sh --host 192.168.1.20      # or set the IP yourself
```

Without the script, pass the address to Gradle:

```bash
./gradlew installDebug -PapiBaseUrl=http://192.168.1.20:8000
```

### Using a different port

The app and the server must use the same port. The server address is built into the app, so
rebuild the app whenever you change the port.

```bash
# The script starts the server on 9000 if it isn't already running, then builds the app for it
scripts/run.sh --port 9000

# Or start the server yourself first, then run the script
cd server && python3 server.py 9000
scripts/run.sh --port 9000

# Without the script
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:9000        # emulator
./gradlew installDebug -PapiBaseUrl=http://192.168.x.x:9000     # physical phone
```

When you press Run in Android Studio, Gradle doesn't receive `-P` values. Change the default
in `gradle.properties` instead:

```properties
apiBaseUrl=http://10.0.2.2:9000
```

If the port changes and the app isn't rebuilt, it keeps calling the old port and shows
"Couldn't reach the server".

## Testing

```bash
scripts/run.sh test     # unit tests
scripts/run.sh all      # unit tests, then start the server and launch the app
```

The unit tests cover form validation, how query parameters are built, merging duplicate
listings, the HTTP client against a mock server (success, empty results, 400, 5xx, invalid JSON
and an unreachable server), and the ViewModel's loading, error, retry and paging states.
If tests fail, the HTML report is at `app/build/reports/tests/testDebugUnitTest/index.html`.

### Things to try manually

| Try | You should see |
|---|---|
| Tap **Search** with every field empty | All 12 listings, ranked by relevance |
| Min price `400000`, max price `550000` | 8 listings |
| City `Atlantis` | The "No listings match" message |
| Min price `600000`, max price `500000` | An error on the max price field, with no request sent |
| Stop the server (`scripts/server.sh stop`), then search | An error screen with **Try again** |
| Scroll to the bottom of an unfiltered search | Page 2 loads automatically, then "End of results" |
| Tap a listing | It expands to show the description, amenities, listing date and source |

## Script reference

All scripts live in `scripts/`, can be run from any directory, and print their usage with
`--help`.

### `scripts/run.sh`: the app

```
scripts/run.sh [run|test|all] [--host <ip>] [--port <port>] [--avd <name>]
```

| Command | What it does |
|---|---|
| `run` (default) | Start the server if needed, start an emulator if needed, install and launch the app |
| `test` | Run the unit tests |
| `all` | `test`, then `run` |

| Option | What it sets |
|---|---|
| `--host <ip>` | The server address as seen from the device. Defaults to `10.0.2.2` on an emulator and your LAN IP on a phone. |
| `--port <port>` | The server port. Defaults to `8000`. |
| `--avd <name>` | Which emulator to start when no device is connected |

If several devices are connected, set `ANDROID_SERIAL` to choose one. The emulator logs to
`/tmp/property-search-emulator.log`.

### `scripts/server.sh`: the listings server

```
scripts/server.sh [start|stop|status] [--port <port>]
```

| Command | What it does |
|---|---|
| `start` | Start the server in the background, unless one is already running |
| `stop` | Stop a server that these scripts started |
| `status` | Report whether a server is responding on the port |

The server logs to `/tmp/property-search-server.log`.

### `scripts/package.sh`: the zip

```
scripts/package.sh [--no-pdf]
```

See [Packaging](#packaging).

## Packaging

```bash
scripts/package.sh            # creates dist/PropertySearch-<date>.zip, with README.pdf included
scripts/package.sh --no-pdf   # the same, without the PDF
```

The zip contains everything git would track. Build output, `.gradle/` and `local.properties`
are left out. Making `README.pdf` needs Node.js (it runs `marked` through `npx`) and Google
Chrome or Chromium, which prints the PDF. Set `CHROME=/path/to/chrome` if Chrome is installed
somewhere unusual.

## Project layout

```
server/                      Reference API (server.py and data.json)
app/src/main/java/com/example/propertylisting/
  data/Models.kt             API response types
  data/SearchQuery.kt        Validated search parameters and how they become query parameters
  data/ListingsService.kt    HTTP client (OkHttp + kotlinx.serialization), cancellation, error types
  ui/SearchForm.kt           Raw form input and validation
  ui/SearchViewModel.kt      Search state (idle, loading, error, loaded), paging and retry
  ui/ListingGroup.kt         Merging of duplicate listings
  ui/SearchScreen.kt         Search form, list, and loading, error and empty states
  ui/ListingCard.kt          A single listing card
  ui/Scrollbar.kt            Scrollbar drawn over the list
app/src/test/...             Unit tests
scripts/
  run.sh                     Build, test and launch the app (starts the server if needed)
  server.sh                  Start, stop or check the listings server
  package.sh                 Build the distributable zip, including README.pdf
  lib/common.sh              Shared helpers: logging, project root, Android SDK lookup
  lib/server.sh              Shared server start/stop logic used by run.sh and server.sh
```

## Troubleshooting

- **"Couldn't reach the server" in the app.** Check that the server is running
  (`scripts/server.sh status`, or `scripts/server.sh start` to start it). On a phone, also check that it's on the same Wi-Fi network and that your
  Mac's firewall allows incoming connections to Python.
- **The port is already in use.** Something else is listening on port 8000. Use
  `scripts/run.sh --port 9000`.
- **"Android SDK not found".** Set `ANDROID_HOME`, or create `local.properties` containing
  `sdk.dir=/path/to/sdk`.
