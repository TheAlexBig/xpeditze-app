# Xpeditze El Salvador

Xpeditze is an offline-first Windows desktop application for generating
legal documents in El Salvador. Its guided Spanish workflows prepare vehicle
purchase-and-sale agreements, mutual agreements, and marriage files in Word
and PDF.
The packaged application runs on the user's computer without a cloud account
or external database.

## Download

Download the current MSI and its SHA-256 checksum from the
[latest Xpeditze release](https://github.com/TheAlexBig/xpeditze-app/releases/latest).

Code signing policy: Free code signing provided by SignPath.io, certificate by
SignPath Foundation. Releases published before completion of the SignPath
onboarding process, including `v1.0.3`, are not yet Authenticode-signed and may
display `Unknown publisher` in Windows.

Xpeditze is licensed under the [Apache License 2.0](LICENSE). See the
[privacy policy](PRIVACY.md), [security policy](SECURITY.md), and
[code signing policy](CODE_SIGNING_POLICY.md).

## Development

```bash
mvn spring-boot:run
```

Run the canonical backend verification before opening a pull request. It runs
the tests and maximum-effort SpotBugs static analysis; high-priority findings
fail the check.

```bash
java scripts/Verify.java
```

The server binds only to `127.0.0.1`. During React development, Vite proxies
`/api` requests to the backend at `http://127.0.0.1:8080`. Set
`DOC_WEB_ORIGINS` only when a different local development origin is needed.

## Generate Documents

| Document | Direct generation | Generation with history |
| --- | --- | --- |
| Vehicle sale | `POST /api/v1/documents/car-sale` | `POST /api/v1/documents/car-sale/history` |
| Mutual agreement | `POST /api/v1/documents/mutual` | `POST /api/v1/documents/mutual/history` |
| Marriage file | — | `POST /api/v1/documents/marriage/history` |

Pass `?format=docx` or `?format=pdf`. Tracked endpoints receive
`{"documento": {...}, "borrador": {...}}`, store the generated file, and
return its identifier in `X-Document-History-Id`.

The vehicle-sale request uses these Spanish domain names:

```json
{
  "vendedor": {},
  "comprador": {},
  "vehiculo": {},
  "documento": {},
  "agente_juridico": {}
}
```

The mutual-agreement request uses `deudor`, `acreedor`, `condiciones`, and
`agente_juridico`. Conditions cover principal, term, due date, installments,
payment bank/account, optional interest and default interest, funds purpose,
optional bill-of-exchange guarantee, optional administrative expenses,
special jurisdiction, signing location/date/time, and notarial identification.
It also supports represented companies, calculated or individually agreed
installments, bank/cash/other payment arrangements, early-payment terms, and
monthly or annual default interest.

The marriage request covers both parties, parents, recent birth certificates,
witnesses, the property regime, prior-marriage evidence, and conditional proxy,
interpreter, capitulation, and recognized-child details. The generated file
contains the premarital act, marriage deed, and an internal registration-control
sheet.

Missing required fields return HTTP `400` with a field-level validation map.
Generated Word and PDF files are returned as downloads and are also saved
locally.

## Offline Desktop Behavior

The installed application starts a local Spring Boot server on
`http://xpeditze.localhost:17831`, serves the compiled React application from that
process, and opens it in the user's default browser. API requests remain local
under `/api`. A second application launch checks the running Xpeditze
instance and opens its browser interface instead of starting another server.

No network service, database, Docker installation, or separately installed
Java runtime is needed after the MSI is installed. Default templates are
created locally on first launch and remain editable offline:

| Content             | Windows location                                     |
| ------------------- | ---------------------------------------------------- |
| Editable car-sale templates | `%LOCALAPPDATA%\Xpeditze\templates-v2\car-sale\` |
| Optional settings   | `%LOCALAPPDATA%\Xpeditze\config\application.yml` |
| Saved legal agents  | `%LOCALAPPDATA%\Xpeditze\agents.json`            |
| Generated documents | `%USERPROFILE%\Documents\Xpeditze\Documents\`    |

Upgraded installations continue using an existing `Central Docs` data or
documents directory so licenses, drafts, templates, and history remain available.

The template placeholders must remain present where the associated form values
should be inserted. The generated local settings file contains an example for
changing the document output directory.

Saved legal agents are added, edited, and removed in the application through
the notary selection step. The local JSON file begins empty and is not bundled
with personal data.

## Local maintenance

The **Configuración** tab provides operational maintenance without requiring
access to the filesystem:

- export and restore a portable ZIP backup of saved data, templates, history,
  and generated documents;
- search, edit, and remove saved people;
- edit templates for every document type with required-placeholder validation
  and restore the bundled defaults; and
- display the installed version and check GitHub for a newer release.

Document history includes vehicle sales, mutual agreements, and marriages. It can be
filtered by dates, document type, names, DUI, vehicle, plate, bank, account,
or responsible person. Historical Word/PDF files can be downloaded again and
their saved draft opens in the correct workflow. In-progress forms use an
independent autosave key per document type and are recovered after an
accidental close.

The diagnostics section can export a support ZIP containing only application
version, platform details, and sanitized logs. It excludes licenses, generated
documents, templates, and saved customer data, and redacts common identifiers
from log text.

Licenses are deliberately excluded from backups and remain tied to their
original installation. Before replacing a JSON data file, Xpeditze keeps
up to 25 local backups. If the active file becomes unreadable, the newest valid
backup is restored automatically and the damaged file is retained with a
`.corrupt-<timestamp>` suffix for diagnosis.

History and configuration are global application areas available at
`/historial` and `/configuracion`; they are not nested inside a specific
document workflow. Saved people, agents, and vehicle suggestions are managed
from the global data view. People are reusable as buyer, seller, debtor, or
creditor. Car-sale template text is presented as an ordered
set of blocks whose combination produces the final document.
All document types use versioned editable template blocks. Legal wording that
still requires product-owner or notarial confirmation is listed in
[LEGAL_REVIEW.md](LEGAL_REVIEW.md).
The logs-folder action supports the Windows desktop API, `open` on macOS, and
`xdg-open` when running the backend from a Linux desktop.

## Windows Installer

Oracle `jpackage` builds native packages only on their target platform, so a
Windows MSI cannot be generated directly by Linux `jpackage`. From Linux,
push backend and frontend branches, authenticate `gh`, and dispatch the
included Windows build workflow:

```bash
./packaging/windows/build-from-linux.sh 1.0.0 main master
```

It waits for the Windows runner and downloads the MSI artifact under
`target/windows-installer/<workflow-run-id>/`. A successful release build also
publishes `Xpeditze-<version>.msi` and `SHA256SUMS.txt` automatically in a
new GitHub Release. Existing releases are never overwritten.

Each MSI created by the GitHub workflow receives a GitHub artifact attestation.
When the installed GitHub CLI supports `gh attestation`, the Linux helper
verifies the downloaded MSI's build provenance automatically; otherwise it
prints the command to run after upgrading `gh`. This proves the MSI came from
this repository's workflow; it does not change Windows' `Unknown publisher`
warning, which requires Authenticode code signing.

To build directly on Windows, install a Java 21 JDK that supplies `jpackage`,
Node.js/npm, and WiX Toolset. Place `xpeditze-app` and `xpeditze-forms`
beside one another, then run from this repository:

```powershell
.\packaging\windows\build-installer.ps1 -Version 1.0.0
```

The script builds React, embeds its production files in the Spring Boot jar,
generates the application icon, and creates an MSI under `target\installer`.
The MSI bundles its Java runtime and supplies a desktop shortcut, Start Menu
entry, stable upgrade identity, normal Windows upgrade behavior, and uninstall
support.
Increment the installer version for upgrades and keep the Maven project
version aligned for published application releases.

See Oracle's Java 21 `jpackage` documentation for the platform-specific native
packaging restriction:
https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html
