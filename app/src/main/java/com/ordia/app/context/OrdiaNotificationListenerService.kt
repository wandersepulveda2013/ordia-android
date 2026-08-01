package com.ordia.app.context

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.conversations.ChatImportParser
import com.ordia.app.conversations.ChatMessage
import com.ordia.app.conversations.CommitmentEngine
import com.ordia.app.conversations.ConversationPreview
import com.ordia.app.conversations.ConversationPrivacyPolicy
import com.ordia.app.conversations.ConversationSummaryEngine
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Observación opcional de notificaciones. Solo procesa fuentes autorizadas y
 * persiste propuestas de compromiso; nunca conserva la notificación completa.
 */
class OrdiaNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        val notification = posted.notification ?: return
        if (posted.packageName == packageName) return
        val text = extractText(notification)
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(120)
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val settings = ContextualSettingsStore(this)
        val app = application as? OrdiaApplication ?: return

        scope.launch {
            val source = app.container.observationRepository.getSource(posted.packageName)
            val fingerprint = NotificationObservationPolicy.fingerprint(posted.packageName, posted.key, text)
            val alreadySeen = app.container.conversationRepository.findByHash(fingerprint) != null
            val decision = NotificationObservationPolicy.evaluate(
                globalEnabled = settings.enabled,
                notificationAccessEnabled = settings.notificationSuggestions,
                pausedUntil = settings.pausedUntil,
                sourceEnabled = source?.enabled == true,
                packageName = posted.packageName,
                notificationKey = posted.key,
                title = title,
                text = text,
                isOngoing = posted.isOngoing,
                isGroupSummary = isGroupSummary,
                alreadySeen = alreadySeen
            )
            if (!decision.accepted) return@launch

            val zone = ZoneId.systemDefault()
            val startOfDay = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            if (app.container.conversationRepository.countSince(ConversationSourceType.NOTIFICATION, startOfDay) >= settings.dailyLimit) {
                return@launch
            }

            val actor = title.trim().takeIf { it.isNotBlank() && !ConversationPrivacyPolicy.containsSensitiveContent(it) }
            val message = ChatMessage(actor, decision.normalizedText, posted.postTime)
            val drafts = CommitmentEngine.extract(listOf(message), scopeHash = decision.fingerprint)
            if (drafts.isEmpty()) return@launch

            val preview = ConversationPreview(
                title = source?.displayName ?: posted.packageName,
                participants = listOfNotNull(actor),
                messages = listOf(message),
                rawContent = "",
                contentHash = decision.fingerprint
            )
            val now = System.currentTimeMillis()
            val conversation = ConversationEntity(
                sourceType = ConversationSourceType.NOTIFICATION,
                sourcePackage = posted.packageName,
                title = getString(R.string.notification_conversation_title, source?.displayName ?: posted.packageName),
                participants = ChatImportParser.encodeParticipants(preview.participants),
                summary = ConversationSummaryEngine.summarize(preview, drafts),
                rawContent = "",
                retainsOriginal = false,
                contentHash = decision.fingerprint,
                messageCount = 1,
                createdAt = now,
                updatedAt = now
            )
            val commitments = drafts.map { draft ->
                CommitmentEntity(
                    conversationId = 0,
                    kind = draft.kind,
                    owner = draft.owner,
                    actor = draft.actor,
                    action = draft.action,
                    location = draft.location,
                    dueAt = draft.dueAt,
                    confidence = draft.confidence,
                    suggestedReminderAt = draft.suggestedReminderAt,
                    fingerprint = draft.fingerprint,
                    createdAt = now,
                    updatedAt = now
                )
            }
            app.container.conversationRepository.saveGraph(conversation, commitments)
        }
    }

    private fun extractText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (big.isNotBlank()) return big.take(NotificationObservationPolicy.MAX_TEXT_LENGTH)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }.orEmpty()
        if (lines.isNotBlank()) return lines.take(NotificationObservationPolicy.MAX_TEXT_LENGTH)
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            .take(NotificationObservationPolicy.MAX_TEXT_LENGTH)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
