/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.demoiselle.jee.security.basic.impl;

import java.util.logging.Logger;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.demoiselle.jee.core.interfaces.security.DemoisellePrincipal;
import org.demoiselle.jee.core.interfaces.security.Token;
import org.demoiselle.jee.core.interfaces.security.TokensManager;

/**
 *
 * @author 70744416353
 */
@Dependent
public class TokensManagerImpl implements TokensManager {

    @Inject
    private Logger logger;

    @Inject
    private Token token;

    @Inject
    private DemoisellePrincipal loggedUser;

    @Override
    public DemoisellePrincipal getUser() {
        if (loggedUser == null) {
            if (token.getKey() != null && !token.getKey().isEmpty()) {
                // desfaz o basic
                return loggedUser;
            }
        }
        return loggedUser;
    }

    @Override
    public void setUser(DemoisellePrincipal user) {
        String value = null;

    }

    @Override
    public boolean validate() {
        return true;//(getUser() != null && repo.get(token.getKey()).);
    }

}
