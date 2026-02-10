package com.tanglycohort.smshooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents.getMessagesFromIntent
import android.telephony.SmsMessage
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.text.DateFormat

class SmsBroadcastReceiver : BroadcastReceiver() {

    private var isEnabled = false
    private var userId: String? = null
    private lateinit var message: CompleteSmsMessage

    override fun onReceive(context: Context, intent: Intent) {
        PreferenceManager.getDefaultSharedPreferences(context).also {
            isEnabled = it.getBoolean("webhookEnabled", false)
            userId = it.getString("userId", null)
            if (!isEnabled) {
                return
            }
        }

        message = getFullMessage(getMessagesFromIntent(intent))

        WorkManager.getInstance(context).also { workManager ->
            OneTimeWorkRequestBuilder<WebhookWorker>().apply {
                setInputData(
                    workDataOf(
                        "SMS_BODY" to message.body,
                        "SMS_FROM" to message.originatingAddress,
                        "SMS_TIMESTAMP" to message.timestampMillis,
                        "WEBHOOK_URL" to WEBHOOK_URL,
                        "USER_ID" to userId
                    )
                )
                setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                workManager.enqueue(this.build())
            }
        }

        // Log
        StringBuilder().apply {
            append("Received broadcast for SMS ")
            append(
                "[ ${message.originatingAddress}@${
                    DateFormat.getInstance().format(message.timestampMillis)
                } ]\n"
            )
            toString().also { log ->
                Log.i(SmsBroadcastReceiver::class.simpleName, log)
                context.openFileOutput("log", Context.MODE_PRIVATE.or(Context.MODE_APPEND)).use {
                    it.write(log.toByteArray())
                }
            }
        }
    }

    companion object {
        private const val WEBHOOK_URL = "https://hook.us2.make.com/potofsy3uj2w6xq237l7cfya6kiei9cf"

        /**
         * This class represents a complete SMS message after concatenating all bodies from the PDUs.
         * The rest of the fields are from the final SMS PDU.
         */
        class CompleteSmsMessage(
            val body: String,
            var originatingAddress: String?,
            var timestampMillis: Long
        )

        /**
         * Combines SMS message parts into a complete message.
         */
        private fun getFullMessage(messageParts: Array<SmsMessage>): CompleteSmsMessage {
            messageParts.last().also { part ->
                return CompleteSmsMessage(
                    timestampMillis = part.timestampMillis,
                    originatingAddress = part.originatingAddress,
                    body = messageParts
                        .map { it.messageBody }
                        .reduce { acc, body -> acc + body }
                )
            }
        }
    }
}
