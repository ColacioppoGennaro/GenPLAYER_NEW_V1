package com.genaro.radiomp3.ui

/**
 * Decide quale strategia di caricamento usare basandosi sul numero di file
 */
object LoadingStrategyDecider {

    enum class LoadingStrategy {
        LOAD_ALL,           // < 1000 file: carica tutto subito
        PAGINATION_SOFT,    // 1000-10000 file: pagina soft (50 per volta)
        PAGINATION_STRICT   // > 10000 file: paginazione obbligatoria
    }

    data class LoadingConfig(
        val strategy: LoadingStrategy,
        val pageSize: Int = 50,
        val shouldWarnUser: Boolean = false,
        val warningMessage: String = ""
    )

    fun decideStrategy(fileCount: Int): LoadingConfig {
        return when {
            fileCount < 1000 -> {
                LoadingConfig(
                    strategy = LoadingStrategy.LOAD_ALL,
                    pageSize = Int.MAX_VALUE,
                    shouldWarnUser = false
                )
            }
            fileCount in 1000..10000 -> {
                LoadingConfig(
                    strategy = LoadingStrategy.PAGINATION_SOFT,
                    pageSize = 50,
                    shouldWarnUser = fileCount > 5000,
                    warningMessage = "Hai $fileCount file. I caricati inizialmente: 50.\n\nScorri per caricare altri."
                )
            }
            else -> {
                LoadingConfig(
                    strategy = LoadingStrategy.PAGINATION_STRICT,
                    pageSize = 50,
                    shouldWarnUser = true,
                    warningMessage = "⚠️ Hai $fileCount file!\n\nPer una migliore esperienza:\n\n" +
                            "1️⃣ Separa i file in cartelle\n" +
                            "2️⃣ Usa le viste Cartelle/Album/Artisti\n" +
                            "3️⃣ Scorri per caricamenti progressivi\n\n" +
                            "Procediamo comunque?"
                )
            }
        }
    }
}
