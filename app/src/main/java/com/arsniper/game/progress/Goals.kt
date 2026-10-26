package com.arsniper.game.progress

/** סוג המדד שהיעד סופר */
enum class GoalMetric { Games, Wins, BestWave, Stars, Photos }

/** פרס: מטבעות או יהלומים */
class GoalReward(val coins: Int = 0, val gems: Int = 0)

/** יעד אחד: מגיעים ל-target במדד, ואז לוחצים CLAIM ומקבלים פרס. Kotlin טהור. */
class Goal(
    val id: String,
    val title: String,
    val metric: GoalMetric,
    val target: Int,
    val reward: GoalReward
)

object Goals {
    val all: List<Goal> = listOf(
        Goal("games_1", "Play your first game", GoalMetric.Games, 1, GoalReward(coins = 50)),
        Goal("photo_1", "Photograph your first arena", GoalMetric.Photos, 1, GoalReward(coins = 80)),
        Goal("wins_1", "Win a battle", GoalMetric.Wins, 1, GoalReward(gems = 2)),
        Goal("stars_3", "Earn 3 arena stars", GoalMetric.Stars, 3, GoalReward(coins = 150)),
        Goal("games_10", "Play 10 games", GoalMetric.Games, 10, GoalReward(coins = 200)),
        Goal("photo_5", "Photograph 5 arenas", GoalMetric.Photos, 5, GoalReward(gems = 3)),
        Goal("wave_5", "Reach wave 5", GoalMetric.BestWave, 5, GoalReward(coins = 200)),
        Goal("wins_5", "Win 5 battles", GoalMetric.Wins, 5, GoalReward(gems = 5)),
        Goal("stars_10", "Earn 10 arena stars", GoalMetric.Stars, 10, GoalReward(gems = 6)),
        Goal("games_50", "Play 50 games", GoalMetric.Games, 50, GoalReward(coins = 600)),
        Goal("wins_25", "Win 25 battles", GoalMetric.Wins, 25, GoalReward(gems = 15)),
        Goal("stars_27", "Earn all 27 stars", GoalMetric.Stars, 27, GoalReward(gems = 30))
    )
}
