SHELL := /bin/bash
COMPOSE := docker compose -f docker/docker-compose.yml

.PHONY: help build jar test smoke uat rebuild down logs clean

help:
	@echo "Targets:"
	@echo "  build    - compile, test, and assemble the plugin jar"
	@echo "  jar      - assemble the plugin jar only"
	@echo "  test     - run unit tests"
	@echo "  smoke    - functional smoke test (boots Paper + plugin in Docker)"
	@echo "  uat      - launch a local UAT server at localhost:25565 (set docker/.env first)"
	@echo "  rebuild  - rebuild the jar and restart the running UAT server"
	@echo "  down     - stop and remove the UAT server"
	@echo "  logs     - follow the UAT server logs"

build:
	./gradlew build

jar:
	./gradlew assemble

test:
	./gradlew test

smoke:
	./scripts/smoke-test.sh

# Stage the freshly built jar into the server's plugins dir, removing any
# stale build so only the current version is loaded.
_stage-plugin: jar
	mkdir -p docker/data/plugins
	rm -f docker/data/plugins/*.jar
	cp build/libs/*.jar docker/data/plugins/

uat: _stage-plugin
	$(COMPOSE) up

rebuild: _stage-plugin
	$(COMPOSE) restart mc

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f mc

clean:
	./gradlew clean
	rm -rf docker/data/plugins/*.jar
