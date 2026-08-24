# Normalization Contract 2.0

Normalize `5s`, `5 seconds`, and `5000ms` to integer `timeoutMs: 5000`. Uppercase HTTP methods; canonicalize whitespace; omit null/empty optional values where allowed; sort entities by stable ID; and order access values as READ, WRITE, READ_WRITE.

Do not change Kafka topics, database names, error codes, state values, feature-property names, or other semantic identifiers.
