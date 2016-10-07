/*
 * Demoiselle Framework
 *
 * License: GNU Lesser General Public License (LGPL), version 3 or later.
 * See the lgpl.txt file in the root directory or <https://www.gnu.org/licenses/lgpl.html>.
 */
package org.demoiselle.jee.core.interfaces.security;

import java.io.Serializable;

public interface TokensManager {

    public DemoisellePrincipal getUser();

    public void setUser(DemoisellePrincipal user);

    public boolean validate();

}