# Revision history for reflex-dom-core

## 0.5

* Add HydrationDomBuilderT to support hydration of statically rendered DOM nodes. See the note at the top of Reflex.Dom.Builder.Immediate.
* As a result of the hydration changes, the Prerender class has changed, the type of `prerender` has changed and it is now a class method.
* Add the Reflex.Dom.Xhr.FormData module to make posting formdata over xhr more convenient.
