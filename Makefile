clean:
	rm -rf target

run:
	clj -M:dev

repl:
	clj -M:dev:nrepl --port 7777

l:
	clj -M:dev:local-nrepl

test:
	clj -M:test

uberjar:
	clj -T:build all

shadow:
	npx shadow-cljs watch app

fulldeploy: uberjar
	./scripts/deploy.sh 1 2 3 4 5 6 7 8
