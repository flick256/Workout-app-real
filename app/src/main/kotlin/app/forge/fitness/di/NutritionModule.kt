package app.forge.fitness.di

import app.forge.fitness.data.nutrition.FoodCatalog
import app.forge.fitness.data.nutrition.OpenFoodFactsClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NutritionModule {
    @Binds
    abstract fun bindFoodCatalog(client: OpenFoodFactsClient): FoodCatalog
}
