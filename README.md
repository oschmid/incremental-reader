# incremental-reader

The goal is to build a mobile-first incremental reading companion to Anki. Letting you read on your phone and process articles into Anki flashcards.

## Commands

```
# Run tests
clj -X:test

# Run dev mode
clj -X:dev

# Run linter
clj -M:clj-kondo --lint src test

# Run code formatter
clj -Tcljfmt fix
```

## Notes 
- Ignore [Repl Auth](https://docs.replit.com/hosting/authenticating-users-repl-auth#retrieving-information-from-the-authenticated-account) until [2.0](https://docs.replit.com/hosting/repl-auth-sidebar) comes out of Beta and I can put the entire site behind a login page.

