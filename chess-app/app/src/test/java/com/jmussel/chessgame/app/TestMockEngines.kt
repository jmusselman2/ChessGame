package com.jmussel.chessgame.app

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A [MockEngine] that answers on the test's own thread, on [scheduler]: the one a test's
 * `Dispatchers.Main` runs on.
 *
 * Ktor's engines otherwise answer on worker threads, which then resume the view model's
 * coroutines onto `Dispatchers.Main` from there. One that lands while a test's `@After`
 * resets `Main` fails whichever test is finishing ("Dispatchers.Main is used concurrently
 * with setting it"), as a CI run did (`M21.14`). The dispatcher is unconfined, so a call made
 * outside `runTest` still runs.
 */
fun mockEngineOn(
    scheduler: TestCoroutineScheduler,
    handler: MockRequestHandler,
): MockEngine =
    MockEngine(
        MockEngineConfig().apply {
            dispatcher = UnconfinedTestDispatcher(scheduler)
            addHandler(handler)
        },
    )
