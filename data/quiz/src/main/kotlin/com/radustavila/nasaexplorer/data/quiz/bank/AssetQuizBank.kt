package com.radustavila.nasaexplorer.data.quiz.bank

import android.content.Context
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizBank
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bank bundled as an asset: `quiz_bank_<language>.json` for the device's language when the
 * app ships one, else the English `quiz_bank_en.json`. Read once, then kept in memory (a few
 * hundred kilobytes at most).
 */
@Singleton
class AssetQuizBank @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : QuizBank {

    private val mutex = Mutex()
    private var loaded: ParsedQuizBank? = null

    override suspend fun questions(): List<QuizQuestion> = load().questions

    override suspend fun facts(): List<FunFact> = load().facts

    private suspend fun load(): ParsedQuizBank = mutex.withLock {
        loaded ?: withContext(ioDispatcher) { QuizBankParser.parse(readAsset()) }.also { loaded = it }
    }

    private fun readAsset(): String {
        val language = Locale.getDefault().language
        val available = try {
            context.assets.list("").orEmpty().toSet()
        } catch (_: IOException) {
            emptySet()
        }
        val name = fileName(language).takeIf { it in available } ?: fileName(DEFAULT_LANGUAGE)
        return try {
            context.assets.open(name).bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            ""
        }
    }

    companion object {
        const val DEFAULT_LANGUAGE = "en"

        fun fileName(language: String): String = "quiz_bank_$language.json"
    }
}

/** The bank's own "Did you know" facts. */
class BankFunFactSource @Inject constructor(
    private val bank: QuizBank,
) : FunFactSource {

    override val key: String = "bank"

    override suspend fun facts(now: Instant): List<FunFact> = bank.facts()
}
