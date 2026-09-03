/*
 * Copyright (C) 2017. OpenLattice, Inc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the owner of the copyright at support@openlattice.com
 *
 */

package com.geekbeast.auth0;

import com.geekbeast.rhizome.pods.ConfigurationLoader;
import com.geekbeast.authentication.Auth0Configuration;
import jakarta.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads JWT authentication configuration from auth0.yaml.
 * Despite the Auth0 naming (kept for backward compatibility), this pod only loads
 * generic JWT configuration -- no Auth0 cloud SDK is used.
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlatice.com&gt;
 */
@Configuration
public class Auth0Pod {

    @Inject
    private ConfigurationLoader configurationLoader;

    @Bean
    public Auth0Configuration auth0Configuration() {
        return configurationLoader.load( Auth0Configuration.class );
    }
}
