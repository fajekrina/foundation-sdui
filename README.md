# foundation-sdui

Implementasi acuan SDUI, default yang direkomendasikan untuk service channel: penyimpanan, alur kerja, dan wiring
Spring. Aturan (validasi, rebase, bahasa, pilihan release, envelope, kode error) ada di `../foundation-sdui-contract`,
dibawa sebagai `api`. Package `com.fajekrina.sduir.foundation.sdui`. Java 17, Spring Boot 3.3 (`compileOnly`).

| Folder | Isi |
|---|---|
| `annotation` | `@SduiApi`, `@SduiSource`, `@SduiEndpoint` |
| `config`, `properties` | autoconfig opt-in, `SduiProperties` (`sdui.*`) |
| `exception` | `SduiErrorGuard` (format error setiap handler), `SduiErrorHandler` (advice, hanya `@SduiEndpoint`) |
| `model` | `dto/` (`SduiActor`, `SduiContext`), `response/` (`SduiResponse`, `SduiErrorResponse`) |
| `repository` | SQL: `TemplateRepository`, `ValueRepository`, `MerchantRepository`, `AssetRepository`, `DeliveryRepository`, `JdbcAudit`, `IdempotencyStore` |
| `security` | `ActorResolver`, `SigningKeys` |
| `service/<fitur>` (+ `impl/`) | `template`, `page`, `delivery`, `action`, `audit`, `idempotency`: interface handler + bawaan `…Impl` |
| `utils` | `Json`, `TraceIds`, `UrlFetcher`, `SduiRequests` |
| resources | Flyway H2 `sdui/db/{template,value}`, skrip Oracle `sdui/sql/oracle` |

Tidak ada route dan tidak ada path: service menulis controller dan peta route-nya sendiri. Setiap bagian mati sampai
dinyalakan: `sdui.authoring.templates.enabled`, `sdui.authoring.pages.enabled`, `sdui.delivery.enabled` (action selalu
ada, tanpa efek bila service tidak punya `SduiHandler`).

Tes: `architecture/` (`NoRoutesTest`, `PackagesTest`: folder, lapisan, JDBC hanya di `repository`; `OptInTest`) dan
`reference/`, yaitu app acuan dengan semua bagian menyala yang lulus keempat suite server conformance kit.

Build mandiri: `./gradlew build publishToMavenLocal` (setelah `foundation-sdui-contract`: `../publish-local.sh`).
Desain: `docs/ARSITEKTUR.md`, cara pakai: `docs/IMPLEMENTASI.md` (folder `docs/` tidak di-commit).
