SHELL := /bin/bash
COMPOSE := docker compose -f docker/docker-compose.yml

.PHONY: help build jar test smoke e2e uat uat-fresh rebuild down logs clean

help:
	@echo "Targets:"
	@echo "  build    - compile, test, and assemble the plugin jar"
	@echo "  jar      - assemble the plugin jar only"
	@echo "  test     - run unit tests"
	@echo "  smoke    - functional smoke test (boots Paper + plugin in Docker)"
	@echo "  e2e      - end-to-end test (Mineflayer bot drives swing/bow input path)"
	@echo "  uat       - rebuild the jar and bounce the server (fast restart), then follow logs"
	@echo "  uat-fresh - rebuild the jar and fully recreate the container, then follow logs"
	@echo "  rebuild   - rebuild the jar and restart the server in place (no log follow)"
	@echo "  down      - stop and remove the UAT server"
	@echo "  logs      - follow the UAT server logs"

build:
	./gradlew build

jar:
	./gradlew assemble

test:
	./gradlew test

smoke:
	./scripts/smoke-test.sh

e2e:
	./scripts/e2e-test.sh

# Stage the freshly built jar into the server's plugins dir, removing any
# stale build so only the current version is loaded.
_stage-plugin: jar
	mkdir -p docker/data/plugins
	rm -f docker/data/plugins/*.jar
	cp build/libs/*.jar docker/data/plugins/

# Bounce the server so the freshly staged jar loads (plugins load at startup),
# then follow logs. `restart` reloads in place if it's already running; the
# fallback starts it the first time. Ctrl-C stops following — the server keeps
# running, so you can detach and come back.
uat: _stage-plugin
	$(COMPOSE) restart mc 2>/dev/null || $(COMPOSE) up -d
	$(COMPOSE) logs -f mc

# Heavier option: fully recreate the container (picks up compose/env changes
# too), then follow logs.
uat-fresh: _stage-plugin
	$(COMPOSE) up -d --force-recreate
	$(COMPOSE) logs -f mc

rebuild: _stage-plugin
	$(COMPOSE) restart mc

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f mc

clean:
	./gradlew clean
	rm -rf docker/data/plugins/*.jar
