@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.StoredSeries
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A [SeriesService] over [database], for tests that only need the routes to work. */
fun seriesService(
    database: Database,
    random: Random = Random.Default,
): SeriesService =
    SeriesService(
        database = database,
        series = GameSeriesRepository(database),
        games = GameRepository(database),
        random = random,
    )

/**
 * Starts the first series between two players who have none, as their first tap on Play does,
 * and fails the test if they already had one (`D053`: Play would have offered it instead).
 */
fun SeriesService.startSeries(
    caller: Uuid,
    friend: Uuid,
): StoredSeries =
    when (val outcome = play(caller, friend)) {
        is PlayOutcome.Started -> outcome.series
        is PlayOutcome.Offered -> error("These players already have a series: ${outcome.existing.map { it.id }}")
    }
