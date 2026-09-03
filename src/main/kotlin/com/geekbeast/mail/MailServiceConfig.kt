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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.geekbeast.rhizome.configuration.Configuration
import com.geekbeast.rhizome.configuration.ConfigurationKey
import com.geekbeast.rhizome.configuration.SimpleConfigurationKey
import com.geekbeast.rhizome.configuration.configuration.annotation.ReloadableConfiguration
import com.google.common.collect.Sets

@ReloadableConfiguration(uri = "mail.yaml")
data class MailServiceConfig(
        val smtpHost: String,
        val smtpPort: Int,
        val username: String,
        val password: String,
        val defaultFromEmail: String,
        val domainBlacklist: Set<String> = Sets.newHashSet("someblacklisteddomain.com"),
        val enabled: Boolean = false,
        /**
         * Selects implicit SSL/TLS (SMTPS) vs. plaintext + STARTTLS for the connection.
         * When null (the default, and the shape of every existing mail.yaml), the legacy
         * behavior is preserved: implicit SSL is used iff [smtpPort] == 465, otherwise
         * STARTTLS. Set explicitly to drive implicit SSL on a non-standard SMTPS port
         * (e.g. an embedded test server bound to a high port).
         */
        val useSsl: Boolean? = null,
        /**
         * Extra jakarta-mail session properties layered onto the SMTP connection. Defaults
         * to empty, so production mail.yaml (which never sets it) is unchanged. Used by tests
         * to plug in a trust-all/`DummySSLSocketFactory` via `mail.smtp.ssl.socketFactory`
         * so an embedded SMTPS server with a self-signed cert can be reached. Not read from
         * YAML — purely a programmatic, runtime-only knob.
         */
        @get:JsonIgnore
        val extraSessionProperties: Map<String, Any> = emptyMap()
) : Configuration {

    /**
     * True when the connection should negotiate TLS implicitly on connect (SMTPS).
     * Honors an explicit [useSsl] override, otherwise falls back to the canonical
     * implicit-SSL port 465.
     */
    @JsonIgnore
    fun isImplicitSsl(): Boolean = useSsl ?: (smtpPort == 465)

    @JsonIgnore
    override fun getKey(): ConfigurationKey {
        return Companion.key
    }

    companion object {
        private const val serialVersionUID = -6047689414585379842L
        @JvmField
        val key: ConfigurationKey = SimpleConfigurationKey("mail.yaml")

        @JvmStatic
        fun key(): ConfigurationKey {
            return key
        }
    }
}
