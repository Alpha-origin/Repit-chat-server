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
