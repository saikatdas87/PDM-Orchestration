# PDM Orchestration Service

A Spring Boot service that orchestrates file sync from a PDM system to S3.

## Flow

```
POST /api/v1/upload
  → Query PDM for matching files
  → Download each file
  → Get presigned S3 URL
  → Upload to S3
  → Return succeeded/failed lists
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/upload` | Full orchestration: query → download → upload |
| POST | `/api/v1/query` | Query PDM only, no upload |

### Request body (both endpoints)

```json
{
  "modelId": "model-123",
  "orderId": "order-456",
  "dateFrom": "2024-01-01",
  "dateTo": "2024-06-30"
}
```

`modelId` is required. All other fields are optional.

### Upload response

```json
{
  "succeeded": ["f1", "f2"],
  "failed": [
    { "fileId": "f3", "reason": "Failed to download fileId=f3" }
  ]
}
```

## Configuration

The app uses Spring profiles to switch between local and AWS config sources. No `.env` file is required.

### Local (default profile)

Config is read from environment variables with built-in fallback defaults in `application.yml`.
You can run with no setup at all using the defaults, or override specific values:

**Option 1 — use defaults (no setup needed)**
```bash
./gradlew bootRun
```

**Option 2 — pass env vars inline**
```bash
PDM_BASE_URL=http://pdm.example.com \
PDM_API_KEY=secret \
UPLOAD_BASE_URL=http://upload.example.com \
UPLOAD_API_KEY=secret \
./gradlew bootRun
```

**Option 3 — IntelliJ run configuration**

Set environment variables under `Run > Edit Configurations > Environment Variables`. Secrets stay off the filesystem.

| Variable | Description |
|----------|-------------|
| `PDM_BASE_URL` | Base URL of the PDM query/download API |
| `PDM_API_KEY` | API key for PDM authentication |
| `UPLOAD_BASE_URL` | Base URL of the presigned URL API |
| `UPLOAD_API_KEY` | API key for upload API authentication |
| `ORCHESTRATION_MAX_CONCURRENCY` | Max parallel file transfers (default: 100) |

### AWS (aws profile)

Activate the `aws` profile to read all config values from **AWS Secrets Manager** instead of environment variables:

```bash
SPRING_PROFILES_ACTIVE=aws AWS_REGION=eu-west-1 ./gradlew bootRun
```

The following secrets must exist in Secrets Manager:

| Secret path | Description |
|-------------|-------------|
| `/pdm-orchestration/pdm-base-url` | Base URL of the PDM API |
| `/pdm-orchestration/pdm-api-key` | API key for PDM authentication |
| `/pdm-orchestration/upload-base-url` | Base URL of the upload API |
| `/pdm-orchestration/upload-api-key` | API key for upload API authentication |

The EC2/ECS task role must have `secretsmanager:GetSecretValue` permission on `/pdm-orchestration/*`. No code changes are needed to switch between profiles — config resolution is entirely in `application-aws.yml`.

## Test

```bash
./gradlew test
```

## Design Decisions

**Virtual threads for concurrency** — `spring.threads.virtual.enabled=true` makes Tomcat serve each request on a virtual thread. Within a request, file processing is parallelized using `async/awaitAll` on a `Dispatchers.Virtual` coroutine dispatcher backed by `Executors.newVirtualThreadPerTaskExecutor()`. A `Semaphore` limits concurrency to `orchestration.max-concurrency` (default 100) to prevent overwhelming downstream services or exhausting memory. Total processing time is the slowest single file, not the sum of all files. The blocking code in `PdmClient` and `UploadClient` is unchanged — the JVM handles the scheduling. The dispatcher is defined in `coroutines/Dispatchers.kt` as a reusable extension property on `Dispatchers`, keeping it separate from orchestration logic.

**`@ConfigurationProperties`** — typed config classes over `@Value`. Validated at startup, no scattered string keys. If a required value is missing the app fails fast with a clear error, not at runtime.

**Spring profiles for config sources** — `application.yml` handles local/env var config, `application-aws.yml` handles AWS Secrets Manager. Switching environments is a profile flag, not a code change.

**Separate `RestClient` beans per integration** — each client gets its own base URL and auth header baked in at construction. A dedicated `s3RestClient` (no base URL, no headers) handles presigned URL uploads. No shared state between PDM, upload, and S3 concerns. All clients have connect and read timeouts configured via `JdkClientHttpRequestFactory` to prevent hung connections from blocking threads indefinitely.

**Clients own only HTTP** — `PdmClient` and `UploadClient` know nothing about orchestration. `OrchestrationService` knows nothing about HTTP. Clean separation means each layer is independently testable.

**Per-file error isolation** — a single file failure doesn't abort the batch. Each file is retried up to 3 times with linear backoff before being marked as failed. Query/auth failures (unrecoverable) propagate as exceptions and return `502 Bad Gateway`. Per-file failures are collected and returned in the response.

**Retry strategy** — a coroutine-level `retry` in `OrchestrationService` retries the full download→presign→upload flow per file (3 attempts, linear backoff). This ensures a fresh presigned URL on each attempt. S3 uploads are not retried in isolation since presigned URLs may be single-use.

**`ProblemDetail` error responses** — RFC 9457 compliant error format via `@RestControllerAdvice`.
