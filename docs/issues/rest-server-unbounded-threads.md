# The REST server uses an unbounded thread pool

`LegacyHttpServer` runs its handlers on `Executors.newCachedThreadPool`, which creates a new thread for every concurrent
request without a limit.

- **Effect:** many concurrent or slow requests (a misbehaving client on the LAN) create any number of threads that
  compete with the recording for CPU, memory and the database lock.
- **Where:** `alphatimer-legacy-server/.../legacy/server/LegacyHttpServer`.
- **Idea:** use a small fixed pool (a few threads are enough for JAuswertung) and reject or queue further requests.
