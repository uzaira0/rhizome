/**
 * Copyright 2022 Matthew Tamayo-Rios (matthew@geekbeast.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.geekbeast.mail

import com.google.common.base.Preconditions
import jodd.mail.Email
import jodd.mail.EmailAttachment
import jodd.mail.MailServer
import jodd.mail.RFC2822AddressParser
import jodd.mail.SmtpServer
import jodd.mail.SmtpSslServer
import org.slf4j.LoggerFactory
import java.io.IOException

class MailService(val config: MailServiceConfig) {
    private val logger = LoggerFactory.getLogger(MailService::class.java)
    private val smtpServer: SmtpServer

    init {
        Preconditions.checkNotNull<Any>(config, "Mail Service configuration cannot be null.")
        val implicitSsl = config.isImplicitSsl()
        smtpServer = if (implicitSsl) {
            // Implicit SSL/TLS (SMTPS) — full SSL from the start (e.g. port 465)
            SmtpSslServer
                .create()
                .host(config.smtpHost)
                .port(config.smtpPort)
                .auth(config.username, config.password)
                .ssl(true)
                .also { builder -> config.extraSessionProperties.forEach { (k, v) -> builder.property(k, v) } }
                .buildSmtpMailServer()
        } else {
            // Port 587 (or other): plaintext connection upgraded via STARTTLS
            MailServer.create()
                .host(config.smtpHost)
                .port(config.smtpPort)
                .auth(config.username, config.password)
                .ssl(false)
                .property("mail.smtp.starttls.enable", "true")
                .property("mail.smtp.starttls.required", "true")
                .also { builder -> config.extraSessionProperties.forEach { (k, v) -> builder.property(k, v) } }
                .buildSmtpMailServer()
        }
        logger.info("Mail Service configured on {}:{} ({})",
            config.smtpHost, config.smtpPort,
            if (implicitSsl) "implicit SSL" else "STARTTLS")
    }

    fun sendEmails(emailRequests: List<EmailRequest>) {
        if (config.enabled) {
            val session = smtpServer.createSession()
            session.open()
            session.use { s ->
                emailRequests.forEach { emailRequest -> sendSingleEmail(s, emailRequest) }
            }
        } else {
            logger.info("Mail service disabled, ignoring $emailRequests")
        }
    }

    // reason: boundary catch — one failed send must be logged and must not abort the rest of the batch
    @Suppress("TooGenericExceptionCaught")
    private fun sendSingleEmail(session: jodd.mail.SendMailSession, emailRequest: EmailRequest) {
        try {
            session.sendMail(renderEmail(emailRequest))
        } catch (e: Exception) {
            logger.error("Error sending email to {}: {}", emailRequest.to, e.message, e)
        }
    }

    fun sendEmailAfterRendering(emailRequest: RenderableEmailRequest) {
        sendEmails(listOf(emailRequest))
    }

    // reason: jodd .to() is a vararg API requiring spread; recipient arrays are small
    @Suppress("SpreadOperator")
    private fun renderPlaintextEmail(emailRequest: EmailRequest): Email {
        check(emailRequest !is RenderableEmailRequest) { "Only raw e-mail is supported by this API." }
        val toAddresses = getToAddresses(emailRequest)
        val email = Email.create()
            .from(emailRequest.from.orElse(config.defaultFromEmail))
            .subject(emailRequest.subject)
            .to(*toAddresses.toTypedArray())
        return if (emailRequest.html) {
            email.htmlMessage(emailRequest.body)
        } else {
            email.textMessage(emailRequest.body)
        }
    }

    private fun renderEmail(emailRequest: EmailRequest): Email {
        return when (emailRequest) {
            is RenderableEmailRequest -> renderEmailTemplate(emailRequest)
            else -> renderPlaintextEmail(emailRequest)
        }
    }

    private fun getToAddresses(emailRequest: EmailRequest): List<String> {
        val toAddresses = emailRequest.to.filter { isNotBlacklisted(it) }
        logger.info("filtered e-mail addresses that are blacklisted.")
        check(toAddresses.isNotEmpty()) { "Must include at least one valid e-mail address." }
        return toAddresses
    }

    // reason: jodd .to() is a vararg API requiring spread; recipient arrays are small
    @Suppress("SpreadOperator")
    private fun renderEmailTemplate(emailRequest: RenderableEmailRequest): Email {
        val toAddresses = getToAddresses(emailRequest)

        val template: String = try {
            TemplateUtils.loadTemplate(emailRequest.templatePath)
        } catch (e: IOException) {
            throw InvalidTemplateException(
                "Invalid Email Template: " + emailRequest.templatePath,
                e
            )
        }

        val templateHtml: String = TemplateUtils.DEFAULT_TEMPLATE_COMPILER
            .compile(template)
            .execute(emailRequest.templateObjs ?: Any())

        val email: Email = Email.create()
            .from(emailRequest.from.orElse(config.defaultFromEmail))
            .subject(emailRequest.subject)
            .htmlMessage(templateHtml)
            .to(*toAddresses.toTypedArray())

        if (emailRequest.byteArrayAttachment.isPresent) {
            val attachments: Array<EmailAttachment<*>> = emailRequest.byteArrayAttachment.get()
            for (attachment in attachments) {
                email.attachment(attachment)
            }

        }
        if (emailRequest.attachmentPaths.isPresent) {
            val paths: Array<String> = emailRequest.attachmentPaths.get()
            for (path in paths) {
                email.attachment(EmailAttachment.with().content(path))
            }
        }

        return email
    }

    // reason: boundary catch — any address-parse failure must fail safe to "blacklisted" without leaking the failure type
    @Suppress("TooGenericExceptionCaught")
    fun isNotBlacklisted(to: String): Boolean {
        return try {
            val parsedAddress = RFC2822AddressParser.STRICT.parse(to)
            !config.domainBlacklist.contains(parsedAddress.domain)
        } catch (ex: Exception) {
            logger.debug("Failed to parse e-mail address for blacklist check: {}", ex.message)
            false
        }
    }
}
