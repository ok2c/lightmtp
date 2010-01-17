/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ok2c.lightmtp.impl.protocol.cmd;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ok2c.lightmtp.SMTPCode;
import com.ok2c.lightmtp.SMTPCodes;
import com.ok2c.lightmtp.SMTPCommand;
import com.ok2c.lightmtp.SMTPErrorException;
import com.ok2c.lightmtp.impl.protocol.ServerState;
import com.ok2c.lightmtp.protocol.Action;
import com.ok2c.lightmtp.protocol.CommandHandler;
import com.ok2c.lightmtp.protocol.ProtocolHandler;

public class DefaultProtocolHandler implements ProtocolHandler<ServerState> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final Map<String, CommandHandler<ServerState>> map;

    public DefaultProtocolHandler() {
        super();
        this.map = new HashMap<String, CommandHandler<ServerState>>();
    }

    public void register(final String cmd, final CommandHandler<ServerState> handler) {
        if (cmd == null) {
            throw new IllegalArgumentException("Command name may not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Command handler may not be null");
        }
        this.map.put(cmd.toUpperCase(Locale.US), handler);
    }

    public void unregister(final String cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("Command name may not be null");
        }
        this.map.remove(cmd.toUpperCase(Locale.US));
    }

    public Action<ServerState> handle(final SMTPCommand command) throws SMTPErrorException {
        if (command == null) {
            throw new IllegalArgumentException("Command may not be null");
        }
        String cmd = command.getVerb();
        CommandHandler<ServerState> handler = this.map.get(cmd.toUpperCase(Locale.US));
        if (handler != null) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Command " + command);
            }
            return handler.handle(command.getArgument(), command.getParams());
        } else {
            throw new SMTPErrorException(SMTPCodes.ERR_PERM_SYNTAX_ERR_COMMAND, 
                    new SMTPCode(5, 5, 1),
                    "command not recognized");
        }
    }

}
