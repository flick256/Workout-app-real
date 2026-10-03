package app.forge.fitness.data.nutrition

import app.forge.domain.nutrition.GenericFood
import app.forge.domain.nutrition.GenericFoodIndex
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.MealItem
import app.forge.domain.nutrition.MealSentence
import app.forge.domain.nutrition.Nutrients
import app.forge.domain.nutrition.Portion
import app.forge.fitness.data.ai.AiAssistant
import app.forge.fitness.data.db.FoodEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** A food a typed item could be: one of yours, or one from the built-in database. */
sealed interface FoodChoice {
    val name: String
    val per100g: Nutrients

    data class Saved(val food: FoodEntity) : FoodChoice {
        override val name get() = listOfNotNull(food.brand, food.name).joinToString(" ")
        override val per100g get() = food.per100g
    }

    data class Builtin(val food: GenericFood) : FoodChoice {
        override val name get() = food.name
        override val per100g get() = food.per100g
    }
}

/** One line of a typed meal: what you wrote, what Forge thinks it is, and how much. */
data class MealLine(
    val item: MealItem,
    val choices: List<FoodChoice>,
    val chosen: Int,
    val grams: Double,
    val explanation: String,
) {
    val choice: FoodChoice? get() = choices.getOrNull(chosen)
    val nutrients: Nutrients? get() = choice?.per100g?.forGrams(grams)
}

/**
 * "2 weet-bix with milk and a banana" → three foods with amounts, ready to check and log
 * in one go. Matches your own foods first, then the built-in Australian database, and
 * works out grams from real portion sizes. The on-device AI only helps split the
 * sentence when the simple rules can't; it never makes up nutrition numbers.
 */
@Singleton
class MealLogger @Inject constructor(
    private val repository: FoodRepository,
    private val generic: GenericFoodsRepository,
    private val ai: AiAssistant,
) {
    suspend fun read(text: String): List<MealLine> {
        val lines = MealSentence.parse(text).map { resolve(it) }
        val matched = lines.count { it.choice != null }
        if (ai.isAvailable && (lines.isEmpty() || matched < lines.size)) {
            val byAi = ai.parseMeal(text)?.map { resolve(it) }
            if (byAi != null && byAi.count { it.choice != null } > matched) return byAi
        }
        return lines
    }

    /** You picked a different match: grams are worked out again from its portions. */
    suspend fun choose(line: MealLine, index: Int): MealLine {
        val choice = line.choices.getOrNull(index) ?: return line
        val amount = amountFor(line.item, choice)
        return line.copy(chosen = index, grams = amount.grams, explanation = amount.explanation)
    }

    /** Logs every line that has a food and an amount. Returns how many were added. */
    suspend fun logAll(lines: List<MealLine>, meal: Meal, day: LocalDate): Int {
        var added = 0
        for (line in lines) {
            val entity = when (val c = line.choice) {
                is FoodChoice.Saved -> c.food
                is FoodChoice.Builtin -> repository.saveGeneric(c.food)
                null -> continue
            }
            if (line.grams <= 0) continue
            repository.log(entity, line.grams, meal, day)
            added++
        }
        return added
    }

    private suspend fun resolve(item: MealItem): MealLine {
        val words = GenericFoodIndex.words(item.food)
        val saved = repository.search(item.food).first().take(MAX_SAVED)
        val builtin = generic.search(item.food, MAX_BUILTIN).takeIf { it.ignoredWords.isEmpty() }?.foods.orEmpty()
        val savedKeys = saved.map { it.id }.toSet()
        // Your own food comes first when its name has every word you typed.
        val (strong, weak) = saved.partition { f -> GenericFoodIndex.words(f.name + " " + f.brand.orEmpty()).let { n -> words.all { w -> n.any { it.startsWith(w) } } } }
        val choices = strong.map { FoodChoice.Saved(it) } +
            builtin.filter { "ausnut-${it.key}" !in savedKeys }.map { FoodChoice.Builtin(it) } +
            weak.map { FoodChoice.Saved(it) }
        val first = choices.firstOrNull() ?: return MealLine(item, emptyList(), 0, 0.0, "No match")
        val amount = amountFor(item, first)
        return MealLine(item, choices, 0, amount.grams, amount.explanation)
    }

    private suspend fun amountFor(item: MealItem, choice: FoodChoice) = when (choice) {
        is FoodChoice.Saved -> {
            // Saved from the built-in database: its portion sizes still apply.
            val portions: List<Portion> = choice.food.id.removePrefix("ausnut-").takeIf { choice.food.id.startsWith("ausnut-") }
                ?.let { generic.byKey(it)?.portions }.orEmpty()
            MealSentence.amount(item, portions, choice.food.servingG, choice.food.servingLabel)
        }
        is FoodChoice.Builtin -> MealSentence.amount(item, choice.food.portions, null, null)
    }

    private companion object {
        const val MAX_SAVED = 3
        const val MAX_BUILTIN = 5
    }
}
