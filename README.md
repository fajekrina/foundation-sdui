# foundation-sdui

Runtime foundation SDUI dalam satu library, dibagi per package `com.fajekrina.sduir.sdui.*`. Kontrak (package `contract`:
spec, validator, `SduiProblem`, JSON kanonik, `SduiEnvelopes`, `SduiNames`) ada di library sendiri `../foundation-sdui-contract`, dibawa sebagai `api`.
Java 17, Spring Boot 3.3 (`compileOnly`).

| Package | Isi |
|---|---|
| `core` | `SduiActor`, `Signer` + Ed25519, `ClientInfo`, port audit / trace. Tanpa Spring |
| `web` | `SduiResponse`, `SduiErrors`, `@SduiEndpoint` + advice-nya, `ActorResolver`, `SduiIdempotency` |
| `persistence` | tabel `sdui_*` + view `*_read` (Flyway H2 `sdui/db/{template,value}`, skrip Oracle `sdui/sql/oracle`), `JdbcAudit` |
| `authoring`, `delivery`, `action` | handler ketiga API: interface (`SduiTemplateAuthoring`, `SduiPageAuthoring`, `SduiAssetAuthoring`, `SduiDelivery`, `SduiActionDispatcher`) + bawaan `…Impl`, diganti bila service punya bean sendiri |

Tidak ada route: service menulis controller-nya sendiri. Setiap bagian mati sampai dinyalakan:
`sdui.authoring.templates.enabled`, `sdui.authoring.pages.enabled`, `sdui.delivery.enabled` (action selalu ada, tanpa
efek bila service tidak punya `SduiHandler`). Tes arsitektur: `architecture/NoRoutesTest`, `PackagesTest` (`core` tanpa
Spring), `OptInTest`.

Build mandiri: `./gradlew build publishToMavenLocal` (setelah `foundation-sdui-contract`: `../publish-local.sh`).
Desain: `docs/ARSITEKTUR.md`, cara pakai: `docs/IMPLEMENTASI.md` (folder `docs/` tidak di-commit).
