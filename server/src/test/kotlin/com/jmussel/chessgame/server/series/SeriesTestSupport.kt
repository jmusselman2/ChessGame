package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random

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
