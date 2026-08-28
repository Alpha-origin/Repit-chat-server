# Load-test mock environment

The load-test profile redirects Redis, Anthropic, and the API server to local test containers.
It never requires a real Anthropic API key.

Start the default 3-second Anthropic delay scenario:

```bash
docker compose -f docker-compose.loadtest.yml up -d
SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun
```

Select a different Anthropic scenario before starting the containers:

```bash
MOCK_SCENARIO=normal docker compose -f docker-compose.loadtest.yml up -d
MOCK_SCENARIO=error docker compose -f docker-compose.loadtest.yml up -d
MOCK_SCENARIO=timeout docker compose -f docker-compose.loadtest.yml up -d
```

Available scenarios:

- `normal`: 100 ms successful response
- `delay`: 3 second successful response (default)
- `error`: HTTP 500 response
- `timeout`: 30 second delayed response

Stop and remove only the load-test containers:

```bash
docker compose -f docker-compose.loadtest.yml down
```

## k6 WebSocket tests

Start the mock dependencies and run the application with the `loadtest` profile first.
The Docker commands below assume that the application listens on the host's port 8080.

```bash
docker compose -f docker-compose.loadtest.yml up -d redis-loadtest wiremock
SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun
```

Run each scenario with the pinned k6 Docker image:

```bash
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/smoke.js
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/baseline.js
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/load.js
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/stress.js
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/spike.js
```

Override the target when the application uses another port:

```bash
BASE_URL=http://host.docker.internal:18081 \
WS_URL=ws://host.docker.internal:18081 \
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/smoke.js
```

The baseline scenario accepts shorter test settings for quick checks:

```bash
VUS=5 DURATION=20s \
docker compose -f docker-compose.loadtest.yml run --rm k6 run /scripts/k6/baseline.js
```

Measured k6 metrics include:

- `repit_ws_answer_duration`: time from sending an answer to receiving the next server message
- `repit_ws_failures`: failed interview proportion
- `repit_ws_messages`: received WebSocket message count
- `repit_interviews_completed`: successfully completed interview count
- built-in `ws_connecting`, `ws_session_duration`, checks, and HTTP metrics
