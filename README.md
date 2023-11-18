# incremental-reader

The goal is to build a mobile-first incremental reading companion to Anki. Letting you read on your phone and process articles into Anki flashcards.

```
Uncaught Error: Invalid hook call. Hooks can only be called inside of the body of a function component. This could happen for one of the following reasons:
1. You might have mismatching versions of React and the renderer (such as React DOM)
2. You might be breaking the Rules of Hooks
3. You might have more than one copy of React in the same app
See https://reactjs.org/link/invalid-hook-call for tips about how to debug and fix this problem.
    at Object.throwInvalidHookError (react-dom.development.js:16228:13)
    at exports.useState (react.development.js:1623:21)
    at useEditor (index.cjs:134:39)
    at exports.EditorProvider [as reagentRender] (index.cjs:223:20)
    at eval (component.cljs:87:28)
    at Object.reagent$impl$component$wrap_render [as wrap_render] (component.cljs:91:31)
    at Object.reagent$impl$component$do_render [as do_render] (component.cljs:117:6)
    at eval (component.cljs:260:64)
    at Object.reagent$ratom$in_context [as in_context] (ratom.cljs:44:6)
    at Object.reagent$ratom$deref_capture [as deref_capture] (ratom.cljs:57:14)
```

## Notes
- Oct 29, 2023: Looks like custom [file extension icons](https://ask.replit.com/t/custom-file-icon/20905/2) [are on the roadmap](https://ask.replit.com/t/file-icons-extention/11574/2) (Would be nice for .cljc/.cljs)
- Oct 29, 2023: Need a [datascript schema](https://github.com/kristianmandrup/datascript-tutorial/blob/master/create_schema.md)
- Oct 9, 2023: Run tests with `clj -X:test`
- Oct 5, 2023: Ignore [Repl Auth](https://docs.replit.com/hosting/authenticating-users-repl-auth#retrieving-information-from-the-authenticated-account) until [2.0](https://docs.replit.com/hosting/repl-auth-sidebar) comes out of Beta and I can put the entire site behind a login page.
